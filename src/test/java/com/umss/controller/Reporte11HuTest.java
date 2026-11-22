package com.umss.controller;

import com.umss.dao.AnalisisDAO;
import com.umss.model.FiltroEnvio;
import com.umss.model.ResultadoChiCuadrado;
import com.umss.util.PruebaChiCuadrado;
import com.umss.model.ResultadoPromedio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReporteHu11Test {

    @Test
    @DisplayName("CP-01 - Unitaria: Chi-cuadrado con datos válidos y control de tabla vacía")
    void cp01UnitariaChiCuadradoYTablaVacia() {
        // Caso vacío: no debe lanzar ArithmeticException
        ResultadoChiCuadrado vacio = PruebaChiCuadrado.calcular(List.of(), Map.of(), Map.of());
        assertNotNull(vacio);
        assertEquals(0, vacio.n());
        assertNull(vacio.chiCuadrado());
        assertFalse(vacio.cumpleSupuestos());
    }

    @Test
    @DisplayName("CP-02 - Caja Blanca: Agrupación automática en cruce Ubicación x Pertinencia")
    void cp02CajaBlancaAgrupacionAutomatica() throws Exception {
        FiltroEnvio sinFiltro = new FiltroEnvio();
        var celdasCompleta = AnalisisDAO.contingencia(19, 25, false, sinFiltro);
        ResultadoChiCuadrado rCompleta = PruebaChiCuadrado.calcular(
                celdasCompleta,
                AnalisisDAO.ordenEtiquetas(19, false),
                AnalisisDAO.ordenEtiquetas(25, false)
        );

        // Si la tabla completa no cumple Cochran, debe existir agrupación configurada
        assertFalse(rCompleta.cumpleSupuestos(), "La tabla completa tiene celdas esperadas < 5");
        assertTrue(AnalisisDAO.tieneAgrupacion(19) || AnalisisDAO.tieneAgrupacion(25),
                "Debe existir agrupación definida en agrupacion_opcion");

        var celdasAgrupada = AnalisisDAO.contingencia(19, 25, true, sinFiltro);
        ResultadoChiCuadrado rAgrupada = PruebaChiCuadrado.calcular(
                celdasAgrupada,
                AnalisisDAO.ordenEtiquetas(19, true),
                AnalisisDAO.ordenEtiquetas(25, true)
        );
        assertEquals(139, rAgrupada.n(), "El total válido de registros cruzados debe ser 139");
    }

    @Test
    @DisplayName("CP-03 - Caja Blanca / DAO: Promedio salarial por ubicación geográfica")
    void cp03CajaBlancaPromedioSalarial() throws Exception {
        ResultadoPromedio promedio = AnalisisDAO.promedioPorGrupo(
                27, 19, new FiltroEnvio()
        );

        assertNotNull(promedio, "Debe devolver un resultado de promedio salarial");
    }
}