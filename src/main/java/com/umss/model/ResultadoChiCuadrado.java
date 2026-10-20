package com.umss.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado completo de la prueba de independencia, con los pasos intermedios
 * para poder repetir el cálculo a mano: observadas, esperadas, contribuciones, totales, gl, χ².
 * chiCuadrado, valorCritico, pValor y rechazaIndependencia son nulos si la prueba no es calculable.
 */
public record ResultadoChiCuadrado(
        String modoAplicado,
        List<String> etiquetasFila,
        List<String> etiquetasColumna,
        long[][] observada,
        double[][] esperada,
        double[][] contribucion,
        long[] totalesFila,
        long[] totalesColumna,
        long n,
        int gradosLibertad,
        Double chiCuadrado,
        double alfa,
        Double valorCritico,
        Double pValor,
        Boolean rechazaIndependencia,
        boolean cumpleSupuestos,
        double porcentajeEsperadasMenorA5,
        List<String> advertencias) {

    /** Copia del resultado indicando qué modo se aplicó y, opcionalmente, un aviso adicional. */
    public ResultadoChiCuadrado conModo(String modo, String aviso) {
        List<String> avisos = new ArrayList<>(advertencias);
        if (aviso != null) avisos.add(0, aviso);
        return new ResultadoChiCuadrado(modo, etiquetasFila, etiquetasColumna, observada, esperada,
                contribucion, totalesFila, totalesColumna, n, gradosLibertad, chiCuadrado, alfa,
                valorCritico, pValor, rechazaIndependencia, cumpleSupuestos,
                porcentajeEsperadasMenorA5, avisos);
    }
}