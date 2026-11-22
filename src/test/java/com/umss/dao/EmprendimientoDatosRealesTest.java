package com.umss.dao;

import com.umss.model.RegistroEmprendimiento;
import com.umss.util.CalculadoraEmprendimiento;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Prueba de coherencia del reporte con los datos reales de la base de datos.
 * Si la BD no está disponible, la prueba se omite (no falla).
 */
class EmprendimientoDatosRealesTest {

    @Test
    @DisplayName("Los datos reales de p30–p32 son coherentes con el reporte")
    void datosRealesSonCoherentes() {
        List<RegistroEmprendimiento> registros;
        try {
            registros = EncuestaDAO.obtenerRegistrosEmprendimiento();
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "BD no disponible: " + e.getMessage());
            return;
        }

        assertFalse(registros.isEmpty(), "No hay respuestas de emprendimiento en la BD");

        CalculadoraEmprendimiento.AnalisisExploratorio a =
                CalculadoraEmprendimiento.analisisExploratorio(registros);

        assertEquals(registros.size(), a.total());
        assertEquals(a.total(), a.completos() + a.incompletos());

        assertEquals(a.completos(), a.origen().stream()
                .mapToInt(CalculadoraEmprendimiento.Frecuencia::total).sum());
        assertEquals(a.completos(), a.entregable().stream()
                .mapToInt(CalculadoraEmprendimiento.Frecuencia::total).sum());
        assertEquals(a.completos(), a.financiamiento().stream()
                .mapToInt(CalculadoraEmprendimiento.Frecuencia::total).sum());

        assertEquals(a.completos(), a.origenEntregable().total());
        assertEquals(a.completos(), a.origenFinanciamiento().total());
        assertEquals(a.completos(), a.entregableFinanciamiento().total());

        assertEquals(a.completos(), a.bloques().stream()
                .mapToInt(CalculadoraEmprendimiento.BloqueFinanciamiento::total).sum());

        if (a.completos() > 0) {
            assertFalse(a.resumen().isEmpty(), "El reporte debe incluir el resumen");
        }
    }
}
