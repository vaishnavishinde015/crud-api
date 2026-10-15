package com.example.crud.cli;

import com.example.crud.dao.UserDao;
import com.example.crud.model.User;
import com.example.crud.server.ApiServer;
import com.example.crud.util.Json;

import java.net.BindException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

/** Optional text menu for managing users directly or trying the REST API locally. */
public final class Cli {
    private static final String LINE = "=".repeat(64);

    private final Scanner in = new Scanner(System.in);
    private final UserDao dao;
    private final HttpClient http = HttpClient.newHttpClient();
    private ApiServer server;

    public Cli() {
        this(new UserDao());
    }

    public Cli(UserDao dao) {
        this.dao = dao;
    }

    public void run() {
        banner();
        boolean running = true;

        while (running) {
            menu();
            String choice = ask("Option");
            try {
                switch (choice) {
                    case "1":
                        listUsers();
                        break;
                    case "2":
                        getUser();
                        break;
                    case "3":
                        createUser();
                        break;
                    case "4":
                        updateUser();
                        break;
                    case "5":
                        deleteUser();
                        break;
                    case "6":
                        startServer();
                        break;
                    case "7":
                        stopServer();
                        break;
                    case "8":
                        apiClient();
                        break;
                    case "0":
                    case "q":
                    case "quit":
                    case "exit":
                        running = false;
                        break;
                    default:
                        warn("Unknown option '" + choice + "'.");
                }
            } catch (SQLException e) {
                warn("Database error: " + e.getMessage());
            } catch (Exception e) {
                warn(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        stopServer();
        System.out.println("\nGoodbye.");
    }

    private void banner() {
        System.out.println(LINE);
        System.out.println("  Core-Java CRUD REST API  |  SQLite local storage  |  CLI");
        System.out.println(LINE);
    }

    private void menu() {
        System.out.println();
        System.out.println("  ---- Direct database (CLI) ----");
        System.out.println("   1) List all users");
        System.out.println("   2) Find user by id");
        System.out.println("   3) Create user");
        System.out.println("   4) Update user");
        System.out.println("   5) Delete user");
        System.out.println("  ---- REST server ----");
        System.out.println("   6) Start REST API server   " +
                (server != null ? "[running on port " + server.getPort() + "]" : "[stopped]"));
        System.out.println("   7) Stop REST API server");
        System.out.println("   8) Call the API (HTTP client)");
        System.out.println("   0) Quit");
    }

    private String ask(String label) {
        System.out.print("  " + label + ": ");
        System.out.flush();
        if (!in.hasNextLine()) {
            System.out.println();
            return "0";
        }
        return in.nextLine().trim();
    }

    private String askRequired(String label) {
        while (true) {
            String value = ask(label);
            if (!value.isEmpty()) {
                return value;
            }
            warn(label + " cannot be empty.");
        }
    }

    private String askKeepingCurrent(String label, String current) {
        String value = ask(label + " [" + current + "]");
        return value.isEmpty() ? current : value;
    }

    private long askId(String label) {
        while (true) {
            String value = ask(label);
            try {
                long id = Long.parseLong(value);
                if (id > 0) {
                    return id;
                }
            } catch (NumberFormatException e) {
                warn("Please type a positive whole-number ID.");
                continue;
            }
            warn("Please type a positive whole-number ID.");
        }
    }

    private int askAge(int current) {
        while (true) {
            String prompt = current < 0 ? "Age" : "Age [" + current + "]";
            String value = ask(prompt);
            if (value.isEmpty()) {
                if (current >= 0) {
                    return current;
                }
                warn("Age is required.");
                continue;
            }
            try {
                int age = Integer.parseInt(value);
                if (age >= 0 && age <= 150) {
                    return age;
                }
                warn("Age must be between 0 and 150.");
            } catch (NumberFormatException e) {
                warn("Please type a whole number.");
            }
        }
    }

    private boolean looksLikeEmail(String value) {
        int at = value.indexOf('@');
        return at > 0 && at < value.length() - 1 && value.indexOf(' ') < 0;
    }

    private String askEmail() {
        while (true) {
            String email = ask("Email");
            if (looksLikeEmail(email)) {
                return email;
            }
            warn("That doesn't look like an email address. Try again.");
        }
    }

    private String askKeepingEmail(String current) {
        while (true) {
            String email = ask("Email [" + current + "]");
            if (email.isEmpty()) {
                return current;
            }
            if (looksLikeEmail(email)) {
                return email;
            }
            warn("That doesn't look like an email address. Try again.");
        }
    }

    private void ok(String message) {
        System.out.println("  [OK]   " + message);
    }

    private void warn(String message) {
        System.out.println("  [!]    " + message);
    }

    private void listUsers() throws SQLException {
        List<User> users = dao.findAll();
        System.out.println();
        if (users.isEmpty()) {
            System.out.println("  (no users yet)");
            return;
        }
        System.out.printf("  %-5s %-20s %-30s %s%n", "ID", "NAME", "EMAIL", "AGE");
        System.out.println("  " + "-".repeat(62));
        for (User user : users) {
            System.out.printf("  %-5d %-20s %-30s %d%n",
                    user.getId(), user.getName(), user.getEmail(), user.getAge());
        }
        System.out.println("  " + users.size() + " row(s).");
    }

    private void getUser() throws SQLException {
        long id = askId("User id");
        Optional<User> user = dao.findById(id);
        if (user.isEmpty()) {
            warn("No user with id " + id + ".");
            return;
        }
        printUser(user.get());
    }

    private void createUser() throws SQLException {
        System.out.println("\n  -- New user --");
        String name = askRequired("Name");
        String email = askEmail();
        int age = askAge(-1);

        try {
            User created = dao.insert(new User(name, email, age));
            ok("Created user #" + created.getId());
            printUser(created);
        } catch (SQLException e) {
            if (String.valueOf(e.getMessage()).contains("UNIQUE")) {
                warn("That email is already in use.");
                return;
            }
            throw e;
        }
    }

    private void updateUser() throws SQLException {
        long id = askId("User id to update");
        Optional<User> found = dao.findById(id);
        if (found.isEmpty()) {
            warn("No user with id " + id + ".");
            return;
        }
        User user = found.get();

        System.out.println("  Current:");
        printUser(user);
        System.out.println("  (press Enter to keep the current value)");

        String name = askKeepingCurrent("Name", user.getName());
        if (name.trim().isEmpty()) {
            warn("Name cannot be empty.");
            return;
        }
        String email = askKeepingEmail(user.getEmail());
        int age = askAge(user.getAge());

        try {
            if (!dao.update(id, new User(id, name, email, age))) {
                warn("User disappeared before the update.");
                return;
            }
        } catch (SQLException e) {
            if (String.valueOf(e.getMessage()).contains("UNIQUE")) {
                warn("That email is already used by another user.");
                return;
            }
            throw e;
        }

        ok("Updated user #" + id);
        printUser(dao.findById(id).orElseThrow());
    }

    private void deleteUser() throws SQLException {
        long id = askId("User id to delete");
        Optional<User> found = dao.findById(id);
        if (found.isEmpty()) {
            warn("No user with id " + id + ".");
            return;
        }

        printUser(found.get());
        if (!ask("Really delete? (y/N)").equalsIgnoreCase("y")) {
            System.out.println("  Cancelled.");
            return;
        }
        if (dao.delete(id)) {
            ok("Deleted user #" + id);
        } else {
            warn("User disappeared before deletion.");
        }
    }

    private void printUser(User user) {
        System.out.println("  +--------------------------------------------+");
        System.out.printf("  | id    : %-34d |%n", user.getId());
        System.out.printf("  | name  : %-34s |%n", user.getName());
        System.out.printf("  | email : %-34s |%n", user.getEmail());
        System.out.printf("  | age   : %-34d |%n", user.getAge());
        System.out.println("  +--------------------------------------------+");
    }

    private void startServer() throws Exception {
        if (server != null) {
            warn("Server already running on port " + server.getPort() + ".");
            return;
        }

        int port = (int) askPort();
        ApiServer candidate;
        try {
            candidate = new ApiServer(port);
        } catch (BindException e) {
            warn("Port " + port + " is already in use. Try another one.");
            return;
        }
        try {
            candidate.start();
        } catch (RuntimeException e) {
            candidate.close();
            throw e;
        }
        server = candidate;
        ok("REST API listening on http://localhost:" + server.getPort() + "/api/users");
        System.out.println("     Try: curl http://localhost:" + server.getPort() + "/api/users");
    }

    private long askPort() {
        while (true) {
            String value = ask("Port [8080]");
            if (value.isEmpty()) {
                return 8080;
            }
            try {
                long port = Long.parseLong(value);
                if (port >= 0 && port <= 65535) {
                    return port;
                }
            } catch (NumberFormatException e) {
                warn("Port must be a number from 0 to 65535.");
                continue;
            }
            warn("Port must be a number from 0 to 65535.");
        }
    }

    private void stopServer() {
        if (server == null) {
            return;
        }
        server.stop();
        server = null;
        ok("Server stopped.");
    }

    private void apiClient() throws Exception {
        if (server == null) {
            warn("The REST server is not running. Use option 6 first.");
            return;
        }
        String base = "http://localhost:" + server.getPort();
        System.out.println("\n  -- HTTP client --  target " + base);
        System.out.println("   1) GET    /api/users");
        System.out.println("   2) GET    /api/users/{id}");
        System.out.println("   3) POST   /api/users");
        System.out.println("   4) PUT    /api/users/{id}");
        System.out.println("   5) DELETE /api/users/{id}");
        System.out.println("   0) Back");

        switch (ask("Action")) {
            case "1":
                System.out.println(send("GET", base + "/api/users", null));
                break;
            case "2": {
                long id = askId("id");
                System.out.println(send("GET", base + "/api/users/" + id, null));
                break;
            }
            case "3": {
                String name = askRequired("name");
                String email = askEmail();
                int age = askAge(-1);
                String body = Json.stringify(Map.of("name", name, "email", email, "age", age));
                System.out.println(send("POST", base + "/api/users", body));
                break;
            }
            case "4": {
                long id = askId("id");
                String name = askRequired("name");
                String email = askEmail();
                int age = askAge(-1);
                String body = Json.stringify(Map.of("name", name, "email", email, "age", age));
                System.out.println(send("PUT", base + "/api/users/" + id, body));
                break;
            }
            case "5": {
                long id = askId("id");
                System.out.println(send("DELETE", base + "/api/users/" + id, null));
                break;
            }
            case "0":
                break;
            default:
                warn("Unknown action.");
        }
    }

    private String send(String method, String url, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json");
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = http.send(
                builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        StringBuilder output = new StringBuilder()
                .append("  HTTP ").append(response.statusCode()).append("  ")
                .append(method).append(' ').append(url).append('\n');
        String body = response.body();
        if (body == null || body.isBlank()) {
            output.append("  (empty body)");
        } else {
            try {
                output.append("  ").append(Json.pretty(body).replace("\n", "\n  "));
            } catch (IllegalArgumentException e) {
                output.append("  ").append(body);
            }
        }
        return output.toString();
    }
}
