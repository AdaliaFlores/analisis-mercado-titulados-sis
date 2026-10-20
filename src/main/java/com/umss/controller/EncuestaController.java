package com.umss.controller;

import java.sql.Connection;
import java.util.Map;

import com.umss.config.Database;
import com.umss.dao.EncuestaDAO;
import com.umss.dao.ExcelImporterService;
import com.umss.model.RespuestaDTO;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

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

    public static void importarExcel(Context ctx) {
        UploadedFile file = ctx.uploadedFile("archivoExcel");
        // Puedes recibir el id de la encuesta como parámetro en la URL o query string, o asignarlo por defecto:
        int encuestaId = ctx.queryParamAsClass("encuesta_id", Integer.class).getOrDefault(1);

        if (file == null) {
            ctx.status(400).json(Map.of("status", "error", "mensaje", "No se adjuntó ningún archivo."));
            return;
        }

        try (Connection conn = Database.getConnection()) {
            ExcelImporterService service = new ExcelImporterService();
            int encuestasImportadas = service.importarRespuestasExcel(file.content(), encuestaId, conn);

            ctx.json(Map.of(
                "status", "success",
                "mensaje", "Se importaron exitosamente " + encuestasImportadas + " encuestas completas."
            ));
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of(
                "status", "error",
                "mensaje", "Error al procesar el archivo Excel: " + e.getMessage()
            ));
        }
    }
    public static void importarExcelEmpleadores(Context ctx) {
        UploadedFile file = ctx.uploadedFile("archivoExcel");
        int encuestaIdEmpleadores = 2; // ID correspondiente a la encuesta de empleadores en la BD

        if (file == null) {
            ctx.status(400).json(Map.of("status", "error", "mensaje", "No se adjuntó ningún archivo Excel."));
            return;
        }

        try (Connection conn = Database.getConnection()) {
            ExcelImporterService service = new ExcelImporterService();
            int encuestasImportadas = service.importarRespuestasExcel(file.content(), encuestaIdEmpleadores, conn);

            ctx.json(Map.of(
                "status", "success",
                "mensaje", "Se importaron exitosamente " + encuestasImportadas + " encuestas de empleadores."
            ));
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of(
                "status", "error",
                "mensaje", "Error al procesar el archivo de empleadores: " + e.getMessage()
            ));
        }
    }
}