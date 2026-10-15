package com.example.crud.server;

import com.example.crud.dao.UserDao;
import com.example.crud.model.User;
import com.example.crud.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Validates HTTP requests and connects the user routes to the DAO. */
public final class UserHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(UserHandler.class.getName());
    private static final int MAX_REQUEST_BYTES = 1024 * 1024;

    private final UserDao userDao;
    private final String basePath;

    public UserHandler(UserDao userDao) {
        this(userDao, "/users");
    }

    public UserHandler(UserDao userDao, String basePath) {
        this.userDao = userDao;
        this.basePath = basePath;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String suffix = path.substring(basePath.length());
            if (!suffix.isEmpty() && !suffix.startsWith("/")) {
                sendError(exchange, 404, "Not found.");
                return;
            }

            if (suffix.isEmpty() || "/".equals(suffix)) {
                handleCollection(exchange);
                return;
            }

            String idPart = suffix.substring(1);
            if (idPart.isEmpty() || idPart.contains("/")) {
                sendError(exchange, 404, "Not found.");
                return;
            }
            long id = parseId(idPart);
            handleUser(exchange, id);
        } catch (PayloadTooLargeException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (SQLException e) {
            if (e.getErrorCode() == 19) {
                sendError(exchange, 409, "A user with that email already exists.");
            } else {
                LOGGER.log(Level.SEVERE, "Database operation failed.", e);
                sendError(exchange, 500, "Internal server error.");
            }
        } finally {
            exchange.close();
        }
    }

    private void handleCollection(HttpExchange exchange) throws IOException, SQLException {
        String method = exchange.getRequestMethod();
        if ("GET".equals(method)) {
            List<Object> users = new ArrayList<Object>();
            for (User user : userDao.findAll()) {
                users.add(toJson(user));
            }
            sendJson(exchange, 200, users);
        } else if ("POST".equals(method)) {
            UserInput input = readUserInput(exchange);
            User user = userDao.create(input.name, input.email, input.age);
            exchange.getResponseHeaders().set("Location", basePath + "/" + user.getId());
            sendJson(exchange, 201, toJson(user));
        } else {
            exchange.getResponseHeaders().set("Allow", "GET, POST");
            sendError(exchange, 405, "Method not allowed.");
        }
    }

    private void handleUser(HttpExchange exchange, long id) throws IOException, SQLException {
        String method = exchange.getRequestMethod();
        if ("GET".equals(method)) {
            Optional<User> user = userDao.findById(id);
            if (user.isPresent()) {
                sendJson(exchange, 200, toJson(user.get()));
            } else {
                sendError(exchange, 404, "User not found.");
            }
        } else if ("PUT".equals(method)) {
            UserInput input = readUserInput(exchange);
            Optional<User> user = userDao.update(id, input.name, input.email, input.age);
            if (user.isPresent()) {
                sendJson(exchange, 200, toJson(user.get()));
            } else {
                sendError(exchange, 404, "User not found.");
            }
        } else if ("DELETE".equals(method)) {
            if (userDao.delete(id)) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                sendError(exchange, 404, "User not found.");
            }
        } else {
            exchange.getResponseHeaders().set("Allow", "GET, PUT, DELETE");
            sendError(exchange, 405, "Method not allowed.");
        }
    }

    private UserInput readUserInput(HttpExchange exchange) throws IOException {
        byte[] body;
        try (InputStream input = exchange.getRequestBody()) {
            body = input.readNBytes(MAX_REQUEST_BYTES + 1);
        }
        if (body.length > MAX_REQUEST_BYTES) {
            throw new PayloadTooLargeException();
        }
        Object parsed = Json.parse(new String(body, StandardCharsets.UTF_8));
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Request body must be a JSON object.");
        }
        Map<?, ?> values = (Map<?, ?>) parsed;
        Object name = values.get("name");
        Object email = values.get("email");
        Object age = values.get("age");
        if (!(name instanceof String) || ((String) name).trim().isEmpty()) {
            throw new IllegalArgumentException("'name' must be a non-empty string.");
        }
        if (!(email instanceof String) || !((String) email).trim().contains("@")) {
            throw new IllegalArgumentException("'email' must be a valid address.");
        }
        if (!(age instanceof Long) || ((Long) age) < 0 || ((Long) age) > 150) {
            throw new IllegalArgumentException("'age' must be between 0 and 150.");
        }
        return new UserInput(((String) name).trim(), ((String) email).trim(), ((Long) age).intValue());
    }

    private long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new IllegalArgumentException("User ID must be a positive integer.");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("User ID must be a positive integer.");
        }
    }

    private Map<String, Object> toJson(User user) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("id", user.getId());
        values.put("name", user.getName());
        values.put("email", user.getEmail());
        values.put("age", user.getAge());
        return values;
    }

    private void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = Json.stringify(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("error", message);
        sendJson(exchange, status, error);
    }

    private static final class UserInput {
        private final String name;
        private final String email;
        private final int age;

        private UserInput(String name, String email, int age) {
            this.name = name;
            this.email = email;
            this.age = age;
        }
    }

    private static final class PayloadTooLargeException extends IllegalArgumentException {
        private PayloadTooLargeException() {
            super("Request body exceeds the 1 MiB limit.");
        }
    }
}
