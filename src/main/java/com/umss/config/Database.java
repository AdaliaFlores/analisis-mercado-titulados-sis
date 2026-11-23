package com.umss.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

/**
 * Obtiene conexiones JDBC a PostgreSQL (Neon).
 *
 * Neon suspende la instancia cuando no recibe tráfico; la primera conexión
 * después de ese periodo puede tardar varios segundos o fallar con un error
 * transitorio. Para que los reportes no fallen durante ese arranque en frío se
 * configuran tiempos de espera explícitos y se reintenta la conexión.
 */
public final class Database {

    /** Tiempo máximo para establecer la conexión (segundos). */
    private static final int CONNECT_TIMEOUT_SEGUNDOS =
            Math.max(1, leerEntero("DB_CONNECT_TIMEOUT", 12));

    /** Reintentos adicionales ante errores transitorios (arranque en frío). */
    private static final int REINTENTOS =
            Math.min(leerEntero("DB_CONNECT_RETRIES", 2), 5);

    /** Espera entre reintentos (milisegundos). */
    private static final long ESPERA_REINTENTO_MS =
            leerEntero("DB_RETRY_DELAY_MS", 1000);

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USER");
        String dbPassword = System.getenv("DB_PASSWORD");

        if (dbUrl == null || dbUser == null || dbPassword == null) {
            throw new SQLException(
                "Faltan configurar DB_URL, DB_USER o DB_PASSWORD"
            );
        }

        SQLException ultimoError = null;
        for (int intento = 0; intento <= REINTENTOS; intento++) {
            try {
                return DriverManager.getConnection(
                        dbUrl, propiedadesConexion(dbUser, dbPassword));
            } catch (SQLException e) {
                ultimoError = e;
                if (intento >= REINTENTOS || !esErrorTransitorio(e)) {
                    throw e;
                }
                System.err.println("[Database] Conexión fallida (intento "
                        + (intento + 1) + " de " + (REINTENTOS + 1) + "): "
                        + e.getMessage() + ". Reintentando en "
                        + ESPERA_REINTENTO_MS + " ms.");
                esperar(ESPERA_REINTENTO_MS, e);
            }
        }
        throw ultimoError;
    }

    private static Properties propiedadesConexion(String usuario, String clave) {
        Properties propiedades = new Properties();
        propiedades.setProperty("user", usuario);
        propiedades.setProperty("password", clave);
        // Neon puede tardar en despertar; no fallar antes de ese tiempo.
        propiedades.setProperty("connectTimeout",
                String.valueOf(CONNECT_TIMEOUT_SEGUNDOS));
        propiedades.setProperty("tcpKeepAlive", "true");
        propiedades.setProperty("ApplicationName", "encuestas-web");
        return propiedades;
    }

    /**
     * Indica si el error corresponde a una falla transitoria de conexión
     * (por ejemplo, Neon despertando) que conviene reintentar.
     */
    public static boolean esErrorTransitorio(Throwable error) {
        Throwable actual = error;
        boolean transitorio = false;
        for (int profundidad = 0; actual != null && profundidad < 6; profundidad++) {
            String mensaje = actual.getMessage();
            String normalizado = mensaje == null ? "" : mensaje.toLowerCase(Locale.ROOT);
            // Un controlador ausente o una URL inválida no se arreglan reintentando.
            if (normalizado.contains("no suitable driver")) {
                return false;
            }
            if (actual instanceof SQLException) {
                String estado = ((SQLException) actual).getSQLState();
                if (estado != null && (estado.startsWith("08")
                        || estado.equals("57P03")
                        || estado.equals("53300"))) {
                    transitorio = true;
                }
            }
            if (contieneFalloTransitorio(normalizado)) {
                transitorio = true;
            }
            if (actual.getCause() == actual) {
                break;
            }
            actual = actual.getCause();
        }
        return transitorio;
    }

    private static boolean contieneFalloTransitorio(String mensaje) {
        return mensaje.contains("timeout")
                || mensaje.contains("timed out")
                || mensaje.contains("connection refused")
                || mensaje.contains("connection reset")
                || mensaje.contains("connection attempt failed")
                || mensaje.contains("el intento de conexi")
                || mensaje.contains("could not connect")
                || mensaje.contains("could not receive data from server")
                || mensaje.contains("i/o error")
                || mensaje.contains("starting up")
                || mensaje.contains("temporarily unavailable")
                || mensaje.contains("too many clients")
                || mensaje.contains("terminating connection")
                || mensaje.contains("connection is closed")
                || mensaje.contains("this connection has been closed");
    }

    private static void esperar(long milisegundos, SQLException error)
            throws SQLException {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw error;
        }
    }

    private static int leerEntero(String nombre, int valorPorDefecto) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            return valorPorDefecto;
        }
        try {
            int numero = Integer.parseInt(valor.trim());
            return numero >= 0 ? numero : valorPorDefecto;
        } catch (NumberFormatException e) {
            return valorPorDefecto;
        }
    }
}
