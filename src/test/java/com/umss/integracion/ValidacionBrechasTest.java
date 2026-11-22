package com.umss.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umss.controller.AuthController;
import com.umss.controller.ReporteController;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de caja negra del endpoint /api/reportes/brechas.
 *
 * Levanta el controlador real en un puerto efímero, inicia sesión y consume
 * el JSON exacto que usa la interfaz. Los valores esperados son los validados
 * en las hojas Validacion_Brechas e Indicadores_Estado_Actual del Excel.
 */
class ValidacionBrechasTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private static Javalin app;
    private static int puerto;
    private static String cookie;

    @BeforeAll
    static void iniciarServidor() throws IOException {
        app = Javalin.create();
        app.before(ctx -> {
            if (ctx.path().startsWith("/api/reportes") && !AuthController.estaAutenticado(ctx)) {
                ctx.status(401).json(Map.of("error", "No autorizado. Inicie sesión."));
            }
        });
        AuthController.registrarRutas(app);
        app.get("/api/reportes/brechas", ReporteController::reporteBrechas);
        app.start("127.0.0.1", 0);
        puerto = app.port();
        cookie = iniciarSesion();
    }

    @AfterAll
    static void detenerServidor() {
        if (app != null) app.stop();
    }

    private static String iniciarSesion() throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + puerto + "/api/login").openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        String usuario = System.getenv().getOrDefault("ADMIN_USER", "admin");
        String clave = System.getenv().getOrDefault("ADMIN_PASS", "admin123");
        String cuerpo = "{\"usuario\":\"" + usuario + "\",\"password\":\"" + clave + "\"}";
        try (OutputStream os = c.getOutputStream()) {
            os.write(cuerpo.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, c.getResponseCode());
        String setCookie = c.getHeaderField("Set-Cookie");
        assertNotNull(setCookie, "El login debe devolver la cookie de sesión");
        return setCookie.split(";", 2)[0];
    }

    private JsonNode reporte() throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + puerto + "/api/reportes/brechas").openConnection();
        c.setRequestProperty("Cookie", cookie);
        assertEquals(200, c.getResponseCode());
        return mapper.readTree(c.getInputStream());
    }

    private static JsonNode buscar(JsonNode arreglo, String campo, String valor) {
        for (JsonNode n : arreglo) {
            if (n.path(campo).asText().equals(valor)) return n;
        }
        throw new AssertionError("No se encontró " + campo + " = " + valor);
    }

    private static int suma(JsonNode arreglo) {
        int total = 0;
        for (JsonNode n : arreglo) total += n.path("total").asInt();
        return total;
    }

    private static int celda(JsonNode arreglo, String x, String y) {
        for (JsonNode n : arreglo) {
            if (n.path("valorX").asText().equals(x) && n.path("valorY").asText().equals(y))
                return n.path("total").asInt();
        }
        throw new AssertionError("No se encontró la celda " + x + " × " + y);
    }

    @Test
    @DisplayName("VALB-01: el endpoint expone todas las secciones del reporte")
    void seccionesDelReporte() throws IOException {
        JsonNode r = reporte();

        assertTrue(r.has("totalTitulados"));
        assertTrue(r.has("totalEmpleadores"));
        assertEquals(4, r.get("indicadores").size());
        assertFalse(r.get("radar").isEmpty());
        assertEquals(9, r.get("asignaturasVentaja").size());
        assertEquals(11, r.get("asignaturasPocaUtilidad").size());
        assertFalse(r.get("cruceVentajaSituacion").isEmpty());
        assertFalse(r.get("crucePocaSituacion").isEmpty());
        assertFalse(r.get("crucePocaRazon").isEmpty());
        assertFalse(r.get("demandaTendencias").isEmpty());
        assertFalse(r.get("demandaHerramientas").isEmpty());
        assertFalse(r.get("demandaCompetencias").isEmpty());
        assertFalse(r.get("contextoEmpleador").isEmpty());
    }

    @Test
    @DisplayName("VALB-02: totales validados de titulados y empleadores")
    void totales() throws IOException {
        JsonNode r = reporte();
        assertEquals(184, r.get("totalTitulados").asInt());
        assertEquals(79, r.get("totalEmpleadores").asInt());
    }

    @Test
    @DisplayName("VALB-03: indicadores del Estado Actual con n, media y desviación")
    void indicadores() throws IOException {
        JsonNode inds = reporte().get("indicadores");

        JsonNode sat = buscar(inds, "etiqueta", "Satisfacción con la formación recibida");
        assertEquals(38, sat.get("pregunta").asInt());
        assertEquals(182, sat.get("n").asInt());
        assertEquals(2.8, sat.get("media").asDouble(), 0.001);
        assertEquals(0.6, sat.get("desviacion").asDouble(), 0.001);
        assertEquals(7, sat.get("distribucion").get(0).get("total").asInt());
        assertEquals(38, sat.get("distribucion").get(1).get("total").asInt());
        assertEquals(122, sat.get("distribucion").get(2).get("total").asInt());
        assertEquals(15, sat.get("distribucion").get(3).get("total").asInt());

        JsonNode conc = buscar(inds, "etiqueta", "Concordancia formación ↔ mercado laboral");
        assertEquals(183, conc.get("n").asInt());
        assertEquals(2.6, conc.get("media").asDouble(), 0.001);
        assertEquals(0.6, conc.get("desviacion").asDouble(), 0.001);

        JsonNode pert = buscar(inds, "etiqueta", "Pertinencia del puesto de trabajo");
        assertEquals(139, pert.get("n").asInt());
        assertEquals(3.3, pert.get("media").asDouble(), 0.001);
        assertEquals(0.7, pert.get("desviacion").asDouble(), 0.001);

        JsonNode pos = buscar(inds, "etiqueta", "Oferta de posgrado afín a la especialidad");
        assertEquals(184, pos.get("n").asInt());
        assertEquals(2.6, pos.get("media").asDouble(), 0.001);
        assertEquals(0.8, pos.get("desviacion").asDouble(), 0.001);
    }

    @Test
    @DisplayName("VALB-04: radar objetivo vs actual con brechas firmadas")
    void radar() throws IOException {
        JsonNode radar = reporte().get("radar");
        assertEquals(17, radar.size());

        JsonNode ia = buscar(radar, "dimension", "IA / Machine Learning");
        assertEquals(75.9, ia.get("objetivo").asDouble(), 0.001);
        assertEquals(45.1, ia.get("actual").asDouble(), 0.001);
        assertEquals(30.8, ia.get("brecha").asDouble(), 0.001);

        JsonNode bd = buscar(radar, "dimension", "Base de Datos / SQL");
        assertEquals(7.6, bd.get("objetivo").asDouble(), 0.001);
        assertEquals(93.5, bd.get("actual").asDouble(), 0.001);
        assertEquals(-85.9, bd.get("brecha").asDouble(), 0.001);

        JsonNode blandas = buscar(radar, "dimension", "Competencias blandas");
        assertEquals(40.5, blandas.get("objetivo").asDouble(), 0.001);
        assertEquals(0.0, blandas.get("actual").asDouble(), 0.001);
        assertEquals(40.5, blandas.get("brecha").asDouble(), 0.001);
    }

    @Test
    @DisplayName("VALB-05: frecuencias de asignaturas del Estado Actual")
    void frecuenciasDeAsignaturas() throws IOException {
        JsonNode r = reporte();

        JsonNode ventaja = r.get("asignaturasVentaja").get(0);
        assertEquals("Base de Datos", ventaja.get("respuesta").asText());
        assertEquals(172, ventaja.get("total").asInt());
        assertEquals(93.5, ventaja.get("porcentaje").asDouble(), 0.001);
        assertEquals(920, suma(r.get("asignaturasVentaja")));

        JsonNode poca = r.get("asignaturasPocaUtilidad").get(0);
        assertEquals("Aplic. Interactivas para Televisión Digital", poca.get("respuesta").asText());
        assertEquals(97, poca.get("total").asInt());
        assertEquals(716, suma(r.get("asignaturasPocaUtilidad")));
    }

    @Test
    @DisplayName("VALB-06: mapas de calor con totales y celdas validadas en Excel")
    void mapasDeCalor() throws IOException {
        JsonNode r = reporte();

        assertEquals(920, suma(r.get("cruceVentajaSituacion")));
        assertEquals(716, suma(r.get("crucePocaSituacion")));
        assertEquals(104, suma(r.get("crucePocaRazon")));

        assertEquals(129, celda(r.get("cruceVentajaSituacion"), "Base de Datos",
                "Actualmente me encuentro trabajando un una organización"));
        assertEquals(27, celda(r.get("cruceVentajaSituacion"), "Base de Datos",
                "Actualmente NO trabajo"));
        assertEquals(16, celda(r.get("cruceVentajaSituacion"), "Base de Datos",
                "Actualmente dedico mi tiempo COMPLETO a un emprendimiento propio"));
    }

    @Test
    @DisplayName("VALB-07: tendencias de empleadores con porcentajes entre 0 y 100")
    void palabrasClave() throws IOException {
        JsonNode r = reporte();
        JsonNode ia = r.get("demandaTendencias").get(0);
        assertEquals("IA / Machine Learning", ia.get("palabra").asText());
        assertEquals(49, ia.get("total").asInt());
        assertEquals(62.0, ia.get("porcentaje").asDouble(), 0.001);

        for (JsonNode n : r.get("demandaTendencias")) {
            assertTrue(n.get("porcentaje").asDouble() >= 0.0);
            assertTrue(n.get("porcentaje").asDouble() <= 100.0);
        }
    }
}
