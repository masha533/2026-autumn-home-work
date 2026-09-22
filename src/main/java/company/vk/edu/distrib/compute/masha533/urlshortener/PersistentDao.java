package company.vk.edu.distrib.compute.masha533.urlshortener;

import company.vk.edu.distrib.compute.Dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Properties;

public class PersistentDao implements Dao<String> {
    private final Path file;
    private final Map<String, String> storage = new ConcurrentHashMap<>();

    public PersistentDao(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            try (var input = Files.newInputStream(file)) {
                var properties = new Properties();
                properties.load(input);
                for (String key : properties.stringPropertyNames()) {
                    var value = properties.getProperty(key);
                    storage.put(key, value);
                }
            }
        }
    }

    private void save() throws IOException {
        var properties = new Properties();
        for (var entry : storage.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            properties.setProperty(key, value);
        }
        try (var output = Files.newOutputStream(file)) {
            properties.store(output, null);
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
        storage.put(key, value);
        save();
    }

    @Override
    public void delete(String key) throws IOException {
        storage.remove(key);
        save();
    }

    @Override
    public void close() throws IOException {
        save();
    }

}
