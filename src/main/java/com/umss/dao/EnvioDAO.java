package com.umss.dao;

import com.umss.config.Database;
import com.umss.model.EnvioFila;
import com.umss.model.PaginaResultado;
import com.umss.model.RespuestaDetalle;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnvioDAO {

    public static PaginaResultado<EnvioFila> obtenerPagina(int encuestaId, int pagina, int tamanio)
            throws SQLException {
        try (Connection conn = Database.getConnection()) {
            long total = contarEnvios(conn, encuestaId);
            Map<String, EnvioFila> filas = obtenerEnvios(conn, encuestaId, pagina, tamanio);
            cargarRespuestas(conn, encuestaId, filas);
            return new PaginaResultado<>(new ArrayList<>(filas.values()), total, pagina, tamanio);
        }
    }

    public static List<RespuestaDetalle> obtenerDetalle(String envioId) throws SQLException {
        List<RespuestaDetalle> lista = new ArrayList<>();
        String sql = """
            SELECT r.id, r.pregunta_id, p.texto_pregunta, r.respuesta,
                   p.formato, %s AS editable
            FROM respuestas r
            JOIN preguntas p ON p.id = r.pregunta_id
            WHERE r.envio_id = ?::uuid
              AND COALESCE(TRIM(r.respuesta), '') <> ''
            ORDER BY r.pregunta_id
            """.formatted(ReglasEdicion.ES_EDITABLE);
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, envioId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    RespuestaDetalle d = new RespuestaDetalle();
                    d.setId(rs.getInt("id"));
                    d.setPreguntaId(rs.getInt("pregunta_id"));
                    d.setTextoPregunta(rs.getString("texto_pregunta"));
                    d.setRespuesta(rs.getString("respuesta"));
                    d.setFormato(rs.getString("formato"));
                    d.setEditable(rs.getBoolean("editable"));
                    lista.add(d);
                }
            }
        }
        return lista;
    }

    private static long contarEnvios(Connection conn, int encuestaId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT envio_id) FROM respuestas "
                   + "WHERE encuesta_id = ? AND envio_id IS NOT NULL";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, encuestaId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    // LinkedHashMap conserva el orden de la página (más recientes primero)
    private static Map<String, EnvioFila> obtenerEnvios(Connection conn, int encuestaId,
                                                        int pagina, int tamanio) throws SQLException {
        Map<String, EnvioFila> filas = new LinkedHashMap<>();
        String sql = """
            SELECT envio_id, MIN(fecha_registro) AS fecha
            FROM respuestas
            WHERE encuesta_id = ? AND envio_id IS NOT NULL
            GROUP BY envio_id
            ORDER BY fecha DESC, envio_id
            LIMIT ? OFFSET ?
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, encuestaId);
            stmt.setInt(2, tamanio);
            stmt.setInt(3, (pagina - 1) * tamanio);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("envio_id");
                    Timestamp ts = rs.getTimestamp("fecha");
                    filas.put(id, new EnvioFila(id, ts != null ? ts.toLocalDateTime().toString() : null));
                }
            }
        }
        return filas;
    }

    private static void cargarRespuestas(Connection conn, int encuestaId,
                                         Map<String, EnvioFila> filas) throws SQLException {
        if (filas.isEmpty()) return;
        String sql = """
            SELECT envio_id, pregunta_id, respuesta
            FROM respuestas
            WHERE encuesta_id = ?
              AND envio_id = ANY (?::uuid[])
              AND COALESCE(TRIM(respuesta), '') <> ''
            """;
        Array ids = conn.createArrayOf("text", filas.keySet().toArray());
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, encuestaId);
            stmt.setArray(2, ids);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    filas.get(rs.getString("envio_id"))
                         .getRespuestas()
                         .put(rs.getInt("pregunta_id"), rs.getString("respuesta"));
                }
            }
        } finally {
            ids.free();
        }
    }
}