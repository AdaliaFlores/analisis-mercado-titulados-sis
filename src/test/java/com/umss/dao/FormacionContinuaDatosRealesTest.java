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
import com.umss.model.AreaInteresPosgrado;
import com.umss.model.ConteoRespuesta;

/**
 * Pruebas de integración de los datos de formación continua (HU-5).
 * Si la BD no está disponible, las pruebas se omiten (no fallan).
 */
class FormacionContinuaDatosRealesTest {

    @Test
    @DisplayName("HU-5: las áreas de interés tienen frecuencias y porcentajes válidos")
    void areasInteresTienenDatosValidos() throws Exception {
        asumirBdDisponible();

        List<AreaInteresPosgrado> areas = EncuestaDAO.areasInteresPosgrado();

        assertNotNull(areas, "Las áreas de interés no deben ser nulas");
        double anterior = Double.MAX_VALUE;
        int sumaN = 0;
        for (AreaInteresPosgrado area : areas) {
            assertNotNull(area.getArea(), "El nombre del área no debe ser nulo");
            assertFalse(area.getArea().isBlank(), "El nombre del área no debe estar vacío");
            assertTrue(area.getN() > 0, "La frecuencia del área debe ser positiva");
            assertTrue(area.getPorcentaje() >= 0.0 && area.getPorcentaje() <= 100.0,
                    "El porcentaje debe estar entre 0 y 100");
            assertTrue(area.getPorcentaje() <= anterior,
                    "Las áreas deben estar ordenadas de mayor a menor frecuencia");
            anterior = area.getPorcentaje();
            sumaN += area.getN();
        }

        if (!areas.isEmpty()) {
            assertTrue(sumaN > 0, "Debe existir al menos una respuesta contabilizada");
            boolean esperada = areas.size() >= 2
                    && areas.get(0).getPorcentaje() + areas.get(1).getPorcentaje() >= 70.0;
            assertEquals(esperada, EncuestaDAO.alertaConcentracionCritica(areas),
                    "La alerta debe coincidir con la suma de las dos áreas principales");
        }
    }

    @Test
    @DisplayName("HU-5: los niveles de formación deseados están ordenados por frecuencia")
    void nivelesFormacionOrdenados() throws Exception {
        asumirBdDisponible();

        List<ConteoRespuesta> niveles = EncuestaDAO.nivelesFormacionDeseados();

        assertNotNull(niveles, "Los niveles de formación no deben ser nulos");
        for (int i = 1; i < niveles.size(); i++) {
            assertTrue(niveles.get(i - 1).getTotal() >= niveles.get(i).getTotal(),
                    "Los niveles deben estar ordenados de mayor a menor frecuencia");
        }
        for (ConteoRespuesta nivel : niveles) {
            assertTrue(nivel.getTotal() >= 0, "La frecuencia no puede ser negativa");
            assertTrue(nivel.getPorcentaje() >= 0.0 && nivel.getPorcentaje() <= 100.0,
                    "El porcentaje debe estar entre 0 y 100");
        }
    }

    @Test
    @DisplayName("HU-5: el cruce área × nivel expone área, nivel, total y porcentaje")
    void cruceAreaNivelTieneEstructura() throws Exception {
        asumirBdDisponible();

        List<?> filas = EncuestaDAO.cruceAreaNivel();

        assertNotNull(filas, "El cruce área × nivel no debe ser nulo");
        for (Object obj : filas) {
            Map<?, ?> fila = (Map<?, ?>) obj;
            assertNotNull(fila.get("area"), "Falta el área en el cruce");
            assertNotNull(fila.get("nivel"), "Falta el nivel en el cruce");
            assertTrue(((Number) fila.get("total")).intValue() > 0,
                    "El total del cruce debe ser positivo");
            double porcentaje = ((Number) fila.get("porcentaje")).doubleValue();
            assertTrue(porcentaje >= 0.0 && porcentaje <= 100.0,
                    "El porcentaje del cruce debe estar entre 0 y 100");
        }
    }

    @Test
    @DisplayName("HU-5: la preferencia institucional expone nivel, organización, total y porcentaje")
    void cruceNivelOrganizacionTieneEstructura() throws Exception {
        asumirBdDisponible();

        List<?> filas = EncuestaDAO.cruceNivelOrganizacion(10);

        assertNotNull(filas, "El cruce nivel × organización no debe ser nulo");
        for (Object obj : filas) {
            Map<?, ?> fila = (Map<?, ?>) obj;
            assertNotNull(fila.get("nivel"), "Falta el nivel en el cruce");
            assertNotNull(fila.get("organizacion"), "Falta la organización en el cruce");
            assertTrue(((Number) fila.get("total")).intValue() > 0,
                    "El total del cruce debe ser positivo");
            double porcentaje = ((Number) fila.get("porcentaje")).doubleValue();
            assertTrue(porcentaje >= 0.0 && porcentaje <= 100.0,
                    "El porcentaje del cruce debe estar entre 0 y 100");
        }
    }

    private static void asumirBdDisponible() {
        try (Connection conn = Database.getConnection()) {
            // La conexión se cierra automáticamente.
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "BD no disponible: " + e.getMessage());
        }
    }
}
