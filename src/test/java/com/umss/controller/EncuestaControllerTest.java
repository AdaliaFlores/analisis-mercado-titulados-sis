package com.umss.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umss.dao.ReglasEncuesta;
import com.umss.model.RespuestaDTO;

import io.javalin.Javalin;

/**
 * Suite de la HU-1: registro de encuestas para titulados y empleadores.
 *
 * <p>No se realiza ninguna inserción en Neon. Las consultas de validación se
 * atienden con un stub JDBC en memoria y el guardado se sustituye por la
 * respuesta 201 del propio manejador de prueba.</p>
 */
@Timeout(20)
class EncuestaControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String OPCIONES_ASIGNATURAS =
            "[\"Física\",\"Química\",\"Matemáticas\",\"Historia\",\"Lenguaje\",\"Biología\",\"Arte\"]";

    /** Catálogo mínimo que reproduce los metadatos usados por ReglasEncuesta. */
    private static final Map<Integer, List<Map<String, Object>>> CATALOGO = Map.of(
            1, List.of(
                    pregunta(1, "¿Cuál es su área de trabajo?", "OPCION_UNICA",
                            "[\"Administración\",\"Otro\"]"),
                    pregunta(15, "Observaciones generales", "TEXTO", "[]"),
                    pregunta(42, "Indique 5 asignaturas que más le gustaron",
                            "OPCION_MULTIPLE", OPCIONES_ASIGNATURAS),
                    pregunta(47, "Correo electrónico de contacto", "TEXTO", "null"),
                    pregunta(48, "Número telefónico de contacto", "TEXTO", "[]"),
                    pregunta(49, "LINK de LinkedIn", "TEXTO", "[]")),
            2, List.of(
                    pregunta(2, "Sector de la empresa", "OPCION_UNICA",
                            "[\"Industrial\",\"Otro\"]"),
                    pregunta(50, "Nombre de la Empresa", "TEXTO", "[]"),
                    pregunta(59, "¿Qué nuevas áreas de conocimiento requiere?", "TEXTO", "[]"),
                    pregunta(60, "Nuevas herramientas tecnológicas", "TEXTO", "[]"),
                    pregunta(61, "Habilidades, competencias y destrezas", "TEXTO", "[]")));

    @Test
    @DisplayName("CP01 - Unitario: correo, telefono, caracteres especiales y URL de LinkedIn")
    void cp01ValidacionUnitariaCampos() throws SQLException {
        long inicio = System.nanoTime();

        // Correo: valido vs invalido sin punto en el dominio
        assertThrows(IllegalArgumentException.class, () -> validar(dto(1, null, Map.of("p_47", "usuario@dominio"))));
        assertEquals("usuario@dominio.com", validar(dto(1, null, Map.of("p_47", "usuario@dominio.com"))).get(47));

        // Telefono: 6 y 15 digitos pasan; 5, 16 y letras fallan.
        assertEquals("700123", validar(dto(1, null, Map.of("p_48", "700123"))).get(48));
        assertEquals("700123456789012", validar(dto(1, null, Map.of("p_48", "700123456789012"))).get(48));
        assertThrows(IllegalArgumentException.class, () -> validar(dto(1, null, Map.of("p_48", "70012"))));
        assertThrows(IllegalArgumentException.class, () -> validar(dto(1, null, Map.of("p_48", "7001234567890123"))));
        assertThrows(IllegalArgumentException.class, () -> validar(dto(1, null, Map.of("p_48", "70012abc"))));
        assertThrows(IllegalArgumentException.class, () -> validar(dto(1, null, Map.of("p_48", "abc700123"))));

        // Texto abierto: se rechazan especiales y se aceptan tildes/ñ/puntuación basica.
        assertThrows(IllegalArgumentException.class, () -> validar(dto(1, null, Map.of("p_15", "Texto con #$%&"))));
        assertEquals("Información válida, con tildes y ñ. ¿Correcto?",
                validar(dto(1, null, Map.of("p_15", "Información válida, con tildes y ñ. ¿Correcto?"))).get(15));

        // LinkedIn (p_49): acepta caracteres de URL, rechaza llaves y exceso de longitud.
        String url = "https://www.linkedin.com/in/juan-perez?locale=es_ES&trk=profile";
        assertEquals(url, validar(dto(1, null, Map.of("p_49", url))).get(49));
        assertThrows(IllegalArgumentException.class, () -> validar(dto(1, null, Map.of("p_49", url + "{"))));
        assertThrows(IllegalArgumentException.class, () -> validar(dto(1, null, Map.of("p_49", "a".repeat(201)))));

        imprimirTiempo("CP01", inicio);
    }

    @Test
    @DisplayName("CP02 - Unitario: exactamente 5 asignaturas, opcion Otro y limites 100,200,250")
    void cp02ValidacionUnitariaReglasEncuesta() throws SQLException {
        long inicio = System.nanoTime();

        String cinco = "Física, Química, Matemáticas, Historia, Lenguaje";
        assertEquals(cinco, validar(dto(1, null, Map.of("p_42", cinco))).get(42));

        String tres = "Física, Química, Matemáticas";
        String seis = "Física, Química, Matemáticas, Historia, Lenguaje, Biología";
        assertThrows(IllegalArgumentException.class, () -> validar(dto(1, null, Map.of("p_42", tres))));
        assertThrows(IllegalArgumentException.class, () -> validar(dto(1, null, Map.of("p_42", seis))));

        // "Otro" requiere detalle; con detalle se acepta.
        assertThrows(IllegalArgumentException.class, () -> validar(dto(1, null, Map.of("p_1", "Otro"))));
        assertEquals("Otro: Computación",
                validar(dto(1, null, Map.of("p_1", "Otro: Computación"))).get(1));

        // Límites: 100 en texto común, 200 en enlaces y 250 en textos extensos de empleador.
        assertEquals(100, validar(dto(1, null, Map.of("p_15", "a".repeat(100)))).get(15).length());
        assertThrows(IllegalArgumentException.class,
                () -> validar(dto(1, null, Map.of("p_15", "a".repeat(101)))));

        assertEquals(200, validar(dto(1, null, Map.of("p_49", "a".repeat(200)))).get(49).length());
        assertThrows(IllegalArgumentException.class,
                () -> validar(dto(1, null, Map.of("p_49", "a".repeat(201)))));

        assertEquals(250, validar(dto(2, "rrhh@empresa.com", Map.of("p_59", "a".repeat(250)))).get(59).length());
        assertThrows(IllegalArgumentException.class,
                () -> validar(dto(2, "rrhh@empresa.com", Map.of("p_59", "a".repeat(251)))));

        imprimirTiempo("CP02", inicio);
    }

    @Test
    @DisplayName("CP03 - Caja blanca: encuesta inválida, pregunta ajena y omisión de respuestas vacías")
    void cp03CajaBlancaFlujoValidacionYFiltrado() throws SQLException {
        long inicio = System.nanoTime();

        IllegalArgumentException encuestaInvalida = assertThrows(IllegalArgumentException.class,
                () -> validar(dto(99, null, Map.of("p_1", "Administración"))));
        assertEquals("El tipo de encuesta no es válido.", encuestaInvalida.getMessage());

        IllegalArgumentException preguntaAjena = assertThrows(IllegalArgumentException.class,
                () -> validar(dto(1, null, Map.of("p_999999", "x"))));
        assertEquals("La pregunta 999999 no pertenece a la encuesta seleccionada.",
                preguntaAjena.getMessage());

        // Ramas isEmpty()/continue: las preguntas ocultas llegan vacías y no se insertan.
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("p_47", "");
        payload.put("p_48", "70012345");
        payload.put("p_15", "   ");
        Map<Integer, String> filtradas = validar(dto(1, null, payload));
        assertEquals(1, filtradas.size());
        assertEquals("70012345", filtradas.get(48));
        assertFalse(filtradas.containsKey(47));
        assertFalse(filtradas.containsKey(15));

        IllegalArgumentException sinRespuestas = assertThrows(IllegalArgumentException.class,
                () -> validar(dto(1, null, Map.of("p_47", "", "p_15", " "))));
        assertEquals("Debe enviar al menos una respuesta.", sinRespuestas.getMessage());

        imprimirTiempo("CP03", inicio);
    }

    @Test
    @DisplayName("CP04 - Integración HTTP: GET 200, POST 400 y POST 201 con guardado simulado")
    void cp04IntegracionEndpointsEncuestas() throws Exception {
        long inicio = System.nanoTime();

        Javalin app = Javalin.create();
        app.get("/api/encuestas/{id}", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            ctx.json(Map.of(
                    "id", id,
                    "titulo", id == 1 ? "Titulados" : "Empleadores",
                    "destinatario", id == 1 ? "Titulados" : "Empleadores"));
        });
        app.post("/api/respuestas", ctx -> {
            String cuerpo = ctx.body();
            if (cuerpo == null || cuerpo.isBlank()) {
                ctx.status(400).result("El cuerpo de la solicitud es obligatorio.");
                return;
            }
            RespuestaDTO dto;
            try {
                dto = MAPPER.readValue(cuerpo, RespuestaDTO.class);
            } catch (Exception e) {
                ctx.status(400).result("El contenido de la solicitud no es un JSON válido.");
                return;
            }
            try {
                // EncuestaDAO.guardarRespuestas queda sustituido por este manejador:
                // valida con el stub en memoria y devuelve 201 sin escribir en BD.
                Map<Integer, String> guardadas = ReglasEncuesta.validar(conexionMemoria(), dto);
                ctx.status(201).result("Respuestas guardadas exitosamente (" + guardadas.size() + ")");
            } catch (IllegalArgumentException e) {
                ctx.status(400).result(e.getMessage());
            } catch (SQLException e) {
                ctx.status(500).result("No se pudieron guardar las respuestas. Intente nuevamente.");
            }
        });

        app.start("127.0.0.1", 0);
        try {
            int puerto = app.port();
            HttpClient cliente = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5)).build();

            HttpResponse<String> r1 = cliente.send(get(puerto, "/api/encuestas/1"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            HttpResponse<String> r2 = cliente.send(get(puerto, "/api/encuestas/2"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, r1.statusCode());
            assertEquals(200, r2.statusCode());
            assertTrue(r1.body().contains("Titulados"));
            assertTrue(r2.body().contains("Empleadores"));

            assertEquals(400, post(cliente, puerto, " ").statusCode());
            assertMensaje(post(cliente, puerto, "{"), 400, "El contenido de la solicitud no es un JSON válido.");
            assertMensaje(post(cliente, puerto, "{\"encuestaId\":1,\"respuestas\":{}}"), 400,
                    "Debe enviar al menos una respuesta.");
            assertMensaje(post(cliente, puerto,
                    "{\"encuestaId\":2,\"contacto\":\"correo-invalido\",\"respuestas\":{\"p_50\":\"Empresa\"}}"),
                    400, "Ingrese un correo electrónico válido");

            HttpResponse<String> valido = post(cliente, puerto,
                    "{\"encuestaId\":1,\"respuestas\":{"
                    + "\"p_47\":\"usuario@dominio.com\","
                    + "\"p_48\":\"70012345\","
                    + "\"p_49\":\"https://www.linkedin.com/in/usuario\","
                    + "\"p_15\":\"Observación válida\"}}");
            assertEquals(201, valido.statusCode());
            assertTrue(valido.body().contains("Respuestas guardadas exitosamente"));
        } finally {
            app.stop();
        }

        imprimirTiempo("CP04", inicio);
    }

    // ------------------------------------------------------------------
    // Helpers en memoria y de HTTP
    // ------------------------------------------------------------------

    private static Map<Integer, String> validar(RespuestaDTO dto) throws SQLException {
        return ReglasEncuesta.validar(conexionMemoria(), dto);
    }

    private static RespuestaDTO dto(int encuestaId, String contacto, Map<String, String> respuestas) {
        RespuestaDTO dto = new RespuestaDTO();
        dto.setEncuestaId(encuestaId);
        dto.setContacto(contacto);
        dto.setRespuestas(respuestas);
        return dto;
    }

    private static Map<String, Object> pregunta(int id, String texto, String tipo, String opciones) {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("id", id);
        fila.put("texto_pregunta", texto);
        fila.put("tipo_pregunta", tipo);
        fila.put("opciones", opciones);
        fila.put("formato", null);
        return fila;
    }

    /** Connection de JDK Proxy: solo responde a las dos SELECT de ReglasEncuesta. */
    private static Connection conexionMemoria() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "prepareStatement":
                            return statementMemoria((String) args[0]);
                        case "close":
                        case "setAutoCommit":
                        case "commit":
                        case "rollback":
                            return null;
                        case "isClosed":
                            return false;
                        case "toString":
                            return "ConnectionEnMemoriaHU1";
                        default:
                            return valorPorDefecto(method.getReturnType());
                    }
                });
    }

    private static PreparedStatement statementMemoria(String sql) {
        boolean consultaEncuestas = sql.contains("FROM encuestas");
        boolean consultaPreguntas = sql.contains("FROM preguntas");
        int[] encuestaId = {0};

        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setInt":
                            if (consultaEncuestas || consultaPreguntas) {
                                encuestaId[0] = (Integer) args[1];
                            }
                            return null;
                        case "setString":
                        case "setObject":
                        case "addBatch":
                        case "clearParameters":
                        case "close":
                            return null;
                        case "executeBatch":
                            return new int[0];
                        case "executeQuery":
                            if (consultaEncuestas) {
                                boolean existe = encuestaId[0] == 1 || encuestaId[0] == 2;
                                return resultSetMemoria(existe ? List.of(Map.of()) : List.of());
                            }
                            if (consultaPreguntas) {
                                return resultSetMemoria(
                                        CATALOGO.getOrDefault(encuestaId[0], List.of()));
                            }
                            return resultSetMemoria(List.of());
                        case "toString":
                            return "StatementEnMemoriaHU1";
                        default:
                            return valorPorDefecto(method.getReturnType());
                    }
                });
    }

    private static ResultSet resultSetMemoria(List<Map<String, Object>> filas) {
        int[] indice = {-1};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "next":
                            indice[0]++;
                            return indice[0] < filas.size();
                        case "getInt":
                        case "getString":
                        case "getObject": {
                            Map<String, Object> fila = filas.get(indice[0]);
                            String columna = ((String) args[0]).toLowerCase(Locale.ROOT);
                            Object valor = fila.get(columna);
                            if (valor == null) {
                                return "getInt".equals(method.getName()) ? 0 : null;
                            }
                            if (valor instanceof Number && !"getString".equals(method.getName())) {
                                return ((Number) valor).intValue();
                            }
                            return valor.toString();
                        }
                        case "wasNull":
                            return false;
                        case "close":
                            return null;
                        case "toString":
                            return "ResultSetEnMemoriaHU1";
                        default:
                            return valorPorDefecto(method.getReturnType());
                    }
                });
    }

    private static Object valorPorDefecto(Class<?> tipo) {
        if (!tipo.isPrimitive()) return null;
        if (tipo == boolean.class) return false;
        if (tipo == byte.class) return (byte) 0;
        if (tipo == short.class) return (short) 0;
        if (tipo == int.class) return 0;
        if (tipo == long.class) return 0L;
        if (tipo == float.class) return 0f;
        if (tipo == double.class) return 0d;
        if (tipo == char.class) return '\0';
        return null;
    }

    private static HttpRequest get(int puerto, String ruta) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
                .timeout(Duration.ofSeconds(5)).GET().build();
    }

    private static HttpResponse<String> post(HttpClient cliente, int puerto, String cuerpo) throws Exception {
        return cliente.send(HttpRequest
                .newBuilder(URI.create("http://127.0.0.1:" + puerto + "/api/respuestas"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void assertMensaje(HttpResponse<String> respuesta, int estadoEsperado, String fragmento) {
        assertEquals(estadoEsperado, respuesta.statusCode());
        assertTrue(respuesta.body().contains(fragmento),
                "Se esperaba el mensaje '" + fragmento + "' pero se obtuvo: " + respuesta.body());
    }

    private static void imprimirTiempo(String caso, long inicioNanos) {
        long milis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNanos);
        System.out.printf("[HU-1] %s: %d ms%n", caso, milis);
        assertTrue(milis < 15_000, "El caso " + caso + " tardó " + milis + " ms");
    }
}
