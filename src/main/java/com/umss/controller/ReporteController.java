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
}
