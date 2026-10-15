package com.example.crud;

import com.example.crud.cli.Cli;
import com.example.crud.db.Database;
import com.example.crud.server.ApiServer;

import java.util.concurrent.CountDownLatch;

public final class Main {
    private Main() {
    }

    /** Initializes the database, then starts either the CLI or the REST server. */
    public static void main(String[] args) throws Exception {
        Database.init();

        if (args.length > 0 && ("server".equalsIgnoreCase(args[0]) || "--server".equalsIgnoreCase(args[0]))) {
            if (args.length > 2) {
                printUsage();
                return;
            }
            int port = args.length == 2 ? Integer.parseInt(args[1]) : 8080;
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 0 and 65535.");
            }

            ApiServer server = new ApiServer(port);
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            server.start();
            System.out.println("REST API running on http://localhost:" + server.getPort() + "/api/users");
            System.out.println("Press Ctrl+C to stop.");
            new CountDownLatch(1).await();
            return;
        }

        if (args.length > 0 && "--help".equals(args[0])) {
            printUsage();
            return;
        }
        if (args.length > 0) {
            printUsage();
            throw new IllegalArgumentException("Unknown command: " + args[0]);
        }

        new Cli().run();
    }

    private static void printUsage() {
        System.out.println("Usage: java com.example.crud.Main [server [port] | --server [port] | --help]");
        System.out.println("Without arguments, the interactive CLI is started.");
    }
}
