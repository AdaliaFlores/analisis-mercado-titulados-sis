package com.umss.controller;

import com.umss.dao.EncuestaDAO;
import com.umss.model.RespuestaDTO;
import io.javalin.http.Context;

public class EncuestaController {

    public static void listarEncuestas(Context ctx) {
        try {
            ctx.json(EncuestaDAO.obtenerEncuestas());
        } catch (Exception e) {
            ctx.status(500).result("Error al obtener encuestas: " + e.getMessage());
        }
    }

    public static void obtenerPreguntas(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            ctx.json(EncuestaDAO.obtenerPreguntasPorEncuesta(id));
        } catch (Exception e) {
            ctx.status(500).result("Error al obtener preguntas: " + e.getMessage());
        }
    }

    public static void guardarRespuesta(Context ctx) {
        try {
            RespuestaDTO dto = ctx.bodyAsClass(RespuestaDTO.class);
            EncuestaDAO.guardarRespuestas(dto);
            ctx.status(201).result("Respuestas guardadas exitosamente");
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Error al guardar respuestas: " + e.getMessage());
        }
    }
}