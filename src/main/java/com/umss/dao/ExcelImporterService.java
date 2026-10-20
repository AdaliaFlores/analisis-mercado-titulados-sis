package com.umss.dao;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.Normalizer;
import java.util.*;

public class ExcelImporterService {

    /**
     * Importa un Excel procesando preguntas en la Fila 1 y respuestas a partir de la Fila 2.
     * Inserta los datos en la tabla 'respuestas' vinculándolos con 'preguntas.id' y generando un 'envio_id' por encuesta realizada.
     * 
     * @param excelInputStream Stream del archivo subido.
     * @param encuestaId ID de la encuesta objetivo (ej. 1 para Titulados).
     * @param connection Conexión activa a PostgreSQL.
     */
    public int importarRespuestasExcel(InputStream excelInputStream, int encuestaId, Connection connection) throws Exception {
        Workbook workbook = new XSSFWorkbook(excelInputStream);
        Sheet sheet = workbook.getSheetAt(0);

        if (sheet.getPhysicalNumberOfRows() < 2) {
            workbook.close();
            return 0;
        }

        // 1. Cargar todas las preguntas de la BD para esta encuesta en memoria para hacer el matching
        Map<String, Integer> mapaPreguntasBd = cargarMapaPreguntas(encuestaId, connection);

        // 2. Leer la Fila 1 (Encabezados / Preguntas del Excel)
        Row headerRow = sheet.getRow(0);
        Map<Integer, Integer> excelColToPreguntaId = new HashMap<>();

        for (Cell cell : headerRow) {
            String preguntaExcel = getCellValueAsString(cell);
            String preguntaNorm = normalizeText(preguntaExcel);

            if (preguntaNorm.isEmpty()) continue;

            // Buscar si la pregunta del Excel coincide con alguna pregunta registrada en la BD
            for (Map.Entry<String, Integer> entry : mapaPreguntasBd.entrySet()) {
                String preguntaBdNorm = entry.getKey();
                int preguntaId = entry.getValue();

                // Coincidencia por subcadena o similitud
                if (preguntaNorm.contains(preguntaBdNorm) || preguntaBdNorm.contains(preguntaNorm)) {
                    excelColToPreguntaId.put(cell.getColumnIndex(), preguntaId);
                    break;
                }
            }
        }

        if (excelColToPreguntaId.isEmpty()) {
            workbook.close();
            throw new IllegalArgumentException("No se encontraron columnas en el Excel que coincidan con las preguntas registradas en la base de datos.");
        }

        // 3. Preparar INSERT para la tabla 'respuestas'
        String sqlInsert = "INSERT INTO respuestas (encuesta_id, pregunta_id, respuesta, fecha_registro, envio_id) " +
                           "VALUES (?, ?, ?, NOW(), ?::uuid)";

        int totalEnviosImportados = 0;

        try (PreparedStatement stmt = connection.prepareStatement(sqlInsert)) {

            // 4. Recorrer desde la Fila 2 (Respuestas)
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowEmpty(row)) continue;

                // Un UUID único para agrupar todas las respuestas de ESTE titulado/encuestado
                String envioId = UUID.randomUUID().toString();
                boolean tieneRespuestasValidas = false;

                for (Map.Entry<Integer, Integer> entry : excelColToPreguntaId.entrySet()) {
                    int colIndex = entry.getKey();
                    int preguntaId = entry.getValue();

                    Cell cell = row.getCell(colIndex);
                    String valorRespuesta = getCellValueAsString(cell).trim();

                    // Insertar únicamente si la celda no está vacía
                    if (!valorRespuesta.isEmpty()) {
                        stmt.setInt(1, encuestaId);
                        stmt.setInt(2, preguntaId);
                        stmt.setString(3, valorRespuesta);
                        stmt.setString(4, envioId);

                        stmt.addBatch();
                        tieneRespuestasValidas = true;
                    }
                }

                if (tieneRespuestasValidas) {
                    totalEnviosImportados++;
                }
            }

            // Ejecutar la inserción masiva
            stmt.executeBatch();
        }

        workbook.close();
        return totalEnviosImportados;
    }

    /**
     * Consulta las preguntas de la BD para la encuesta dada y devuelve un Map de [pregunta_normalizada -> pregunta_id].
     */
    private Map<String, Integer> cargarMapaPreguntas(int encuestaId, Connection conn) throws Exception {
        Map<String, Integer> map = new HashMap<>();
        String sql = "SELECT id, texto_pregunta FROM preguntas WHERE encuesta_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, encuestaId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String texto = rs.getString("texto_pregunta");
                    map.put(normalizeText(texto), id);
                }
            }
        }
        return map;
    }

    private String normalizeText(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double numVal = cell.getNumericCellValue();
                    if (numVal == (long) numVal) {
                        return String.valueOf((long) numVal);
                    }
                    return String.valueOf(numVal);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK && !getCellValueAsString(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
