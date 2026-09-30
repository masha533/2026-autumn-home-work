package company.vk.edu.distrib.compute.masha533.urlshortener;

import company.vk.edu.distrib.compute.Dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.nio.file.StandardOpenOption;

public class PersistentDao implements Dao<String> {
    private final Path file;
    private final Map<String, String> storage = new ConcurrentHashMap<>();
    private static final byte UPSERT = 0;
    private static final byte DELETE = 1;

    public PersistentDao(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            try (var input = new DataInputStream(Files.newInputStream(file))) {
                while (true) {
                    try {
                        byte op = input.readByte();
                        String key = input.readUTF();

                        if (op == UPSERT) {
                            String value = input.readUTF();
                            storage.put(key, value);
                        } else if (op == DELETE) {
                            storage.remove(key);
                        }
                    } catch (EOFException e) {
                        break;
                    }
                }
            }
        }
    }

    @Override
    public String get(String key) throws NoSuchElementException {
        String value = storage.get(key);
        if (value == null) {
            throw new NoSuchElementException();
        }

        return value;
    }

    @Override
    public void upsert(String key, String value) throws IOException {
        try (var output = new DataOutputStream(
            Files.newOutputStream(
                file,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        )) {
            output.writeByte(UPSERT);
            output.writeUTF(key);
            output.writeUTF(value);
        }
        storage.put(key, value);
    }

    @Override
    public void delete(String key) throws IOException {
        try (var output = new DataOutputStream(
            Files.newOutputStream(
                file,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        )) {
            output.writeByte(DELETE);
            output.writeUTF(key);
        }
        storage.remove(key);
    }

    @Override
    public void close() throws IOException {
        //
    }

}
