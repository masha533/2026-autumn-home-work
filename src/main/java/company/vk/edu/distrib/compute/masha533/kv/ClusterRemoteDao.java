package company.vk.edu.distrib.compute.masha533.kv;

import company.vk.edu.distrib.compute.Dao;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ClusterRemoteDao implements Dao<String> {

    private final int[] ports;
    private final RemoteDao[] nodes;

    private long hash(String key, int port) {
        String nodeKey = key + ":" + port;
        byte[] input = nodeKey.getBytes(StandardCharsets.UTF_8);

        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] result = sha256.digest(input);
            return ByteBuffer.wrap(result).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    ClusterRemoteDao(int... ports) {
        if (ports.length == 0) {
            throw new IllegalArgumentException();
        }
        this.ports = ports.clone();
        this.nodes = new RemoteDao[ports.length];
        for (int i = 0; i < ports.length; i++) {
            nodes[i] = new RemoteDao(ports[i]);
        }
    }

    private RemoteDao selectNode(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException();
        }
        int bestIndex = 0;
        long bestHash = Long.MIN_VALUE;
        for (int i = 0; i < ports.length; i++) {
            long hash = hash(key, ports[i]);
            if ((hash > bestHash) || (hash == bestHash && ports[i] < ports[bestIndex])) {
                bestHash = hash;
                bestIndex = i;
            }
        }
        return nodes[bestIndex];
    }

    @Override
    public String get(String key) throws IOException {
        return selectNode(key).get(key);
    }

    @Override
    public void upsert(String key, String value) throws IOException {
        selectNode(key).upsert(key, value);
    }

    @Override
    public void delete(String key) throws IOException {
        selectNode(key).delete(key);
    }

    @Override
    public void close() {
        for (RemoteDao node : nodes) {
            node.close();
        }
    }

}
