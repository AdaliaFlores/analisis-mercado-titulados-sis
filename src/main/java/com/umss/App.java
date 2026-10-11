package com.umss;

import com.umss.controller.EncuestaController;
import io.javalin.Javalin;

public class App {
    public static void main(String[] args) {
        var app = Javalin.create(config -> {
            config.staticFiles.add("/public");
        }).start(7070);

        // Rutas API REST
        app.get("/api/encuestas", EncuestaController::listarEncuestas);
        app.get("/api/encuestas/{id}/preguntas", EncuestaController::obtenerPreguntas);
        app.post("/api/respuestas", EncuestaController::guardarRespuesta);
    }
}