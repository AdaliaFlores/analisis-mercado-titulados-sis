package com.umss.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.umss.model.CeldaCruce;
import com.umss.model.ResultadoChiCuadrado;

/**
 * Pruebas unitarias del motor genérico de Chi-cuadrado.
 * No dependen de la base de datos.
 */
class PruebaChiCuadradoTest {

    @Test
    @DisplayName("Una tabla vacia no es calculable y no divide por cero")
    void tablaVaciaNoEsCalculable() {
        ResultadoChiCuadrado r = PruebaChiCuadrado.calcular(
                new ArrayList<>(),
                Map.of(),
                Map.of());

        assertNotNull(r, "El resultado no debe ser nulo");
        assertEquals(0, r.n());
        assertTrue(r.etiquetasFila().isEmpty());
        assertTrue(r.etiquetasColumna().isEmpty());
        assertNull(r.chiCuadrado(), "Sin datos el Chi-cuadrado debe ser nulo");
        assertNull(r.pValor(), "Sin datos el p-valor debe ser nulo");
        assertTrue(r.gradosLibertad() >= 0,
                "Los grados de libertad no deben ser negativos (no se usan sin datos)");
        assertFalse(r.cumpleSupuestos());
        assertTrue(r.advertencias().stream().anyMatch(a -> a.contains("No hay datos")),
                "Debe informar que no hay datos");
    }

    @Test
    @DisplayName("Una tabla 2×2 calcula Chi-cuadrado, gl y p-valor correctamente")
    void tablaDosPorDosCalculaCorrectamente() {
        List<CeldaCruce> celdas = List.of(
                celda("A", "X", 10),
                celda("A", "Y", 20),
                celda("B", "X", 30),
                celda("B", "Y", 40));

        ResultadoChiCuadrado r = PruebaChiCuadrado.calcular(
                celdas,
                Map.of("A", 1, "B", 2),
                Map.of("X", 1, "Y", 2));

        assertEquals(100, r.n());
        assertEquals(1, r.gradosLibertad());
        assertNotNull(r.chiCuadrado());
        assertEquals(0.79365079365, r.chiCuadrado(), 1e-9,
                "χ² = Σ (O-E)²/E para la tabla indicada");

        double sumaContribuciones = 0.0;
        for (double[] fila : r.contribucion()) {
            for (double valor : fila) sumaContribuciones += valor;
        }
        assertEquals(r.chiCuadrado(), sumaContribuciones, 1e-9,
                "La suma de contribuciones debe igualar el Chi-cuadrado");

        assertEquals(10L, r.observada()[0][0]);
        assertEquals(20L, r.observada()[0][1]);
        assertEquals(30L, r.observada()[1][0]);
        assertEquals(40L, r.observada()[1][1]);

        assertNotNull(r.pValor());
        assertTrue(Double.isFinite(r.pValor()));
        assertTrue(r.pValor() >= 0.0 && r.pValor() <= 1.0);
        assertFalse(r.rechazaIndependencia(),
                "Con χ² = 0.794 no se rechaza la independencia");
        assertTrue(r.cumpleSupuestos(), "Todas las esperadas superan 5 y N >= 30");
    }

    @Test
    @DisplayName("Una tabla con una sola fila no es calculable")
    void tablaConUnaSolaFilaNoEsCalculable() {
        List<CeldaCruce> celdas = List.of(
                celda("A", "X", 5),
                celda("A", "Y", 7));

        ResultadoChiCuadrado r = PruebaChiCuadrado.calcular(
                celdas,
                Map.of("A", 1),
                Map.of("X", 1, "Y", 2));

        assertEquals(12, r.n());
        assertEquals(0, r.gradosLibertad());
        assertNull(r.chiCuadrado(), "Con una sola fila no se calcula el Chi-cuadrado");
        assertNull(r.pValor());
        assertTrue(r.advertencias().stream().anyMatch(a -> a.contains("al menos 2 categorías")),
                "Debe explicar que se requieren dos categorías por variable");
    }

    @Test
    @DisplayName("Una tabla con una sola columna no es calculable")
    void tablaConUnaSolaColumnaNoEsCalculable() {
        List<CeldaCruce> celdas = List.of(
                celda("A", "X", 4),
                celda("B", "X", 6));

        ResultadoChiCuadrado r = PruebaChiCuadrado.calcular(
                celdas,
                Map.of("A", 1, "B", 2),
                Map.of("X", 1));

        assertEquals(10, r.n());
        assertEquals(0, r.gradosLibertad());
        assertNull(r.chiCuadrado());
        assertNull(r.pValor());
        assertTrue(r.advertencias().stream().anyMatch(a -> a.contains("al menos 2 categorías")));
    }

    private static CeldaCruce celda(String x, String y, int total) {
        CeldaCruce c = new CeldaCruce();
        c.setValorX(x);
        c.setValorY(y);
        c.setTotal(total);
        return c;
    }
}
