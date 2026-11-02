package company.vk.edu.distrib.compute.masha533.kv;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.Dao;
import company.vk.edu.distrib.compute.kv.KVService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KVServiceImpl implements KVService {
    private final HttpServer server;
    private final Dao<byte[]> dao;
    private static final String GET_METHOD = "GET";
    private static final String PUT_METHOD = "PUT";
    private static final String DELETE_METHOD = "DELETE";
    private static final int PARTS_COUNT = 2;
    private static final String ID_PARAMETER = "id";
    private static final String ACK_PARAMETER = "ack";
    private final ExecutorService executor;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private final int[] ports;
    private final int replicationFactor;
    private final LocalEntityHandler localHandler;
    private final ReplicationHandler replicationHandler;

    private String extractId(String query) {
        if (query == null) {
            throw new IllegalArgumentException();
        }
        var params = query.split("&");
        for (String param : params) {
            var parts = param.split("=", PARTS_COUNT);
            if (parts.length == PARTS_COUNT && ID_PARAMETER.equals(parts[0])) {
                String id = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                if (id.isEmpty()) {
                    throw new IllegalArgumentException();
                }
                return id;
            }
        }
        throw new IllegalArgumentException();
    }

    private int extractAck(String query, int replicationFactor) {
        if (query == null) {
            return 1;
        }
        var params = query.split("&");
        for (String param : params) {
            var parts = param.split("=", PARTS_COUNT);
            if (ACK_PARAMETER.equals(parts[0])) {
                if (parts.length != PARTS_COUNT) {
                    throw new IllegalArgumentException();
                }
                String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                int ack = Integer.parseInt(value);
                if (ack < 1 || ack > replicationFactor) {
                    throw new IllegalArgumentException();
                }
                return ack;
            }
        }
        return 1;
    }

    private int[] getClusterPorts() {
        String cluster = System.getProperty("CLUSTER");
        if (cluster == null) {
            return ports.clone();
        }
        String[] parts = cluster.split(",");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Integer.parseInt(parts[i]);
        }
        return values;
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }

    private void handleEntity(HttpExchange exchange) throws IOException {
        final var method = exchange.getRequestMethod();
        if (!GET_METHOD.equals(method)
                && !PUT_METHOD.equals(method)
                && !DELETE_METHOD.equals(method)) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        final String id;
        final int ack;
        final int[] replicas;
        try {
            String query = exchange.getRequestURI().getRawQuery();
            int[] clusterPorts = getClusterPorts();
            int currentReplicationFactor;
            if (System.getProperty("CLUSTER") != null) {
                currentReplicationFactor = clusterPorts.length;
            } else {
                currentReplicationFactor = replicationFactor;
            }
            ack = extractAck(query, currentReplicationFactor);
            id = extractId(query);
            ReplicaSelector currentSelector = new ReplicaSelector(clusterPorts);
            replicas = currentSelector.selectReplicas(id, currentReplicationFactor);
        } catch (IllegalArgumentException e) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
        }
        boolean internal = "true".equals(exchange.getRequestHeaders().getFirst("X-Internal-Request"));
        if (internal) {
            if (GET_METHOD.equals(method)) {
                localHandler.handleGet(exchange, id);
            } else if (PUT_METHOD.equals(method)) {
                localHandler.handlePut(exchange, id);
            } else {
                localHandler.handleDelete(exchange, id);
            }
            return;
        }
        if (PUT_METHOD.equals(method)) {
            replicationHandler.handlePut(exchange, id, ack, replicas);
        } else if (DELETE_METHOD.equals(method)) {
            replicationHandler.handleDelete(exchange, id, ack, replicas);
        } else {
            replicationHandler.handleGet(exchange, id, ack, replicas);
        }
    }

    public KVServiceImpl(int port, Dao<byte[]> dao) throws IOException {
        this(port, dao, new int[]{port}, 1);
    }

    public KVServiceImpl(int port, Dao<byte[]> dao, int[] ports, int replicationFactor) throws IOException {
        if (replicationFactor < 1 || replicationFactor > ports.length) {
            throw new IllegalArgumentException();
        }
        this.ports = ports.clone();
        this.replicationFactor = replicationFactor;
        this.dao = dao;
        this.localHandler = new LocalEntityHandler(dao);
        this.replicationHandler = new ReplicationHandler(port, dao, httpClient);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/v0/status", this::handleStatus);
        server.createContext("/v0/entity", this::handleEntity);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public void stop() {
        server.stop(5);
        executor.shutdown();
        try {
            dao.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
