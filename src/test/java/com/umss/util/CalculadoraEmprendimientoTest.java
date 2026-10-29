package com.umss.util;

import com.umss.model.ConteoRespuesta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias de las fórmulas del reporte de Emprendimiento.
 * Usan datos sintéticos, por lo que no dependen de la base de datos.
 */
class CalculadoraEmprendimientoTest {

    private static ConteoRespuesta fila(int preguntaId, String respuesta, int total) {
        ConteoRespuesta c = new ConteoRespuesta();
        c.setPreguntaId(preguntaId);
        c.setRespuesta(respuesta);
        c.setTotal(total);
        c.setPorcentaje(0);
        return c;
    }

    /** Reproduce los datos que había en la BD al documentar el reporte. */
    private static List<ConteoRespuesta> datosSnapshot() {
        List<ConteoRespuesta> d = new ArrayList<>();
        d.add(fila(30, "Idea conjunta con compañeros.", 6));
        d.add(fila(30, "Idea propia.", 6));
        d.add(fila(30, "Otro.", 2));
        d.add(fila(30, "StartUp", 1));

        d.add(fila(31, "Ambos", 7));
        d.add(fila(31, "Producto", 5));
        d.add(fila(31, "Servicio", 3));

        d.add(fila(32, "No, financiamiento propio.", 10));
        d.add(fila(32, "Si, mediante inversores.", 3));
        d.add(fila(32, "Si, mediante préstamos bancarios.", 2));

        d.add(fila(33, "Satisfecho", 8));
        d.add(fila(33, "Algo satisfecho", 6));
        d.add(fila(33, "Insatisfecho", 1));

        d.add(fila(34, "Importante", 6));
        d.add(fila(34, "Muy importante", 6));
        d.add(fila(34, "Poco Importante", 3));
        return d;
    }

    @Test
    @DisplayName("Universo, origen y entregable predominante coinciden con el reporte")
    void universoOrigenYEntregable() {
        CalculadoraEmprendimiento.Metricas m =
                CalculadoraEmprendimiento.metricas(datosSnapshot());

        assertEquals(15, m.universo());
        assertEquals(0, m.spinOff());
        assertEquals(1, m.startUp());
        assertEquals(6, m.ideaPropia());

        assertEquals("Ambos", m.topEntregable());
        assertEquals(7, m.topEntregableTotal());
        assertEquals(15, m.totalEntregable());
    }

    @Test
    @DisplayName("Financiamiento: externo = respuestas que inician con 'Si'; propio = resto")
    void financiamientoExternoYPropio() {
        CalculadoraEmprendimiento.Metricas m =
                CalculadoraEmprendimiento.metricas(datosSnapshot());

        assertEquals(5, m.financiamientoExterno());   // 3 inversores + 2 préstamos
        assertEquals(10, m.financiamientoPropio());   // 15 - 5
        assertEquals(15, m.totalFinanciamiento());
    }

    @Test
    @DisplayName("Satisfacción e importancia: etiquetas verbales se convierten a escala 1–5")
    void escalasVerbalas() {
        CalculadoraEmprendimiento.Metricas m =
                CalculadoraEmprendimiento.metricas(datosSnapshot());

        // (4*8 + 3*6 + 2*1) / 15 = 52 / 15 = 3.4667
        assertEquals(52.0 / 15.0, m.promedioSatisfaccion(), 1e-9);
        assertEquals(8, m.altaSatisfaccion());        // solo "Satisfecho" (valor 4)
        assertEquals(15, m.nSatisfaccion());
        assertEquals(0, m.sinCodificarSatisfaccion());

        // (4*6 + 5*6 + 2*3) / 15 = 60 / 15 = 4.0
        assertEquals(4.0, m.promedioImportancia(), 1e-9);
        assertEquals(12, m.altaImportancia());        // Importante + Muy importante
        assertEquals(15, m.nImportancia());
        assertEquals(0, m.sinCodificarImportancia());
    }

    @Test
    @DisplayName("El resumen textual reproduce las cifras calculadas")
    void bulletsConCifrasExactas() {
        List<String> bullets = CalculadoraEmprendimiento.bullets(datosSnapshot());

        assertTrue(bullets.contains(
                "Universo analizado: 15 emprendimiento(s) registrados por los titulados."));
        assertTrue(bullets.contains(
                "Origen: Idea propia 40.0% · StartUp 6.7%. Predomina la StartUp."));
        assertTrue(bullets.contains(
                "Tipo de entregable predominante: Ambos (7 de 15, 46.7%)."));
        assertTrue(bullets.contains(
                "Financiamiento externo: 33.3% accedió a financiamiento (inversores/banca) "
                        + "y 66.7% trabajó con recursos propios."));
        assertTrue(bullets.contains(
                "Satisfacción con el rendimiento: promedio 3.5/5; 53.3% calificó con 4 o 5."));
        assertTrue(bullets.contains(
                "Importancia de la formación: promedio 4.0/5; 80.0% la valoró con 4 o 5, "
                        + "lo que respalda el fortalecimiento de competencias emprendedoras."));
    }

    @Test
    @DisplayName("Las recomendaciones se activan según los datos")
    void recomendacionesSegunDatos() {
        List<String> r = CalculadoraEmprendimiento.recomendaciones(datosSnapshot());

        assertEquals(3, r.size());
        assertTrue(r.get(0).contains("financiamiento externo"));
        assertTrue(r.get(1).contains("StartUps"));
        assertTrue(r.get(2).contains("formación práctica"));
    }

    @Test
    @DisplayName("Respuestas numéricas 1–5 también se aceptan")
    void respuestasNumericas() {
        List<ConteoRespuesta> datos = List.of(
                fila(33, "5", 2),
                fila(33, "4", 3),
                fila(33, "3", 1));

        CalculadoraEmprendimiento.Metricas m = CalculadoraEmprendimiento.metricas(datos);

        // (5*2 + 4*3 + 3*1) / 6 = 25 / 6
        assertEquals(25.0 / 6.0, m.promedioSatisfaccion(), 1e-9);
        assertEquals(5, m.altaSatisfaccion());
        assertEquals(6, m.nSatisfaccion());
    }

    @Test
    @DisplayName("Las respuestas no reconocidas se excluyen y se informan")
    void respuestasSinCodificar() {
        List<ConteoRespuesta> datos = List.of(
                fila(30, "Idea propia", 4),
                fila(33, "Satisfecho", 4),
                fila(33, "Ns/Nc", 2));

        CalculadoraEmprendimiento.Metricas m = CalculadoraEmprendimiento.metricas(datos);
        assertEquals(4.0, m.promedioSatisfaccion(), 1e-9);
        assertEquals(4, m.nSatisfaccion());
        assertEquals(2, m.sinCodificarSatisfaccion());

        List<String> bullets = CalculadoraEmprendimiento.bullets(m);
        assertTrue(bullets.stream().anyMatch(b ->
                b.contains("2 respuesta(s) de satisfacción no tienen un valor 1–5 reconocible")));
    }

    @Test
    @DisplayName("Mayúsculas y tildes no afectan el mapeo")
    void normalizacionDeEtiquetas() {
        List<ConteoRespuesta> datos = List.of(
                fila(34, "MUY IMPORTANTE", 1),
                fila(34, "Poco importante", 1));

        CalculadoraEmprendimiento.Metricas m = CalculadoraEmprendimiento.metricas(datos);

        assertEquals(3.5, m.promedioImportancia(), 1e-9); // (5 + 2) / 2
        assertEquals(1, m.altaImportancia());
        assertEquals(2, m.nImportancia());
    }

    @Test
    @DisplayName("Financiamiento reconoce 'Si' con y sin tilde")
    void financiamientoConTilde() {
        List<ConteoRespuesta> datos = List.of(
                fila(32, "Si, mediante inversores", 2),
                fila(32, "Sí, mediante préstamos", 1),
                fila(32, "No, financiamiento propio", 4));

        CalculadoraEmprendimiento.Metricas m = CalculadoraEmprendimiento.metricas(datos);

        assertEquals(3, m.financiamientoExterno());
        assertEquals(4, m.financiamientoPropio());
    }

    @Test
    @DisplayName("Ante empate de entregables se conserva el primero (orden de la consulta)")
    void empateEntregable() {
        List<ConteoRespuesta> datos = List.of(
                fila(31, "Servicio", 5),
                fila(31, "Producto", 5));

        CalculadoraEmprendimiento.Metricas m = CalculadoraEmprendimiento.metricas(datos);

        assertEquals("Servicio", m.topEntregable());
    }

    @Test
    @DisplayName("Sin datos no se producen textos ni errores")
    void sinDatos() {
        CalculadoraEmprendimiento.Metricas m = CalculadoraEmprendimiento.metricas(List.of());

        assertEquals(0, m.universo());
        assertNull(m.topEntregable());
        assertTrue(CalculadoraEmprendimiento.bullets(m).isEmpty());
        assertTrue(CalculadoraEmprendimiento.recomendaciones(m).isEmpty());
    }

    @Test
    @DisplayName("valorEscala rechaza valores fuera de 1–5 y vacíos")
    void valorEscalaBordes() {
        Map<String, Integer> mapa = CalculadoraEmprendimiento.MAPA_SATISFACCION;

        assertEquals(4, CalculadoraEmprendimiento.valorEscala("Satisfecho", mapa));
        assertEquals(4, CalculadoraEmprendimiento.valorEscala(" 4 ", mapa));
        assertNull(CalculadoraEmprendimiento.valorEscala("6", mapa));
        assertNull(CalculadoraEmprendimiento.valorEscala("0", mapa));
        assertNull(CalculadoraEmprendimiento.valorEscala("", mapa));
        assertNull(CalculadoraEmprendimiento.valorEscala(null, mapa));
    }

    @Test
    @DisplayName("resumen() expone métricas, bullets y recomendaciones")
    void estructuraDelResumen() {
        Map<String, Object> resumen = CalculadoraEmprendimiento.resumen(datosSnapshot());

        assertTrue(resumen.containsKey("metricas"));
        assertTrue(resumen.containsKey("bullets"));
        assertTrue(resumen.containsKey("recomendaciones"));
        assertFalse(((List<?>) resumen.get("bullets")).isEmpty());
    }
}
