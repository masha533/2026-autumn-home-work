package company.vk.edu.distrib.compute.masha533.kv;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ReplicaSelector {
    private final int[] ports;

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

    public int[] selectReplicas(String key, int count) {
        if (key == null || key.isEmpty() || count < 1 || count > ports.length) {
            throw new IllegalArgumentException();
        }
        int[] replicas = new int[count];
        boolean[] selected = new boolean[ports.length];
        for (int replica = 0; replica < count; replica++) {
            int bestIndex = -1;
            long bestHash = Long.MIN_VALUE;

            for (int i = 0; i < ports.length; i++) {
                if (selected[i]) {
                    continue;
                }
                long currentHash = hash(key, ports[i]);
                if (bestIndex == -1
                        || currentHash > bestHash
                        || (currentHash == bestHash && ports[i] < ports[bestIndex])) {
                    bestIndex = i;
                    bestHash = currentHash;
                }
            }
            replicas[replica] = ports[bestIndex];
            selected[bestIndex] = true;
        }

        return replicas;
    }

    ReplicaSelector(int... ports) {
        if (ports.length == 0) {
            throw new IllegalArgumentException();
        }
        this.ports = ports.clone();
    }
}
