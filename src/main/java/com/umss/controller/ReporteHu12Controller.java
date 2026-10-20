
package com.umss.controller;

import com.umss.dao.ReporteHu12DAO;
import io.javalin.http.Context;

public class ReporteHu12Controller {

    private ReporteHu12Controller() {}

    public static void frecuencias(Context ctx) {
        try {
            int encuestaId = Integer.parseInt(
                    ctx.queryParam("encuesta"));
            int preguntaId = Integer.parseInt(
                    ctx.queryParam("pregunta"));

            // Esta HU analiza las encuestas de titulados y empleadores.
            if (encuestaId != 1 && encuestaId != 2) {
                ctx.status(400).json(
                        java.util.Map.of("error", "Encuesta no válida"));
                return;
            }

            // Limitar las preguntas a las incluidas en la HU12.
            boolean valida =
                    (encuestaId == 1 &&
                     (preguntaId == 26 || preguntaId == 46)) ||
                    (encuestaId == 2 &&
                     preguntaId >= 54 && preguntaId <= 79);

            if (!valida) {
                ctx.status(400).json(
                        java.util.Map.of("error",
                                "Pregunta fuera del alcance de la HU12"));
                return;
            }

            ctx.json(ReporteHu12DAO.obtenerFrecuencias(
                    encuestaId, preguntaId));

        } catch (NumberFormatException e) {
            ctx.status(400).json(
                    java.util.Map.of("error",
                            "Los parámetros encuesta y pregunta deben ser números"));
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(
                    java.util.Map.of("error",
                            "No se pudieron obtener las frecuencias"));
        }
    }

    public static void alumno(Context ctx) {
        try {
            ctx.json(ReporteHu12DAO.obteneralumno());
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(
                    java.util.Map.of("error",
                            "No se pudo generar el reporte alumno"));
        }
    }
}
