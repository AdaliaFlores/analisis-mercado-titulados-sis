package com.umss.dao;

import com.umss.config.Database;
import com.umss.model.CeldaCruce;
import com.umss.model.ConteoRespuesta;
import com.umss.model.Encuesta;
import com.umss.model.Pregunta;
import com.umss.model.RespuestaDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EncuestaDAO {

    public static List<Encuesta> obtenerEncuestas() throws SQLException {
        List<Encuesta> lista = new ArrayList<>();
        String sql = "SELECT * FROM encuestas ORDER BY id";
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Encuesta e = new Encuesta();
                e.setId(rs.getInt("id"));
                e.setTitulo(rs.getString("titulo"));
                e.setDestinatario(rs.getString("destinatario"));
                e.setDescripcion(rs.getString("descripcion"));
                lista.add(e);
            }
        }
        return lista;
    }

    public static List<Pregunta> obtenerPreguntasPorEncuesta(int encuestaId) throws SQLException {
        List<Pregunta> lista = new ArrayList<>();
        String sql = "SELECT * FROM preguntas WHERE encuesta_id = ? ORDER BY id";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, encuestaId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Pregunta p = new Pregunta();
                    p.setId(rs.getInt("id"));
                    p.setEncuestaId(rs.getInt("encuesta_id"));
                    p.setTextoPregunta(rs.getString("texto_pregunta"));
                    p.setTipoPregunta(rs.getString("tipo_pregunta"));
                    p.setOpciones(rs.getString("opciones"));
                    lista.add(p);
                }
            }
        }
        return lista;
    }

    public static List<ConteoRespuesta> obtenerConteoEmprendimiento() throws SQLException {
        List<ConteoRespuesta> lista = new ArrayList<>();
        String sql = """
            SELECT pregunta_id,
                TRIM(respuesta) AS respuesta,
                COUNT(*) AS total,
                ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (PARTITION BY pregunta_id), 1) AS porcentaje
            FROM respuestas
            WHERE pregunta_id IN (30,31,32,33,34)
            AND respuesta IS NOT NULL AND TRIM(respuesta) <> ''
            GROUP BY pregunta_id, TRIM(respuesta)
            ORDER BY pregunta_id, total DESC
            """;
        try (Connection conn = Database.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ConteoRespuesta c = new ConteoRespuesta();
                c.setPreguntaId(rs.getInt("pregunta_id"));
                c.setRespuesta(rs.getString("respuesta"));
                c.setTotal(rs.getInt("total"));
                c.setPorcentaje(rs.getDouble("porcentaje"));
                lista.add(c);
            }
     }
        return lista;
    }

    public static void guardarRespuestas(RespuestaDTO dto) throws SQLException {
        String sql = "INSERT INTO respuestas (envio_id, encuesta_id, pregunta_id, respuesta) VALUES (?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            try {
                java.util.UUID envioId = java.util.UUID.randomUUID();   // 👈 1 id para TODA la encuesta
                if (dto.getRespuestas() != null) {
                    for (Map.Entry<String, String> entry : dto.getRespuestas().entrySet()) {
                        int preguntaId = Integer.parseInt(entry.getKey().replace("p_", ""));
                        stmt.setObject(1, envioId);
                        stmt.setInt(2, dto.getEncuestaId());
                        stmt.setInt(3, preguntaId);
                        stmt.setString(4, entry.getValue());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();          
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static List<CeldaCruce> obtenerCruce(int preguntaX, int preguntaY) throws SQLException {
        List<CeldaCruce> lista = new ArrayList<>();
        String sql = """
            SELECT a.respuesta AS x, b.respuesta AS y, COUNT(*) AS total
            FROM respuestas a
            JOIN respuestas b
                ON b.envio_id = a.envio_id
            WHERE a.pregunta_id = ? AND b.pregunta_id = ?
            AND COALESCE(TRIM(a.respuesta),'') <> ''
            AND COALESCE(TRIM(b.respuesta),'') <> ''
            GROUP BY a.respuesta, b.respuesta
            ORDER BY a.respuesta, b.respuesta
            """;
        try (Connection conn = Database.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, preguntaX);
            stmt.setInt(2, preguntaY);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CeldaCruce c = new CeldaCruce();
                    c.setValorX(rs.getString("x"));
                    c.setValorY(rs.getString("y"));
                    c.setTotal(rs.getInt("total"));
                    lista.add(c);
                }
            }
        }
        return lista;
    }

    public static List<CeldaCruce> obtenerCruceFiltrado(int x, int y,
                                                        int f1Preg, String f1Val,
                                                        int f2Preg, String f2Val) throws SQLException {
        List<CeldaCruce> lista = new ArrayList<>();

        // Consulta base del cruce
        StringBuilder sql = new StringBuilder("""
            SELECT a.respuesta AS x, b.respuesta AS y, COUNT(*) AS total
            FROM respuestas a
            JOIN respuestas b ON b.envio_id = a.envio_id
            WHERE a.pregunta_id = ? AND b.pregunta_id = ?
              AND COALESCE(TRIM(a.respuesta),'') <> ''
              AND COALESCE(TRIM(b.respuesta),'') <> ''
            """);

        // Filtro 1 (opcional): el mismo envío debe tener esta respuesta
        if (f1Preg > 0) {
            sql.append(" AND EXISTS (SELECT 1 FROM respuestas f1 WHERE f1.envio_id = a.envio_id ")
               .append("AND f1.pregunta_id = ? AND f1.respuesta = ?)");
        }
        // Filtro 2 (opcional)
        if (f2Preg > 0) {
            sql.append(" AND EXISTS (SELECT 1 FROM respuestas f2 WHERE f2.envio_id = a.envio_id ")
               .append("AND f2.pregunta_id = ? AND f2.respuesta = ?)");
        }

        sql.append(" GROUP BY a.respuesta, b.respuesta ORDER BY a.respuesta, b.respuesta");

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int i = 1;
            stmt.setInt(i++, x);
            stmt.setInt(i++, y);
            if (f1Preg > 0) { stmt.setInt(i++, f1Preg); stmt.setString(i++, f1Val); }
            if (f2Preg > 0) { stmt.setInt(i++, f2Preg); stmt.setString(i++, f2Val); }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CeldaCruce c = new CeldaCruce();
                    c.setValorX(rs.getString("x"));
                    c.setValorY(rs.getString("y"));
                    c.setTotal(rs.getInt("total"));
                    lista.add(c);
                }
            }
        }
        return lista;
    }
    
}