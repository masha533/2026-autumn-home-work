package company.vk.edu.distrib.compute.masha533.kv;

import company.vk.edu.distrib.compute.Dao;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

public class RemoteDao implements Dao<String> {
    private final int port;
    private final HttpClient client;

    public RemoteDao(int port) {
        this.port = port;
        this.client = HttpClient.newHttpClient();
    }

    @Override
    public String get(String key) throws NoSuchElementException, IllegalArgumentException, IOException {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException();
        }
        var encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
        String link = "http://localhost:" + port + "/v0/entity?id=" + encodedKey;
        var request = HttpRequest.newBuilder().uri(URI.create(link)).GET().build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                return response.body();
            } else if (response.statusCode() == 404) {
                throw new NoSuchElementException();
            } else {
                throw new IOException();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    @Override
    public void upsert(String key, String value) throws IOException {
        if (value == null || key == null || key.isEmpty()) {
            throw new IllegalArgumentException();
        }
        var encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
        String link = "http://localhost:" + port + "/v0/entity?id=" + encodedKey;
        var request = HttpRequest.newBuilder()
                .uri(URI.create(link))
                .PUT(HttpRequest.BodyPublishers.ofString(value, StandardCharsets.UTF_8))
                .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 201) {
                throw new IOException();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException();
        }
        var encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
        String link = "http://localhost:" + port + "/v0/entity?id=" + encodedKey;
        var request = HttpRequest.newBuilder().uri(URI.create(link)).DELETE().build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 202) {
                throw new IOException();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
