package com.example.crud.model;

/** Represents one row in the users table and one user in API responses. */
public class User {
    private long id;
    private String name;
    private String email;
    private int age;

    public User() {
    }

    public User(String name, String email, int age) {
        this(0, name, email, age);
    }

    public User(long id, String name, String email, int age) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.age = age;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return String.format("User{id=%d, name='%s', email='%s', age=%d}", id, name, email, age);
    }
}
