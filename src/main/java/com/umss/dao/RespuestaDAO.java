package com.umss.dao;

import com.umss.config.Database;
import com.umss.model.AuditoriaRegistro;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class RespuestaDAO {

    private static final int MAX_MOTIVO = 200;

    /**
     * Corrige una respuesta de texto y registra la auditoría en la misma transacción.
     * IllegalArgumentException -> dato inválido (400). NoSuchElementException -> no existe (404).
     * Devuelve el valor finalmente guardado (ya normalizado).
     */
    public static String editar(int respuestaId, String nuevoValor, String motivo) throws SQLException {
        String valor = nuevoValor == null ? "" : nuevoValor.trim();
        String razon = motivo == null ? "" : motivo.trim();

        if (valor.isEmpty()) throw new IllegalArgumentException("El valor no puede estar vacío");
        if (razon.isEmpty()) throw new IllegalArgumentException("Indique el motivo de la corrección");
        if (razon.length() > MAX_MOTIVO)
            throw new IllegalArgumentException("El motivo no puede superar " + MAX_MOTIVO + " caracteres");

        String sqlSelect = """
            SELECT r.respuesta, p.formato, %s AS editable
            FROM respuestas r
            JOIN preguntas p ON p.id = r.pregunta_id
            WHERE r.id = ?
            FOR UPDATE OF r
            """.formatted(ReglasEdicion.ES_EDITABLE);
        String sqlUpdate = "UPDATE respuestas SET respuesta = ? WHERE id = ?";
        String sqlAuditoria = "INSERT INTO respuestas_auditoria "
                + "(respuesta_id, valor_anterior, valor_nuevo, motivo) VALUES (?, ?, ?, ?)";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String actual, formato;
                boolean editable;
                try (PreparedStatement stmt = conn.prepareStatement(sqlSelect)) {
                    stmt.setInt(1, respuestaId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) throw new NoSuchElementException("Respuesta no encontrada");
                        actual = rs.getString("respuesta");
                        editable = rs.getBoolean("editable");
                        formato = rs.getString("formato");
                    }
                }

                if (!editable)
                    throw new IllegalArgumentException(
                        "Solo se pueden corregir respuestas escritas, no las de selección");

                valor = validarFormato(formato, valor);

                if (valor.equals(actual))
                    throw new IllegalArgumentException("El nuevo valor es igual al actual");

                try (PreparedStatement stmt = conn.prepareStatement(sqlUpdate)) {
                    stmt.setString(1, valor);
                    stmt.setInt(2, respuestaId);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(sqlAuditoria)) {
                    stmt.setInt(1, respuestaId);
                    stmt.setString(2, actual);
                    stmt.setString(3, valor);
                    stmt.setString(4, razon);
                    stmt.executeUpdate();
                }
                conn.commit();
                return valor;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static List<AuditoriaRegistro> obtenerAuditoriaPorEnvio(String envioId) throws SQLException {
        List<AuditoriaRegistro> lista = new ArrayList<>();
        String sql = """
            SELECT a.id, a.respuesta_id, p.texto_pregunta,
                   a.valor_anterior, a.valor_nuevo, a.motivo, a.fecha
            FROM respuestas_auditoria a
            JOIN respuestas r ON r.id = a.respuesta_id
            JOIN preguntas p ON p.id = r.pregunta_id
            WHERE r.envio_id = ?::uuid
            ORDER BY a.fecha DESC, a.id DESC
            """;
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, envioId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    AuditoriaRegistro a = new AuditoriaRegistro();
                    a.setId(rs.getInt("id"));
                    a.setRespuestaId(rs.getInt("respuesta_id"));
                    a.setTextoPregunta(rs.getString("texto_pregunta"));
                    a.setValorAnterior(rs.getString("valor_anterior"));
                    a.setValorNuevo(rs.getString("valor_nuevo"));
                    a.setMotivo(rs.getString("motivo"));
                    Timestamp ts = rs.getTimestamp("fecha");
                    a.setFecha(ts != null ? ts.toLocalDateTime().toString() : null);
                    lista.add(a);
                }
            }
        }
        return lista;
    }

    // Un solo lugar para las reglas de formato: agregar un formato nuevo = agregar un caso aquí
    private static String validarFormato(String formato, String valor) {
        if ("NUMERO".equals(formato)) {
            if (!valor.matches("\\d+([.,]\\d{1,2})?"))
                throw new IllegalArgumentException("Esta pregunta requiere un número (ej. 5 o 2.5)");
            return valor.replace(',', '.');
        }
        return valor;
    }
}