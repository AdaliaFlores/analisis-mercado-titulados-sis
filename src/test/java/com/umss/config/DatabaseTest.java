package com.umss.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica la clasificación de errores usada para reintentar la conexión
 * cuando Neon está despertando. No requiere una base de datos real.
 */
class DatabaseTest {

    @Test
    @DisplayName("Los errores de conexión de arranque en frío se reintentan")
    void erroresDeConexionSonTransitorios() {
        assertTrue(Database.esErrorTransitorio(
                new SQLException("The connection attempt failed.", "08001")));
        assertTrue(Database.esErrorTransitorio(
                new SQLException("FATAL: the database system is starting up", "57P03")));
        assertTrue(Database.esErrorTransitorio(
                new SQLException("Connection refused")));
        assertTrue(Database.esErrorTransitorio(
                new RuntimeException(new SQLException("Connection reset"))));
    }

    @Test
    @DisplayName("Los errores de configuración o credenciales no se reintentan")
    void erroresPermanentesNoSonTransitorios() {
        assertFalse(Database.esErrorTransitorio(
                new SQLException("No suitable driver found for jdbc:postgresql://host/db", "08001")));
        assertFalse(Database.esErrorTransitorio(
                new SQLException("FATAL: password authentication failed for user \"demo\"", "28P01")));
        assertFalse(Database.esErrorTransitorio(
                new SQLException("syntax error at or near \"SELEC\"", "42601")));
    }
}
