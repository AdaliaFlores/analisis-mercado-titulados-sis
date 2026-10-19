package com.umss.controller;

import com.umss.dao.AnalisisDAO;
import com.umss.model.FiltroEnvio;
import com.umss.model.ResultadoChiCuadrado;
import com.umss.util.PruebaChiCuadrado;
import io.javalin.http.Context;

import java.sql.SQLException;

public class AnalisisController {

    private static final int MAX_FILTROS = 5;   // parámetros fp1/fv1 ... fp5/fv5

    /** GET /api/analisis/frecuencias?pregunta=19&agrupada=true&fp1=18&fv1=PRIVADO */
    public static void frecuencias(Context ctx) {
        try {
            int pregunta = Integer.parseInt(ctx.queryParam("pregunta"));
            boolean agrupada = "true".equals(ctx.queryParam("agrupada"));
            ctx.json(AnalisisDAO.frecuencias(pregunta, agrupada, leerFiltros(ctx)));
        } catch (NumberFormatException e) {
            ctx.status(400).result("Parámetros inválidos");
        } catch (Exception e) {
            responderError(ctx, e);
        }
    }

    /** GET /api/analisis/promedio?valor=27&grupo=19&fp1=18&fv1=PRIVADO */
    public static void promedio(Context ctx) {
        try {
            int valor = Integer.parseInt(ctx.queryParam("valor"));
            int grupo = Integer.parseInt(ctx.queryParam("grupo"));
            ctx.json(AnalisisDAO.promedioPorGrupo(valor, grupo, leerFiltros(ctx)));
        } catch (NumberFormatException e) {
            ctx.status(400).result("Parámetros inválidos");
        } catch (Exception e) {
            responderError(ctx, e);
        }
    }

    /**
     * GET /api/analisis/chi-cuadrado?x=19&y=27&modo=auto|completa|agrupada
     * auto: usa la tabla completa; si no cumple los supuestos y existe agrupación, usa la agrupada.
     */
    public static void chiCuadrado(Context ctx) {
        try {
            int x = Integer.parseInt(ctx.queryParam("x"));
            int y = Integer.parseInt(ctx.queryParam("y"));
            String modo = ctx.queryParam("modo") == null ? "auto" : ctx.queryParam("modo");
            FiltroEnvio filtro = leerFiltros(ctx);

            ResultadoChiCuadrado resultado;
            if ("agrupada".equals(modo)) {
                resultado = tabla(x, y, true, filtro);
            } else {
                resultado = tabla(x, y, false, filtro);
                boolean hayAgrupacion = AnalisisDAO.tieneAgrupacion(x) || AnalisisDAO.tieneAgrupacion(y);
                if ("auto".equals(modo) && !resultado.cumpleSupuestos() && hayAgrupacion) {
                    resultado = tabla(x, y, true, filtro).conModo("AGRUPADA",
                        "Se aplicó la agrupación de categorías porque la tabla completa no cumplía los supuestos.");
                }
            }
            ctx.json(resultado);
        } catch (NumberFormatException e) {
            ctx.status(400).result("Parámetros inválidos");
        } catch (Exception e) {
            responderError(ctx, e);
        }
    }

    private static ResultadoChiCuadrado tabla(int x, int y, boolean agrupada,
                                              FiltroEnvio filtro) throws SQLException {
        var celdas = AnalisisDAO.contingencia(x, y, agrupada, filtro);
        var resultado = PruebaChiCuadrado.calcular(celdas,
                AnalisisDAO.ordenEtiquetas(x, agrupada),
                AnalisisDAO.ordenEtiquetas(y, agrupada));
        return resultado.conModo(agrupada ? "AGRUPADA" : "COMPLETA", null);
    }

    private static FiltroEnvio leerFiltros(Context ctx) {
        FiltroEnvio filtro = new FiltroEnvio();
        for (int i = 1; i <= MAX_FILTROS; i++) {
            String pregunta = ctx.queryParam("fp" + i);
            String valor = ctx.queryParam("fv" + i);
            if (pregunta != null && valor != null) {
                filtro.agregar(Integer.parseInt(pregunta), valor.trim());
            }
        }
        return filtro;
    }

    private static void responderError(Context ctx, Exception e) {
        e.printStackTrace();
        ctx.status(500).result("Error en el análisis: " + e.getMessage());
    }
}