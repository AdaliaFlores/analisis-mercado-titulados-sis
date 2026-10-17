package com.umss.controller;

import com.umss.dao.EncuestaDAO;
import io.javalin.http.Context;

public class ReporteController {

    public static void reporteEmprendimiento(Context ctx) {
        try {
            ctx.json(EncuestaDAO.obtenerConteoEmprendimiento());
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Error al generar reporte: " + e.getMessage());
        }
    }

    public static void reporteCruce(Context ctx) {
        try {
            int x = Integer.parseInt(ctx.queryParam("x"));
            int y = Integer.parseInt(ctx.queryParam("y"));
            String fp1 = ctx.queryParam("fp1");
            String fv1 = ctx.queryParam("fv1");
            String fp2 = ctx.queryParam("fp2");
            String fv2 = ctx.queryParam("fv2");

            if (fp1 != null && fv1 != null) {
                int p2 = (fp2 != null && fv2 != null) ? Integer.parseInt(fp2) : -1;
                ctx.json(EncuestaDAO.obtenerCruceFiltrado(x, y,
                        Integer.parseInt(fp1), fv1, p2, fv2));
            } else {
                ctx.json(EncuestaDAO.obtenerCruce(x, y));
            }
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Error en el cruce: " + e.getMessage());
        }
    }
}
