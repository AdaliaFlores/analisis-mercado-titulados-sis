package com.umss.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.umss.config.Database;

import io.javalin.http.Context;

/**
 * Pruebas de caja negra de la respuesta del controlador (HU-5 y HU-6).
 * Si la BD no está disponible, las pruebas se omiten (no fallan).
 */
class ReporteFormacionFinanciamientoTest {

    @Test
    @DisplayName("HU-5/HU-6: el reporte responde 200 y expone las claves del frontend")
    void reporteRespondeClavesEsperadas() throws Exception {
        asumirBdDisponible();

        ContextoCapturador capturador = new ContextoCapturador();
        Context ctx = (Context) Proxy.newProxyInstance(
                Context.class.getClassLoader(),
                new Class<?>[]{Context.class},
                capturador);

        ReporteController.reporteFormacionFinanciamiento(ctx);

        assertEquals(200, capturador.status,
                "El controlador devolvió error: " + capturador.resultado);
        assertNotNull(capturador.json, "El controlador no devolvió JSON");
        assertTrue(capturador.json instanceof Map,
                "El JSON del reporte debe ser un mapa");

        Map<?, ?> json = (Map<?, ?>) capturador.json;
        for (String clave : List.of(
                "areasInteres",
                "alertaConcentracion",
                "nivelesFormacion",
                "cruceNivelOrg",
                "cruceAreaNivel",
                "financiamiento")) {
            assertTrue(json.containsKey(clave), "Falta la clave " + clave);
        }

        assertTrue(json.get("areasInteres") instanceof List, "areasInteres debe ser una lista");
        assertTrue(json.get("nivelesFormacion") instanceof List,
                "nivelesFormacion debe ser una lista");
        assertTrue(json.get("cruceNivelOrg") instanceof List, "cruceNivelOrg debe ser una lista");
        assertTrue(json.get("cruceAreaNivel") instanceof List,
                "cruceAreaNivel debe ser una lista");
        assertTrue(json.get("alertaConcentracion") instanceof Boolean,
                "alertaConcentracion debe ser booleano");

        assertTrue(json.get("financiamiento") instanceof Map,
                "financiamiento debe ser un mapa");
        Map<?, ?> financiamiento = (Map<?, ?>) json.get("financiamiento");
        assertNotNull(financiamiento.get("chi2"), "El financiamiento debe incluir chi2");
        assertNotNull(financiamiento.get("filas"),
                "El financiamiento debe incluir filas");
        assertNotNull(financiamiento.get("columnas"),
                "El financiamiento debe incluir columnas");
        assertNotNull(financiamiento.get("celdas"),
                "El financiamiento debe incluir celdas");
    }

    private static void asumirBdDisponible() {
        try (Connection conn = Database.getConnection()) {
            // La conexión se cierra automáticamente.
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "BD no disponible: " + e.getMessage());
        }
    }

    /** Captura el resultado de ctx.json(...) sin levantar un servidor HTTP. */
    private static final class ContextoCapturador implements InvocationHandler {

        private Object json;
        private int status = 200;
        private String resultado;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "json":
                    this.json = args[0];
                    return proxy;
                case "status":
                    this.status = args[0] instanceof Integer
                            ? (Integer) args[0]
                            : ((io.javalin.http.HttpStatus) args[0]).getCode();
                    return proxy;
                case "result":
                    this.resultado = String.valueOf(args[0]);
                    return proxy;
                default:
                    return valorPorDefecto(method.getReturnType());
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
}
