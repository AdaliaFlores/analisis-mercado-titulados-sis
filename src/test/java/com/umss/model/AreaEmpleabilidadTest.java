package com.umss.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de la formula de empleabilidad ponderada.
 * No dependen de la base de datos.
 */
class AreaEmpleabilidadTest {

    @Test
    @DisplayName("CA-05: calcula el promedio de demanda e interes con valores normales")
    void calculaPromedioConValoresNormales() {
        AreaEmpleabilidad area = new AreaEmpleabilidad("IA", 65.8, 45.2);

        assertEquals(55.5, area.getEmpleabilidad(), 1e-9,
                "(65.8 + 45.2) / 2 debe ser 55.5");
        assertEquals(65.8, area.getDemandaEmpleador(), 1e-9);
        assertEquals(45.2, area.getInteresTitulados(), 1e-9);
    }

    @Test
    @DisplayName("CA-05: redondea la empleabilidad a un decimal")
    void redondeaAUnDecimal() {
        AreaEmpleabilidad area = new AreaEmpleabilidad("Software", 30.4, 23.9);

        assertEquals(27.2, area.getEmpleabilidad(), 1e-9,
                "(30.4 + 23.9) / 2 = 27.15 debe redondearse a 27.2");
    }

    @Test
    @DisplayName("CA-05: con demanda e interes en cero la empleabilidad es cero")
    void valoresLimiteEnCero() {
        AreaEmpleabilidad area = new AreaEmpleabilidad("Redes", 0.0, 0.0);

        assertEquals(0.0, area.getEmpleabilidad(), 1e-9);
    }
}
