package com.umss.util;

import com.umss.model.RegistroEmprendimiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de validación del reporte de Ecosistema de Emprendimiento e Innovación.
 *
 * Los 17 registros corresponden a la hoja "Emprendedores_Limpio" del archivo
 * de respuestas usado para validar el reporte. Las pruebas no dependen de la
 * base de datos ni modifican información.
 */
class ValidacionEmprendimientoTest {

    private static RegistroEmprendimiento r(String origen, String entregable, String financiamiento) {
        return new RegistroEmprendimiento(origen, entregable, financiamiento);
    }

    /** Copia fiel de la hoja Emprendedores_Limpio (17 filas). */
    private static List<RegistroEmprendimiento> datosReales() {
        return List.of(
                r("Otro.", "Ambos", "No, financiamiento propio."),
                r("Idea conjunta con compañeros.", "Ambos", "Si, mediante préstamos bancarios."),
                r("Idea propia.", "Producto", "No, financiamiento propio."),
                r("Idea conjunta con compañeros.", "Producto", "Si, mediante inversores."),
                r("Idea conjunta con compañeros.", "Producto", "No, financiamiento propio."),
                r("Idea propia.", "Ambos", "No, financiamiento propio."),
                r("Idea propia.", "Producto", "No, financiamiento propio."),
                r("Idea conjunta con compañeros.", "Servicio", "Si, mediante préstamos bancarios."),
                r("Idea propia.", "Ambos", "No, financiamiento propio."),
                r("Idea propia.", "Servicio", "No, financiamiento propio."),
                r("Otro.", "Servicio", "No, financiamiento propio."),
                r("StartUp", "Ambos", "Si, mediante inversores."),
                r("Idea conjunta con compañeros.", "Ambos", "No, financiamiento propio."),
                r("Idea propia.", "Ambos", "No, financiamiento propio."),
                r("Idea conjunta con compañeros.", "Producto", "Si, mediante inversores."),
                r("Idea conjunta con compañeros.", "Producto", "No, financiamiento propio."),
                r("Idea conjunta con compañeros.", "Producto", "No, financiamiento propio."));
    }

    private static int total(CalculadoraEmprendimiento.Frecuencia... filas) {
        int total = 0;
        for (CalculadoraEmprendimiento.Frecuencia f : filas) {
            total += f.total();
        }
        return total;
    }

    private static CalculadoraEmprendimiento.Frecuencia frecuencia(
            List<CalculadoraEmprendimiento.Frecuencia> lista, String etiqueta) {
        return lista.stream()
                .filter(f -> etiqueta.equals(f.etiqueta()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró la categoría: " + etiqueta));
    }

    private static int celda(CalculadoraEmprendimiento.TablaCruce tabla, String fila, String columna) {
        int i = tabla.filas().indexOf(fila);
        int j = tabla.columnas().indexOf(columna);
        assertTrue(i >= 0, "Fila no encontrada: " + fila);
        assertTrue(j >= 0, "Columna no encontrada: " + columna);
        return tabla.celdas().get(i).get(j);
    }

    @Test
    @DisplayName("VALID-01: integridad del conjunto de datos")
    void integridadDelConjunto() {
        List<RegistroEmprendimiento> datos = datosReales();
        CalculadoraEmprendimiento.AnalisisExploratorio a =
                CalculadoraEmprendimiento.analisisExploratorio(datos);

        assertEquals(17, datos.size());
        assertEquals(17, a.total());
        assertEquals(17, a.completos());
        assertEquals(0, a.incompletos());
        assertTrue(datos.stream().allMatch(RegistroEmprendimiento::completo));
    }

    @Test
    @DisplayName("VALID-02: frecuencias y porcentajes de origen")
    void frecuenciasOrigen() {
        CalculadoraEmprendimiento.AnalisisExploratorio a =
                CalculadoraEmprendimiento.analisisExploratorio(datosReales());

        assertEquals(6, frecuencia(a.origen(), "Idea propia").total());
        assertEquals(8, frecuencia(a.origen(), "Idea conjunta").total());
        assertEquals(1, frecuencia(a.origen(), "StartUp").total());
        assertEquals(2, frecuencia(a.origen(), "Otro").total());

        assertEquals(35.3, frecuencia(a.origen(), "Idea propia").porcentaje());
        assertEquals(47.1, frecuencia(a.origen(), "Idea conjunta").porcentaje());
        assertEquals(5.9, frecuencia(a.origen(), "StartUp").porcentaje());
        assertEquals(11.8, frecuencia(a.origen(), "Otro").porcentaje());

        assertEquals(17, total(a.origen().toArray(CalculadoraEmprendimiento.Frecuencia[]::new)));
    }

    @Test
    @DisplayName("VALID-03: frecuencias y porcentajes de entregable y financiamiento")
    void frecuenciasEntregableYFinanciamiento() {
        CalculadoraEmprendimiento.AnalisisExploratorio a =
                CalculadoraEmprendimiento.analisisExploratorio(datosReales());

        assertEquals(7, frecuencia(a.entregable(), "Producto").total());
        assertEquals(3, frecuencia(a.entregable(), "Servicio").total());
        assertEquals(7, frecuencia(a.entregable(), "Ambos").total());
        assertEquals(41.2, frecuencia(a.entregable(), "Producto").porcentaje());
        assertEquals(17.6, frecuencia(a.entregable(), "Servicio").porcentaje());
        assertEquals(41.2, frecuencia(a.entregable(), "Ambos").porcentaje());

        assertEquals(12, frecuencia(a.financiamiento(), "No, financiamiento propio").total());
        assertEquals(3, frecuencia(a.financiamiento(), "Sí, con inversores").total());
        assertEquals(2, frecuencia(a.financiamiento(), "Sí, con préstamos bancarios").total());
        assertEquals(70.6, frecuencia(a.financiamiento(), "No, financiamiento propio").porcentaje());
        assertEquals(17.6, frecuencia(a.financiamiento(), "Sí, con inversores").porcentaje());
        assertEquals(11.8, frecuencia(a.financiamiento(), "Sí, con préstamos bancarios").porcentaje());
    }

    @Test
    @DisplayName("VALID-04: la matriz origen × entregable conserva los totales")
    void cruceOrigenEntregable() {
        CalculadoraEmprendimiento.AnalisisExploratorio a =
                CalculadoraEmprendimiento.analisisExploratorio(datosReales());
        CalculadoraEmprendimiento.TablaCruce t = a.origenEntregable();

        assertEquals(17, t.total());
        assertEquals(17, t.totalesFila().stream().mapToInt(Integer::intValue).sum());
        assertEquals(17, t.totalesColumna().stream().mapToInt(Integer::intValue).sum());

        assertEquals(2, celda(t, "Idea propia", "Producto"));
        assertEquals(1, celda(t, "Idea propia", "Servicio"));
        assertEquals(3, celda(t, "Idea propia", "Ambos"));
        assertEquals(5, celda(t, "Idea conjunta", "Producto"));
        assertEquals(1, celda(t, "Idea conjunta", "Servicio"));
        assertEquals(2, celda(t, "Idea conjunta", "Ambos"));
        assertEquals(1, celda(t, "StartUp", "Ambos"));
        assertEquals(1, celda(t, "Otro", "Servicio"));
        assertEquals(1, celda(t, "Otro", "Ambos"));
    }

    @Test
    @DisplayName("VALID-05: la matriz entregable × financiamiento conserva los totales")
    void cruceEntregableFinanciamiento() {
        CalculadoraEmprendimiento.AnalisisExploratorio a =
                CalculadoraEmprendimiento.analisisExploratorio(datosReales());
        CalculadoraEmprendimiento.TablaCruce t = a.entregableFinanciamiento();

        assertEquals(17, t.total());
        assertEquals(7, celda(t, "Producto", "No, financiamiento propio")
                + celda(t, "Producto", "Sí, con inversores")
                + celda(t, "Producto", "Sí, con préstamos bancarios"));
        assertEquals(3, celda(t, "Servicio", "No, financiamiento propio")
                + celda(t, "Servicio", "Sí, con inversores")
                + celda(t, "Servicio", "Sí, con préstamos bancarios"));
        assertEquals(7, celda(t, "Ambos", "No, financiamiento propio")
                + celda(t, "Ambos", "Sí, con inversores")
                + celda(t, "Ambos", "Sí, con préstamos bancarios"));
    }

    @Test
    @DisplayName("VALID-06: los bloques de tres variables suman el total y respetan el filtro")
    void bloquesTresVariables() {
        CalculadoraEmprendimiento.AnalisisExploratorio a =
                CalculadoraEmprendimiento.analisisExploratorio(datosReales());

        assertEquals(3, a.bloques().size());
        assertEquals(17, a.bloques().stream()
                .mapToInt(CalculadoraEmprendimiento.BloqueFinanciamiento::total).sum());

        CalculadoraEmprendimiento.BloqueFinanciamiento propio = a.bloques().stream()
                .filter(b -> b.financiamiento().equals("No, financiamiento propio"))
                .findFirst().orElseThrow();
        assertEquals(12, propio.total());
        assertEquals(12, propio.tabla().total());
        assertEquals(6, celda(propio.tabla(), "Idea propia", "Producto")
                + celda(propio.tabla(), "Idea propia", "Servicio")
                + celda(propio.tabla(), "Idea propia", "Ambos"));

        CalculadoraEmprendimiento.BloqueFinanciamiento inversores = a.bloques().stream()
                .filter(b -> b.financiamiento().equals("Sí, con inversores"))
                .findFirst().orElseThrow();
        assertEquals(3, inversores.total());
        assertEquals(1, celda(inversores.tabla(), "StartUp", "Ambos"));

        CalculadoraEmprendimiento.BloqueFinanciamiento bancos = a.bloques().stream()
                .filter(b -> b.financiamiento().equals("Sí, con préstamos bancarios"))
                .findFirst().orElseThrow();
        assertEquals(2, bancos.total());
        assertEquals(1, celda(bancos.tabla(), "Idea conjunta", "Servicio"));
        assertEquals(1, celda(bancos.tabla(), "Idea conjunta", "Ambos"));
    }

    @Test
    @DisplayName("VALID-07: el resumen reproduce los hallazgos del reporte")
    void resumenConCifrasExactas() {
        CalculadoraEmprendimiento.AnalisisExploratorio a =
                CalculadoraEmprendimiento.analisisExploratorio(datosReales());

        assertEquals(List.of(
                "Origen más frecuente: Idea conjunta (8 de 17; 47.1%).",
                "Tipo de entregable más frecuente: Producto y Ambos (7 cada uno; 41.2%).",
                "Financiamiento: propio 12 (70.6%); externo 5 (29.4%)."),
                a.resumen());
    }

    @Test
    @DisplayName("VALID-08: normalización de mayúsculas, tildes, puntos y sinónimos")
    void normalizacionDeRespuestas() {
        List<RegistroEmprendimiento> datos = List.of(
                r("IDEA PROPIA.", "producto y servicio", "Si, mediante inversores."),
                r("Idea conjunta con compañeros.", "SERVICIO", "No, financiamiento propio."),
                r("startup", "Ambos", "Si, mediante prestamos bancarios."),
                r("Otro.", "Producto", "No, propio."));

        CalculadoraEmprendimiento.AnalisisExploratorio a =
                CalculadoraEmprendimiento.analisisExploratorio(datos);

        assertEquals(4, a.completos());
        assertEquals(1, frecuencia(a.origen(), "Idea propia").total());
        assertEquals(1, frecuencia(a.origen(), "Idea conjunta").total());
        assertEquals(1, frecuencia(a.origen(), "StartUp").total());
        assertEquals(1, frecuencia(a.origen(), "Otro").total());
        assertEquals(2, frecuencia(a.entregable(), "Ambos").total());
        assertEquals(1, frecuencia(a.entregable(), "Servicio").total());
        assertEquals(2, frecuencia(a.financiamiento(), "No, financiamiento propio").total());
        assertEquals(1, frecuencia(a.financiamiento(), "Sí, con inversores").total());
        assertEquals(1, frecuencia(a.financiamiento(), "Sí, con préstamos bancarios").total());
    }

    @Test
    @DisplayName("VALID-09: casos incompletos y datos vacíos no rompen el reporte")
    void casosIncompletosYVacios() {
        CalculadoraEmprendimiento.AnalisisExploratorio vacio =
                CalculadoraEmprendimiento.analisisExploratorio(List.of());
        assertEquals(0, vacio.total());
        assertEquals(0, vacio.completos());
        assertEquals(0, vacio.incompletos());
        assertTrue(vacio.resumen().isEmpty());

        List<RegistroEmprendimiento> conIncompleto = new ArrayList<>(datosReales());
        conIncompleto.add(r("Idea propia.", null, "No, financiamiento propio."));
        CalculadoraEmprendimiento.AnalisisExploratorio a =
                CalculadoraEmprendimiento.analisisExploratorio(conIncompleto);

        assertEquals(18, a.total());
        assertEquals(17, a.completos());
        assertEquals(1, a.incompletos());
        assertEquals(17, a.origenEntregable().total());
        assertEquals(17, a.entregableFinanciamiento().total());
    }

    @Test
    @DisplayName("VALID-10: el cálculo es reproducible y no muta la entrada")
    void reproducibilidadSinMutacion() {
        List<RegistroEmprendimiento> datos = new ArrayList<>(datosReales());
        List<RegistroEmprendimiento> copia = new ArrayList<>(datos);

        CalculadoraEmprendimiento.AnalisisExploratorio primera =
                CalculadoraEmprendimiento.analisisExploratorio(datos);
        CalculadoraEmprendimiento.AnalisisExploratorio segunda =
                CalculadoraEmprendimiento.analisisExploratorio(datos);

        assertEquals(copia, datos);
        assertEquals(primera.total(), segunda.total());
        assertEquals(primera.completos(), segunda.completos());
        assertEquals(primera.resumen(), segunda.resumen());
        assertEquals(primera.origen(), segunda.origen());
        assertEquals(primera.origenEntregable().celdas(), segunda.origenEntregable().celdas());
        assertFalse(primera.bloques().isEmpty());
    }
}
