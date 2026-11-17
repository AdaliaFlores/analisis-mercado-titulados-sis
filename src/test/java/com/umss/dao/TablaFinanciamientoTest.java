package com.umss.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.umss.config.Database;
import com.umss.model.CeldaCruce;
import com.umss.model.ResultadoChi2;

/**
 * Pruebas de integración de la matriz de contingencia y el Chi-cuadrado (HU-6).
 * Si la BD no está disponible, las pruebas se omiten (no fallan).
 */
class TablaFinanciamientoTest {

    @Test
    @DisplayName("HU-6: la tabla expone todas las claves requeridas")
    void tablaExponeEstructuraCompleta() throws Exception {
        asumirBdDisponible();

        Map<String, Object> fin = EncuestaDAO.tablaFinanciamiento();

        assertNotNull(fin, "La tabla de financiamiento no debe ser nula");
        for (String clave : List.of("filas", "columnas", "conteoFilas", "conteoCols",
                "celdas", "tieneDatos", "totalN", "chi2")) {
            assertTrue(fin.containsKey(clave), "Falta la clave " + clave);
        }
        assertNotNull(fin.get("chi2"), "El resultado Chi-cuadrado no debe ser nulo");
    }

    @Test
    @DisplayName("HU-6: los totales marginales son coherentes con las celdas")
    void totalesMarginalesCoherentes() throws Exception {
        asumirBdDisponible();

        Map<String, Object> fin = EncuestaDAO.tablaFinanciamiento();
        int totalN = ((Number) fin.get("totalN")).intValue();
        List<?> filas = (List<?>) fin.get("filas");
        List<?> columnas = (List<?>) fin.get("columnas");
        List<?> celdas = (List<?>) fin.get("celdas");

        if (totalN == 0) {
            assertTrue(filas.isEmpty(), "Sin datos no debe haber filas");
            assertTrue(columnas.isEmpty(), "Sin datos no debe haber columnas");
            assertTrue(celdas.isEmpty(), "Sin datos no debe haber celdas");
            assertFalse((Boolean) fin.get("tieneDatos"), "Sin datos tieneDatos debe ser false");
        } else {
            int sumaCeldas = 0;
            for (Object obj : celdas) {
                int total = ((CeldaCruce) obj).getTotal();
                assertTrue(total >= 0, "El total de una celda no puede ser negativo");
                sumaCeldas += total;
            }
            assertEquals(totalN, sumaCeldas, "La suma de celdas debe igualar N");
            assertEquals(totalN, sumaConteos((Map<?, ?>) fin.get("conteoFilas")),
                    "La suma de totales de fila debe igualar N");
            assertEquals(totalN, sumaConteos((Map<?, ?>) fin.get("conteoCols")),
                    "La suma de totales de columna debe igualar N");
        }
    }

    @Test
    @DisplayName("HU-6: los grados de libertad y el p-valor son válidos")
    void gradosLibertadYValorPValidos() throws Exception {
        asumirBdDisponible();

        Map<String, Object> fin = EncuestaDAO.tablaFinanciamiento();
        int totalN = ((Number) fin.get("totalN")).intValue();
        int nFilas = ((List<?>) fin.get("filas")).size();
        int nColumnas = ((List<?>) fin.get("columnas")).size();
        ResultadoChi2 chi2 = (ResultadoChi2) fin.get("chi2");

        int glEsperado = totalN == 0 ? 0 : Math.max(0, (nFilas - 1) * (nColumnas - 1));
        assertEquals(glEsperado, chi2.getGradosLibertad(),
                "Los grados de libertad deben ser (filas-1)(columnas-1)");

        double p = chi2.getValorP();
        assertTrue(Double.isFinite(p), "El p-valor debe ser finito");
        assertTrue(p >= 0.0 && p <= 1.0, "El p-valor debe estar entre 0 y 1");
        assertEquals(p >= 0.05, chi2.isIndependiente(),
                "La decisión de independencia debe corresponder al umbral p >= 0.05");
        assertTrue(chi2.getChiCuadrado() >= 0.0, "El Chi-cuadrado no puede ser negativo");
        assertNotNull(chi2.getInterpretacion(), "La interpretación no debe ser nula");
        assertFalse(chi2.getInterpretacion().isBlank(), "La interpretación no debe estar vacía");
    }

    @Test
    @DisplayName("HU-6: con menos de 5 observaciones no se calcula la prueba")
    void muestraInsuficienteNoCalculaPrueba() throws Exception {
        asumirBdDisponible();

        Map<String, Object> fin = EncuestaDAO.tablaFinanciamiento();
        int totalN = ((Number) fin.get("totalN")).intValue();
        ResultadoChi2 chi2 = (ResultadoChi2) fin.get("chi2");

        if (totalN > 0 && totalN < 5) {
            assertFalse((Boolean) fin.get("tieneDatos"),
                    "Con N < 5 no debe considerarse una muestra suficiente");
            assertEquals(1.0, chi2.getValorP(), 1e-9,
                    "Con muestra insuficiente el p-valor debe ser 1.0");
        }
    }

    private static int sumaConteos(Map<?, ?> mapa) {
        int suma = 0;
        for (Object valor : mapa.values()) suma += ((Number) valor).intValue();
        return suma;
    }

    private static void asumirBdDisponible() {
        try (Connection conn = Database.getConnection()) {
            // La conexión se cierra automáticamente.
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "BD no disponible: " + e.getMessage());
        }
    }
}
