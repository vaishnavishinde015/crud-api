package com.example.crud.dao;

import com.example.crud.db.Database;
import com.example.crud.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Contains the SQL statements used to create, read, update, and delete users. */
public final class UserDao {
    /** Returns every user, ordered by ID. */
    public List<User> findAll() throws SQLException {
        String sql = "SELECT id, name, email, age FROM users ORDER BY id";
        List<User> users = new ArrayList<User>();
        try (Connection connection = Database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                users.add(readUser(resultSet));
            }
        }
        return users;
    }

    /** Finds one user by ID, or returns an empty result when the ID does not exist. */
    public Optional<User> findById(long id) throws SQLException {
        String sql = "SELECT id, name, email, age FROM users WHERE id = ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readUser(resultSet)) : Optional.<User>empty();
            }
        }
    }

    /** Inserts a user and returns it with the ID assigned by SQLite. */
    public User insert(User user) throws SQLException {
        String sql = "INSERT INTO users (name, email, age) VALUES (?, ?, ?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.getName());
            statement.setString(2, user.getEmail());
            statement.setInt(3, user.getAge());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getLong(1));
                    return user;
                }
            }
        }

        throw new SQLException("The database did not return an ID for the new user.");
    }

    public User create(String name, String email, int age) throws SQLException {
        return insert(new User(name, email, age));
    }

    /** Replaces a user's name, email, and age. Returns false if the ID does not exist. */
    public boolean update(long id, User user) throws SQLException {
        String sql = "UPDATE users SET name = ?, email = ?, age = ? WHERE id = ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.getName());
            statement.setString(2, user.getEmail());
            statement.setInt(3, user.getAge());
            statement.setLong(4, id);
            return statement.executeUpdate() > 0;
        }
    }

    public Optional<User> update(long id, String name, String email, int age) throws SQLException {
        if (!update(id, new User(name, email, age))) {
            return Optional.empty();
        }
        return findById(id);
    }

    /** Deletes a user and returns false if the ID does not exist. */
    public boolean delete(long id) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    private User readUser(ResultSet resultSet) throws SQLException {
        return new User(resultSet.getLong("id"), resultSet.getString("name"), resultSet.getString("email"),
                resultSet.getInt("age"));
    }
}
