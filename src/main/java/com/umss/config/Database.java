package com.umss.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {
    private static final String DB_URL = "jdbc:postgresql://ep-spring-darkness-b5tyu6bg-pooler.c-7.us-east-2.aws.neon.tech/neondb?user=neondb_owner&password=npg_1fHyclUhnTx3&sslmode=require";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }
}
