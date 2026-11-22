package com.umss.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del DTO de áreas de interés de posgrado (HU-5).
 * No dependen de la base de datos.
 */
class AreaInteresPosgradoTest {

    @Test
    @DisplayName("HU-5: el constructor conserva area, frecuencia y porcentaje")
    void constructorConservaValores() {
        AreaInteresPosgrado area = new AreaInteresPosgrado("Inteligencia Artificial", 42, 38.5);

        assertEquals("Inteligencia Artificial", area.getArea());
        assertEquals(42, area.getN());
        assertEquals(38.5, area.getPorcentaje(), 1e-9);
    }

    @Test
    @DisplayName("HU-5: el constructor por defecto deja valores neutros")
    void constructorPorDefecto() {
        AreaInteresPosgrado area = new AreaInteresPosgrado();

        assertNull(area.getArea());
        assertEquals(0, area.getN());
        assertEquals(0.0, area.getPorcentaje(), 1e-9);
    }

    @Test
    @DisplayName("HU-5: los setters actualizan todos los campos")
    void settersActualizanCampos() {
        AreaInteresPosgrado area = new AreaInteresPosgrado();
        area.setArea("Gestión de Proyectos");
        area.setN(7);
        area.setPorcentaje(12.3);

        assertEquals("Gestión de Proyectos", area.getArea());
        assertEquals(7, area.getN());
        assertEquals(12.3, area.getPorcentaje(), 1e-9);
    }
}
