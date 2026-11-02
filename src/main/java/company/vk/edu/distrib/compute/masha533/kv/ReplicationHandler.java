package company.vk.edu.distrib.compute.masha533.kv;

import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.Dao;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ReplicationHandler {
    private final int port;
    private final Dao<byte[]> dao;
    private final HttpClient httpClient;
    private static final int MAX_BODY_SIZE = 1024 * 1024;

    public ReplicationHandler(int port, Dao<byte[]> dao, HttpClient httpClient) {
        this.port = port;
        this.dao = dao;
        this.httpClient = httpClient;
    }

    private boolean waitForAck(
            BlockingQueue<Boolean> results,
            int initialConfirmations,
            int initialRemaining,
            int ack
    ) {
        int confirmations = initialConfirmations;
        int remaining = initialRemaining;
        try {
            while (confirmations < ack && confirmations + remaining >= ack) {
                boolean success = results.take();
                remaining--;
                if (success) {
                    confirmations++;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return confirmations >= ack;
    }

    private boolean isReadConfirmation(int status) {
        return status == 200 || status == 404;
    }

    private ReplicaResult waitForGet(
            BlockingQueue<ReplicaResult> results,
            int initialConfirmations,
            int initialRemaining,
            int ack,
            byte[] initialValue
    ) {
        int confirmations = initialConfirmations;
        int remaining = initialRemaining;
        byte[] value = initialValue;
        try {
            while (confirmations < ack && confirmations + remaining >= ack) {
                ReplicaResult result = results.take();
                remaining--;

                if (result.status() == 200 || result.status() == 404) {
                    confirmations++;
                }
                if (result.status() == 200 && value == null) {
                    value = result.value();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ReplicaResult(503, null);
        }

        if (confirmations < ack) {
            return new ReplicaResult(503, null);
        }
        if (value == null) {
            return new ReplicaResult(404, null);
        }
        return new ReplicaResult(200, value);
    }

    private record ReplicaResult(int status, byte[] value) {
    }

    private ReplicaResult getLocal(String id) {
        try {
            return new ReplicaResult(200, dao.get(id));
        } catch (NoSuchElementException e) {
            return new ReplicaResult(404, null);
        } catch (IOException e) {
            return new ReplicaResult(503, null);
        }
    }

    public void handlePut(HttpExchange exchange, String id, int ack, int... replicas) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_SIZE + 1);
        if (body.length > MAX_BODY_SIZE) {
            exchange.sendResponseHeaders(413, -1);
            exchange.close();
            return;
        }
        int confirmations = 0;
        BlockingQueue<Boolean> results = new LinkedBlockingQueue<>();
        int remoteRequests = 0;
        for (int replicaPort : replicas) {
            if (replicaPort == port) {
                try {
                    dao.upsert(id, body);
                    confirmations++;
                } catch (IOException e) {
                    //no
                }
            } else {
                String encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8);
                URI uri = URI.create(
                        "http://localhost:" + replicaPort + "/v0/entity?id=" + encodedId
                );
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(1))
                        .header("X-Internal-Request", "true")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                        .whenComplete((response, error) -> {
                            boolean success = error == null && response.statusCode() == 201;
                            results.add(success);
                        });
                remoteRequests++;
            }
        }
        if (waitForAck(results, confirmations, remoteRequests, ack)) {
            exchange.sendResponseHeaders(201, -1);
        } else {
            exchange.sendResponseHeaders(503, -1);
        }
        exchange.close();
    }

    public void handleDelete(HttpExchange exchange, String id, int ack, int... replicas) throws IOException {
        int confirmations = 0;
        BlockingQueue<Boolean> results = new LinkedBlockingQueue<>();
        int remoteRequests = 0;
        for (int replicaPort : replicas) {
            if (replicaPort == port) {
                try {
                    dao.delete(id);
                    confirmations++;
                } catch (IOException e) {
                    //no
                }
            } else {
                String encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8);
                URI uri = URI.create(
                        "http://localhost:" + replicaPort + "/v0/entity?id=" + encodedId
                );
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(1))
                        .header("X-Internal-Request", "true")
                        .DELETE()
                        .build();
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                        .whenComplete((response, error) -> {
                            boolean success = error == null && response.statusCode() == 202;
                            results.add(success);
                        });
                remoteRequests++;
            }
        }
        if (waitForAck(results, confirmations, remoteRequests, ack)) {
            exchange.sendResponseHeaders(202, -1);
        } else {
            exchange.sendResponseHeaders(503, -1);
        }
        exchange.close();
    }

    public void handleGet(HttpExchange exchange, String id, int ack, int... replicas) throws IOException {
        int confirmations = 0;
        byte[] value = null;
        BlockingQueue<ReplicaResult> results = new LinkedBlockingQueue<>();
        int remoteRequests = 0;
        for (int replicaPort : replicas) {
            if (replicaPort == port) {
                ReplicaResult localResult = getLocal(id);
                if (isReadConfirmation(localResult.status())) {
                    confirmations++;
                }
                value = localResult.value();
            } else {
                String encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8);
                URI uri = URI.create("http://localhost:" + replicaPort + "/v0/entity?id=" + encodedId);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(1))
                        .header("X-Internal-Request", "true")
                        .GET()
                        .build();
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                        .whenComplete((response, error) -> {
                            if (error != null) {
                                results.add(new ReplicaResult(503, null));
                            } else {
                                results.add(new ReplicaResult(response.statusCode(), response.body()));
                            }
                        });
                remoteRequests++;
            }
        }
        ReplicaResult result = waitForGet(results, confirmations, remoteRequests, ack, value);
        if (result.status() == 200) {
            byte[] responseBody = result.value();
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
        } else {
            exchange.sendResponseHeaders(result.status(), -1);
        }
        exchange.close();
    }
}
