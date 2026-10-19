package com.umss.dao;

import com.umss.config.Database;
import com.umss.model.CeldaCruce;
import com.umss.model.ConteoRespuesta;
import com.umss.model.DimensionRadar;
import com.umss.model.Encuesta;
import com.umss.model.IndicadorEscala;
import com.umss.model.Pregunta;
import com.umss.model.RespuestaDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.umss.model.ConteoPalabra;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import com.umss.model.AreaEmpleabilidad;
import com.umss.model.AreaInteresPosgrado;
import com.umss.model.ResultadoChi2;

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
                java.util.UUID envioId = java.util.UUID.randomUUID(); 
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
        String sql = "SELECT a.respuesta AS x, b.respuesta AS y, COUNT(*) AS total "
                   + "FROM respuestas a JOIN respuestas b ON " + claveEnvio("b") + " = " + claveEnvio("a") + " "
                   + "WHERE a.pregunta_id = ? AND b.pregunta_id = ? "
                   + "AND COALESCE(TRIM(a.respuesta),'') <> '' "
                   + "AND COALESCE(TRIM(b.respuesta),'') <> '' "
                   + "GROUP BY a.respuesta, b.respuesta "
                   + "ORDER BY a.respuesta, b.respuesta";
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

        StringBuilder sql = new StringBuilder(
            "SELECT a.respuesta AS x, b.respuesta AS y, COUNT(*) AS total "
          + "FROM respuestas a JOIN respuestas b ON " + claveEnvio("b") + " = " + claveEnvio("a") + " "
          + "WHERE a.pregunta_id = ? AND b.pregunta_id = ? "
          + "  AND COALESCE(TRIM(a.respuesta),'') <> '' "
          + "  AND COALESCE(TRIM(b.respuesta),'') <> '' ");

        if (f1Preg > 0) {
            sql.append(" AND EXISTS (SELECT 1 FROM respuestas f1 WHERE ").append(claveEnvio("f1"))
               .append(" = ").append(claveEnvio("a"))
               .append(" AND f1.pregunta_id = ? AND f1.respuesta = ?)");
        }
        // Filtro 2 (opcional)
        if (f2Preg > 0) {
            sql.append(" AND EXISTS (SELECT 1 FROM respuestas f2 WHERE ").append(claveEnvio("f2"))
               .append(" = ").append(claveEnvio("a"))
               .append(" AND f2.pregunta_id = ? AND f2.respuesta = ?)");
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

    // REPORTE DE DESEMPLEO Y BARRERAS
    public static List<ConteoRespuesta> contarRespuestas(int... preguntas) throws SQLException {
        List<ConteoRespuesta> lista = new ArrayList<>();
        if (preguntas == null || preguntas.length == 0) return lista;

        StringBuilder in = new StringBuilder();
        for (int i = 0; i < preguntas.length; i++) in.append(i == 0 ? "?" : ",?");

        String sql = "SELECT pregunta_id, TRIM(respuesta) AS respuesta, COUNT(*) AS total, "
                   + "ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (PARTITION BY pregunta_id), 1) AS porcentaje "
                   + "FROM respuestas "
                   + "WHERE pregunta_id IN (" + in + ") AND respuesta IS NOT NULL AND TRIM(respuesta) <> '' "
                   + "GROUP BY pregunta_id, TRIM(respuesta) "
                   + "ORDER BY pregunta_id, total DESC";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < preguntas.length; i++) stmt.setInt(i + 1, preguntas[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ConteoRespuesta c = new ConteoRespuesta();
                    c.setPreguntaId(rs.getInt("pregunta_id"));
                    c.setRespuesta(rs.getString("respuesta"));
                    c.setTotal(rs.getInt("total"));
                    c.setPorcentaje(rs.getDouble("porcentaje"));
                    lista.add(c);
                }
            }
        }
        return lista;
    }

    /** Poblacion objetivo: titulados que no trabajan o con tiempo de desempleo > 0.*/
    public static int[] contarPoblacionObjetivo() throws SQLException {
        int[] r = new int[4];
        String sql =
            "SELECT "
          + "(SELECT COUNT(DISTINCT fecha_registro) FROM respuestas WHERE encuesta_id = 1) AS total, "
          + "(SELECT COUNT(DISTINCT fecha_registro) FROM respuestas WHERE encuesta_id = 1 "
          +   "AND pregunta_id = 14 AND TRIM(respuesta) = 'Actualmente NO trabajo') AS no_trabaja, "
          + "(SELECT COUNT(DISTINCT fecha_registro) FROM respuestas WHERE encuesta_id = 1 "
          +   "AND pregunta_id = 13 AND TRIM(respuesta) ~ '^[0-9]+$' AND CAST(TRIM(respuesta) AS INT) > 0) AS con_tiempo, "
          + "(SELECT COUNT(DISTINCT fecha_registro) FROM respuestas WHERE encuesta_id = 1 AND ("
          +   "(pregunta_id = 14 AND TRIM(respuesta) = 'Actualmente NO trabajo') "
          +   "OR (pregunta_id = 13 AND TRIM(respuesta) ~ '^[0-9]+$' AND CAST(TRIM(respuesta) AS INT) > 0))) AS objetivo";
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                r[0] = rs.getInt("total");
                r[1] = rs.getInt("no_trabaja");
                r[2] = rs.getInt("con_tiempo");
                r[3] = rs.getInt("objetivo");
            }
        }
        return r;
    }

    public static List<String> obtenerRespuestasTexto(int pregunta) throws SQLException {
        List<String> lista = new ArrayList<>();
        String sql = "SELECT TRIM(respuesta) AS r FROM respuestas "
                   + "WHERE pregunta_id = ? AND respuesta IS NOT NULL AND TRIM(respuesta) <> '' "
                   + "ORDER BY fecha_registro";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, pregunta);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) lista.add(rs.getString("r"));
            }
        }
        return lista;
    }

    private static final Map<String, String[]> DICCIONARIO = new LinkedHashMap<>();
    static {
        DICCIONARIO.put("IA / Machine Learning", new String[]{"ia","inteligencia artificial","machine learning","deep learning","redes neuronales","nlp","procesamiento de lenguaje natural"});
        DICCIONARIO.put("Cloud / Nube", new String[]{"cloud","nube","aws","azure","gcp","google cloud","computacion en la nube"});
        DICCIONARIO.put("Contenedores (Docker/K8s)", new String[]{"docker","kubernetes","k8s","contenedor","contenedores"});
        DICCIONARIO.put("Python", new String[]{"python"});
        DICCIONARIO.put("Java", new String[]{"java"});
        DICCIONARIO.put("JavaScript / Web", new String[]{"javascript","js","react","angular","vue","node","nodejs","html","css","frontend"});
        DICCIONARIO.put("Ciberseguridad", new String[]{"ciberseguridad","seguridad informatica","seguridad","hacking","pentesting","iso 27001","owasp"});
        DICCIONARIO.put("Base de Datos / SQL", new String[]{"base de datos","bases de datos","sql","postgres","postgresql","mysql","mongodb","oracle","sql server","nosql"});
        DICCIONARIO.put("DevOps / Git", new String[]{"devops","git","github","gitlab","ci cd","jenkins","integracion continua"});
        DICCIONARIO.put("Big Data / Analitica", new String[]{"big data","analitica","business intelligence","power bi","tableau","etl","data warehouse","analisis de datos"});
        DICCIONARIO.put("Metodologias agiles", new String[]{"scrum","agil","agile","kanban"});
        DICCIONARIO.put("APIs / Microservicios", new String[]{"api","apis","rest","microservicios","microservices","soap"});
        DICCIONARIO.put("Desarrollo Movil", new String[]{"movil","android","ios","flutter","react native","swift","kotlin"});
        DICCIONARIO.put("Redes", new String[]{"redes","networking","cisco","tcp ip"});
        DICCIONARIO.put("Gestion de Proyectos", new String[]{"gestion de proyectos","project management","pmp"});
        DICCIONARIO.put("Posgrado / Especializacion", new String[]{"diplomado","maestria","especialidad","posgrado","doctorado","certificacion"});
        DICCIONARIO.put("Competencias blandas", new String[]{"comunicacion","trabajo en equipo","liderazgo","puntualidad","responsabilidad","etica","proactividad","adaptabilidad","aprendizaje continuo","resolucion de problemas","pensamiento critico","creatividad"});
        DICCIONARIO.put("Experiencia practica", new String[]{"experiencia","practica","pasantia","experiencia laboral"});
    }

    private static String normalizar(String s) {
        String t = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return t.toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    /** Cuenta en cuantas respuestas de una pregunta aparece cada grupo de palabras clave. */
    public static List<ConteoPalabra> contarPalabrasClave(int pregunta) throws SQLException {
        List<String> respuestas = obtenerRespuestasTexto(pregunta);
        List<ConteoPalabra> res = new ArrayList<>();
        int total = respuestas.size();
        if (total == 0) return res;

        for (Map.Entry<String, String[]> e : DICCIONARIO.entrySet()) {
            int n = 0;
            for (String txt : respuestas) {
                String norm = " " + normalizar(txt) + " ";
                for (String sin : e.getValue()) {
                    if (norm.contains(" " + normalizar(sin) + " ")) { n++; break; }
                }
            }
            if (n > 0) {
                double pct = Math.round(1000.0 * n / total) / 10.0;
                res.add(new ConteoPalabra(e.getKey(), n, pct));
            }
        }
        res.sort((a, b) -> Integer.compare(b.getTotal(), a.getTotal()));
        return res;
    }

    //  EVALUACION DE BRECHAS Y COMPETENCIAS
    private static String claveEnvio(String alias) {
        return "COALESCE(" + alias + ".envio_id::text, " + alias + ".encuesta_id::text || '|' || " + alias + ".fecha_registro::text)";
    }

    private static double porcentaje(int parte, int total) {
        return total > 0 ? Math.round(1000.0 * parte / total) / 10.0 : 0.0;
    }

    private static double redondear1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static List<String> separarOpciones(String texto) {
        List<String> items = new ArrayList<>();
        if (texto == null) return items;
        int nivel = 0;
        StringBuilder actual = new StringBuilder();
        for (char ch : texto.toCharArray()) {
            if (ch == '(') nivel++;
            if (ch == ')') nivel = Math.max(0, nivel - 1);
            if (ch == ',' && nivel == 0) {
                String t = actual.toString().trim();
                if (!t.isEmpty()) items.add(t);
                actual.setLength(0);
            } else {
                actual.append(ch);
            }
        }
        String t = actual.toString().trim();
        if (!t.isEmpty()) items.add(t);
        return items;
    }

    private static final String[] ETIQUETAS_LIKERT = {
        "Totalmente en desacuerdo", "En desacuerdo", "De acuerdo", "Totalmente de Acuerdo"
    };

    private static int valorLikert(String etiqueta) {
        switch (etiqueta) {
            case "Totalmente en desacuerdo": return 1;
            case "En desacuerdo": return 2;
            case "De acuerdo": return 3;
            case "Totalmente de Acuerdo": return 4;
            default: return -1;
        }
    }

    private static ConteoRespuesta nuevoConteo(int pregunta, String respuesta, int total, int denominador) {
        ConteoRespuesta c = new ConteoRespuesta();
        c.setPreguntaId(pregunta);
        c.setRespuesta(respuesta);
        c.setTotal(total);
        c.setPorcentaje(porcentaje(total, denominador));
        return c;
    }

    /** Media, desviacion estandar y distribucion de una escala Likert de 4 puntos. */
    public static IndicadorEscala escalaIndicador(int pregunta, String etiqueta) throws SQLException {
        IndicadorEscala ind = new IndicadorEscala();
        ind.setPregunta(pregunta);
        ind.setEtiqueta(etiqueta);

        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (String e : ETIQUETAS_LIKERT) conteo.put(e, 0);

        double suma = 0, suma2 = 0;
        int n = 0;
        String sql = "SELECT TRIM(respuesta) AS r FROM respuestas "
                   + "WHERE pregunta_id = ? AND respuesta IS NOT NULL AND TRIM(respuesta) <> ''";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, pregunta);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String r = rs.getString("r");
                    int v = valorLikert(r);
                    if (v > 0) { suma += v; suma2 += (double) v * v; n++; }
                    conteo.merge(r, 1, Integer::sum);
                }
            }
        }

        ind.setN(n);
        if (n > 0) {
            double media = suma / n;
            double var = (suma2 / n) - (media * media);
            ind.setMedia(redondear1(media));
            ind.setDesviacion(redondear1(Math.sqrt(Math.max(var, 0))));
        }

        int total = 0;
        for (int c : conteo.values()) total += c;
        List<ConteoRespuesta> dist = new ArrayList<>();
        for (Map.Entry<String, Integer> e : conteo.entrySet()) {
            if (e.getValue() == 0) continue;
            dist.add(nuevoConteo(pregunta, e.getKey(), e.getValue(), total));
        }
        ind.setDistribucion(dist);
        return ind;
    }

    public static List<ConteoRespuesta> asignaturasFrecuencia(int pregunta) throws SQLException {
        List<String> respuestas = obtenerRespuestasTexto(pregunta);
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (String r : respuestas)
            for (String a : separarOpciones(r)) conteo.merge(a, 1, Integer::sum);

        int total = respuestas.size();
        List<ConteoRespuesta> lista = new ArrayList<>();
        for (Map.Entry<String, Integer> e : conteo.entrySet())
            lista.add(nuevoConteo(pregunta, e.getKey(), e.getValue(), total));
        lista.sort((a, b) -> Integer.compare(b.getTotal(), a.getTotal()));
        return lista;
    }

    /** Cruce entre una pregunta multiseleccion (asignaturas) y una pregunta simple. */
    public static List<CeldaCruce> cruceAsignaturaVariable(int pAsignatura, int pVariable) throws SQLException {
        List<CeldaCruce> lista = new ArrayList<>();
        Map<String, Map<String, Integer>> mapa = new LinkedHashMap<>();
        String sql = "SELECT a.respuesta AS asignaturas, TRIM(b.respuesta) AS variable "
                   + "FROM respuestas a JOIN respuestas b ON " + claveEnvio("b") + " = " + claveEnvio("a") + " "
                   + "WHERE a.pregunta_id = ? AND b.pregunta_id = ? "
                   + "AND COALESCE(TRIM(a.respuesta),'') <> '' AND COALESCE(TRIM(b.respuesta),'') <> ''";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, pAsignatura);
            stmt.setInt(2, pVariable);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String variable = rs.getString("variable");
                    for (String a : separarOpciones(rs.getString("asignaturas")))
                        mapa.computeIfAbsent(a, k -> new LinkedHashMap<>()).merge(variable, 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<String, Map<String, Integer>> fila : mapa.entrySet())
            for (Map.Entry<String, Integer> col : fila.getValue().entrySet()) {
                CeldaCruce c = new CeldaCruce();
                c.setValorX(fila.getKey());
                c.setValorY(col.getKey());
                c.setTotal(col.getValue());
                lista.add(c);
            }
        return lista;
    }

    public static int contarEnvios(int... preguntas) throws SQLException {
        if (preguntas == null || preguntas.length == 0) return 0;
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < preguntas.length; i++) in.append(i == 0 ? "?" : ",?");
        String sql = "SELECT COUNT(DISTINCT " + claveEnvio("r") + ") FROM respuestas r "
                   + "WHERE r.pregunta_id IN (" + in + ") AND r.respuesta IS NOT NULL AND TRIM(r.respuesta) <> ''";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < preguntas.length; i++) stmt.setInt(i + 1, preguntas[i]);
            try (ResultSet rs = stmt.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        }
        return 0;
    }

    /** Asignaturas del titulado que se corresponden con cada area del diccionario. */
    private static final Map<String, String[]> ASIG_POR_AREA = new LinkedHashMap<>();
    static {
        ASIG_POR_AREA.put("IA / Machine Learning", new String[]{"Inteligencia Artificial"});
        ASIG_POR_AREA.put("Base de Datos / SQL", new String[]{"Base de Datos"});
        ASIG_POR_AREA.put("Redes", new String[]{"Redes de Computadora", "Tecnologia Redes Avanzadas"});
        ASIG_POR_AREA.put("Gestion de Proyectos", new String[]{"Planificación de Proyectos y Gestión"});
    }

    private static boolean mencionaAlguna(List<String> textos, String[] claves) {
        for (String t : textos) {
            String norm = " " + normalizar(t) + " ";
            for (String c : claves)
                if (norm.contains(" " + normalizar(c) + " ")) return true;
        }
        return false;
    }

    private static boolean tieneAsignatura(List<String> respuestas, String[] asignaturas) {
        for (String r : respuestas) {
            List<String> seleccion = separarOpciones(r);
            for (String a : asignaturas)
                for (String s : seleccion)
                    if (normalizar(s).equals(normalizar(a))) return true;
        }
        return false;
    }

    // Radar de brechas:
    public static List<DimensionRadar> radarBrechas() throws SQLException {
        //Estado Objetivo: agrupar textos del empleador por envio 
        Map<String, List<String>> porEmpleador = new LinkedHashMap<>();
        String sqlE = "SELECT " + claveEnvio("r") + " AS k, TRIM(r.respuesta) AS resp FROM respuestas r "
                    + "WHERE r.pregunta_id IN (59,60,61) AND r.respuesta IS NOT NULL AND TRIM(r.respuesta) <> ''";
        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sqlE)) {
            while (rs.next())
                porEmpleador.computeIfAbsent(rs.getString("k"), k -> new ArrayList<>()).add(rs.getString("resp"));
        }
        int totalEmpleadores = porEmpleador.size();

        //Estado Actual: asignaturas de ventaja por titulado 
        Map<String, List<String>> porTitulado = new LinkedHashMap<>();
        String sqlT = "SELECT " + claveEnvio("r") + " AS k, TRIM(r.respuesta) AS resp FROM respuestas r "
                    + "WHERE r.pregunta_id = 42 AND r.respuesta IS NOT NULL AND TRIM(r.respuesta) <> ''";
        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sqlT)) {
            while (rs.next())
                porTitulado.computeIfAbsent(rs.getString("k"), k -> new ArrayList<>()).add(rs.getString("resp"));
        }
        int totalTitulados = porTitulado.size();

        List<DimensionRadar> lista = new ArrayList<>();
        for (Map.Entry<String, String[]> area : DICCIONARIO.entrySet()) {
            int obj = 0;
            for (List<String> resp : porEmpleador.values())
                if (mencionaAlguna(resp, area.getValue())) obj++;

            int act = 0;
            String[] asigs = ASIG_POR_AREA.get(area.getKey());
            if (asigs != null) {
                for (List<String> resp : porTitulado.values())
                    if (tieneAsignatura(resp, asigs)) act++;
            }

            double objetivo = porcentaje(obj, totalEmpleadores);
            double actual = porcentaje(act, totalTitulados);
            if (objetivo > 0 || actual > 0)
                lista.add(new DimensionRadar(area.getKey(), objetivo, actual));
        }
        return lista;
    }

    public static List<AreaEmpleabilidad> analisisEmpleabilidad() throws SQLException {
        // Empleadores
        Map<String, List<String>> porEmpleador = new LinkedHashMap<>();
        String sqlE = "SELECT " + claveEnvio("r") + " AS k, TRIM(r.respuesta) AS resp FROM respuestas r "
                    + "WHERE r.pregunta_id IN (59,60,61) AND r.respuesta IS NOT NULL AND TRIM(r.respuesta) <> ''";
        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sqlE)) {
            while (rs.next())
                porEmpleador.computeIfAbsent(rs.getString("k"), k -> new ArrayList<>()).add(rs.getString("resp"));
        }
        int totalEmpleadores = porEmpleador.size();

        Map<String, List<String>> porTitulado = new LinkedHashMap<>();
        String sqlT = "SELECT " + claveEnvio("r") + " AS k, TRIM(r.respuesta) AS resp FROM respuestas r "
                    + "WHERE r.pregunta_id = 42 AND r.respuesta IS NOT NULL AND TRIM(r.respuesta) <> ''";
        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sqlT)) {
            while (rs.next())
                porTitulado.computeIfAbsent(rs.getString("k"), k -> new ArrayList<>()).add(rs.getString("resp"));
        }
        int totalTitulados = porTitulado.size();

        List<AreaEmpleabilidad> lista = new ArrayList<>();
        for (Map.Entry<String, String[]> area : DICCIONARIO.entrySet()) {
            int obj = 0;
            for (List<String> resp : porEmpleador.values())
                if (mencionaAlguna(resp, area.getValue())) obj++;

            int act = 0;
            String[] asigs = ASIG_POR_AREA.get(area.getKey());
            if (asigs != null) {
                for (List<String> resp : porTitulado.values())
                    if (tieneAsignatura(resp, asigs)) act++;
            }

            double demanda = porcentaje(obj, totalEmpleadores);
            double interes = porcentaje(act, totalTitulados);
            if (demanda > 0 || interes > 0) {
                lista.add(new AreaEmpleabilidad(area.getKey(), demanda, interes));
            }
        }
        lista.sort((a, b) -> Double.compare(b.getEmpleabilidad(), a.getEmpleabilidad()));
        return lista;
    }

    // CHI-CUADRADO 
    private static double logGamma(double x) {
        double[] cof = {76.18009172947146, -86.50532032941677, 24.01409824083091,
                        -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};
        double ser = 1.000000000190015;
        double xx = x;
        double y = x;
        double tmp = xx + 5.5;
        tmp -= (xx + 0.5) * Math.log(tmp);
        for (int j = 0; j < 6; j++) {
            ser += cof[j] / (++y);
        }
        return -tmp + Math.log(2.5066282746310005 * ser / xx);
    }

    private static double gammaQ(double s, double x) {
        if (x < 0.0) return 1.0;
        if (x == 0.0) return 1.0;
        double igamma = regularizedLowerIncompleteGamma(s, x);
        return 1.0 - igamma;
    }

    // Aproximación para Gamma regularizada incompleta inferior P(s,x)
    private static double regularizedLowerIncompleteGamma(double s, double x) {
        if (x <= 0.0) return 0.0;
        if (x < s + 1.0) {
            double ap = s;
            double sum = 1.0 / s;
            double del = sum;
            for (int n = 1; n < 1000; n++) {
                ap += 1.0;
                del *= x / ap;
                sum += del;
                if (Math.abs(del) < Math.abs(sum) * 1e-12) break;
            }
            return sum * Math.exp(-x + s * Math.log(x) - logGamma(s));
        } else {
            // Continuación fraccional
            double a0 = 1.0;
            double a1 = x;
            double b0 = 0.0;
            double b1 = 1.0;
            double fac = 1.0;
            double g = b1;
            for (int n = 1; n < 1000; n++) {
                double an = n - s;
                double ana = an * a0 + x * a1;
                double anb = an * b0 + x * b1;
                a0 = a1; a1 = ana;
                b0 = b1; b1 = anb;
                if (Math.abs(a1) > 1e-30) {
                    fac = 1.0 / a1;
                    g = b1 * fac;
                    if (Math.abs(fac * (b1 - g * a1)) < 1e-12) break;
                }
            }
            return 1.0 - Math.exp(-x + s * Math.log(x) - logGamma(s)) * g;
        }
    }

    private static double chi2PValue(double chi2, int df) {
        if (df <= 0) return 1.0;
        if (chi2 <= 0.0) return 1.0;
        double p = gammaQ(df / 2.0, chi2 / 2.0);
        if (p < 0.0) p = 0.0;
        if (p > 1.0) p = 1.0;
        return Math.round(p * 1000.0) / 1000.0;
    }

    private static String interpretarChi2(double chi2, int df, double p) {
        if (df <= 0) return "Sin grados de libertad. No es posible aplicar Chi-cuadrado.";
        StringBuilder sb = new StringBuilder();
        sb.append("Chi-cuadrado (χ²)=").append(String.format("%.3f", chi2))
          .append(", gl=" + df + ", p=" + p);
        if (p < 0.05) {
            sb.append(" (p < 0.05). Se rechaza la hipótesis nula de independencia. Existe asociación significativa entre nivel de posgrado deseado y fuente de financiamiento.");
        } else {
            sb.append(" (p >= 0.05). No se rechaza la hipótesis nula de independencia. No hay evidencia suficiente de asociación significativa entre ambas variables.");
        }
        sb.append(" Nivel de confianza aproximado 95%.");
        return sb.toString();
    }

    /**Formación continua: áreas de interés de posgrado tomadas directamente de.*/
    public static List<AreaInteresPosgrado> areasInteresPosgrado() throws SQLException {
        List<String> respuestas = obtenerRespuestasTexto(9);
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (String r : respuestas) {
            String t = r == null ? "" : r.trim();
            if (!t.isEmpty()) conteo.merge(t, 1, Integer::sum);
        }
        int total = respuestas.size();
        if (total == 0) return new ArrayList<>();

        List<AreaInteresPosgrado> lista = new ArrayList<>();
        for (Map.Entry<String, Integer> e : conteo.entrySet()) {
            lista.add(new AreaInteresPosgrado(e.getKey(), e.getValue(), porcentaje(e.getValue(), total)));
        }
        lista.sort((a, b) -> Integer.compare(b.getN(), a.getN()));
        return lista;
    }

        /** Niveles/tipos de posgrado (titulados)*/
    public static List<ConteoRespuesta> nivelesFormacionDeseados() throws SQLException {
        List<ConteoRespuesta> lista = contarRespuestas(4);
        lista.sort((a, b) -> Integer.compare(b.getTotal(), a.getTotal()));
        return lista;
    }

    /** Retorna true si la suma de los 2 principales >= 70% (alerta de concentración crítica). */
    public static boolean alertaConcentracionCritica(List<AreaInteresPosgrado> areas) {
        if (areas == null || areas.size() < 2) return false;
        double sumaTop2 = areas.get(0).getPorcentaje() + areas.get(1).getPorcentaje();
        return sumaTop2 >= 70.0;
    }

        /** Tabla de contingencia: Nivel/Tipo de posgrado × Fuente de financiamiento.
     *  Calcula Chi-cuadrado, gl, valor p e interpretación. */
    public static Map<String, Object> tablaFinanciamiento() throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();

        // Pares (nivel, financiamiento) enlazados por envío (fallback)
        String sql = "SELECT TRIM(a.respuesta) AS nivel, TRIM(b.respuesta) AS financiamiento "
                   + "FROM respuestas a JOIN respuestas b ON " + claveEnvio("b") + " = " + claveEnvio("a") + " "
                   + "WHERE a.pregunta_id = 4 AND b.pregunta_id = 6 "
                   + "AND COALESCE(TRIM(a.respuesta),'') <> '' AND COALESCE(TRIM(b.respuesta),'') <> ''";
        List<String[]> pares = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                pares.add(new String[]{rs.getString("nivel"), rs.getString("financiamiento")});
            }
        }

        // Filas y columnas únicas
        LinkedHashSet<String> filasSet = new LinkedHashSet<>();
        LinkedHashSet<String> colsSet = new LinkedHashSet<>();
        for (String[] p : pares) {
            if (p[0] != null && !p[0].isBlank()) filasSet.add(p[0].trim());
            if (p[1] != null && !p[1].isBlank()) colsSet.add(p[1].trim());
        }
        ArrayList<String> filas = new ArrayList<>(filasSet);
        ArrayList<String> columnas = new ArrayList<>(colsSet);
        filas.sort(String::compareTo);
        columnas.sort(String::compareTo);
        LinkedHashMap<String, Integer> O = new LinkedHashMap<>();
        for (String f : filas)
            for (String c : columnas)
                O.put(f + "||" + c, 0);
        for (String[] p : pares) {
            String f = p[0] == null ? "" : p[0].trim();
            String c = p[1] == null ? "" : p[1].trim();
            String key = f + "||" + c;
            if (O.containsKey(key)) O.put(key, O.getOrDefault(key, 0) + 1);
        }

        // Totales
        long[] R = new long[filas.size()];
        long[] C = new long[columnas.size()];
        long N = 0;
        for (int i = 0; i < filas.size(); i++) {
            for (int j = 0; j < columnas.size(); j++) {
                int v = O.get(filas.get(i) + "||" + columnas.get(j));
                R[i] += v; C[j] += v; N += v;
            }
        }

        ArrayList<CeldaCruce> celdasObs = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            for (int j = 0; j < columnas.size(); j++) {
                int v = O.get(filas.get(i) + "||" + columnas.get(j));
                CeldaCruce cc = new CeldaCruce();
                cc.setValorX(filas.get(i));
                cc.setValorY(columnas.get(j));
                cc.setTotal(v);
                celdasObs.add(cc);
            }
        }

        // Chi-cuadrado
        double chi2 = 0.0;
        boolean tieneDatos = N >= 5 && filas.size() >= 2 && columnas.size() >= 2;
        if (tieneDatos) {
            for (int i = 0; i < filas.size(); i++) {
                for (int j = 0; j < columnas.size(); j++) {
                    int v = O.get(filas.get(i) + "||" + columnas.get(j));
                    double Eij = ((double) R[i] * (double) C[j]) / (double) N;
                    if (Eij >= 1e-12) {
                        chi2 += ((double) v - Eij) * ((double) v - Eij) / Eij;
                    }
                }
            }
        }
        int df = Math.max(0, (filas.size() - 1) * (columnas.size() - 1));
        double p = 1.0;
        if (tieneDatos && df >= 1) {
            p = chi2PValue(chi2, df);
        } else {
            p = 1.0;
        }

        ResultadoChi2 rchi = new ResultadoChi2();
        rchi.setChiCuadrado(Math.round(chi2 * 1000.0) / 1000.0);
        rchi.setGradosLibertad(df);
        rchi.setValorP(p);
        rchi.setIndependiente(p >= 0.05);
        rchi.setInterpretacion(interpretarChi2(chi2, df, p));

        LinkedHashMap<String, Integer> conteoFilas = new LinkedHashMap<>();
        for (int i = 0; i < filas.size(); i++) conteoFilas.put(filas.get(i), (int) R[i]);
        LinkedHashMap<String, Integer> conteoCols = new LinkedHashMap<>();
        for (int j = 0; j < columnas.size(); j++) conteoCols.put(columnas.get(j), (int) C[j]);

        out.put("filas", filas);
        out.put("columnas", columnas);
        out.put("conteoFilas", conteoFilas);
        out.put("conteoCols", conteoCols);
        out.put("celdas", celdasObs);
        out.put("tieneDatos", celdasObs.size() > 0 && N > 0);
        out.put("totalN", (int) N);
        out.put("chi2", rchi);
        return out;
    }
}