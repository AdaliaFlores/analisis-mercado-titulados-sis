package com.umss.dao;

import com.umss.model.CeldaCruce;
import com.umss.model.ConteoRespuesta;
import com.umss.model.DimensionRadar;
import com.umss.model.IndicadorEscala;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-BRE: prueba de caja blanca con los datos reales de la base de datos.
 * Valida que el DAO produzca exactamente los mismos números que el Excel.
 * Si la BD no está disponible, la prueba se omite (no falla).
 */
class BrechasDatosRealesTest {

    private static int suma(List<ConteoRespuesta> filas) {
        return filas.stream().mapToInt(ConteoRespuesta::getTotal).sum();
    }

    private static int sumaCruce(List<CeldaCruce> filas) {
        return filas.stream().mapToInt(CeldaCruce::getTotal).sum();
    }

    private static ConteoRespuesta frecuencia(List<ConteoRespuesta> filas, String respuesta) {
        return filas.stream()
                .filter(f -> f.getRespuesta().equals(respuesta))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró: " + respuesta));
    }

    private static DimensionRadar dimension(List<DimensionRadar> filas, String nombre) {
        return filas.stream()
                .filter(d -> d.getDimension().equals(nombre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró: " + nombre));
    }

    private static int celda(List<CeldaCruce> filas, String x, String y) {
        return filas.stream()
                .filter(c -> c.getValorX().equals(x) && c.getValorY().equals(y))
                .mapToInt(CeldaCruce::getTotal)
                .sum();
    }

    private static void cargarBD() {
        try {
            EncuestaDAO.contarEnvios(42);
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "BD no disponible: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("CP-BRE-01: totales e indicadores coinciden con Indicadores_Estado_Actual")
    void totalesEIndicadores() throws Exception {
        cargarBD();

        assertEquals(184, EncuestaDAO.contarEnvios(42));
        assertEquals(79, EncuestaDAO.contarEnvios(59, 60, 61));

        List<IndicadorEscala> inds = List.of(
                EncuestaDAO.escalaIndicador(38, "Satisfacción con la formación recibida"),
                EncuestaDAO.escalaIndicador(39, "Concordancia formación ↔ mercado laboral"),
                EncuestaDAO.escalaIndicador(24, "Pertinencia del puesto de trabajo"),
                EncuestaDAO.escalaIndicador(11, "Oferta de posgrado afín a la especialidad"));

        assertEquals(182, inds.get(0).getN());
        assertEquals(2.8, inds.get(0).getMedia(), 0.001);
        assertEquals(0.6, inds.get(0).getDesviacion(), 0.001);
        assertEquals(List.of(7, 38, 122, 15),
                inds.get(0).getDistribucion().stream().map(ConteoRespuesta::getTotal).toList());

        assertEquals(183, inds.get(1).getN());
        assertEquals(2.6, inds.get(1).getMedia(), 0.001);
        assertEquals(0.6, inds.get(1).getDesviacion(), 0.001);

        assertEquals(139, inds.get(2).getN());
        assertEquals(3.3, inds.get(2).getMedia(), 0.001);
        assertEquals(0.7, inds.get(2).getDesviacion(), 0.001);

        assertEquals(184, inds.get(3).getN());
        assertEquals(2.6, inds.get(3).getMedia(), 0.001);
        assertEquals(0.8, inds.get(3).getDesviacion(), 0.001);
    }

    @Test
    @DisplayName("CP-BRE-02: el radar coincide con la tabla 4 de Validacion_Brechas")
    void radar() throws Exception {
        cargarBD();
        List<DimensionRadar> radar = EncuestaDAO.radarBrechas();

        assertEquals(17, radar.size());

        DimensionRadar ia = dimension(radar, "IA / Machine Learning");
        assertEquals(75.9, ia.getObjetivo(), 0.001);
        assertEquals(45.1, ia.getActual(), 0.001);
        assertEquals(30.8, ia.getBrecha(), 0.001);

        DimensionRadar bd = dimension(radar, "Base de Datos / SQL");
        assertEquals(7.6, bd.getObjetivo(), 0.001);
        assertEquals(93.5, bd.getActual(), 0.001);
        assertEquals(-85.9, bd.getBrecha(), 0.001);

        DimensionRadar redes = dimension(radar, "Redes");
        assertEquals(5.1, redes.getObjetivo(), 0.001);
        assertEquals(78.3, redes.getActual(), 0.001);
        assertEquals(-73.2, redes.getBrecha(), 0.001);

        DimensionRadar blandas = dimension(radar, "Competencias blandas");
        assertEquals(40.5, blandas.getObjetivo(), 0.001);
        assertEquals(0.0, blandas.getActual(), 0.001);
        assertEquals(40.5, blandas.getBrecha(), 0.001);
    }

    @Test
    @DisplayName("CP-BRE-03: frecuencias y mapas de calor coinciden con el Excel")
    void frecuenciasYCruces() throws Exception {
        cargarBD();

        List<ConteoRespuesta> ventaja = EncuestaDAO.asignaturasFrecuencia(42);
        List<ConteoRespuesta> poca = EncuestaDAO.asignaturasFrecuencia(44);
        assertEquals(9, ventaja.size());
        assertEquals(11, poca.size());
        assertEquals(920, suma(ventaja));
        assertEquals(716, suma(poca));
        assertEquals(172, frecuencia(ventaja, "Base de Datos").getTotal());
        assertEquals(97, frecuencia(poca, "Aplic. Interactivas para Televisión Digital").getTotal());

        List<CeldaCruce> cruceVentaja = EncuestaDAO.cruceAsignaturaVariable(42, 14);
        List<CeldaCruce> crucePocaSituacion = EncuestaDAO.cruceAsignaturaVariable(44, 14);
        List<CeldaCruce> crucePocaRazon = EncuestaDAO.cruceAsignaturaVariable(44, 28);

        assertEquals(920, sumaCruce(cruceVentaja));
        assertEquals(716, sumaCruce(crucePocaSituacion));
        assertEquals(104, sumaCruce(crucePocaRazon));

        assertEquals(129, celda(cruceVentaja, "Base de Datos",
                "Actualmente me encuentro trabajando un una organización"));
        assertEquals(27, celda(cruceVentaja, "Base de Datos", "Actualmente NO trabajo"));
        assertEquals(16, celda(cruceVentaja, "Base de Datos",
                "Actualmente dedico mi tiempo COMPLETO a un emprendimiento propio"));
    }

    @Test
    @DisplayName("CP-BRE-04: palabras clave de empleadores y coherencia general")
    void palabrasClave() throws Exception {
        cargarBD();
        List<com.umss.model.ConteoPalabra> tendencias = EncuestaDAO.contarPalabrasClave(59);

        assertFalse(tendencias.isEmpty());
        assertEquals("IA / Machine Learning", tendencias.get(0).getPalabra());
        assertEquals(49, tendencias.get(0).getTotal());
        assertEquals(62.0, tendencias.get(0).getPorcentaje(), 0.001);
        assertTrue(tendencias.stream().allMatch(c -> c.getPorcentaje() >= 0.0 && c.getPorcentaje() <= 100.0));

        int titulados = EncuestaDAO.contarEnvios(42);
        assertTrue(suma(EncuestaDAO.asignaturasFrecuencia(42)) >= titulados,
                "Cada titulado con p42 selecciona al menos una asignatura");
    }
}
