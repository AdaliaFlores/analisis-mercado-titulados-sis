package com.umss.util;

import com.umss.model.RegistroEmprendimiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pruebas unitarias del análisis exploratorio del reporte de Emprendimiento.
 * Usan datos sintéticos, por lo que no dependen de la base de datos.
 */
class CalculadoraEmprendimientoTest {

    @Test
    @DisplayName("El análisis exploratorio arma frecuencias, cruces y bloques de financiamiento")
    void analisisExploratorio() {
        List<RegistroEmprendimiento> datos = List.of(
                new RegistroEmprendimiento("Idea propia.", "Producto", "No, financiamiento propio."),
                new RegistroEmprendimiento("IDEA PROPIA", "Servicio", "No, financiamiento propio."),
                new RegistroEmprendimiento("Idea conjunta", "Producto", "Sí, con inversores."),
                new RegistroEmprendimiento("Idea conjunta.", "Ambos", "Sí, con préstamos bancarios."),
                new RegistroEmprendimiento("StartUp", "Producto", "Sí, con inversores."),
                new RegistroEmprendimiento("Otro", "Producto y servicio", "No, financiamiento propio."),
                new RegistroEmprendimiento("Idea propia", null, "No, financiamiento propio."));

        CalculadoraEmprendimiento.AnalisisExploratorio a =
                CalculadoraEmprendimiento.analisisExploratorio(datos);

        assertEquals(7, a.total());
        assertEquals(6, a.completos());
        assertEquals(1, a.incompletos());

        assertEquals(2, a.origen().stream()
                .filter(f -> f.etiqueta().equals("Idea propia")).findFirst().orElseThrow().total());
        assertEquals(3, a.entregable().stream()
                .filter(f -> f.etiqueta().equals("Producto")).findFirst().orElseThrow().total());
        assertEquals(2, a.entregable().stream()
                .filter(f -> f.etiqueta().equals("Ambos")).findFirst().orElseThrow().total());
        assertEquals(3, a.financiamiento().stream()
                .filter(f -> f.etiqueta().equals("No, financiamiento propio")).findFirst().orElseThrow().total());

        CalculadoraEmprendimiento.TablaCruce oe = a.origenEntregable();
        assertEquals(List.of("Idea propia", "Idea conjunta", "Spin Off", "StartUp", "Otro"), oe.filas());
        assertEquals(List.of("Producto", "Servicio", "Ambos"), oe.columnas());
        assertEquals(6, oe.total());
        assertEquals(2, oe.totalesFila().get(0));
        assertEquals(3, oe.totalesColumna().get(0));

        assertEquals(3, a.bloques().size());
        assertEquals(3, a.bloques().get(0).total());
        assertEquals(2, a.bloques().get(1).total());
        assertEquals(1, a.bloques().get(2).total());
        assertEquals(6, a.bloques().stream()
                .mapToInt(CalculadoraEmprendimiento.BloqueFinanciamiento::total).sum());

        assertEquals(List.of(
                "Origen más frecuente: Idea propia y Idea conjunta (2 cada uno; 33.3%).",
                "Tipo de entregable más frecuente: Producto (3 de 6; 50.0%).",
                "Financiamiento: propio 3 (50.0%); externo 3 (50.0%)."),
                a.resumen());
    }
}
