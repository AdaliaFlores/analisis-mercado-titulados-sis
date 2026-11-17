package com.umss.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.umss.model.AreaInteresPosgrado;

/**
 * Pruebas unitarias de la alerta de concentración crítica (HU-5).
 * Umbral: la suma de las dos áreas principales debe ser >= 70%.
 */
class ConcentracionCriticaTest {

    @Test
    @DisplayName("HU-5: una lista nula no genera alerta")
    void listaNulaNoGeneraAlerta() {
        assertFalse(EncuestaDAO.alertaConcentracionCritica(null));
    }

    @Test
    @DisplayName("HU-5: con menos de dos areas no se puede calcular la alerta")
    void menosDeDosAreasNoGeneraAlerta() {
        assertFalse(EncuestaDAO.alertaConcentracionCritica(new ArrayList<>()));
        assertFalse(EncuestaDAO.alertaConcentracionCritica(List.of(area("Única", 100.0))));
    }

    @Test
    @DisplayName("HU-5: exactamente 70.0% activa la alerta")
    void umbralExactoActivaAlerta() {
        assertTrue(EncuestaDAO.alertaConcentracionCritica(
                List.of(area("A", 40.0), area("B", 30.0))));
    }

    @Test
    @DisplayName("HU-5: 69.9% no activa la alerta")
    void justoDebajoDelUmbralNoActivaAlerta() {
        assertFalse(EncuestaDAO.alertaConcentracionCritica(
                List.of(area("A", 40.0), area("B", 29.9))));
    }

    @Test
    @DisplayName("HU-5: 70.1% activa la alerta")
    void justoEncimaDelUmbralActivaAlerta() {
        assertTrue(EncuestaDAO.alertaConcentracionCritica(
                List.of(area("A", 40.0), area("B", 30.1))));
    }

    @Test
    @DisplayName("HU-5: solo cuentan las dos primeras áreas de la lista")
    void soloCuentanLasDosPrimeras() {
        assertFalse(EncuestaDAO.alertaConcentracionCritica(
                List.of(area("A", 30.0), area("B", 30.0), area("C", 40.0))));
    }

    private static AreaInteresPosgrado area(String nombre, double porcentaje) {
        return new AreaInteresPosgrado(nombre, 1, porcentaje);
    }
}
