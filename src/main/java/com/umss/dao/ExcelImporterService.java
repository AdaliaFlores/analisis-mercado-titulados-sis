package com.umss.dao;

import org.apache.poi.ss.usermodel.*;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExcelImporterService {

    /** Similitud mínima (0..1) para aceptar una columna como pregunta de la BD. */
    private static final double UMBRAL = 0.60;
    /** Diferencia mínima entre el mejor y el segundo mejor para evitar asignaciones ambiguas. */
    private static final double MARGEN_AMBIGUEDAD = 0.05;
    private static final int LONGITUD_MIN_CONTENCION = 12;
    private static final int MIN_PALABRAS_SOLAPADAS = 3;

    /** Numeración inicial de las preguntas: "1)", "2)", "3.", "4 -" (se ignora al comparar). */
    private static final Pattern PATRON_NUMERACION = Pattern.compile("^\\s*\\d{1,3}\\s*[).:,-]\\s*");
    /** Encabezado que es solo un código de pregunta: "p4", "P4", "p_04", "pregunta 12". */
    private static final Pattern PATRON_CODIGO =
            Pattern.compile("^\\s*(?:pregunta|p)\\s*[_-]?\\s*(\\d{1,3})\\s*[).:,-]?\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "y", "o", "u",
            "a", "al", "en", "con", "por", "para", "que", "se", "su", "sus", "es", "son", "lo",
            "le", "les", "me", "mi", "como", "mas", "muy", "ya", "si", "no", "ni",
            "cual", "cuales", "donde", "cuantos", "cuanto", "ha", "han", "he",
            "esta", "estan", "hay", "fue", "ser", "entre", "sobre", "desde", "hasta",
            "mismo", "misma", "dicho", "dicha"));

    /** Base de tiempo compartida para que cada encuestado tenga fecha_registro distinto. */
    private static volatile long ultimaBaseMillis = 0;

    public static class Resultado {
        public int encuestas;
        public int respuestas;
        public int columnas;        // columnas asociadas a una pregunta de la BD
        public int columnasTotales; // columnas con encabezado no vacío

        public String mensaje() {
            return encuestas + " encuesta(s) importada(s), " + respuestas + " respuesta(s) en "
                    + columnas + " de " + columnasTotales + " columna(s).";
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

            List<PreguntaBd> preguntas = cargarPreguntas(encuestaId, conn);
            if (preguntas.isEmpty()) {
                throw new IllegalArgumentException("La encuesta " + encuestaId + " no tiene preguntas registradas en la base de datos.");
            }

            List<Asignacion> asignaciones = asignarColumnas(sheet.getRow(filaEncabezado), preguntas, res);
            if (asignaciones.isEmpty()) {
                throw new IllegalArgumentException("Ninguna columna del Excel coincidió con las preguntas de la encuesta "
                        + encuestaId + ". Revise que la fila 1 contenga el texto de las preguntas.");
            }

            insertarRespuestas(sheet, filaEncabezado, asignaciones, encuestaId, conn, res);
        }
        return res;
    }

    /** Compatibilidad: devuelve solo la cantidad de encuestas insertadas. */
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

    private List<Asignacion> asignarColumnas(Row headerRow, List<PreguntaBd> preguntas, Resultado res) {
        List<Candidato> candidatos = new ArrayList<>();

        for (Cell celda : headerRow) {
            String encabezado = textoCelda(celda);
            String encNorm = normalizar(encabezado);
            if (encNorm.isEmpty()) continue;
            res.columnasTotales++;

            Matcher m = PATRON_CODIGO.matcher(encabezado.trim());
            if (m.matches()) {
                PreguntaBd objetivo = porId(preguntas, Integer.parseInt(m.group(1)));
                if (objetivo != null) {
                    candidatos.add(new Candidato(celda.getColumnIndex(), encabezado, objetivo, 1.0));
                    continue;
                }
            }

            //Similitud difusa: mejor y segundo mejor puntaje
            Set<String> encTokens = tokenizar(encNorm);
            double mejor = -1, segundo = -1;
            PreguntaBd mejorPregunta = null;
            for (PreguntaBd p : preguntas) {
                double s = similitud(encNorm, p.norm, encTokens, p.tokens);
                if (s > mejor) { segundo = mejor; mejor = s; mejorPregunta = p; }
                else if (s > segundo) { segundo = s; }
            }

            if (mejorPregunta == null || mejor < UMBRAL) continue;                     // no corresponde
            if (segundo >= UMBRAL && (mejor - segundo) < MARGEN_AMBIGUEDAD) continue;  // ambigua: se omite

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
        for (Candidato cd : elegidas.values()) {
            asignaciones.add(new Asignacion(cd.colIndex, cd.pregunta.id));
        }
        asignaciones.sort(Comparator.comparingInt(a -> a.colIndex));
        res.columnas = asignaciones.size();
        return asignaciones;
    }

    private static void insertarRespuestas(Sheet sheet, int filaEncabezado, List<Asignacion> asignaciones,
                                           int encuestaId, Connection conn, Resultado res) throws Exception {
        // Base de tiempo exclusiva de esta importación: cada encuestado queda con
        // fecha_registro DISTINTO (lo exigen los reportes que cuentan por fecha).
        long base;
        synchronized (ExcelImporterService.class) {
            long ahora = System.currentTimeMillis();
            base = Math.max(ahora, ultimaBaseMillis + (sheet.getLastRowNum() - filaEncabezado) + 2);
            ultimaBaseMillis = base;
        }

        String sql = "INSERT INTO respuestas (encuesta_id, pregunta_id, respuesta, fecha_registro, envio_id) "
                   + "VALUES (?, ?, ?, ?, ?::uuid)";

        int insertadas = 0, filas = 0;
        conn.setAutoCommit(false);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = filaEncabezado + 1; i <= sheet.getLastRowNum(); i++) {
                Row fila = sheet.getRow(i);
                if (fila == null || filaVacia(fila)) continue;

                String envioId = UUID.randomUUID().toString();
                Timestamp fecha = new Timestamp(base + (i - filaEncabezado));
                boolean conRespuestas = false;

                for (Asignacion a : asignaciones) {
                    String valor = textoCelda(fila.getCell(a.colIndex)).trim();
                    if (valor.isEmpty()) continue;
                    stmt.setInt(1, encuestaId);
                    stmt.setInt(2, a.preguntaId);
                    stmt.setString(3, valor);
                    stmt.setTimestamp(4, fecha);
                    stmt.setString(5, envioId);
                    stmt.addBatch();
                    insertadas++;
                    conRespuestas = true;
                }
                if (conRespuestas) filas++;
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
    }

    private static double similitud(String aNorm, String bNorm, Set<String> aTokens, Set<String> bTokens) {
        if (aNorm.isEmpty() || bNorm.isEmpty()) return 0;
        if (aNorm.equals(bNorm)) return 1.0;

        double mejor = 0;

        // Contención: una pregunta contiene (casi) a la otra
        String corto = aNorm.length() <= bNorm.length() ? aNorm : bNorm;
        String largo = corto.equals(aNorm) ? bNorm : aNorm;
        if (corto.length() >= LONGITUD_MIN_CONTENCION && largo.contains(corto)) {
            mejor = Math.max(mejor, 0.75 + 0.25 * ((double) corto.length() / largo.length()));
        }

        //  Dice sobre bigramas (tolera typos y variaciones)
        mejor = Math.max(mejor, diceBigramas(aNorm, bNorm));

        // Jaccard sobre términos significativos
        mejor = Math.max(mejor, jaccard(aTokens, bTokens));

        //  Solapamiento: cubre el caso de una versión más corta de la misma pregunta
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
            if (w.length() > 3 && w.endsWith("s")) w = w.substring(0, w.length() - 1); // programas -> programa
            tokens.add(w);
        }
        return tokens;
    }

    private static List<PreguntaBd> cargarPreguntas(int encuestaId, Connection conn) throws Exception {
        List<PreguntaBd> lista = new ArrayList<>();
        String sql = "SELECT id, texto_pregunta FROM preguntas WHERE encuesta_id = ? ORDER BY id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, encuestaId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PreguntaBd p = new PreguntaBd();
                    p.id = rs.getInt("id");
                    p.texto = rs.getString("texto_pregunta");
                    p.norm = normalizar(p.texto);
                    p.tokens = tokenizar(p.norm);
                    lista.add(p);
                }
            }
        }
        return lista;
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
}
