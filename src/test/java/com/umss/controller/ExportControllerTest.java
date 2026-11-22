package com.umss.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.umss.config.Database;
import com.umss.dao.EncuestaDAO;

import io.javalin.Javalin;
import io.javalin.http.Context;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Suite automatizada de la HU-8: Dashboard Analítico y Frecuencias Cruzadas
 * (Módulo de Exportación).
 *
 * <p>CP-01 y CP-02 son pruebas unitarias puras (no requieren base de datos).
 * CP-03, CP-04 y CP-05 se omiten con {@link Assumptions} cuando la BD no está
 * disponible, igual que el resto de pruebas de integración del proyecto.</p>
 */
class ExportControllerTest {

    private static final byte[] BOM_UTF8 = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final List<String> REPORTES = List.of(
            "brechas",
            "emprendimiento",
            "desempleo",
            "formacion",
            "empleabilidad",
            "ubicacion",
            "hu12");

    // =====================================================================
    // CP-01 (Unitaria - Formato CSV)
    // =====================================================================

    @Test
    @DisplayName("CP-01: convertirCampo, repararEncoding y reformatearCsv con comas, comillas, nulos y acentos")
    void cp01FormatoCsv() {
        char decSep = DecimalFormatSymbols.getInstance(Locale.getDefault()).getDecimalSeparator();
        char listSep = (decSep == ',') ? ';' : ',';

        // --- convertirCampo: solo convierte números con punto decimal si el locale usa coma ---
        String esperadoPositivo = (decSep == ',') ? "123,45" : "123.45";
        assertEquals(esperadoPositivo, ExportController.convertirCampo("123.45"),
                "Un número con punto decimal debe adaptarse al separador local");

        String esperadoNegativo = (decSep == ',') ? "-0,50" : "-0.50";
        assertEquals(esperadoNegativo, ExportController.convertirCampo("-0.50"),
                "Los números negativos también deben adaptarse");

        String esperadoConEspacios = (decSep == ',') ? " 12,5 " : " 12.5 ";
        assertEquals(esperadoConEspacios, ExportController.convertirCampo(" 12.5 "),
                "convertirCampo valida con trim pero conserva el espaciado original");

        assertEquals("12.3.4", ExportController.convertirCampo("12.3.4"),
                "Un valor no numérico no debe transformarse");
        assertEquals("v1.2", ExportController.convertirCampo("v1.2"),
                "Un texto con punto no debe transformarse");
        assertEquals("12,5", ExportController.convertirCampo("12,5"),
                "Un valor que ya usa coma decimal no debe transformarse");
        assertEquals("", ExportController.convertirCampo(""),
                "Un campo vacío debe permanecer vacío");

        // --- repararEncoding: nulos, mojibake y eliminación de tildes ---
        assertEquals("", ExportController.repararEncoding(null),
                "Un texto nulo debe convertirse en cadena vacía");
        assertEquals("", ExportController.repararEncoding(""),
                "Un texto vacío debe permanecer vacío");
        assertEquals("Informacion", ExportController.repararEncoding("InformaciÃ³n"),
                "El mojibake Ã³ debe repararse como o");
        assertEquals("Diseno", ExportController.repararEncoding("DiseÃ±o"),
                "El mojibake Ã± debe repararse como n");
        assertEquals("arbol", ExportController.repararEncoding("Ã¡rbol"),
                "El mojibake Ã¡ debe repararse como a");
        assertEquals("Chi2", ExportController.repararEncoding("χ²"),
                "El símbolo χ² debe convertirse a Chi2 para Excel");
        assertEquals("Educacion y Gestion", ExportController.repararEncoding("Educación y Gestión"),
                "Las tildes deben eliminarse para máxima compatibilidad");
        assertEquals("Ano", ExportController.repararEncoding("Año"),
                "La eñe debe normalizarse a n");

        // --- reformatearCsv: comas internas entre comillas, comillas escapadas y campos nulos ---
        String entrada = "\"Ciencias, Exactas\",12.5\r\n"
                + "a,,c\r\n"
                + "\"dice \"\"hola\"\"\",3.75\r\n";
        String valorDecimal1 = (decSep == ',') ? "12,5" : "12.5";
        String valorDecimal2 = (decSep == ',') ? "3,75" : "3.75";
        // Si el locale usa coma decimal, reformatearCsv antepone "sep=;" para Excel.
        String prefijoSep = (listSep == ';') ? "sep=;\r\n" : "";
        String esperado = prefijoSep
                + "\"Ciencias, Exactas\"" + listSep + valorDecimal1 + "\r\n"
                + "a" + listSep + listSep + "c\r\n"
                + "\"dice \"\"hola\"\"\"" + listSep + valorDecimal2 + "\r\n";

        String salida = ExportController.reformatearCsv(entrada);
        assertEquals(esperado, salida,
                "reformatearCsv debe respetar comas entre comillas, comillas escapadas y campos vacíos");
        assertTrue(salida.contains("\"Ciencias, Exactas\""),
                "La coma interna de un campo entre comillas no debe tratarse como separador");
        assertTrue(salida.contains("\"dice \"\"hola\"\"\""),
                "Las comillas escapadas deben conservarse");
        assertFalse(salida.contains("\r\r"),
                "No deben generarse retornos de carro duplicados");

        // Encabezado simple: los separadores no citados se adaptan al locale.
        assertEquals(prefijoSep + "nombre" + listSep + "valor\r\n",
                ExportController.reformatearCsv("nombre,valor\n"),
                "Las líneas deben terminar en CRLF y usar el separador local");
    }

    // =====================================================================
    // CP-02 (Unitaria - Manejo de errores y validación)
    // =====================================================================

    @Test
    @DisplayName("CP-02: mensajeError con causas encadenadas y tipo inválido con error controlado")
    void cp02ManejoDeErrores() throws Exception {
        // --- mensajeError recorre toda la cadena hasta la causa raíz ---
        Exception cadena = new RuntimeException("nivel 1",
                new IllegalStateException("nivel 2",
                        new IOException("causa raíz")));
        assertEquals("causa raíz", ExportController.mensajeError(cadena),
                "Debe devolverse el mensaje de la causa raíz");

        assertEquals("IOException",
                ExportController.mensajeError(new RuntimeException(new IOException())),
                "Si la causa no tiene mensaje debe usarse el nombre simple de la clase");

        assertEquals("RuntimeException",
                ExportController.mensajeError(new RuntimeException()),
                "Si no hay mensaje debe usarse el nombre simple de la clase");

        assertEquals("RuntimeException",
                ExportController.mensajeError(new RuntimeException("   ")),
                "Un mensaje en blanco debe tratarse como ausente");

        // --- Tipo inválido: los tres exportadores responden de forma controlada ---
        ContextoExportacion csv = new ContextoExportacion("tipo-inexistente");
        assertDoesNotThrow(() -> ExportController.exportCsv(csv.ctx()),
                "Un tipo inválido no debe lanzar una excepción en CSV");
        assertEquals(200, csv.status(),
                "CSV debe responder de forma controlada ante un tipo inválido");
        assertNotNull(csv.texto(), "CSV debe devolver contenido");
        assertTrue(csv.texto().toUpperCase(Locale.ROOT).contains("NO SOPORTADO"),
                "CSV debe indicar que el tipo no está soportado. Respuesta: " + csv.texto());

        ContextoExportacion excel = new ContextoExportacion("tipo-inexistente");
        assertDoesNotThrow(() -> ExportController.exportExcel(excel.ctx()),
                "Un tipo inválido no debe lanzar una excepción en Excel");
        assertEquals(200, excel.status(),
                "Excel debe responder de forma controlada ante un tipo inválido");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel.bytes()))) {
            String mensaje = leerCelda(wb.getSheetAt(0), 1, 0);
            assertTrue(mensaje.toUpperCase(Locale.ROOT).contains("NO IMPLEMENTADO"),
                    "Excel debe indicar que el tipo no está implementado. Celda: " + mensaje);
        }

        ContextoExportacion pdf = new ContextoExportacion("tipo-inexistente");
        assertDoesNotThrow(() -> ExportController.exportPdf(pdf.ctx()),
                "Un tipo inválido no debe lanzar una excepción en PDF");
        assertEquals(200, pdf.status(),
                "PDF debe responder de forma controlada ante un tipo inválido");
        assertPdfValido(pdf.bytes(), "tipo inválido", 0);
    }

    // =====================================================================
    // CP-03 (Caja Blanca - CSV y Excel XLSX)
    // =====================================================================

    @Test
    @DisplayName("CP-03: CSV con encabezados y XLSX válido con gráficos en formación y empleabilidad")
    void cp03CsvYExcel() throws Exception {
        asumirBdDisponible();
        Assumptions.assumeFalse(EncuestaDAO.cruceAreaNivel().isEmpty(),
                "La BD debe tener datos de formación para generar los gráficos");

        // ---------- CSV ----------
        ContextoExportacion csv = exportarConReintento(
                ExportController::exportCsv, "formacion", "CSV de formación");

        byte[] csvBytes = csv.bytes();
        assertNotNull(csvBytes, "El CSV no debe ser nulo");
        assertTrue(csvBytes.length > BOM_UTF8.length, "El CSV debe tener contenido");
        assertArrayEquals(BOM_UTF8, Arrays.copyOf(csvBytes, BOM_UTF8.length),
                "El CSV debe incluir el BOM UTF-8 para Excel");

        String csvTexto = new String(csvBytes, BOM_UTF8.length,
                csvBytes.length - BOM_UTF8.length, StandardCharsets.UTF_8);
        assertTrue(csvTexto.contains("REPORTE UMSS - FORMACION"),
                "El CSV debe incluir el encabezado del reporte");
        assertTrue(csvTexto.contains("AREA_DE_INTERES"),
                "El CSV de formación debe incluir la tabla cruzada Área de interés");
        assertTrue(csvTexto.contains("NIVEL_DE_POSGRADO"),
                "El CSV de formación debe incluir la preferencia institucional");
        assertTrue(csvTexto.contains("MECANISMOS DE FINANCIAMIENTO"),
                "El CSV de formación debe incluir la matriz de financiamiento");
        assertFalse(csvTexto.lines().anyMatch(linea -> linea.startsWith("ERROR:")),
                "El CSV no debe contener líneas de error");

        // ---------- XLSX: formación ----------
        ContextoExportacion excelFormacion = exportarConReintento(
                ExportController::exportExcel, "formacion", "XLSX de formación");
        assertXlsxValidoConGraficos(excelFormacion.bytes(), "formacion");

        // ---------- XLSX: empleabilidad ----------
        Assumptions.assumeFalse(EncuestaDAO.analisisEmpleabilidad().isEmpty(),
                "La BD debe tener datos de empleabilidad para generar los gráficos");

        ContextoExportacion excelEmpleabilidad = exportarConReintento(
                ExportController::exportExcel, "empleabilidad", "XLSX de empleabilidad");
        assertXlsxValidoConGraficos(excelEmpleabilidad.bytes(), "empleabilidad");
    }

    // =====================================================================
    // CP-04 (Caja Blanca - PDF y XML/Gráficos)
    // =====================================================================

    @Test
    @DisplayName("CP-04: PDF de emprendimiento y desempleo con cabecera %PDF- y tamaño > 10 KB")
    void cp04Pdf() throws Exception {
        asumirBdDisponible();

        for (String tipo : List.of("emprendimiento", "desempleo")) {
            ContextoExportacion ctx = exportarConReintento(
                    ExportController::exportPdf, tipo, "PDF de " + tipo);
            assertPdfValido(ctx.bytes(), tipo, 10 * 1024);
        }
    }

    // =====================================================================
    // CP-05 (Integración - 21 combinaciones)
    // =====================================================================

    @Test
    @DisplayName("CP-05: endpoints /api/export/{csv,excel,pdf} para los 7 reportes (21 combinaciones)")
    void cp05IntegracionEndpoints() throws Exception {
        asumirBdDisponible();

        Javalin app = Javalin.create();
        boolean iniciado = false;
        try {
            // Se registran exactamente los mismos endpoints y controladores que en App.java.
            // La guardia de autenticación no se replica aquí: el objetivo de CP-05 es la
            // exportación, no el login, y así la prueba no depende de credenciales.
            app.get("/api/export/csv", ExportController::exportCsv);
            app.get("/api/export/excel", ExportController::exportExcel);
            app.get("/api/export/pdf", ExportController::exportPdf);

            app.start("127.0.0.1", 0);
            iniciado = true;
            int puerto = app.port();

            HttpClient cliente = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .build();

            int combinaciones = 0;
            for (String tipo : REPORTES) {
                verificarCsv(cliente, puerto, tipo);
                verificarExcel(cliente, puerto, tipo);
                verificarPdf(cliente, puerto, tipo);
                combinaciones += 3;
            }
            assertEquals(21, combinaciones,
                    "Deben cubrirse los 7 reportes × 3 formatos = 21 combinaciones");
        } finally {
            if (iniciado) app.stop();
        }
    }

    // =====================================================================
    // Utilidades de CP-05
    // =====================================================================

    private static void verificarCsv(HttpClient cliente, int puerto, String tipo) throws Exception {
        HttpResponse<byte[]> respuesta = getConReintento(cliente, puerto, "/api/export/csv", tipo);
        byte[] cuerpo = respuesta.body();

        assertEquals(200, respuesta.statusCode(),
                "CSV " + tipo + " debe responder 200. Cuerpo: "
                        + new String(cuerpo, StandardCharsets.UTF_8));
        assertCabecera(respuesta, "Content-Type", "text/csv", tipo);
        assertCabecera(respuesta, "Content-Disposition", "attachment", tipo);
        assertCabecera(respuesta, "Content-Disposition", "reporte_" + tipo + "_", tipo);
        assertCabecera(respuesta, "Content-Disposition", ".csv", tipo);

        assertTrue(cuerpo.length > BOM_UTF8.length, "CSV " + tipo + " debe tener contenido");
        assertArrayEquals(BOM_UTF8, Arrays.copyOf(cuerpo, BOM_UTF8.length),
                "CSV " + tipo + " debe incluir el BOM UTF-8");

        String texto = new String(cuerpo, BOM_UTF8.length,
                cuerpo.length - BOM_UTF8.length, StandardCharsets.UTF_8);
        assertTrue(texto.contains("REPORTE UMSS"), "CSV " + tipo + " debe incluir el encabezado");
        assertFalse(texto.lines().anyMatch(linea -> linea.startsWith("ERROR:")),
                "CSV " + tipo + " no debe contener líneas de error");
    }

    private static void verificarExcel(HttpClient cliente, int puerto, String tipo) throws Exception {
        HttpResponse<byte[]> respuesta = getConReintento(cliente, puerto, "/api/export/excel", tipo);
        byte[] cuerpo = respuesta.body();

        assertEquals(200, respuesta.statusCode(),
                "Excel " + tipo + " debe responder 200. Cuerpo: "
                        + new String(cuerpo, StandardCharsets.UTF_8));
        assertCabecera(respuesta, "Content-Type", "spreadsheetml.sheet", tipo);
        assertCabecera(respuesta, "Content-Disposition", "attachment", tipo);
        assertCabecera(respuesta, "Content-Disposition", "reporte_" + tipo + "_", tipo);
        assertCabecera(respuesta, "Content-Disposition", ".xlsx", tipo);

        assertTrue(cuerpo.length > 2, "Excel " + tipo + " debe tener contenido");
        assertEquals((byte) 'P', cuerpo[0], "Excel " + tipo + " debe tener firma PK");
        assertEquals((byte) 'K', cuerpo[1], "Excel " + tipo + " debe tener firma PK");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(cuerpo))) {
            assertTrue(wb.getNumberOfSheets() >= 1,
                    "Excel " + tipo + " debe tener al menos una hoja");
            assertSinCeldasDeError(wb, tipo);
        }
    }

    private static void verificarPdf(HttpClient cliente, int puerto, String tipo) throws Exception {
        HttpResponse<byte[]> respuesta = getConReintento(cliente, puerto, "/api/export/pdf", tipo);
        byte[] cuerpo = respuesta.body();

        assertEquals(200, respuesta.statusCode(),
                "PDF " + tipo + " debe responder 200. Cuerpo: "
                        + new String(cuerpo, StandardCharsets.UTF_8));
        assertCabecera(respuesta, "Content-Type", "application/pdf", tipo);
        assertCabecera(respuesta, "Content-Disposition", "attachment", tipo);
        assertCabecera(respuesta, "Content-Disposition", "reporte_" + tipo + "_", tipo);
        assertCabecera(respuesta, "Content-Disposition", ".pdf", tipo);

        assertPdfValido(cuerpo, tipo, 1024);
    }

    /**
     * Reintenta una petición cuando el 500 proviene de una caída transitoria de
     * la conexión con la BD (por ejemplo, Neon cierra conexiones inactivas).
     * Si tras varios intentos la BD sigue sin responder, la prueba se omite.
     */
    private static HttpResponse<byte[]> getConReintento(HttpClient cliente, int puerto,
            String ruta, String tipo) throws Exception {
        HttpResponse<byte[]> respuesta = null;
        for (int intento = 1; intento <= 3; intento++) {
            respuesta = get(cliente, puerto, ruta, tipo);
            if (respuesta.statusCode() != 500) return respuesta;

            String cuerpo = new String(respuesta.body(), StandardCharsets.UTF_8);
            if (!esFalloDeBd(cuerpo)) return respuesta;
            Thread.sleep(1000L * intento);
        }
        String cuerpo = new String(respuesta.body(), StandardCharsets.UTF_8);
        Assumptions.assumeTrue(false,
                "La BD no respondió de forma estable al exportar " + tipo + ": " + cuerpo);
        return respuesta;
    }

    private static HttpResponse<byte[]> get(HttpClient cliente, int puerto,
            String ruta, String tipo) throws Exception {
        HttpRequest solicitud = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + puerto + ruta + "?tipo=" + tipo))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        return cliente.send(solicitud, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static ContextoExportacion exportarConReintento(Exportador exportador,
            String tipo, String descripcion) throws Exception {
        ContextoExportacion ctx = null;
        for (int intento = 1; intento <= 3; intento++) {
            ctx = new ContextoExportacion(tipo);
            ContextoExportacion actual = ctx;
            assertDoesNotThrow(() -> exportador.exportar(actual.ctx()),
                    "La exportación " + descripcion + " no debe lanzar excepciones");

            if (ctx.status() != 500 || !esFalloDeBd(ctx.texto())) {
                assertEquals(200, ctx.status(),
                        "La exportación " + descripcion
                                + " debe generarse. Respuesta: " + ctx.texto());
                return ctx;
            }
            Thread.sleep(1000L * intento);
        }
        Assumptions.assumeTrue(false,
                "La BD no respondió de forma estable al generar " + descripcion
                        + ": " + ctx.texto());
        return ctx;
    }

    /** Detecta mensajes de error de conectividad/BD, no errores de logica del reporte. */
    private static boolean esFalloDeBd(String mensaje) {
        if (mensaje == null) return false;
        String m = mensaje.toLowerCase(Locale.ROOT);
        return m.contains("connection reset")
                || m.contains("connection refused")
                || m.contains("connection is closed")
                || m.contains("this connection has been closed")
                || m.contains("connection attempt failed")
                || m.contains("el intento de conexi")
                || m.contains("socketexception")
                || m.contains("sockettimeout")
                || m.contains("timeout")
                || m.contains("timed out")
                || m.contains("ssl")
                || m.contains("could not receive data from server")
                || m.contains("i/o error occurred")
                || m.contains("too many clients")
                || m.contains("terminating connection");
    }

    @FunctionalInterface
    private interface Exportador {
        void exportar(Context ctx) throws Exception;
    }

    private static void assertCabecera(HttpResponse<?> respuesta, String nombre,
            String fragmento, String tipo) {
        String valor = respuesta.headers().firstValue(nombre).orElse("");
        assertTrue(valor.toLowerCase(Locale.ROOT).contains(fragmento.toLowerCase(Locale.ROOT)),
                "La cabecera " + nombre + " del reporte " + tipo
                        + " debe contener '" + fragmento + "'. Valor: " + valor);
    }

    // =====================================================================
    // Utilidades de XLSX / PDF
    // =====================================================================

    private static void assertXlsxValidoConGraficos(byte[] xlsx, String tipo) throws Exception {
        assertNotNull(xlsx, "El XLSX de " + tipo + " no debe ser nulo");
        assertTrue(xlsx.length > 2, "El XLSX de " + tipo + " debe tener contenido");
        assertEquals((byte) 'P', xlsx[0], "El XLSX de " + tipo + " debe tener firma PK");
        assertEquals((byte) 'K', xlsx[1], "El XLSX de " + tipo + " debe tener firma PK");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertEquals(1, wb.getNumberOfSheets(),
                    "El XLSX de " + tipo + " debe tener una hoja de reporte");
            XSSFSheet hoja = wb.getSheetAt(0);

            assertSinCeldasDeError(wb, tipo);

            XSSFDrawing dibujo = hoja.getDrawingPatriarch();
            assertNotNull(dibujo,
                    "El XLSX de " + tipo + " debe incluir un dibujo con gráficos");
            assertFalse(dibujo.getCharts().isEmpty(),
                    "El XLSX de " + tipo + " debe incluir al menos un gráfico nativo");
        }
    }

    private static void assertSinCeldasDeError(XSSFWorkbook wb, String tipo) {
        DataFormatter formateador = new DataFormatter();
        for (Sheet hoja : wb) {
            for (Row fila : hoja) {
                for (Cell celda : fila) {
                    String valor = formateador.formatCellValue(celda);
                    assertFalse(valor.contains("ERROR:"),
                            "El XLSX de " + tipo + " no debe contener celdas 'ERROR:'. "
                                    + "Hoja=" + hoja.getSheetName()
                                    + ", fila=" + (fila.getRowNum() + 1)
                                    + ", columna=" + (celda.getColumnIndex() + 1)
                                    + ", valor=" + valor);
                }
            }
        }
    }

    private static void assertPdfValido(byte[] pdf, String tipo, long minimoBytes) {
        assertNotNull(pdf, "El PDF de " + tipo + " no debe ser nulo");
        assertTrue(pdf.length > minimoBytes,
                "El PDF de " + tipo + " debe superar " + minimoBytes + " bytes. Tamaño: "
                        + pdf.length);
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1),
                "El PDF de " + tipo + " debe comenzar con la cabecera %PDF-");
    }

    private static String leerCelda(Sheet hoja, int fila, int columna) {
        Row r = hoja.getRow(fila);
        if (r == null) return "";
        Cell c = r.getCell(columna);
        if (c == null) return "";
        return new DataFormatter().formatCellValue(c);
    }

    // =====================================================================
    // Contexto HTTP falso (sin levantar servidor) para CP-02, CP-03 y CP-04
    // =====================================================================

    private static void asumirBdDisponible() {
        try (Connection conn = Database.getConnection()) {
            // La conexión se cierra automáticamente.
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "BD no disponible: " + e.getMessage());
        }
    }

    /**
     * Implementación mínima de {@link Context} que captura lo que los
     * controladores de exportación escriben en la respuesta.
     */
    private static final class ContextoExportacion implements InvocationHandler {

        private final String tipo;
        private final Context proxy;
        private final Map<String, String> cabeceras = new LinkedHashMap<>();
        private final HttpServletResponse respuesta;

        private byte[] bytes;
        private String texto;
        private int status = 200;
        private String contentType;
        private String characterEncoding;

        ContextoExportacion(String tipo) {
            this.tipo = tipo;
            this.respuesta = (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    new RespuestaFalsa());
            this.proxy = (Context) Proxy.newProxyInstance(
                    Context.class.getClassLoader(),
                    new Class<?>[]{Context.class},
                    this);
        }

        Context ctx() {
            return proxy;
        }

        int status() {
            return status;
        }

        byte[] bytes() {
            return bytes;
        }

        String texto() {
            if (texto != null) return texto;
            return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "queryParam":
                    return (args != null && args.length == 1 && "tipo".equals(args[0]))
                            ? tipo : null;
                case "res":
                    return respuesta;
                case "status":
                    if (args != null && args.length == 1) {
                        if (args[0] instanceof Integer codigo) {
                            status = codigo;
                        } else if (args[0] instanceof io.javalin.http.HttpStatus estado) {
                            status = estado.getCode();
                        }
                    }
                    return proxy;
                case "result":
                    if (args != null && args.length == 1) {
                        Object valor = args[0];
                        if (valor instanceof InputStream entrada) {
                            bytes = entrada.readAllBytes();
                        } else if (valor != null) {
                            texto = valor.toString();
                        }
                    }
                    return proxy;
                default:
                    return valorPorDefecto(method.getReturnType());
            }
        }

        private final class RespuestaFalsa implements InvocationHandler {

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "setHeader":
                    case "addHeader":
                        if (args != null && args.length >= 2) {
                            cabeceras.put(String.valueOf(args[0]),
                                    args[1] == null ? "" : String.valueOf(args[1]));
                        }
                        return null;
                    case "setContentType":
                        contentType = args == null || args[0] == null
                                ? null : String.valueOf(args[0]);
                        return null;
                    case "getContentType":
                        return contentType;
                    case "setCharacterEncoding":
                        characterEncoding = args == null || args[0] == null
                                ? null : String.valueOf(args[0]);
                        return null;
                    case "getCharacterEncoding":
                        return characterEncoding;
                    case "getHeader":
                        return args == null ? null : cabeceras.get(String.valueOf(args[0]));
                    case "setStatus":
                        if (args != null && args.length == 1 && args[0] instanceof Integer codigo) {
                            status = codigo;
                        }
                        return null;
                    case "getStatus":
                        return status;
                    default:
                        return valorPorDefecto(method.getReturnType());
                }
            }
        }
    }

    private static Object valorPorDefecto(Class<?> tipo) {
        if (!tipo.isPrimitive()) return null;
        if (tipo == boolean.class) return false;
        if (tipo == char.class) return '\0';
        if (tipo == byte.class) return (byte) 0;
        if (tipo == short.class) return (short) 0;
        if (tipo == int.class) return 0;
        if (tipo == long.class) return 0L;
        if (tipo == float.class) return 0F;
        if (tipo == double.class) return 0D;
        return null;
    }
}
