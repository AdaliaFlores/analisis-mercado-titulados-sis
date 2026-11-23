package com.umss.controller;

import com.umss.config.Database;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construye respuestas de error homogéneas y amigables para los reportes.
 * El detalle técnico se registra en el servidor; al navegador solo se envía un
 * mensaje claro y, cuando aplica, la marca de que el error es reintentable.
 */
final class RespuestaError {

    private static final String MENSAJE_REINTENTABLE =
            "No se pudo obtener la información del reporte en este momento. "
            + "La base de datos puede estar iniciándose; por favor intenta nuevamente.";

    private static final String MENSAJE_GENERAL =
            "No se pudo generar el reporte en este momento. "
            + "Intenta nuevamente en unos segundos.";

    private RespuestaError() {
    }

    static void responder(Context ctx, String contexto, Exception error) {
        boolean transitorio = Database.esErrorTransitorio(error);
        System.err.println("[Reportes] Error al generar " + contexto + ": "
                + error.getMessage());
        error.printStackTrace();

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", transitorio ? MENSAJE_REINTENTABLE : MENSAJE_GENERAL);
        cuerpo.put("reintentable", transitorio);
        ctx.status(transitorio ? 503 : 500).json(cuerpo);
    }
}
