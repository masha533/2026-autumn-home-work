package company.vk.edu.distrib.compute.masha533.kv;

import com.sun.net.httpserver.HttpExchange;
import company.vk.edu.distrib.compute.Dao;
import java.io.IOException;
import java.util.NoSuchElementException;

public class LocalEntityHandler {
    private static final int MAX_BODY_SIZE = 1024 * 1024;
    private final Dao<byte[]> dao;

    public LocalEntityHandler(Dao<byte[]> dao) {
        this.dao = dao;
    }

    public void handleGet(HttpExchange exchange, String id) throws IOException {
        try {
            byte[] value = dao.get(id);
            exchange.sendResponseHeaders(200, value.length);
            exchange.getResponseBody().write(value);
            exchange.close();
        } catch (NoSuchElementException e) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        }
    }

    public void handlePut(HttpExchange exchange, String id) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_SIZE + 1);
        if (body.length > MAX_BODY_SIZE) {
            exchange.sendResponseHeaders(413, -1);
            exchange.close();
            return;
        }
        dao.upsert(id, body);
        exchange.sendResponseHeaders(201, -1);
        exchange.close();
    }

    public void handleDelete(HttpExchange exchange, String id) throws IOException {
        dao.delete(id);
        exchange.sendResponseHeaders(202, -1);
        exchange.close();
    }
}
