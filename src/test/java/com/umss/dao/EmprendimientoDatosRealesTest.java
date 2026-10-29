package com.umss.dao;

import com.umss.model.ConteoRespuesta;
import com.umss.util.CalculadoraEmprendimiento;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de coherencia sobre los datos reales de la base de datos.
 * Si la BD no está disponible, la prueba se omite (no falla).
 */
class EmprendimientoDatosRealesTest {

    @Test
    @DisplayName("Los datos reales de p30–p34 son coherentes con la calculadora")
    void datosRealesSonCoherentes() {
        List<ConteoRespuesta> filas;
        try {
            filas = EncuestaDAO.obtenerConteoEmprendimiento();
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "BD no disponible: " + e.getMessage());
            return;
        }

        assertFalse(filas.isEmpty(), "No hay respuestas de emprendimiento en la BD");

        Map<Integer, Double> porcentajes = new HashMap<>();
        Map<Integer, Integer> totales = new HashMap<>();
        for (ConteoRespuesta c : filas) {
            porcentajes.merge(c.getPreguntaId(), c.getPorcentaje(), Double::sum);
            totales.merge(c.getPreguntaId(), c.getTotal(), Integer::sum);
        }
        for (int pid = 30; pid <= 34; pid++) {
            assertEquals(100.0, porcentajes.getOrDefault(pid, 0.0), 0.5,
                    "Los porcentajes de p" + pid + " deberían sumar 100%");
        }

        CalculadoraEmprendimiento.Metricas m = CalculadoraEmprendimiento.metricas(filas);

        int t30 = totales.get(30);
        int t31 = totales.get(31);
        int t32 = totales.get(32);
        int t33 = totales.get(33);
        int t34 = totales.get(34);

        assertEquals(t30, m.universo(), "Universo = total de p30");
        assertEquals(t31, m.totalEntregable(), "Total entregable = total de p31");
        assertEquals(t32, m.totalFinanciamiento(), "Total financiamiento = total de p32");
        assertEquals(t32, (int) (m.financiamientoExterno() + m.financiamientoPropio()),
                "Externo + propio deben cubrir todo p32");
        assertEquals(t33, m.nSatisfaccion() + m.sinCodificarSatisfaccion(),
                "Codificadas + sin codificar deben cubrir todo p33");
        assertEquals(t34, m.nImportancia() + m.sinCodificarImportancia(),
                "Codificadas + sin codificar deben cubrir todo p34");

        // Calidad de datos: todas las etiquetas actuales de p33 y p34 deben tener valor 1–5.
        assertEquals(0, m.sinCodificarSatisfaccion(),
                "p33 contiene etiquetas sin mapear; agregarlas a MAPA_SATISFACCION");
        assertEquals(0, m.sinCodificarImportancia(),
                "p34 contiene etiquetas sin mapear; agregarlas a MAPA_IMPORTANCIA");

        List<String> bullets = CalculadoraEmprendimiento.bullets(m);
        assertTrue(bullets.size() >= 6, "El resumen debe incluir universo, origen, entregable, "
                + "financiamiento, satisfacción e importancia");
        assertFalse(CalculadoraEmprendimiento.recomendaciones(m).isEmpty());
    }
}
