package com.umss.dao;

import org.apache.poi.ss.usermodel.*;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExcelImporterService {

    private static final double UMBRAL = 0.60;
    // Diferencia mínima entre el mejor y el segundo mejor para evitar asignaciones ambiguas. 
    private static final double MARGEN_AMBIGUEDAD = 0.05;
    private static final int LONGITUD_MIN_CONTENCION = 12;
    private static final int MIN_PALABRAS_SOLAPADAS = 3;
    private static final int MIN_RESPUESTAS_DEDUPE = 15;

    // Numeración inicial de las preguntas: "1)", "2)", "3.", "4 -" (se ignora al comparar). 
    private static final Pattern PATRON_NUMERACION = Pattern.compile("^\\s*\\d{1,3}\\s*[).:,-]\\s*");
    private static final Pattern PATRON_CODIGO =
            Pattern.compile("^\\s*(?:pregunta|p)\\s*[_-]?\\s*(\\d{1,3})\\s*[).:,-]?\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "y", "o", "u",
            "a", "al", "en", "con", "por", "para", "que", "se", "su", "sus", "es", "son", "lo",
            "le", "les", "me", "mi", "como", "mas", "muy", "ya", "si", "no", "ni",
            "cual", "cuales", "donde", "cuantos", "cuanto", "ha", "han", "he",
            "esta", "estan", "hay", "fue", "ser", "entre", "sobre", "desde", "hasta",
            "mismo", "misma", "dicho", "dicha"));

    private static volatile long ultimaBaseMillis = 0;

    public static class Resultado {
        public int encuestas;
        public int respuestas;
        public int columnas;        // columnas asociadas a una pregunta de la BD
        public int columnasTotales; // columnas con encabezado no vacío
        public int duplicadas;      // filas omitidas porque ya existían en la BD

        public String mensaje() {
            StringBuilder sb = new StringBuilder();
            sb.append(encuestas).append(" encuesta(s) importada(s), ")
              .append(respuestas).append(" respuesta(s) en ")
              .append(columnas).append(" de ").append(columnasTotales).append(" columna(s).");
            if (duplicadas > 0) {
                sb.append(" Se omitieron ").append(duplicadas)
                  .append(" fila(s) porque ya existían en la base de datos.");
            }
            return sb.toString();
        }
    }

    public Resultado importar(InputStream excelInputStream, int encuestaId, Connection conn) throws Exception {
        Resultado res = new Resultado();

        try (Workbook workbook = WorkbookFactory.create(excelInputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            int filaEncabezado = buscarFilaEncabezado(sheet);
            if (filaEncabezado < 0 || filaEncabezado >= sheet.getLastRowNum()) {
                throw new IllegalArgumentException("El archivo no tiene datos: se esperaba una fila de preguntas y al menos una fila de respuestas.");
            }

            Map<Integer, List<PreguntaBd>> preguntasPorEncuesta = cargarPreguntasPorEncuesta(conn);
            List<PreguntaBd> preguntas = preguntasPorEncuesta.getOrDefault(encuestaId, List.of());
            if (preguntas.isEmpty()) {
                throw new IllegalArgumentException("La encuesta " + encuestaId + " no tiene preguntas registradas en la base de datos.");
            }

            Row filaCabecera = sheet.getRow(filaEncabezado);
            Emparejamiento emparejamiento = emparejar(filaCabecera, preguntas);
            res.columnasTotales = emparejamiento.columnasTotales;

            // Evita importar, por ejemplo, el Excel de empleadores en la encuesta de titulados.
            // Se valida antes del mensaje genérico para poder indicar a qué encuesta pertenece el archivo.
            validarEncuesta(filaCabecera, encuestaId, preguntasPorEncuesta, emparejamiento, cargarTitulos(conn));

            if (emparejamiento.asignaciones.isEmpty()) {
                throw new IllegalArgumentException("Ninguna columna del Excel coincidió con las preguntas de la encuesta "
                        + encuestaId + ". Revise que la fila 1 contenga el texto de las preguntas.");
            }

            res.columnas = emparejamiento.asignaciones.size();
            insertarRespuestas(sheet, filaEncabezado, emparejamiento.asignaciones, encuestaId, conn, res);
        }
        return res;
    }

    public int importarRespuestasExcel(InputStream excelInputStream, int encuestaId, Connection connection) throws Exception {
        return importar(excelInputStream, encuestaId, connection).encuestas;
    }

    private static int buscarFilaEncabezado(Sheet sheet) {
        for (int i = sheet.getFirstRowNum(); i <= sheet.getLastRowNum(); i++) {
            Row fila = sheet.getRow(i);
            if (fila != null && !filaVacia(fila)) return i;
        }
        return -1;
    }

    /**
     * Compara los encabezados del Excel contra las preguntas de la encuesta solicitada
     * y contra las de las demás encuestas. Si otra encuesta coincide mejor, rechaza
     * la importación para no mezclar datos de titulados con empleadores.
     */
    private static void validarEncuesta(Row headerRow, int encuestaId,
                                        Map<Integer, List<PreguntaBd>> preguntasPorEncuesta,
                                        Emparejamiento propio,
                                        Map<Integer, String> titulos) {
        int mejorId = encuestaId;
        Emparejamiento mejor = propio;

        for (Map.Entry<Integer, List<PreguntaBd>> e : preguntasPorEncuesta.entrySet()) {
            if (e.getKey() == encuestaId) continue;
            Emparejamiento otra = emparejar(headerRow, e.getValue());
            if (otra.columnas > mejor.columnas
                    || (otra.columnas == mejor.columnas && otra.sumaScore > mejor.sumaScore)) {
                mejor = otra;
                mejorId = e.getKey();
            }
        }

        if (mejorId != encuestaId) {
            throw new IllegalArgumentException(
                    "El archivo parece corresponder a la encuesta de " + titulo(titulos, mejorId)
                    + " (" + mejor.columnas + " preguntas reconocidas), pero se intentó importar en la encuesta de "
                    + titulo(titulos, encuestaId) + ". Use el botón de importación correspondiente.");
        }
    }

    private static String titulo(Map<Integer, String> titulos, int encuestaId) {
        String t = titulos.get(encuestaId);
        return t == null || t.isBlank() ? "encuesta " + encuestaId : t.toLowerCase(Locale.ROOT);
    }

    private static Emparejamiento emparejar(Row headerRow, List<PreguntaBd> preguntas) {
        List<Candidato> candidatos = new ArrayList<>();
        int columnasTotales = 0;

        for (Cell celda : headerRow) {
            String encabezado = textoCelda(celda);
            String encNorm = normalizar(encabezado);
            if (encNorm.isEmpty()) continue;
            columnasTotales++;

            Matcher m = PATRON_CODIGO.matcher(encabezado.trim());
            if (m.matches()) {
                PreguntaBd objetivo = porId(preguntas, Integer.parseInt(m.group(1)));
                if (objetivo != null) {
                    candidatos.add(new Candidato(celda.getColumnIndex(), encabezado, objetivo, 1.0));
                    continue;
                }
            }

            // Similitud difusa: mejor y segundo mejor puntaje
            Set<String> encTokens = tokenizar(encNorm);
            double mejor = -1, segundo = -1;
            PreguntaBd mejorPregunta = null;
            for (PreguntaBd p : preguntas) {
                double s = similitud(encNorm, p.norm, encTokens, p.tokens);
                if (s > mejor) { segundo = mejor; mejor = s; mejorPregunta = p; }
                else if (s > segundo) { segundo = s; }
            }

            if (mejorPregunta == null || mejor < UMBRAL) continue;                    
            if (segundo >= UMBRAL && (mejor - segundo) < MARGEN_AMBIGUEDAD) continue;  

            candidatos.add(new Candidato(celda.getColumnIndex(), encabezado, mejorPregunta, mejor));
        }

        // El mejor puntaje gana: cada pregunta recibe como máximo UNA columna
        candidatos.sort((a, b) -> Double.compare(b.score, a.score));
        Set<Integer> preguntasUsadas = new HashSet<>();
        Map<Integer, Candidato> elegidas = new LinkedHashMap<>();

        for (Candidato cd : candidatos) {
            if (preguntasUsadas.add(cd.pregunta.id)) elegidas.put(cd.colIndex, cd);
        }

        List<Asignacion> asignaciones = new ArrayList<>();
        double sumaScore = 0;
        for (Candidato cd : elegidas.values()) {
            asignaciones.add(new Asignacion(cd.colIndex, cd.pregunta.id));
            sumaScore += cd.score;
        }
        asignaciones.sort(Comparator.comparingInt(a -> a.colIndex));
        return new Emparejamiento(asignaciones, sumaScore, columnasTotales);
    }

    private static void insertarRespuestas(Sheet sheet, int filaEncabezado, List<Asignacion> asignaciones,
                                           int encuestaId, Connection conn, Resultado res) throws Exception {
        long base;
        synchronized (ExcelImporterService.class) {
            long ahora = System.currentTimeMillis();
            base = Math.max(ahora, ultimaBaseMillis + (sheet.getLastRowNum() - filaEncabezado) + 2);
            ultimaBaseMillis = base;
        }

        Set<String> huellasExistentes = cargarHuellasExistentes(conn);

        String sql = "INSERT INTO respuestas (encuesta_id, pregunta_id, respuesta, fecha_registro, envio_id) "
                   + "VALUES (?, ?, ?, ?, ?::uuid)";

        int insertadas = 0, filas = 0, duplicadas = 0;
        conn.setAutoCommit(false);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = filaEncabezado + 1; i <= sheet.getLastRowNum(); i++) {
                Row fila = sheet.getRow(i);
                if (fila == null || filaVacia(fila)) continue;

                Map<Integer, String> valores = new LinkedHashMap<>();
                for (Asignacion a : asignaciones) {
                    String valor = textoCelda(fila.getCell(a.colIndex)).trim();
                    if (!valor.isEmpty()) valores.put(a.preguntaId, valor);
                }
                if (valores.isEmpty()) continue;

                // Si la misma combinación de respuestas ya está en la BD, no se inserta otra vez.
                String huella = huella(encuestaId, valores);
                if (huella != null && !huellasExistentes.add(huella)) {
                    duplicadas++;
                    continue;
                }

                String envioId = UUID.randomUUID().toString();
                Timestamp fecha = new Timestamp(base + (i - filaEncabezado));

                for (Map.Entry<Integer, String> e : valores.entrySet()) {
                    stmt.setInt(1, encuestaId);
                    stmt.setInt(2, e.getKey());
                    stmt.setString(3, e.getValue());
                    stmt.setTimestamp(4, fecha);
                    stmt.setString(5, envioId);
                    stmt.addBatch();
                    insertadas++;
                }
                filas++;
            }
            stmt.executeBatch();
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
        res.encuestas = filas;
        res.respuestas = insertadas;
        res.duplicadas = duplicadas;
    }

    /**
     * Huella estable de una fila: encuesta + pares (pregunta, respuesta normalizada) ordenados.
     * Devuelve null si la fila tiene muy pocas respuestas (para no confundir encuestados distintos).
     */
    static String huella(int encuestaId, Map<Integer, String> valores) {
        List<String> partes = new ArrayList<>();
        for (Map.Entry<Integer, String> e : valores.entrySet()) {
            String v = normalizarValor(e.getValue());
            if (!v.isEmpty()) partes.add(e.getKey() + "=" + v);
        }
        if (partes.size() < MIN_RESPUESTAS_DEDUPE) return null;
        Collections.sort(partes);
        return encuestaId + "|" + String.join("|", partes);
    }

    //Huellas de todos los envíos ya guardados, para detectar archivos ya importados.
    private static Set<String> cargarHuellasExistentes(Connection conn) throws Exception {
        Set<String> huellas = new HashSet<>();
        Map<String, List<String>> porEnvio = new LinkedHashMap<>();

        String sql = "SELECT encuesta_id, envio_id, pregunta_id, respuesta FROM respuestas "
                   + "WHERE envio_id IS NOT NULL ORDER BY encuesta_id, envio_id, pregunta_id";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String clave = rs.getInt("encuesta_id") + ":" + rs.getString("envio_id");
                String valor = normalizarValor(rs.getString("respuesta"));
                if (valor.isEmpty()) continue;
                porEnvio.computeIfAbsent(clave, k -> new ArrayList<>())
                        .add(rs.getInt("pregunta_id") + "=" + valor);
            }
        }

        for (Map.Entry<String, List<String>> e : porEnvio.entrySet()) {
            if (e.getValue().size() < MIN_RESPUESTAS_DEDUPE) continue;
            Collections.sort(e.getValue());
            String encuestaId = e.getKey().substring(0, e.getKey().indexOf(':'));
            huellas.add(encuestaId + "|" + String.join("|", e.getValue()));
        }
        return huellas;
    }

    // Minúsculas, sin tildes y con espacios colapsados; solo para comparar contenido. 
    private static String normalizarValor(String texto) {
        if (texto == null) return "";
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static double similitud(String aNorm, String bNorm, Set<String> aTokens, Set<String> bTokens) {
        if (aNorm.isEmpty() || bNorm.isEmpty()) return 0;
        if (aNorm.equals(bNorm)) return 1.0;

        double mejor = 0;
        String corto = aNorm.length() <= bNorm.length() ? aNorm : bNorm;
        String largo = corto.equals(aNorm) ? bNorm : aNorm;
        if (corto.length() >= LONGITUD_MIN_CONTENCION && largo.contains(corto)) {
            mejor = Math.max(mejor, 0.75 + 0.25 * ((double) corto.length() / largo.length()));
        }

        // tolera typos y variaciones
        mejor = Math.max(mejor, diceBigramas(aNorm, bNorm));
        mejor = Math.max(mejor, jaccard(aTokens, bTokens));

        // Solapamiento: cubre el caso de una versión más corta de la misma pregunta
        mejor = Math.max(mejor, solapamiento(aTokens, bTokens));

        return Math.min(mejor, 1.0);
    }

    private static double diceBigramas(String a, String b) {
        if (a.length() < 2 || b.length() < 2) return a.equals(b) ? 1.0 : 0.0;
        Map<String, Integer> freq = new HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) freq.merge(a.substring(i, i + 2), 1, Integer::sum);
        int comun = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String g = b.substring(i, i + 2);
            Integer c = freq.get(g);
            if (c != null && c > 0) { freq.put(g, c - 1); comun++; }
        }
        return (2.0 * comun) / ((a.length() - 1) + (b.length() - 1));
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int comun = 0;
        for (String s : a) if (b.contains(s)) comun++;
        int union = a.size() + b.size() - comun;
        return union == 0 ? 0 : (double) comun / union;
    }

    private static double solapamiento(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int min = Math.min(a.size(), b.size());
        if (min < MIN_PALABRAS_SOLAPADAS) return 0;
        int comun = 0;
        for (String s : a) if (b.contains(s)) comun++;
        return (double) comun / min;
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            sb.append(Character.isLetterOrDigit(ch) ? ch : ' ');
        }
        s = sb.toString().replaceAll("\\s+", " ").trim();
        // Quita la numeración inicial ("1)", "2)") para que no influya en la comparación
        return PATRON_NUMERACION.matcher(s).replaceFirst("").trim();
    }

    private static Set<String> tokenizar(String normalizado) {
        Set<String> tokens = new HashSet<>();
        for (String w : normalizado.split(" ")) {
            if (w.isEmpty() || STOPWORDS.contains(w)) continue;
            if (w.length() > 3 && w.endsWith("s")) w = w.substring(0, w.length() - 1);
            tokens.add(w);
        }
        return tokens;
    }

    private static Map<Integer, List<PreguntaBd>> cargarPreguntasPorEncuesta(Connection conn) throws Exception {
        Map<Integer, List<PreguntaBd>> mapa = new LinkedHashMap<>();
        String sql = "SELECT id, encuesta_id, texto_pregunta FROM preguntas ORDER BY encuesta_id, id";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                PreguntaBd p = new PreguntaBd();
                p.id = rs.getInt("id");
                p.texto = rs.getString("texto_pregunta");
                p.norm = normalizar(p.texto);
                p.tokens = tokenizar(p.norm);
                mapa.computeIfAbsent(rs.getInt("encuesta_id"), k -> new ArrayList<>()).add(p);
            }
        }
        return mapa;
    }

    private static Map<Integer, String> cargarTitulos(Connection conn) throws Exception {
        Map<Integer, String> titulos = new LinkedHashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, titulo FROM encuestas ORDER BY id")) {
            while (rs.next()) {
                titulos.put(rs.getInt("id"), rs.getString("titulo"));
            }
        }
        return titulos;
    }

    private static PreguntaBd porId(List<PreguntaBd> preguntas, int id) {
        for (PreguntaBd p : preguntas) if (p.id == id) return p;
        return null;
    }

    private static String textoCelda(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:  return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) return cell.getDateCellValue().toString();
                double v = cell.getNumericCellValue();
                return v == (long) v ? String.valueOf((long) v) : String.valueOf(v);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return cell.getStringCellValue(); } catch (Exception e) { return cell.getCellFormula(); }
            default:      return "";
        }
    }

    private static boolean filaVacia(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !textoCelda(cell).trim().isEmpty()) return false;
        }
        return true;
    }

    private static class PreguntaBd {
        int id; String texto; String norm; Set<String> tokens;
    }

    private static class Candidato {
        final int colIndex; final String encabezado; final PreguntaBd pregunta; final double score;
        Candidato(int colIndex, String encabezado, PreguntaBd pregunta, double score) {
            this.colIndex = colIndex; this.encabezado = encabezado; this.pregunta = pregunta; this.score = score;
        }
    }

    private static class Asignacion {
        final int colIndex; final int preguntaId;
        Asignacion(int colIndex, int preguntaId) { this.colIndex = colIndex; this.preguntaId = preguntaId; }
    }

    private static class Emparejamiento {
        final List<Asignacion> asignaciones;
        final double sumaScore;
        final int columnasTotales;
        final int columnas;

        Emparejamiento(List<Asignacion> asignaciones, double sumaScore, int columnasTotales) {
            this.asignaciones = asignaciones;
            this.sumaScore = sumaScore;
            this.columnasTotales = columnasTotales;
            this.columnas = asignaciones.size();
        }
    }
}
