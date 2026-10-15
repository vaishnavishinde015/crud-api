package com.example.crud.server;

import com.example.crud.dao.UserDao;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Registers the REST API routes and starts the HTTP server. */
public final class ApiServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService pool;
    private volatile boolean running;

    public ApiServer(int port) throws IOException {
        this(port, new UserDao());
    }

    public ApiServer(int port, UserDao userDao) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/users", new UserHandler(userDao, "/api/users"));
        server.createContext("/users", new UserHandler(userDao, "/users"));
        server.createContext("/", ApiServer::index);
        pool = Executors.newFixedThreadPool(8, task -> {
            Thread thread = new Thread(task, "api-worker");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(pool);
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        server.start();
        running = true;
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        close();
    }

    @Override
    public synchronized void close() {
        if (running) {
            server.stop(0);
            running = false;
        }
        pool.shutdownNow();
    }

    private static void index(HttpExchange exchange) throws IOException {
        String body = """
                {
                  "service": "core-java-crud-api",
                  "storage": "sqlite (data/app.db)",
                  "endpoints": [
                    "GET    /api/users",
                    "POST   /api/users",
                    "GET    /api/users/{id}",
                    "PUT    /api/users/{id}",
                    "DELETE /api/users/{id}"
                  ]
                }
                """;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
