package com.example.crud.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the SQLite database and provides connections for database operations. */
public final class Database {
    private static final String DB_DIR = "data";
    private static final String DB_FILE = DB_DIR + File.separator + "app.db";
    private static final String URL = "jdbc:sqlite:" + DB_FILE;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "SQLite JDBC driver missing. Put sqlite-jdbc-3.42.0.0.jar on the classpath.", e);
        }
    }

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            return connection;
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                e.addSuppressed(closeException);
            }
            throw e;
        }
    }

    public static void init() {
        File directory = new File(DB_DIR);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create directory: " + directory.getAbsolutePath());
        }

        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "email TEXT NOT NULL UNIQUE, " +
                "age INTEGER NOT NULL" +
                ")";

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            if (!hasAgeColumn(connection)) {
                statement.execute("ALTER TABLE users ADD COLUMN age INTEGER NOT NULL DEFAULT 0");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialise database", e);
        }
    }

    private static boolean hasAgeColumn(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(users)")) {
            while (columns.next()) {
                if ("age".equalsIgnoreCase(columns.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
