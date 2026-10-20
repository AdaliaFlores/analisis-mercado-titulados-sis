package com.umss.dao;

import com.umss.config.Database;
import com.umss.model.CeldaCruce;
import com.umss.model.ConteoRespuesta;
import com.umss.model.FiltroEnvio;
import com.umss.model.ResultadoPromedio;
import com.umss.model.ResumenGrupo;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consultas genéricas de análisis: funcionan para cualquier pregunta y cualquier cantidad de datos.
 * Los valores numéricos y las agrupaciones salen de las tablas codificacion_opcion y agrupacion_opcion.
 */
public class AnalisisDAO {

    private static final int MIN_N_GRUPO = 5;

    /** Frecuencias de una pregunta (opcionalmente agrupando sus opciones). */
    public static List<ConteoRespuesta> frecuencias(int preguntaId, boolean agrupada,
                                                    FiltroEnvio filtro) throws SQLException {
        String etiqueta = agrupada ? "COALESCE(g.grupo, TRIM(a.respuesta))" : "TRIM(a.respuesta)";
        String unionGrupo = agrupada
            ? "LEFT JOIN agrupacion_opcion g ON g.pregunta_id = a.pregunta_id AND g.opcion = TRIM(a.respuesta) "
            : "";
        String sql = "SELECT " + etiqueta + " AS etiqueta, COUNT(*) AS total, "
                + "ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 1) AS porcentaje "
                + "FROM respuestas a " + unionGrupo
                + "WHERE a.pregunta_id = ? AND a.envio_id IS NOT NULL "
                + "AND COALESCE(TRIM(a.respuesta), '') <> ''"
                + clausulaFiltros(filtro, "a")
                + " GROUP BY 1 ORDER BY total DESC, etiqueta";

        List<ConteoRespuesta> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int i = 1;
            stmt.setInt(i++, preguntaId);
            enlazarFiltros(stmt, i, filtro);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ConteoRespuesta c = new ConteoRespuesta();
                    c.setPreguntaId(preguntaId);
                    c.setRespuesta(rs.getString("etiqueta"));
                    c.setTotal(rs.getInt("total"));
                    c.setPorcentaje(rs.getDouble("porcentaje"));
                    lista.add(c);
                }
            }
        }
        return lista;
    }

    /** Media, desviación estándar muestral y n del valor de una pregunta, agrupado por otra. */
    public static ResultadoPromedio promedioPorGrupo(int preguntaValor, int preguntaGrupo,
                                                     FiltroEnvio filtro) throws SQLException {
        String sql = """
            SELECT TRIM(g.respuesta)           AS grupo,
                   COUNT(*)                    AS total,
                   COUNT(c.valor)              AS n,
                   COALESCE(SUM(c.valor), 0)   AS suma,
                   AVG(c.valor)                AS media,
                   STDDEV_SAMP(c.valor)        AS desviacion
            FROM respuestas g
            JOIN respuestas v ON v.envio_id = g.envio_id AND v.pregunta_id = ?
            LEFT JOIN codificacion_opcion c
                   ON c.pregunta_id = v.pregunta_id AND c.opcion = TRIM(v.respuesta)
            WHERE g.pregunta_id = ?
              AND COALESCE(TRIM(g.respuesta), '') <> ''
              AND COALESCE(TRIM(v.respuesta), '') <> ''
            """ + clausulaFiltros(filtro, "g")
            + " GROUP BY TRIM(g.respuesta) ORDER BY n DESC, grupo";

        List<ResumenGrupo> grupos = new ArrayList<>();
        int nTotal = 0, sinCod = 0;
        double sumaTotal = 0;
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int i = 1;
            stmt.setInt(i++, preguntaValor);   // el "?" del JOIN va primero
            stmt.setInt(i++, preguntaGrupo);
            enlazarFiltros(stmt, i, filtro);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int total = rs.getInt("total");
                    int n = rs.getInt("n");
                    double suma = rs.getDouble("suma");
                    grupos.add(new ResumenGrupo(rs.getString("grupo"), total, n, total - n, suma,
                            decimalONulo(rs.getBigDecimal("media")),
                            decimalONulo(rs.getBigDecimal("desviacion")),
                            n >= MIN_N_GRUPO));
                    nTotal += n;
                    sinCod += total - n;
                    sumaTotal += suma;
                }
            }
        }
        Double mediaGlobal = nTotal > 0 ? sumaTotal / nTotal : null;
        return new ResultadoPromedio(grupos, nTotal, sinCod, sumaTotal, mediaGlobal);
    }

    /** Celdas de la tabla de contingencia X × Y (opcionalmente con las opciones agrupadas). */
    public static List<CeldaCruce> contingencia(int x, int y, boolean agrupada,
                                                FiltroEnvio filtro) throws SQLException {
        String ex = agrupada ? "COALESCE(ga.grupo, TRIM(a.respuesta))" : "TRIM(a.respuesta)";
        String ey = agrupada ? "COALESCE(gb.grupo, TRIM(b.respuesta))" : "TRIM(b.respuesta)";
        String uniones = agrupada
            ? "LEFT JOIN agrupacion_opcion ga ON ga.pregunta_id = a.pregunta_id AND ga.opcion = TRIM(a.respuesta) "
            + "LEFT JOIN agrupacion_opcion gb ON gb.pregunta_id = b.pregunta_id AND gb.opcion = TRIM(b.respuesta) "
            : "";
        String sql = "SELECT " + ex + " AS x, " + ey + " AS y, COUNT(*) AS total "
                + "FROM respuestas a JOIN respuestas b ON b.envio_id = a.envio_id " + uniones
                + "WHERE a.pregunta_id = ? AND b.pregunta_id = ? "
                + "AND COALESCE(TRIM(a.respuesta), '') <> '' AND COALESCE(TRIM(b.respuesta), '') <> ''"
                + clausulaFiltros(filtro, "a")
                + " GROUP BY 1, 2 ORDER BY 1, 2";

        List<CeldaCruce> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int i = 1;
            stmt.setInt(i++, x);
            stmt.setInt(i++, y);
            enlazarFiltros(stmt, i, filtro);
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

    /** Orden de presentación de las categorías de una pregunta (mapa etiqueta -> orden). */
    public static Map<String, Integer> ordenEtiquetas(int preguntaId, boolean agrupada) throws SQLException {
        String sql = agrupada
            ? "SELECT grupo AS etiqueta, MIN(orden) AS orden FROM agrupacion_opcion WHERE pregunta_id = ? GROUP BY grupo"
            : "SELECT opcion AS etiqueta, orden FROM codificacion_opcion WHERE pregunta_id = ?";
        Map<String, Integer> orden = new HashMap<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, preguntaId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) orden.put(rs.getString("etiqueta"), rs.getInt("orden"));
            }
        }
        return orden;
    }

    public static boolean tieneAgrupacion(int preguntaId) throws SQLException {
        String sql = "SELECT EXISTS (SELECT 1 FROM agrupacion_opcion WHERE pregunta_id = ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, preguntaId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    // ---------- Filtros combinados (cualquier cantidad) ----------

    // Solo se concatenan números de bucle y "?"; los valores siempre van como parámetros
    private static String clausulaFiltros(FiltroEnvio filtro, String alias) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filtro.getCondiciones().size(); i++) {
            sb.append(" AND EXISTS (SELECT 1 FROM respuestas f").append(i)
              .append(" WHERE f").append(i).append(".envio_id = ").append(alias).append(".envio_id")
              .append(" AND f").append(i).append(".pregunta_id = ?")
              .append(" AND TRIM(f").append(i).append(".respuesta) = ?)");
        }
        return sb.toString();
    }

    private static void enlazarFiltros(PreparedStatement stmt, int indice,
                                       FiltroEnvio filtro) throws SQLException {
        for (FiltroEnvio.Condicion c : filtro.getCondiciones()) {
            stmt.setInt(indice++, c.preguntaId());
            stmt.setString(indice++, c.valor());
        }
    }

    private static Double decimalONulo(BigDecimal valor) {
        return valor == null ? null : valor.doubleValue();
    }
}
