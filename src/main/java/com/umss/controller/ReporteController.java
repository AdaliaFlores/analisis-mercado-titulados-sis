package com.umss.controller;

import com.umss.dao.EncuestaDAO;
import io.javalin.http.Context;
import com.umss.model.ConteoPalabra;
import com.umss.model.ConteoRespuesta;
import com.umss.model.IndicadorEscala;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.umss.util.CalculadoraEmprendimiento;
import com.umss.util.CalculadoraDesempleo;
import com.umss.model.AreaInteresPosgrado;
public class ReporteController {

    public static void reporteEmprendimiento(Context ctx) {
        try {
            ctx.json(CalculadoraEmprendimiento.analisisExploratorio(
                    EncuestaDAO.obtenerRegistrosEmprendimiento()));
        } catch (Exception e) {
            RespuestaError.responder(ctx, "el reporte de emprendimiento", e);
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
            RespuestaError.responder(ctx, "el cruce de variables", e);
        }
    }

    public static void reporteDesempleo(Context ctx) {
        try {
            List<ConteoPalabra> tendencias = EncuestaDAO.contarPalabrasClave(59);
            List<ConteoPalabra> herramientas = EncuestaDAO.contarPalabrasClave(60);
            List<ConteoPalabra> competencias = EncuestaDAO.contarPalabrasClave(61);

            List<CalculadoraDesempleo.DemandaEmpleador> demandas = List.of(
                    new CalculadoraDesempleo.DemandaEmpleador(
                            "Tendencias emergentes",
                            EncuestaDAO.obtenerRespuestasTexto(59).size(),
                            tendencias),
                    new CalculadoraDesempleo.DemandaEmpleador(
                            "Herramientas tecnológicas",
                            EncuestaDAO.obtenerRespuestasTexto(60).size(),
                            herramientas),
                    new CalculadoraDesempleo.DemandaEmpleador(
                            "Competencias fundamentales",
                            EncuestaDAO.obtenerRespuestasTexto(61).size(),
                            competencias));

            ctx.json(CalculadoraDesempleo.analisis(
                    EncuestaDAO.obtenerRegistrosDesempleo(), demandas));
        } catch (Exception e) {
            RespuestaError.responder(ctx, "el reporte de desempleo", e);
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
            RespuestaError.responder(ctx, "el reporte de brechas", e);
        }
    }

    public static void reporteEmpleabilidad(Context ctx) {
        try {
            Map<String, Object> r = new LinkedHashMap<>();

            // Áreas con mayor empleabilidad
            r.put("areasEmpleabilidad", EncuestaDAO.analisisEmpleabilidad());

            // Cargar TODAS las razones de desempleo completas (las 7 razones)
            List<ConteoRespuesta> razones = EncuestaDAO.contarRespuestas(28);
            r.put("razones", razones);
            r.put("razonesCriticas", razones);

            // Asignaturas de poca utilidad
            List<ConteoRespuesta> asignaturasPoca = EncuestaDAO.asignaturasFrecuencia(44);
            //if (asignaturasPoca.size() > 10) asignaturasPoca = asignaturasPoca.subList(0, 10);
            r.put("asignaturasPocaUtilidadTop", asignaturasPoca);

            // Rubros/sectores sin contratación prevista 
            List<ConteoRespuesta> contratacion = EncuestaDAO.contarRespuestas(55);
            r.put("contratacionEmpleador", contratacion);
            r.put("rubroEmpleador", EncuestaDAO.contarRespuestas(53));
            // Estadísticos descriptivos de antigüedad profesional (n=181)
            r.put("estadisticosAntiguedad", EncuestaDAO.estadisticosAntiguedad(12));
            ctx.json(r);
        } catch (Exception e) {
            RespuestaError.responder(ctx, "el reporte de empleabilidad", e);
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
            r.put("cruceNivelOrg", EncuestaDAO.cruceNivelOrganizacion(10));
            r.put("cruceAreaNivel", EncuestaDAO.cruceAreaNivel());
            // Financiamiento
            Map<String, Object> fin = EncuestaDAO.tablaFinanciamiento();
            r.put("financiamiento", fin);

            ctx.json(r);
        } catch (Exception e) {
            RespuestaError.responder(ctx, "el reporte de formación y financiamiento", e);
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
