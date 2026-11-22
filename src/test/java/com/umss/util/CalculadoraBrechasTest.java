package com.umss.util;

import com.umss.model.CeldaCruce;
import com.umss.model.ConteoPalabra;
import com.umss.model.ConteoRespuesta;
import com.umss.model.DimensionRadar;
import com.umss.model.IndicadorEscala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias de los cálculos del reporte de Evaluación de Brechas.
 * No dependen de la base de datos: usan respuestas sintéticas.
 */
class CalculadoraBrechasTest {

    private static Map<String, String[]> map(String... pares) {
        Map<String, String[]> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) m.put(pares[i], pares[i + 1].split("\\|"));
        return m;
    }

    private static List<String> grupos(String... grupos) {
        return new ArrayList<>(List.of(grupos));
    }

    @Test
    @DisplayName("Normaliza tildes, NBSP, mayúsculas y signos de puntuación")
    void normalizacion() {
        assertEquals("inteligencia artificial",
                CalculadoraBrechas.normalizar("  Inteligencia\u00a0Artificial...  "));
        assertEquals("basicas fisica calculo algebras",
                CalculadoraBrechas.normalizar("Básicas (Física, Calculo, Algebras)"));
        assertEquals("", CalculadoraBrechas.normalizar(null));
    }

    @Test
    @DisplayName("Separa opciones sin partir las comas dentro de paréntesis")
    void separarOpciones() {
        assertEquals(List.of("Básicas (Física, Calculo, Algebras)", "Base de Datos", "Redes"),
                CalculadoraBrechas.separarOpciones(
                        "Básicas (Física, Calculo, Algebras); Base de Datos | Redes"));
        assertTrue(CalculadoraBrechas.separarOpciones(null).isEmpty());
        assertTrue(CalculadoraBrechas.separarOpciones("   ").isEmpty());
    }

    @Test
    @DisplayName("El porcentaje usa un decimal y el mismo redondeo del DAO")
    void porcentajeYRedondeo() {
        assertEquals(75.9, CalculadoraBrechas.porcentaje(60, 79), 0.001);
        assertEquals(40.5, CalculadoraBrechas.porcentaje(32, 79), 0.001);
        assertEquals(93.5, CalculadoraBrechas.porcentaje(172, 184), 0.001);
        assertEquals(0.0, CalculadoraBrechas.porcentaje(5, 0), 0.001);
        assertEquals(2.8, CalculadoraBrechas.redondear1(2.7967), 0.001);
        assertEquals(-85.9, CalculadoraBrechas.redondear1(7.6 - 93.5), 0.001);
    }

    @Test
    @DisplayName("Calcula media, desviación poblacional y distribución Likert")
    void indicadorLikert() {
        List<String> respuestas = List.of(
                "Totalmente en desacuerdo", "En desacuerdo", "De acuerdo", "Totalmente de Acuerdo",
                "De acuerdo", "De acuerdo", "En desacuerdo", "Totalmente de Acuerdo",
                "De acuerdo", "De acuerdo");

        IndicadorEscala ind = CalculadoraBrechas.indicador(38, "Satisfacción", respuestas);

        assertEquals(38, ind.getPregunta());
        assertEquals("Satisfacción", ind.getEtiqueta());
        assertEquals(10, ind.getN());
        assertEquals(2.8, ind.getMedia(), 0.001);
        assertEquals(0.9, ind.getDesviacion(), 0.001);
        assertEquals(4, ind.getDistribucion().size());
        assertEquals(1, ind.getDistribucion().get(0).getTotal());
        assertEquals(2, ind.getDistribucion().get(1).getTotal());
        assertEquals(5, ind.getDistribucion().get(2).getTotal());
        assertEquals(2, ind.getDistribucion().get(3).getTotal());
        assertEquals(50.0, ind.getDistribucion().get(2).getPorcentaje(), 0.001);
    }

    @Test
    @DisplayName("Ignora respuestas vacías y valores fuera de la escala en la media")
    void indicadorIgnoraVacios() {
        IndicadorEscala ind = CalculadoraBrechas.indicador(11, "Oferta", List.of(
                "", "   ", "De acuerdo", "Totalmente de Acuerdo", "Ns/Nc"));

        assertEquals(2, ind.getN());
        assertEquals(3.5, ind.getMedia(), 0.001);
        assertEquals(0.5, ind.getDesviacion(), 0.001);
        assertEquals(3, ind.getDistribucion().size(), "La distribución conserva el valor extra para el n");
    }

    @Test
    @DisplayName("Frecuencias de asignaturas ordenadas por total")
    void frecuenciasAsignaturas() {
        List<ConteoRespuesta> filas = CalculadoraBrechas.frecuenciasAsignaturas(42, List.of(
                "Base de Datos;Redes de Computadora",
                "Base de Datos, Java",
                "Básicas (Física, Calculo, Algebras)"));

        assertEquals(4, filas.size());
        assertEquals("Base de Datos", filas.get(0).getRespuesta());
        assertEquals(2, filas.get(0).getTotal());
        assertEquals(66.7, filas.get(0).getPorcentaje(), 0.001);
        assertEquals(33.3, filas.get(1).getPorcentaje(), 0.001);
        assertEquals(5, filas.stream().mapToInt(ConteoRespuesta::getTotal).sum());
    }

    @Test
    @DisplayName("El cruce por registro genera una celda por asignatura seleccionada")
    void cruceAsignaturaVariable() {
        List<CeldaCruce> celdas = CalculadoraBrechas.cruceAsignaturaVariable(
                "Base de Datos;Redes de Computadora", "Trabajando");

        assertEquals(2, celdas.size());
        assertEquals("Base de Datos", celdas.get(0).getValorX());
        assertEquals("Trabajando", celdas.get(0).getValorY());
        assertEquals(1, celdas.get(0).getTotal());
        assertTrue(CalculadoraBrechas.cruceAsignaturaVariable("", "Trabajando").isEmpty());
        assertTrue(CalculadoraBrechas.cruceAsignaturaVariable("Base de Datos", "").isEmpty());
    }

    @Test
    @DisplayName("El radar calcula brechas firmadas y omite dimensiones sin datos")
    void radar() {
        Map<String, String[]> diccionario = map(
                "IA / Machine Learning", "ia",
                "Python", "python",
                "Redes", "redes",
                "Java", "java");
        Map<String, String[]> asignaturas = map(
                "IA / Machine Learning", "Inteligencia Artificial",
                "Redes", "Redes de Computadora");
        Map<String, List<String>> empleadores = Map.of(
                "e1", List.of("Trabajo con IA"),
                "e2", List.of("Python"));
        Map<String, List<String>> titulados = Map.of(
                "t1", List.of("Inteligencia Artificial"),
                "t2", List.of("Redes de Computadora"),
                "t3", List.of("Redes de Computadora"),
                "t4", List.of("Base de Datos"));

        List<DimensionRadar> radar = CalculadoraBrechas.radar(
                diccionario, asignaturas, empleadores, titulados);

        assertEquals(3, radar.size());
        DimensionRadar ia = radar.stream()
                .filter(d -> d.getDimension().equals("IA / Machine Learning")).findFirst().orElseThrow();
        assertEquals(50.0, ia.getObjetivo(), 0.001);
        assertEquals(25.0, ia.getActual(), 0.001);
        assertEquals(25.0, ia.getBrecha(), 0.001);

        DimensionRadar python = radar.stream()
                .filter(d -> d.getDimension().equals("Python")).findFirst().orElseThrow();
        assertEquals(50.0, python.getBrecha(), 0.001);

        DimensionRadar redes = radar.stream()
                .filter(d -> d.getDimension().equals("Redes")).findFirst().orElseThrow();
        assertEquals(0.0, redes.getObjetivo(), 0.001);
        assertEquals(50.0, redes.getActual(), 0.001);
        assertEquals(-50.0, redes.getBrecha(), 0.001);
    }

    @Test
    @DisplayName("Las palabras clave se cuentan por respuesta y respetan límites de palabra")
    void contarPalabrasClave() {
        Map<String, String[]> diccionario = map(
                "IA / Machine Learning", "ia|inteligencia artificial",
                "Java", "java");

        List<ConteoPalabra> conteos = CalculadoraBrechas.contarPalabrasClave(diccionario, List.of(
                "IA", "Inteligencia artificial", "java", "javadoc", "ia y java"));

        assertEquals(2, conteos.size());
        assertEquals("IA / Machine Learning", conteos.get(0).getPalabra());
        assertEquals(3, conteos.get(0).getTotal());
        assertEquals(60.0, conteos.get(0).getPorcentaje(), 0.001);
        assertEquals("Java", conteos.get(1).getPalabra());
        assertEquals(2, conteos.get(1).getTotal(), "javadoc no debe contarse como java");
        assertEquals(40.0, conteos.get(1).getPorcentaje(), 0.001);
    }

    @Test
    @DisplayName("Los cálculos con listas vacías no fallan")
    void casosVacios() {
        IndicadorEscala ind = CalculadoraBrechas.indicador(38, "Sin datos", List.of());
        assertEquals(0, ind.getN());
        assertEquals(0.0, ind.getMedia(), 0.001);
        assertEquals(0.0, ind.getDesviacion(), 0.001);
        assertTrue(ind.getDistribucion().isEmpty());
        assertTrue(CalculadoraBrechas.frecuenciasAsignaturas(42, List.of()).isEmpty());
        assertTrue(CalculadoraBrechas.contarPalabrasClave(map(), List.of()).isEmpty());
        assertTrue(CalculadoraBrechas.radar(map(), map(), Map.of(), Map.of()).isEmpty());
    }
}
