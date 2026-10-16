package com.umss.dao;

import com.umss.config.Database;
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
    String sql = "INSERT INTO respuestas (encuesta_id, pregunta_id, respuesta) VALUES (?, ?, ?)";
    try (Connection conn = Database.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
        conn.setAutoCommit(false);
        if (dto.getRespuestas() != null) {
            for (Map.Entry<String, String> entry : dto.getRespuestas().entrySet()) {
                int preguntaId = Integer.parseInt(entry.getKey().replace("p_", ""));
                stmt.setInt(1, dto.getEncuestaId());
                stmt.setInt(2, preguntaId);
                stmt.setString(3, entry.getValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
        conn.commit();
    }
}
}