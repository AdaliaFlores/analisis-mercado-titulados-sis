package com.umss.dao;

import com.umss.model.RegistroDesempleo;
import com.umss.util.CalculadoraDesempleo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * CP-08: prueba de caja blanca con los datos reales de la base de datos.
 * Si la BD no está disponible, la prueba se omite (no falla).
 */
class DesempleoDatosRealesTest {

    private static int suma(List<CalculadoraDesempleo.Frecuencia> filas) {
        return filas.stream().mapToInt(CalculadoraDesempleo.Frecuencia::total).sum();
    }

    private static CalculadoraDesempleo.Frecuencia moda(
            List<CalculadoraDesempleo.Frecuencia> filas) {
        return filas.stream()
                .max(java.util.Comparator.comparingInt(CalculadoraDesempleo.Frecuencia::total))
                .orElseThrow();
    }

    @Test
    @DisplayName("CP-08: los n, modas y cruces reales coinciden con la validación en Excel")
    void datosRealesSonCoherentes() {
        List<RegistroDesempleo> registros;
        try {
            registros = EncuestaDAO.obtenerRegistrosDesempleo();
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "BD no disponible: " + e.getMessage());
            return;
        }

        assertFalse(registros.isEmpty(), "No hay respuestas de desempleo en la BD");

        CalculadoraDesempleo.AnalisisDesempleo a =
                CalculadoraDesempleo.analisis(registros, List.of());

        // n validados en la hoja Normalizado_Desempleo / Frecuencias_Desempleo
        assertEquals(184, a.total());
        assertEquals(17, a.conTresVariables());

        assertEquals(184, suma(a.situacion()));
        assertEquals(182, suma(a.tiempoDesempleo()));
        assertEquals(28, suma(a.razon()));
        assertEquals(28, suma(a.experiencia()));
        assertEquals(134, suma(a.primerEmpleo()));
        assertEquals(180, suma(a.vidaProfesional()));

        // modas validadas en Excel
        assertEquals("Trabajando en una organización", a.situacion().get(0).etiqueta());
        assertEquals(139, a.situacion().get(0).total());
        assertEquals("0 años", a.tiempoDesempleo().get(0).etiqueta());
        assertEquals(69, a.tiempoDesempleo().get(0).total());
        assertEquals(8, a.razon().get(0).total());
        assertEquals("Sí", a.experiencia().get(0).etiqueta());
        assertEquals(17, a.experiencia().get(0).total());
        assertEquals("0 años", a.primerEmpleo().get(0).etiqueta());
        assertEquals(81, a.primerEmpleo().get(0).total());
        // Vida profesional conserva el orden de los intervalos, no el orden por frecuencia.
        CalculadoraDesempleo.Frecuencia modaVida = moda(a.vidaProfesional());
        assertEquals("Más de 10 años", modaVida.etiqueta());
        assertEquals(71, modaVida.total());

        // totales de los cuatro cruces de dos variables
        assertEquals(182, a.situacionTiempo().total());
        assertEquals(28, a.razonExperiencia().total());
        assertEquals(17, a.razonPrimerEmpleo().total());
        assertEquals(27, a.razonVidaProfesional().total());

        // cruce de tres variables: solo bloques no vacíos y suma = casos completos
        assertEquals(5, a.bloques().size());
        assertEquals(a.conTresVariables(), a.bloques().stream()
                .mapToInt(CalculadoraDesempleo.BloqueRazon::total)
                .sum());
    }
}
