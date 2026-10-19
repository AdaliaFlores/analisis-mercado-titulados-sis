package com.umss;

import com.umss.controller.EncuestaController;
import io.javalin.Javalin;
import com.umss.controller.ReporteController;
import com.umss.controller.RespuestaController;
import com.umss.controller.EnvioController;

public class App {
    public static void main(String[] args) {
        var app = Javalin.create(config -> {
            config.staticFiles.add("/public");
        }).start(8080);

        // Rutas API REST
        app.get("/api/encuestas", EncuestaController::listarEncuestas);
        app.get("/api/encuestas/{id}/preguntas", EncuestaController::obtenerPreguntas);
        app.post("/api/respuestas", EncuestaController::guardarRespuesta);
        app.get("/api/encuestas/{id}/envios", EnvioController::listarEnvios);
        app.get("/api/envios/{envioId}", EnvioController::obtenerDetalle);
        app.get("/api/reportes/emprendimiento", ReporteController::reporteEmprendimiento);
        app.get("/api/reportes/cruce", ReporteController::reporteCruce);
        // Data Wrangling: corrección de respuestas de texto (con auditoría)
        app.put("/api/respuestas/{id}", RespuestaController::editar);
        app.get("/api/envios/{envioId}/auditoria", RespuestaController::auditoriaPorEnvio);
    }
}