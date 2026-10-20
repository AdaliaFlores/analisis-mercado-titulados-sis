package com.umss.util;

import com.umss.model.CeldaCruce;
import com.umss.model.ResultadoChiCuadrado;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.util.*;

/**
 * Prueba de independencia Chi-cuadrado sobre una tabla de contingencia.
 *   E(i,j) = total_fila(i) * total_columna(j) / N
 *   χ² = Σ (O - E)² / E        gl = (filas - 1) * (columnas - 1)
 * Cálculo puro (sin base de datos): recibe las celdas y devuelve todos los pasos.
 */
public final class PruebaChiCuadrado {

    public static final double ALFA = 0.05;
    private static final double LIMITE_PORC_ESPERADAS_BAJAS = 20.0;  // regla: máx. 20% con E < 5
    private static final double MIN_ESPERADA = 1.0;                  // y ninguna con E < 1
    private static final int N_MUESTRA_PEQUENA = 30;

    private PruebaChiCuadrado() {}

    public static ResultadoChiCuadrado calcular(List<CeldaCruce> celdas,
                                                Map<String, Integer> ordenFilas,
                                                Map<String, Integer> ordenColumnas) {
        List<String> filas = etiquetas(celdas, true, ordenFilas);
        List<String> cols = etiquetas(celdas, false, ordenColumnas);
        int nf = filas.size(), nc = cols.size();

        long[][] obs = new long[nf][nc];
        for (CeldaCruce c : celdas) {
            obs[filas.indexOf(c.getValorX())][cols.indexOf(c.getValorY())] += c.getTotal();
        }

        long[] tf = new long[nf], tc = new long[nc];
        long n = 0;
        for (int i = 0; i < nf; i++)
            for (int j = 0; j < nc; j++) {
                tf[i] += obs[i][j];
                tc[j] += obs[i][j];
                n += obs[i][j];
            }

        double[][] esp = new double[nf][nc];
        double[][] contrib = new double[nf][nc];
        List<String> avisos = new ArrayList<>();
        int gl = Math.max(0, (nf - 1) * (nc - 1));

        if (n == 0) {
            avisos.add("No hay datos para este cruce.");
            return vacio(filas, cols, obs, esp, contrib, tf, tc, n, gl, avisos);
        }

        double chi2 = 0, minEsp = Double.MAX_VALUE;
        int bajas = 0;
        for (int i = 0; i < nf; i++)
            for (int j = 0; j < nc; j++) {
                esp[i][j] = (double) tf[i] * tc[j] / n;
                contrib[i][j] = Math.pow(obs[i][j] - esp[i][j], 2) / esp[i][j];
                chi2 += contrib[i][j];
                if (esp[i][j] < 5) bajas++;
                minEsp = Math.min(minEsp, esp[i][j]);
            }
        double porcBajas = 100.0 * bajas / (nf * nc);

        if (nf < 2 || nc < 2) {
            avisos.add("Se necesitan al menos 2 categorías en cada variable para aplicar el Chi-cuadrado.");
            return new ResultadoChiCuadrado("", filas, cols, obs, esp, contrib, tf, tc, n, gl,
                    null, ALFA, null, null, null, false, porcBajas, avisos);
        }

        boolean cumple = porcBajas <= LIMITE_PORC_ESPERADAS_BAJAS && minEsp >= MIN_ESPERADA;
        if (!cumple)
            avisos.add(String.format(Locale.US,
                "El %.0f%% de las frecuencias esperadas es menor a 5 (mínima: %.2f). "
              + "El resultado no es confiable: agrupe categorías o reúna más datos.", porcBajas, minEsp));
        if (n < N_MUESTRA_PEQUENA)
            avisos.add("Muestra pequeña (N = " + n + "): interprete el resultado como exploratorio.");

        ChiSquaredDistribution dist = new ChiSquaredDistribution(gl);
        double critico = dist.inverseCumulativeProbability(1 - ALFA);
        double p = 1.0 - dist.cumulativeProbability(chi2);

        return new ResultadoChiCuadrado("", filas, cols, obs, esp, contrib, tf, tc, n, gl,
                chi2, ALFA, critico, p, chi2 > critico, cumple, porcBajas, avisos);
    }

    private static ResultadoChiCuadrado vacio(List<String> f, List<String> c, long[][] obs,
                                              double[][] esp, double[][] contrib, long[] tf, long[] tc,
                                              long n, int gl, List<String> avisos) {
        return new ResultadoChiCuadrado("", f, c, obs, esp, contrib, tf, tc, n, gl,
                null, ALFA, null, null, null, false, 0, avisos);
    }

    // Orden: primero las etiquetas con orden definido; luego el resto por frecuencia y nombre
    private static List<String> etiquetas(List<CeldaCruce> celdas, boolean filas,
                                          Map<String, Integer> orden) {
        Map<String, Long> totales = new HashMap<>();
        for (CeldaCruce c : celdas)
            totales.merge(filas ? c.getValorX() : c.getValorY(), (long) c.getTotal(), Long::sum);

        List<String> lista = new ArrayList<>(totales.keySet());
        lista.sort(Comparator
                .comparing((String e) -> orden.getOrDefault(e, Integer.MAX_VALUE))
                .thenComparing(e -> -totales.get(e))
                .thenComparing(Comparator.naturalOrder()));
        return lista;
    }
}