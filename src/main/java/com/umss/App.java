
package com.umss;

import com.umss.controller.AnalisisController;
import com.umss.controller.EncuestaController;
import com.umss.controller.EnvioController;
import com.umss.controller.ExportController;
import com.umss.controller.ReporteController;
import com.umss.controller.ReporteHu12Controller;
import com.umss.controller.RespuestaController;

import io.javalin.Javalin;

public class App {

    public static void main(String[] args) {

        int port = Integer.parseInt(
            System.getenv().getOrDefault("PORT", "8080")
        );

        var app = Javalin.create(config -> {
            config.staticFiles.add("/public");
        });

        // Verificación de disponibilidad del servidor
        app.get("/health", ctx -> ctx.result("OK"));

        // Encuestas
        app.get("/api/encuestas",
            EncuestaController::listarEncuestas);

        app.get("/api/encuestas/{id}/preguntas",
            EncuestaController::obtenerPreguntas);

        app.post("/api/respuestas",
            EncuestaController::guardarRespuesta);

        // Envíos
        app.get("/api/encuestas/{id}/envios",
            EnvioController::listarEnvios);

        app.get("/api/envios/{envioId}",
            EnvioController::obtenerDetalle);

        // Reportes
        app.get("/api/reportes/emprendimiento",
            ReporteController::reporteEmprendimiento);

        app.get("/api/reportes/cruce",
            ReporteController::reporteCruce);

        app.get("/api/reportes/desempleo",
            ReporteController::reporteDesempleo);

        app.get("/api/reportes/brechas",
            ReporteController::reporteBrechas);

        app.get("/api/reportes/empleabilidad",
            ReporteController::reporteEmpleabilidad);

        app.get("/api/reportes/formacion-financiamiento",
            ReporteController::reporteFormacionFinanciamiento);

        // Exportaciones
        app.get("/api/export/csv",
            ExportController::exportCsv);

        app.get("/api/export/excel",
            ExportController::exportExcel);

        app.get("/api/export/pdf",
            ExportController::exportPdf);

        // Auditoría y corrección de respuestas
        app.put("/api/respuestas/{id}",
            RespuestaController::editar);

        app.get("/api/envios/{envioId}/auditoria",
            RespuestaController::auditoriaPorEnvio);

        // Análisis estadístico
        app.get("/api/analisis/frecuencias",
            AnalisisController::frecuencias);

        app.get("/api/analisis/promedio",
            AnalisisController::promedio);

        app.get("/api/analisis/chi-cuadrado",
            AnalisisController::chiCuadrado);

        // Reportes HU12
        app.get("/api/reportes/hu12/frecuencias",
            ReporteHu12Controller::frecuencias);

        app.get("/api/reportes/hu12/alumno",
            ReporteHu12Controller::alumno);

        // Importaciones Excel
        app.post("/api/import/excel",
            EncuestaController::importarExcel);

        app.post("/api/import/excel/empleadores",
            EncuestaController::importarExcelEmpleadores);

        // Iniciar servidor en el puerto proporcionado por Render
        app.start("0.0.0.0", port);

        System.out.println("Servidor iniciado en el puerto " + port);
    }
}
