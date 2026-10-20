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
        ejecutarImportacion(ctx, 1);
    }

    public static void importarExcelEmpleadores(Context ctx) {
        ejecutarImportacion(ctx, 2);
    }

    /**
     * Sube el Excel y lo inserta directamente en la base de datos.
     * encuesta_id por defecto: 1 = titulados, 2 = empleadores.
     */
    private static void ejecutarImportacion(Context ctx, int encuestaPorDefecto) {
        UploadedFile file = ctx.uploadedFile("archivoExcel");
        int encuestaId = ctx.queryParamAsClass("encuesta_id", Integer.class).getOrDefault(encuestaPorDefecto);

        if (file == null) {
            ctx.status(400).json(Map.of("status", "error", "mensaje", "No se adjuntó ningún archivo."));
            return;
        }

        try (Connection conn = Database.getConnection()) {
            ExcelImporterService service = new ExcelImporterService();
            ExcelImporterService.Resultado r = service.importar(file.content(), encuestaId, conn);

            ctx.json(Map.of(
                "status", "success",
                "mensaje", "Se importaron exitosamente " + r.encuestas + " encuestas (encuesta " + encuestaId + ")."
            ));
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of(
                "status", "error",
                "mensaje", "Error al importar el archivo: " + e.getMessage()
            ));
        }
    }
}