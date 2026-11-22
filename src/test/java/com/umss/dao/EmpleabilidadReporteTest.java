package com.umss.dao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import com.umss.controller.ReporteController;
import com.umss.model.AreaEmpleabilidad;
import com.umss.model.ConteoRespuesta;

import io.javalin.http.Context;

/**
 * Pruebas de caja blanca e integracion del reporte
 * "Analisis de Empleabilidad y Barreras de Insercion" (HU-4).
 * Si la BD no esta disponible, las pruebas se omiten (no fallan).
 */
class EmpleabilidadReporteTest {

    private static final int PREGUNTA_ANTIGUEDAD = 12;

    @Test
    @DisplayName("CA-01 a CA-04: los estadisticos de antigüedad son numericos y validos")
    void estadisticosDeAntiguedadSonValidos() throws Exception {
        asumirBdDisponible();

        Map<String, Object> stats = EncuestaDAO.estadisticosAntiguedad(PREGUNTA_ANTIGUEDAD);

        assertNotNull(stats, "Los estadisticos no deben ser nulos");
        assertTrue(stats.containsKey("n"), "Falta la clave n");
        assertTrue(((Number) stats.get("n")).intValue() >= 0, "n debe ser >= 0");

        assertEstadisticoValido(stats, "media");
        assertEstadisticoValido(stats, "mediana");
        assertEstadisticoValido(stats, "moda");
        assertEstadisticoValido(stats, "desviacion");
    }

    @Test
    @DisplayName("CA-07: un id de pregunta inexistente no lanza excepcion ni divide por cero")
    void idInexistenteNoLanzaExcepcion() throws Exception {
        asumirBdDisponible();

        Map<String, Object> stats = assertDoesNotThrow(
                () -> EncuestaDAO.estadisticosAntiguedad(-999),
                "Un id inexistente no debe lanzar excepción");

        assertNotNull(stats, "Los estadisticos no deben ser nulos");
        assertEquals(0, ((Number) stats.get("n")).intValue(),
                "Sin datos, n debe ser 0");
        assertEstadisticoValido(stats, "media");
        assertEstadisticoValido(stats, "mediana");
        assertEstadisticoValido(stats, "moda");
        assertEstadisticoValido(stats, "desviacion");
    }

    @Test
    @DisplayName("CA-05: cada area cumple la formula de empleabilidad ponderada")
    void empleabilidadPonderadaCumpleFormula() throws Exception {
        asumirBdDisponible();

        List<AreaEmpleabilidad> areas = EncuestaDAO.analisisEmpleabilidad();

        assertNotNull(areas, "Las areas de empleabilidad no deben ser nulas");
        assertFalse(areas.isEmpty(), "Debe haber areas de empleabilidad");
        for (AreaEmpleabilidad area : areas) {
            double esperado = Math.round(
                    ((area.getDemandaEmpleador() + area.getInteresTitulados()) / 2.0) * 10.0
            ) / 10.0;
            assertEquals(esperado, area.getEmpleabilidad(), 1e-9,
                    "La empleabilidad de " + area.getArea()
                            + " debe ser (demanda + interes) / 2 redondeado a 1 decimal");
        }
    }

    @Test
    @DisplayName("CA-06: las razones de desempleo están ordenadas por frecuencia")
    void barrerasDeDesempleoEstanOrdenadas() throws Exception {
        asumirBdDisponible();

        List<ConteoRespuesta> razones = EncuestaDAO.contarRespuestas(28);

        assertOrdenadoDescendente(razones, "Las razones de desempleo");
    }

    @Test
    @DisplayName("CA-08: los rubros están ordenados por frecuencia")
    void rubrosEstanOrdenados() throws Exception {
        asumirBdDisponible();

        List<ConteoRespuesta> rubros = EncuestaDAO.contarRespuestas(53);

        assertOrdenadoDescendente(rubros, "Los rubros del empleador");
    }

    @Test
    @DisplayName("CA-09: la contratación y las materias de poca utilidad están ordenadas")
    void contratacionYMateriasEstanOrdenadas() throws Exception {
        asumirBdDisponible();

        assertOrdenadoDescendente(EncuestaDAO.contarRespuestas(55),
                "La posibilidad de contratación");
        assertOrdenadoDescendente(EncuestaDAO.asignaturasFrecuencia(44),
                "Las materias de poca utilidad");
    }

    @Test
    @DisplayName("CA-10: el reporte expone las claves requeridas por el frontend")
    void reporteExponeClavesDelFrontend() {
        asumirBdDisponible();

        ContextoCapturador capturador = new ContextoCapturador();
        Context ctx = (Context) Proxy.newProxyInstance(
                Context.class.getClassLoader(),
                new Class<?>[]{Context.class},
                capturador);

        ReporteController.reporteEmpleabilidad(ctx);

        assertEquals(200, capturador.status,
                "El controlador devolvió error: " + capturador.resultado);
        assertNotNull(capturador.json, "El controlador no devolvió JSON");
        assertTrue(capturador.json instanceof Map,
                "El JSON del reporte debe ser un mapa");

        Map<?, ?> json = (Map<?, ?>) capturador.json;
        for (String clave : List.of(
                "estadisticosAntiguedad",
                "areasEmpleabilidad",
                "razones",
                "razonesCriticas",
                "asignaturasPocaUtilidadTop",
                "contratacionEmpleador",
                "rubroEmpleador")) {
            assertTrue(json.containsKey(clave), "Falta la clave " + clave);
        }
    }

    private static void asumirBdDisponible() {
        try (Connection conn = Database.getConnection()) {
            // La conexión se cierra automáticamente.
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "BD no disponible: " + e.getMessage());
        }
    }

    private static void assertEstadisticoValido(Map<String, Object> stats, String clave) {
        assertTrue(stats.containsKey(clave), "Falta la clave " + clave);
        Object valor = stats.get(clave);
        assertNotNull(valor, "El valor de " + clave + " no debe ser nulo");
        assertTrue(valor instanceof Number, "El valor de " + clave + " debe ser numérico");
        double numero = ((Number) valor).doubleValue();
        assertTrue(numero >= 0.0, "El valor de " + clave + " debe ser >= 0");
        assertTrue(Double.isFinite(numero), "El valor de " + clave + " debe ser finito");
    }

    private static void assertOrdenadoDescendente(List<ConteoRespuesta> lista, String nombre) {
        assertNotNull(lista, nombre + " no debe ser nulo");
        for (int i = 1; i < lista.size(); i++) {
            assertTrue(lista.get(i - 1).getTotal() >= lista.get(i).getTotal(),
                    nombre + " debe estar ordenado de mayor a menor frecuencia "
                            + "(posición " + i + ")");
        }
    }

    /** Captura el resultado de ctx.json(...) sin levantar un servidor HTTP */
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
                    this.status = (int) args[0];
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
