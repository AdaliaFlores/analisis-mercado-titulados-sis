package com.umss.dao;

import com.umss.config.Database;
import com.umss.model.Pregunta;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelImporterServiceTest {

    /** Marca única en la primera respuesta para poder borrar los datos de prueba. */
    private static final String MARCA = "TEST_IMPORT_";

    @Test
    @DisplayName("La huella de una fila ignora orden, mayúsculas, tildes y espacios")
    void huellaNormalizaElContenido() {
        Map<Integer, String> a = new LinkedHashMap<>();
        Map<Integer, String> b = new LinkedHashMap<>();
        for (int i = 1; i <= 15; i++) {
            a.put(i, "Valor " + i);
            b.put(16 - i, "  VALOR   " + (16 - i) + " ");
        }

        assertEquals(ExcelImporterService.huella(1, a), ExcelImporterService.huella(1, b));
        assertNotEquals(ExcelImporterService.huella(1, a), ExcelImporterService.huella(2, a));
    }

    @Test
    @DisplayName("Filas con pocas respuestas no se usan para deduplicar")
    void huellaConPocasRespuestasEsNula() {
        assertNull(ExcelImporterService.huella(1, Map.of(1, "única")));
    }

    @Test
    @DisplayName("Rechaza encuestas cruzadas y no inserta el mismo Excel dos veces")
    void rechazaCruceYEvitaDuplicados() throws Exception {
        List<Pregunta> titulados = preguntasDe(1);
        List<Pregunta> empleadores = preguntasDe(2);
        Assumptions.assumeTrue(titulados.size() >= 15 && empleadores.size() >= 15,
                "La BD debe tener al menos 15 preguntas por encuesta");

        byte[] excelTitulados = crearExcel(titulados, 2);
        byte[] excelEmpleadores = crearExcel(empleadores, 2);

        try (Connection conn = Database.getConnection()) {
            ExcelImporterService service = new ExcelImporterService();

            IllegalArgumentException cruce1 = assertThrows(IllegalArgumentException.class, () ->
                    service.importar(new ByteArrayInputStream(excelEmpleadores), 1, conn));
            assertTrue(cruce1.getMessage().contains("parece corresponder"),
                    "El Excel de empleadores debe rechazarse en titulados");

            IllegalArgumentException cruce2 = assertThrows(IllegalArgumentException.class, () ->
                    service.importar(new ByteArrayInputStream(excelTitulados), 2, conn));
            assertTrue(cruce2.getMessage().contains("parece corresponder"),
                    "El Excel de titulados debe rechazarse en empleadores");

            ExcelImporterService.Resultado primera =
                    service.importar(new ByteArrayInputStream(excelTitulados), 1, conn);
            assertEquals(2, primera.encuestas);
            assertEquals(30, primera.respuestas); // 15 columnas × 2 filas
            assertEquals(0, primera.duplicadas);

            ExcelImporterService.Resultado segunda =
                    service.importar(new ByteArrayInputStream(excelTitulados), 1, conn);
            assertEquals(0, segunda.encuestas, "No debe insertar filas nuevas");
            assertEquals(0, segunda.respuestas);
            assertEquals(2, segunda.duplicadas, "Las dos filas deben detectarse como duplicadas");
        } finally {
            limpiarDatosDePrueba();
        }
    }

    private static List<Pregunta> preguntasDe(int encuestaId) {
        try {
            return EncuestaDAO.obtenerPreguntasPorEncuesta(encuestaId);
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "BD no disponible: " + e.getMessage());
            return List.of();
        }
    }

    private static byte[] crearExcel(List<Pregunta> preguntas, int filas) throws Exception {
        int columnas = Math.min(15, preguntas.size());
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Datos");
            Row header = sheet.createRow(0);
            for (int c = 0; c < columnas; c++) {
                header.createCell(c).setCellValue(preguntas.get(c).getTextoPregunta());
            }
            for (int f = 1; f <= filas; f++) {
                Row row = sheet.createRow(f);
                row.createCell(0).setCellValue(MARCA + UUID.randomUUID());
                for (int c = 1; c < columnas; c++) {
                    row.createCell(c).setCellValue("valor-" + f + "-" + c + "-" + UUID.randomUUID());
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void limpiarDatosDePrueba() {
        String sql = "DELETE FROM respuestas WHERE envio_id IN ("
                   + "SELECT envio_id FROM respuestas WHERE respuesta LIKE ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, MARCA + "%");
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("No se pudieron limpiar los datos de prueba: " + e.getMessage());
        }
    }
}
