package com.umss.util;

import com.umss.model.RegistroDesempleo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias del reporte de desempleo. No dependen de la base de datos.
 */
class CalculadoraDesempleoTest {

    private static RegistroDesempleo r(
            String situacion, String tiempo, String razon,
            String experiencia, String primer, String vida) {
        return new RegistroDesempleo(situacion, tiempo, razon, experiencia, primer, vida);
    }

    private static List<RegistroDesempleo> datos() {
        return List.of(
                r("Actualmente me encuentro trabajando un una organización", "0", null, null,
                        "Ya trabajaba antes de titularse", "5 años"),
                r("Actualmente me encuentro trabajando un una organización", "1 año", null, null,
                        "Entre 4 - 8 meses", "10"),
                r("Actualmente NO trabajo", "6 meses", "Demanda laboral inexistente.", "SI",
                        "Entre 8 - 12 meses", "2 años"),
                r("Actualmente NO trabajo", "Medio año", "Demanda laboral inexistente.", "NO",
                        "Más de 12 meses", "1 año"),
                r("Actualmente dedico mi tiempo COMPLETO a un emprendimiento propio", "2 MESES",
                        "Incumplimiento de requisitos de experiencia previa.", "SI",
                        "Menos de 1 mes", "20 años"));
    }

    @Test
    @DisplayName("Convierte años y meses a un único valor numérico")
    void convierteTiemposAAnios() {
        assertEquals(5.0, CalculadoraDesempleo.anios("5 años"));
        assertEquals(5.0, CalculadoraDesempleo.anios("5"));
        assertEquals(0.5, CalculadoraDesempleo.anios("6 meses"));
        assertEquals(0.5, CalculadoraDesempleo.anios("Medio año"));
        assertEquals(0.25, CalculadoraDesempleo.anios("Unos 3 meses"));
        assertEquals(0.0, CalculadoraDesempleo.anios("Ninguno, siempre estuve con trabajo"));
        assertNull(CalculadoraDesempleo.anios("Vjbk"));
    }

    @Test
    @DisplayName("Normaliza las etiquetas y calcula frecuencias con su n")
    void frecuenciasYTotales() {
        CalculadoraDesempleo.AnalisisDesempleo a =
                CalculadoraDesempleo.analisis(datos(), List.of());

        assertEquals(5, a.total());
        assertEquals(3, a.conTresVariables());

        assertEquals(2, a.situacion().get(0).total());
        assertEquals(2, a.situacion().get(2).total());

        assertEquals("Hasta 0.5 años", a.tiempoDesempleo().get(1).etiqueta());
        assertEquals(3, a.tiempoDesempleo().get(1).total());

        assertEquals("Demanda laboral inexistente", a.razon().get(0).etiqueta());
        assertEquals(2, a.razon().get(0).total());
        assertEquals(2, a.experiencia().get(0).total());
        assertEquals(1, a.experiencia().get(1).total());

        assertEquals("0 años", a.primerEmpleo().get(0).etiqueta());
        assertEquals(2, a.primerEmpleo().get(0).total());
    }

    @Test
    @DisplayName("Los cruces y bloques usan solo casos completos")
    void crucesYBloques() {
        CalculadoraDesempleo.AnalisisDesempleo a =
                CalculadoraDesempleo.analisis(datos(), List.of());

        assertEquals(5, a.situacionTiempo().total());
        assertEquals(3, a.razonExperiencia().total());
        assertEquals(3, a.razonPrimerEmpleo().total());
        assertEquals(3, a.razonVidaProfesional().total());
        assertEquals(2, a.bloques().size());
        assertEquals(2, a.bloques().get(0).total());
    }

    @Test
    @DisplayName("El resumen es breve y usa la razón y la trayectoria")
    void resumenBreve() {
        CalculadoraDesempleo.AnalisisDesempleo a =
                CalculadoraDesempleo.analisis(datos(), List.of());

        assertEquals(3, a.resumen().size());
        assertTrue(a.resumen().get(0).contains("Trabajando en una organización"));
        assertTrue(a.resumen().get(1).contains("Demanda laboral inexistente"));
        assertTrue(a.resumen().get(2).contains("primer empleo"));
    }
}
