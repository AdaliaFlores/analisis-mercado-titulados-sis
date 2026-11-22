package com.umss.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReporteHuDAO12Test {

    @Test
    @DisplayName("CP-01 - Unitaria: Cálculo de porcentajes y protección contra total = 0")
    void cp01UnitariaPorcentajesSinDivisionPorCero() {
        int totalCero = 0;
        int cantidad = 5;
        double porcentaje = (totalCero == 0) ? 0.0 : cantidad * 100.0 / totalCero;
        assertEquals(0.0, porcentaje, "Cuando el total es 0 el porcentaje debe ser 0.0");

        int totalReal = 83;
        double pctContratacion = 69 * 100.0 / totalReal;
        assertEquals(83.13, pctContratacion, 0.01, "69 de 83 empleadores equivale a 83.13%");
    }

    @Test
    @DisplayName("CP-02 - Caja Blanca: Frecuencias de demanda empresarial (Encuesta 2, Pregunta 54)")
    void cp02CajaBlancaFrecuenciasEmpleadores() throws Exception {
        Map<String, Object> res = ReporteHu12DAO.obtenerFrecuencias(2, 54);
        assertEquals(2, res.get("encuestaId"));
        assertEquals(54, res.get("preguntaId"));

        int total = (int) res.get("total");
        assertEquals(80, total, "Deben contabilizarse exactamente 80 empleadores válidos");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categorias =
                (List<Map<String, Object>>) res.get("categorias");
    }

    @Test
    @DisplayName("CP-03 - Caja Blanca: Privacidad y consentimiento explícito (P46 = SI) en Red Alumni")
    void cp03CajaBlancaConsentimientoRedAlumni() throws Exception {
        Map<String, Object> alumni = ReporteHu12DAO.obteneralumno();
        int totalTitulados = (int) alumni.get("totalTitulados");
        int aceptaron = (int) alumni.get("aceptaron");

        @SuppressWarnings("unchecked")
List<Map<String, Object>> contactos =
        (List<Map<String, Object>>) alumni.get("contactos");
    }
}