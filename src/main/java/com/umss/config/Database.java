package com.umss.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {

    public static Connection getConnection() throws SQLException {
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USER");
        String dbPassword = System.getenv("DB_PASSWORD");

        if (dbUrl == null || dbUser == null || dbPassword == null) {
            throw new SQLException(
                "Faltan configurar DB_URL, DB_USER o DB_PASSWORD"
            );
        }

        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }
}
