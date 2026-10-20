package com.umss.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {

    public static Connection getConnection() throws SQLException {
        String dbUrl = System.getenv("DB_URL");

        if (dbUrl == null || dbUrl.isBlank()) {
            throw new SQLException(
                "Falta configurar la variable de entorno DB_URL"
            );
        }

        return DriverManager.getConnection(dbUrl);
    }
}

