
package com.umss.dao;

import com.umss.config.Database;

import java.sql.*;
import java.util.*;

public class ReporteHu12DAO {

    private ReporteHu12DAO() {}

    /**
     * Frecuencias de respuestas para una pregunta de una encuesta.
     * Cada envío se cuenta una sola vez por categoría.
     */
    public static Map<String, Object> obtenerFrecuencias(
            int encuestaId, int preguntaId) throws SQLException {

        Map<String, Object> resultado = new LinkedHashMap<>();
        List<Map<String, Object>> categorias = new ArrayList<>();

        String sql = """
            SELECT TRIM(respuesta) AS respuesta,
                   COUNT(DISTINCT envio_id) AS total
            FROM respuestas
            WHERE encuesta_id = ?
              AND pregunta_id = ?
              AND envio_id IS NOT NULL
              AND COALESCE(TRIM(respuesta), '') <> ''
            GROUP BY TRIM(respuesta)
            ORDER BY total DESC, respuesta
            """;

        int total = 0;

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, encuestaId);
            ps.setInt(2, preguntaId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("respuesta", rs.getString("respuesta"));
                    item.put("total", rs.getInt("total"));
                    categorias.add(item);
                    total += rs.getInt("total");
                }
            }
        }

        for (Map<String, Object> item : categorias) {
            int cantidad = (int) item.get("total");
            item.put("porcentaje",
                    total == 0 ? 0.0 : cantidad * 100.0 / total);
        }

        resultado.put("encuestaId", encuestaId);
        resultado.put("preguntaId", preguntaId);
        resultado.put("total", total);
        resultado.put("categorias", categorias);

        return resultado;
    }

    /**
     * Resumen de interés y disponibilidad de contactos alumno.
     * Solo devuelve los datos personales de quienes dieron su consentimiento
     * explícito en la pregunta 46.
     */
    public static Map<String, Object> obteneralumno() throws SQLException {

        Map<String, Object> resultado = new LinkedHashMap<>();
        List<Map<String, Object>> contactos = new ArrayList<>();

        String sql = """
            WITH envios AS (
                SELECT DISTINCT envio_id
                FROM respuestas
                WHERE encuesta_id = 1
                  AND envio_id IS NOT NULL
            ),
            consentimiento AS (
                SELECT envio_id
                FROM respuestas
                WHERE encuesta_id = 1
                  AND pregunta_id = 46
                  AND envio_id IS NOT NULL
                  AND UPPER(TRANSLATE(TRIM(respuesta),
                      'ÍÁÉÓÚ', 'IAEOU')) = 'SI'
            ),
            contactos AS (
                SELECT
                    c.envio_id,
                    MAX(CASE WHEN r.pregunta_id = 47
                        THEN NULLIF(TRIM(r.respuesta), '') END) AS correo,
                    MAX(CASE WHEN r.pregunta_id = 48
                        THEN NULLIF(TRIM(r.respuesta), '') END) AS telefono,
                    MAX(CASE WHEN r.pregunta_id = 49
                        THEN NULLIF(TRIM(r.respuesta), '') END) AS linkedin
                FROM consentimiento c
                LEFT JOIN respuestas r
                    ON r.envio_id = c.envio_id
                   AND r.encuesta_id = 1
                   AND r.pregunta_id IN (47, 48, 49)
                GROUP BY c.envio_id
            )
            SELECT correo, telefono, linkedin
            FROM contactos
            ORDER BY correo NULLS LAST
            """;

        String sqlResumen = """
            WITH envios AS (
                SELECT DISTINCT envio_id
                FROM respuestas
                WHERE encuesta_id = 1
                  AND envio_id IS NOT NULL
            ),
            consentimiento AS (
                SELECT DISTINCT envio_id
                FROM respuestas
                WHERE encuesta_id = 1
                  AND pregunta_id = 46
                  AND envio_id IS NOT NULL
                  AND UPPER(TRANSLATE(TRIM(respuesta),
                      'ÍÁÉÓÚ', 'IAEOU')) = 'SI'
            ),
            contactos AS (
                SELECT c.envio_id,
                    MAX(CASE WHEN r.pregunta_id = 47
                        THEN NULLIF(TRIM(r.respuesta), '') END) AS correo,
                    MAX(CASE WHEN r.pregunta_id = 48
                        THEN NULLIF(TRIM(r.respuesta), '') END) AS telefono,
                    MAX(CASE WHEN r.pregunta_id = 49
                        THEN NULLIF(TRIM(r.respuesta), '') END) AS linkedin
                FROM consentimiento c
                LEFT JOIN respuestas r
                    ON r.envio_id = c.envio_id
                   AND r.encuesta_id = 1
                   AND r.pregunta_id IN (47, 48, 49)
                GROUP BY c.envio_id
            )
            SELECT
                (SELECT COUNT(*) FROM envios) AS total_titulados,
                (SELECT COUNT(*) FROM consentimiento) AS aceptaron,
                COUNT(*) FILTER (WHERE correo IS NOT NULL) AS con_correo,
                COUNT(*) FILTER (WHERE telefono IS NOT NULL) AS con_telefono,
                COUNT(*) FILTER (WHERE linkedin IS NOT NULL) AS con_linkedin
            FROM contactos
            """;

        try (Connection conn = Database.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sqlResumen);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    resultado.put("totalTitulados",
                            rs.getInt("total_titulados"));
                    resultado.put("aceptaron",
                            rs.getInt("aceptaron"));
                    resultado.put("conCorreo",
                            rs.getInt("con_correo"));
                    resultado.put("conTelefono",
                            rs.getInt("con_telefono"));
                    resultado.put("conLinkedin",
                            rs.getInt("con_linkedin"));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> contacto = new LinkedHashMap<>();
                    contacto.put("correo", rs.getString("correo"));
                    contacto.put("telefono", rs.getString("telefono"));
                    contacto.put("linkedin", rs.getString("linkedin"));
                    contactos.add(contacto);
                }
            }
        }

        resultado.put("contactos", contactos);
        return resultado;
    }
}
