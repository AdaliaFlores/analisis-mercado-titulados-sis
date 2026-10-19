package com.umss.controller;

import com.umss.dao.EncuestaDAO;
import io.javalin.http.Context;
import com.umss.model.ConteoRespuesta;
import com.umss.model.IndicadorEscala;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.umss.model.AreaEmpleabilidad;
import com.umss.model.AreaInteresPosgrado;
import com.umss.model.ResultadoChi2;

public class ReporteController {

    public static void reporteEmprendimiento(Context ctx) {
        try {
            ctx.json(EncuestaDAO.obtenerConteoEmprendimiento());
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Error al generar reporte: " + e.getMessage());
        }
    }

    public static void reporteCruce(Context ctx) {
        try {
            int x = Integer.parseInt(ctx.queryParam("x"));
            int y = Integer.parseInt(ctx.queryParam("y"));
            String fp1 = ctx.queryParam("fp1");
            String fv1 = ctx.queryParam("fv1");
            String fp2 = ctx.queryParam("fp2");
            String fv2 = ctx.queryParam("fv2");

            if (fp1 != null && fv1 != null) {
                int p2 = (fp2 != null && fv2 != null) ? Integer.parseInt(fp2) : -1;
                ctx.json(EncuestaDAO.obtenerCruceFiltrado(x, y,
                        Integer.parseInt(fp1), fv1, p2, fv2));
            } else {
                ctx.json(EncuestaDAO.obtenerCruce(x, y));
            }
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Error en el cruce: " + e.getMessage());
        }
    }

    public static void reporteDesempleo(Context ctx) {
        try {
            Map<String, Object> r = new LinkedHashMap<>();

            int[] pob = EncuestaDAO.contarPoblacionObjetivo();
            r.put("totalTitulados", pob[0]);
            r.put("noTrabaja", pob[1]);
            r.put("conTiempoDesempleo", pob[2]);
            r.put("poblacionObjetivo", pob[3]);

            List<ConteoRespuesta> situacion = EncuestaDAO.contarRespuestas(14);
            situacion.sort((a, b) -> Integer.compare(ordenSituacion(a.getRespuesta()), ordenSituacion(b.getRespuesta())));
            r.put("situacionActual", situacion);
            r.put("tiempoDesempleo", EncuestaDAO.contarRespuestas(13));
            r.put("razones", EncuestaDAO.contarRespuestas(28));

            r.put("matrizBarreras", EncuestaDAO.obtenerCruce(28, 29));
            r.put("matrizInsercion", EncuestaDAO.obtenerCruce(36, 37));

            // Demandas del empleador (conteo de palabras clave en texto abierto)
            r.put("tendenciasEmpleador", EncuestaDAO.contarPalabrasClave(59));
            r.put("herramientasEmpleador", EncuestaDAO.contarPalabrasClave(60));
            r.put("competenciasEmpleador", EncuestaDAO.contarPalabrasClave(61));
            r.put("respuestasTendencias", EncuestaDAO.obtenerRespuestasTexto(59));
            r.put("respuestasHerramientas", EncuestaDAO.obtenerRespuestasTexto(60));
            r.put("respuestasCompetencias", EncuestaDAO.obtenerRespuestasTexto(61));

            ctx.json(r);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Error al generar reporte de desempleo: " + e.getMessage());
        }
    }

    public static void reporteBrechas(Context ctx) {
        try {
            Map<String, Object> r = new LinkedHashMap<>();

            r.put("totalTitulados", EncuestaDAO.contarEnvios(42));
            r.put("totalEmpleadores", EncuestaDAO.contarEnvios(59, 60, 61));

            List<IndicadorEscala> indicadores = new ArrayList<>();
            indicadores.add(EncuestaDAO.escalaIndicador(38, "Satisfacción con la formación recibida"));
            indicadores.add(EncuestaDAO.escalaIndicador(39, "Concordancia formación ↔ mercado laboral"));
            indicadores.add(EncuestaDAO.escalaIndicador(24, "Pertinencia del puesto de trabajo"));
            indicadores.add(EncuestaDAO.escalaIndicador(11, "Oferta de posgrado afín a la especialidad"));
            r.put("indicadores", indicadores);

            //Radar: Estado Objetivo (empleador) vs Estado Actual (titulado) 
            r.put("radar", EncuestaDAO.radarBrechas());

            r.put("asignaturasVentaja", EncuestaDAO.asignaturasFrecuencia(42));
            r.put("asignaturasPocaUtilidad", EncuestaDAO.asignaturasFrecuencia(44));

            //Tablas cruzadas / mapas de calor
            r.put("cruceVentajaSituacion", EncuestaDAO.cruceAsignaturaVariable(42, 14));
            r.put("crucePocaSituacion", EncuestaDAO.cruceAsignaturaVariable(44, 14));
            r.put("crucePocaRazon", EncuestaDAO.cruceAsignaturaVariable(44, 28));

            r.put("demandaTendencias", EncuestaDAO.contarPalabrasClave(59));
            r.put("demandaHerramientas", EncuestaDAO.contarPalabrasClave(60));
            r.put("demandaCompetencias", EncuestaDAO.contarPalabrasClave(61));
            r.put("contextoEmpleador", EncuestaDAO.contarRespuestas(53, 55, 56));

            ctx.json(r);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Error al generar reporte de brechas: " + e.getMessage());
        }
    }

    public static void reporteEmpleabilidad(Context ctx) {
        try {
            Map<String, Object> r = new LinkedHashMap<>();

            // Áreas con mayor empleabilidad
            r.put("areasEmpleabilidad", EncuestaDAO.analisisEmpleabilidad());

            // Oferta con poca demanda
            List<ConteoRespuesta> razones = EncuestaDAO.contarRespuestas(28);
            List<ConteoRespuesta> criticas = new ArrayList<>();
            for (ConteoRespuesta c : razones) {
                String v = c.getRespuesta() == null ? "" : c.getRespuesta().toLowerCase();
                if (v.contains("demanda laboral inexistente") || v.contains("remuneracion muy baja") || v.contains("remuneración muy baja")) {
                    criticas.add(c);
                }
            }
            if (criticas.isEmpty() && razones.size() > 5) {
                criticas.addAll(razones.subList(0, 5));
            } else if (criticas.isEmpty()) {
                criticas.addAll(razones);
            }
            r.put("razonesCriticas", criticas);

            //Asignaturas de poca utilidad
            List<ConteoRespuesta> asignaturasPoca = EncuestaDAO.asignaturasFrecuencia(44);
            if (asignaturasPoca.size() > 10) asignaturasPoca = asignaturasPoca.subList(0, 10);
            r.put("asignaturasPocaUtilidadTop", asignaturasPoca);

            // Rubros/sectores sin contratación prevista 
            List<ConteoRespuesta> contratacion = EncuestaDAO.contarRespuestas(55);
            r.put("contratacionEmpleador", contratacion);
            r.put("rubroEmpleador", EncuestaDAO.contarRespuestas(53));

            ctx.json(r);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Error al generar reporte de empleabilidad: " + e.getMessage());
        }
    }

    public static void reporteFormacionFinanciamiento(Context ctx) {
        try {
            Map<String, Object> r = new LinkedHashMap<>();

            // Formación continua
            List<AreaInteresPosgrado> areas = EncuestaDAO.areasInteresPosgrado();
            r.put("areasInteres", areas);
            r.put("alertaConcentracion", EncuestaDAO.alertaConcentracionCritica(areas));
            r.put("nivelesFormacion", EncuestaDAO.nivelesFormacionDeseados());

            // Financiamiento
            Map<String, Object> fin = EncuestaDAO.tablaFinanciamiento();
            r.put("financiamiento", fin);

            ctx.json(r);
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Error al generar reporte de formación y financiamiento: " + e.getMessage());
        }
    }

    private static int ordenSituacion(String v) {
        if (v == null) return 9;
        if (v.contains("NO trabajo")) return 1;
        if (v.contains("emprendimiento")) return 2;
        if (v.contains("trabajando")) return 3;
        return 9;
    }
}
