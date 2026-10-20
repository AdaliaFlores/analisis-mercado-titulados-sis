package com.umss;

import com.umss.controller.EncuestaController;
import com.umss.controller.ExportController;

import io.javalin.Javalin;
import com.umss.controller.ReporteController;
import com.umss.controller.RespuestaController;
import com.umss.controller.EnvioController;

public class App {
    public static void main(String[] args) {
        var app = Javalin.create(config -> {
            config.staticFiles.add("/public");
        }).start(7070);

        // Rutas API REST
        app.get("/api/encuestas", EncuestaController::listarEncuestas);
        app.get("/api/encuestas/{id}/preguntas", EncuestaController::obtenerPreguntas);
        app.post("/api/respuestas", EncuestaController::guardarRespuesta);
        app.get("/api/encuestas/{id}/envios", EnvioController::listarEnvios);
        app.get("/api/envios/{envioId}", EnvioController::obtenerDetalle);
        app.get("/api/reportes/emprendimiento", ReporteController::reporteEmprendimiento);
        app.get("/api/reportes/cruce", ReporteController::reporteCruce);
        app.get("/api/reportes/desempleo", ReporteController::reporteDesempleo);
        app.get("/api/reportes/brechas", ReporteController::reporteBrechas);
        app.get("/api/reportes/empleabilidad", ReporteController::reporteEmpleabilidad);
        app.get("/api/reportes/formacion-financiamiento", ReporteController::reporteFormacionFinanciamiento);
        app.get("/api/export/csv", ExportController::exportCsv);
        app.get("/api/export/excel", ExportController::exportExcel);
        app.get("/api/export/pdf", ExportController::exportPdf);
        // Data Wrangling: corrección de respuestas de texto (con auditoría)
        app.put("/api/respuestas/{id}", RespuestaController::editar);
        app.get("/api/envios/{envioId}/auditoria", RespuestaController::auditoriaPorEnvio);
        app.post("/api/import/excel", EncuestaController::importarExcel);
        app.post("/api/import/excel/empleadores", EncuestaController::importarExcelEmpleadores);
    }
}