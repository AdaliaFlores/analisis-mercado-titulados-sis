package com.umss.dao;

import com.umss.config.Database;
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