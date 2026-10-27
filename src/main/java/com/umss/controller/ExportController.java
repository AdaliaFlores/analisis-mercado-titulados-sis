package com.umss.controller;

import com.umss.dao.AnalisisDAO;
import com.umss.dao.EncuestaDAO;
import com.umss.dao.ReporteHu12DAO;
import com.umss.model.*;
import com.umss.util.PruebaChiCuadrado;
import io.javalin.http.Context;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Base64;

import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.SpiderWebPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

public class ExportController {

    static {
        // Los gráficos se generan en servidor 
        System.setProperty("java.awt.headless", "true");
    }

    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat DF2 = new DecimalFormat("0.00", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US));
    private static final DecimalFormat DF3 = new DecimalFormat("0.000", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US));

    private static final char DEC_SEP = new java.text.DecimalFormatSymbols(java.util.Locale.getDefault()).getDecimalSeparator();
    private static final char LIST_SEP = (DEC_SEP == ',') ? ';' : ',';

    /** Gráfico de barras verticales. */
    private static byte[] chartBar(String titulo, String ejeCat, String ejeVal, List<String> cats, List<Double> vals, String seriesName) {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (int i = 0; i < cats.size(); i++) {
            ds.addValue(vals.get(i), seriesName, cats.get(i));
        }
        JFreeChart chart = ChartFactory.createBarChart(titulo, ejeCat, ejeVal, ds, PlotOrientation.VERTICAL, false, true, false);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(java.awt.Color.WHITE);
        plot.setRangeGridlinePaint(java.awt.Color.LIGHT_GRAY);
        BarRenderer r = (BarRenderer) plot.getRenderer();
        r.setSeriesPaint(0, new java.awt.Color(0x0d, 0x6e, 0xfd));
        r.setDrawBarOutline(false);
        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setAutoRangeIncludesZero(true);
        return renderPng(chart, 720, 400);
    }

    /** Barras con varias series (comparativas). */
    private static byte[] chartBarMulti(String titulo, String ejeCat, String ejeVal, List<String> cats, List<String> seriesNames, List<List<Double>> seriesVals) {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (int s = 0; s < seriesNames.size(); s++) {
            List<Double> vals = seriesVals.get(s);
            for (int i = 0; i < cats.size(); i++) {
                ds.addValue(vals.get(i), seriesNames.get(s), cats.get(i));
            }
        }
        JFreeChart chart = ChartFactory.createBarChart(titulo, ejeCat, ejeVal, ds, PlotOrientation.VERTICAL, true, true, false);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(java.awt.Color.WHITE);
        plot.setRangeGridlinePaint(java.awt.Color.LIGHT_GRAY);
        return renderPng(chart, 820, 400);
    }

    /** Gráfico de pastel */
    private static byte[] chartPie(String titulo, List<String> cats, List<Double> vals) {
        DefaultPieDataset<String> ds = new DefaultPieDataset<>();
        for (int i = 0; i < cats.size(); i++) {
            ds.setValue(cats.get(i), vals.get(i));
        }
        JFreeChart chart = ChartFactory.createPieChart(titulo, ds, true, true, false);
        PiePlot plot = (PiePlot) chart.getPlot();
        plot.setBackgroundPaint(java.awt.Color.WHITE);
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0}: {1} ({2})"));
        return renderPng(chart, 720, 420);
    }

    /** Gráfico de radar (telaraña) para el análisis de brechas. */
    private static byte[] chartRadar(String titulo, List<String> cats, List<String> seriesNames, List<List<Double>> seriesVals) {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (int s = 0; s < seriesNames.size(); s++) {
            List<Double> vals = seriesVals.get(s);
            for (int i = 0; i < cats.size(); i++) {
                ds.addValue(vals.get(i), seriesNames.get(s), cats.get(i));
            }
        }
        SpiderWebPlot plot = new SpiderWebPlot(ds);
        plot.setBackgroundPaint(java.awt.Color.WHITE);
        plot.setWebFilled(false);
        JFreeChart chart = new JFreeChart(titulo, JFreeChart.DEFAULT_TITLE_FONT, plot, true);
        chart.addSubtitle(new TextTitle("Comparación dimensión objetivo vs. actual", new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10)));
        return renderPng(chart, 720, 480);
    }

    private static byte[] renderPng(JFreeChart chart, int w, int h) {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            ChartUtils.writeChartAsPNG(bos, chart, w, h);
            return bos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /** Inserta un gráfico (imagen) en la hoja de Excel. */
    private static void insertarImagen(Workbook wb, Sheet sh, byte[] png, int col1, int row1, int col2, int row2) {
        if (png == null || png.length == 0) return;
        int idx = wb.addPicture(png, Workbook.PICTURE_TYPE_PNG);
        XSSFDrawing drawing = ((org.apache.poi.xssf.usermodel.XSSFSheet) sh).createDrawingPatriarch();
        XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0, col1, row1, col2, row2);
        drawing.createPicture(anchor, idx);
    }

    /** Genera todas las imágenes de gráficos del reporte como bloques HTML (para PDF). */
    private static String htmlChart(String titulo, byte[] png) {
        if (png == null || png.length == 0) return "";
        String b64 = Base64.getEncoder().encodeToString(png);
        return "<h3>" + esc(titulo) + "</h3><img src=\"data:image/png;base64," + b64 + "\" style=\"width:520px;height:auto;margin-bottom:10px;\"/>";
    }

    //CSV
    public static void exportCsv(Context ctx) {
        String tipo = ctx.queryParam("tipo");
        if (tipo == null) tipo = "brechas";
        String filename = "reporte_" + tipo + "_" + System.currentTimeMillis() + ".csv";
        String csv = reformatearCsv(generarCsv(tipo));
        ctx.res().setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        ctx.res().setContentType("text/csv; charset=UTF-8");
        ctx.res().setCharacterEncoding("UTF-8");
        try {
            // BOM para Excel
            byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            ctx.result(new java.io.ByteArrayInputStream(concat(bom, csv.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            ctx.status(500).result("Error al generar CSV: " + e.getMessage());
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    /**Adapta el CSV (comas + punto decimal) al formato que Excel espera según el locales*/
    private static String reformatearCsv(String csv) {
        StringBuilder out = new StringBuilder();
        if (LIST_SEP == ';') out.append("sep=;\r\n");
        StringBuilder campo = new StringBuilder();
        boolean enComillas = false;
        for (int i = 0; i < csv.length(); i++) {
            char ch = csv.charAt(i);
            if (ch == '"') {
                enComillas = !enComillas;
                campo.append(ch);
            } else if (ch == ',' && !enComillas) {
                out.append(convertirCampo(campo.toString())).append(LIST_SEP);
                campo.setLength(0);
            } else if (ch == '\n') {
                out.append(convertirCampo(campo.toString())).append("\r\n");
                campo.setLength(0);
                enComillas = false;
            } else if (ch == '\r') {
            } else {
                campo.append(ch);
            }
        }
        out.append(convertirCampo(campo.toString()));
        return out.toString();
    }

    /** Convierte el punto decimal a coma en campos puramente numéricos cuando el locale lo requiere. */
    private static String convertirCampo(String f) {
        if (DEC_SEP == ',') {
            String t = f.trim();
            if (t.matches("-?\\d+\\.\\d+")) return f.replace('.', ',');
        }
        return f;
    }

    private static String generarCsv(String tipo) {
        StringBuilder sb = new StringBuilder();
        sb.append("# REPORTE UMSS - ").append(tipo.toUpperCase()).append(" | GENERADO: ").append(DF.format(new Date())).append("\r\n\r\n");
        try {
            switch (tipo) {
                case "brechas" -> {
                    // Radar de brechas
                    sb.append("=== EVALUACIÓN DE BRECHAS - MATRIZ RADAR (DIMENSIÓN | OBJETIVO(%) | ACTUAL(%) | BRECHA(%)) ===\r\n");
                    sb.append("DIMENSION,OBJETIVO(%),ACTUAL(%),BRECHA(%)\r\n");
                    List<DimensionRadar> radar = EncuestaDAO.radarBrechas();
                    for (DimensionRadar d : radar) {
                        sb.append(csv(d.getDimension())).append(",").append(d.getObjetivo()).append(",").append(d.getActual()).append(",").append(d.getBrecha()).append("\r\n");
                    }
                    sb.append("\r\n");

                    // Asignaturas ventaja 
                    sb.append("=== ASIGNATURAS CON MAYOR VENTAJA PARA EL DESEMPEÑO (p42) ===\r\n");
                    sb.append("ASIGNATURA,TOTAL,%_TITULADOS\r\n");
                    List<ConteoRespuesta> asig42 = EncuestaDAO.asignaturasFrecuencia(42);
                    for (ConteoRespuesta c : asig42) {
                        sb.append(csv(c.getRespuesta())).append(",").append(c.getTotal()).append(",").append(DF2.format(c.getPorcentaje())).append("\r\n");
                    }
                    sb.append("\r\n");
                    sb.append("=== CRUCE: ASIGNATURAS DE VENTAJA (p42) × SITUACIÓN LABORAL ACTUAL (p14) ===\r\n");
                    sb.append("ASIGNATURA,SITUACION_LABORAL,TOTAL\r\n");
                    for (CeldaCruce cc : EncuestaDAO.cruceAsignaturaVariable(42, 14)) {
                        sb.append(csv(cc.getValorX())).append(",").append(csv(cc.getValorY())).append(",").append(cc.getTotal()).append("\r\n");
                    }
                    sb.append("\r\n");

                    // Asignaturas de poca utilidad
                    List<ConteoRespuesta> asig44 = EncuestaDAO.asignaturasFrecuencia(44);
                    sb.append("=== ASIGNATURAS DE POCA UTILIDAD PROFESIONAL (p44) ===\r\n");
                    sb.append("ASIGNATURA,TOTAL,%_TITULADOS\r\n");
                    for (ConteoRespuesta c : asig44) {
                        sb.append(csv(c.getRespuesta())).append(",").append(c.getTotal()).append(",").append(DF2.format(c.getPorcentaje())).append("\r\n");
                    }
                    sb.append("\r\n");
                    sb.append("=== CRUCE: ASIGNATURAS DE POCA UTILIDAD (p44) × SITUACIÓN LABORAL ACTUAL (p14) ===\r\n");
                    sb.append("ASIGNATURA,SITUACION_LABORAL,TOTAL\r\n");
                    for (CeldaCruce cc : EncuestaDAO.cruceAsignaturaVariable(44, 14)) {
                        sb.append(csv(cc.getValorX())).append(",").append(csv(cc.getValorY())).append(",").append(cc.getTotal()).append("\r\n");
                    }
                    sb.append("\r\n");
                    sb.append("=== CRUCE: ASIGNATURAS DE POCA UTILIDAD (p44) × RAZÓN DE INACTIVIDAD LABORAL (p28) ===\r\n");
                    sb.append("ASIGNATURA,RAZON_INACTIVIDAD,TOTAL\r\n");
                    for (CeldaCruce cc : EncuestaDAO.cruceAsignaturaVariable(44, 28)) {
                        sb.append(csv(cc.getValorX())).append(",").append(csv(cc.getValorY())).append(",").append(cc.getTotal()).append("\r\n");
                    }
                }
                case "emprendimiento" -> {
                    @SuppressWarnings("unchecked")
                    List<ConteoRespuesta> filasEmp = EncuestaDAO.obtenerConteoEmprendimiento();
                    if (filasEmp == null) filasEmp = Collections.emptyList();
                    Map<Integer, List<ConteoRespuesta>> porPreg = new LinkedHashMap<>();
                    for (ConteoRespuesta f : filasEmp) {
                        porPreg.computeIfAbsent(f.getPreguntaId(), k -> new ArrayList<>()).add(f);
                    }
                    sb.append("=== ECOSISTEMA DE EMPRENDIMIENTO E INNOVACIÓN ===\r\n\r\n");
                    appendSeccionEmpCsv(sb, 30, "Origen del emprendimiento (p30)", porPreg);
                    appendSeccionEmpCsv(sb, 31, "Tipo de entregable (p31)", porPreg);
                    appendSeccionEmpCsv(sb, 32, "Financiamiento externo (p32)", porPreg);
                    appendSeccionEmpCsv(sb, 33, "Satisfacción con el rendimiento (p33)", porPreg);
                    appendSeccionEmpCsv(sb, 34, "Importancia de la formación (p34)", porPreg);
                    // Comparativo
                    List<ConteoRespuesta> d30 = porPreg.getOrDefault(30, new ArrayList<>());
                    ConteoRespuesta spin = null, start = null;
                    for (ConteoRespuesta cp : d30) {
                        String r = cp.getRespuesta() == null ? "" : cp.getRespuesta().toLowerCase();
                        if (r.contains("spin")) spin = cp;
                        if (r.contains("start")) start = cp;
                    }
                    sb.append("=== COMPARATIVO: SPIN-OFFS VS STARTUPS (p30) ===\r\n");
                    sb.append("CATEGORIA,TOTAL\r\n");
                    sb.append("Spin Off,").append(spin == null ? 0 : spin.getTotal()).append("\r\n");
                    sb.append("StartUp,").append(start == null ? 0 : start.getTotal()).append("\r\n");
                    sb.append("\r\n=== RESUMEN Y RECOMENDACIÓN ===\r\n");
                    for (String b : resumenEmprendimientoBullets(porPreg)) sb.append(csv(b)).append("\r\n");
                    sb.append("\r\nRECOMENDACIONES PARA EL PLAN DE DESARROLLO ESTRATÉGICO\r\n");
                    for (String r : resumenEmprendimientoRecomendaciones(porPreg)) sb.append(csv(r)).append("\r\n");
                }
                case "desempleo" -> {
                    sb.append("=== DESEMPLEO Y BARRERAS ===\r\n\r\n");
                    sb.append("=== MATRIZ DE FRECUENCIA CRUZADA: RAZONES PARA CONTINUAR DESEMPLEADO (p28) × EXPERIENCIA ANTERIOR (p29) ===\r\n");
                    sb.append("RAZON_PARA_CONTINUAR_DESEMPLEADO,EXPERIENCIA_ANTERIOR_EN_EL_SECTOR,TOTAL\r\n");
                    for (CeldaCruce cc : EncuestaDAO.obtenerCruce(28, 29)) {
                        sb.append(csv(cc.getValorX())).append(",").append(csv(cc.getValorY())).append(",").append(cc.getTotal()).append("\r\n");
                    }
                    sb.append("\r\n");
                    sb.append("=== MATRIZ DE FRECUENCIA CRUZADA: TIEMPO HASTA EL PRIMER EMPLEO (p36) × NÚMERO DE EMPLEOS (p37) ===\r\n");
                    sb.append("TIEMPO_HASTA_PRIMER_EMPLEO,CANTIDAD_DE_EMPLEOS_OBTENIDOS,TOTAL\r\n");
                    for (CeldaCruce cc : EncuestaDAO.obtenerCruce(36, 37)) {
                        sb.append(csv(cc.getValorX())).append(",").append(csv(cc.getValorY())).append(",").append(cc.getTotal()).append("\r\n");
                    }
                }
                case "formacion" -> {
                    sb.append("=== FORMACIÓN CONTINUA Y MECANISMOS DE FINANCIAMIENTO ===\r\n\r\n");

                    // Áreas interés 
                    sb.append("=== ÁREAS DE INTERÉS DE POSGRADO (p9) - DECLARADAS POR EL TITULADO ===\r\n");
                    sb.append("AREA_DE_INTERES,n,f_i(%)\r\n");
                    List<AreaInteresPosgrado> areasP9 = EncuestaDAO.areasInteresPosgrado();
                    int Np9 = 0;
                    for (AreaInteresPosgrado a : areasP9) Np9 += a.getN();
                    for (AreaInteresPosgrado a : areasP9) {
                        sb.append(csv(a.getArea())).append(",").append(a.getN()).append(",").append(DF2.format(a.getPorcentaje())).append("\r\n");
                    }
                    if (Np9 > 0) {
                        long top1 = areasP9.isEmpty() ? 0 : areasP9.get(0).getN();
                        long top2 = areasP9.size() < 2 ? top1 : top1 + areasP9.get(1).getN();
                        double pctTop2 = (top2 * 100.0 / (double) Np9);
                        sb.append("\r\nALERTA DE CONCENTRACIÓN CRÍTICA DE MERCADO: Suma Top 2 = ").append(top2).append("/").append(Np9).append(" (").append(DF2.format(pctTop2)).append("%) | Umbral ≥ 70%").append(pctTop2 >= 70.0 ? " → SÍ (CRÍTICA)" : " → NO").append("\r\n\r\n");
                    }

                    sb.append("=== NIVEL/TIPO DE POSGRADO DESEADO (p4) - TITULADOS ===\r\n");
                    sb.append("NIVEL_DESEADO,TOTAL,%\r\n");
                    List<ConteoRespuesta> nivP4 = EncuestaDAO.nivelesFormacionDeseados();
                    int Nn4 = 0;
                    for (ConteoRespuesta c : nivP4) Nn4 += c.getTotal();
                    for (ConteoRespuesta c : nivP4) {
                        double pct = Nn4 == 0 ? 0.0 : (c.getTotal() * 100.0 / Nn4);
                        sb.append(csv(c.getRespuesta())).append(",").append(c.getTotal()).append(",").append(DF2.format(pct)).append("\r\n");
                    }
                    sb.append("\r\n");

                    Map<String, Object> fin = EncuestaDAO.tablaFinanciamiento();
                    @SuppressWarnings("unchecked") List<String> filasF = (List<String>) fin.get("filas");
                    @SuppressWarnings("unchecked") List<String> colsF = (List<String>) fin.get("columnas");
                    @SuppressWarnings("unchecked") List<CeldaCruce> celdasF = (List<CeldaCruce>) fin.get("celdas");
                    sb.append("=== MATRIZ DE CONTINGENCIA: NIVEL DE POSGRADO DESEADO (p4) × FUENTE DE FINANCIAMIENTO (p6) ===\r\n");
                    sb.append("NIVEL/FUENTE_DE_FINANCIAMIENTO");
                    for (String c : colsF) sb.append(",").append(csv(c));
                    sb.append(",TOTAL_FILA\r\n");
                    Map<String, Integer> tfMap = new LinkedHashMap<>();
                    for (CeldaCruce cc : celdasF) tfMap.put(cc.getValorX(), tfMap.getOrDefault(cc.getValorX(), 0) + cc.getTotal());
                    for (String f : filasF) {
                        sb.append(csv(f));
                        for (String c : colsF) {
                            sb.append(",").append(getCelda(celdasF, f, c));
                        }
                        sb.append(",").append(tfMap.getOrDefault(f, 0)).append("\r\n");
                    }
                    sb.append("TOTAL_COLUMNA");
                    Map<String, Integer> tcMap = new LinkedHashMap<>();
                    for (CeldaCruce cc : celdasF) tcMap.put(cc.getValorY(), tcMap.getOrDefault(cc.getValorY(), 0) + cc.getTotal());
                    long NTotF = 0;
                    for (Integer v : tcMap.values()) NTotF += v;
                    for (String c : colsF) sb.append(",").append(tcMap.getOrDefault(c, 0));
                    sb.append(",").append(NTotF).append("\r\n\r\n");

                    // Chi2
                    ResultadoChi2 rchi = (ResultadoChi2) fin.get("chi2");
                    Boolean tieneDatosF = (Boolean) fin.get("tieneDatos");
                    if (rchi != null) {
                        sb.append("=== DIAGNÓSTICO ESTADÍSTICO (χ² de Independencia) ===\r\n");
                        sb.append("χ²,").append(rchi.getChiCuadrado()).append("\r\n");
                        sb.append("gl (grados de libertad),").append(rchi.getGradosLibertad()).append("\r\n");
                        sb.append("p-valor,").append(rchi.getValorP()).append("\r\n");
                        sb.append("Independiente (H0),").append(rchi.isIndependiente() ? "SÍ (p ≥ 0.05)" : "NO (p < 0.05)").append("\r\n");
                        sb.append("Interpretación,").append(csv(rchi.getInterpretacion())).append("\r\n");
                        sb.append("Datos suficientes para el test,").append((tieneDatosF != null && tieneDatosF) ? "SÍ" : "NO").append("\r\n");
                    }
                }
                case "empleabilidad" -> {
                    sb.append("=== EMPLEABILIDAD Y OFERTAS CON POCA DEMANDA ===\r\n\r\n");

                    // Empleabilidad
                    sb.append("=== ÁREAS CON MAYOR EMPLEABILIDAD (Demanda + Interés) ===\r\n");
                    sb.append("AREA,DEMANDA_EMPLEADOR(%),INTERES_TITULADOS(%),EMPLEABILIDAD(%),ORDEN\r\n");
                    List<AreaEmpleabilidad> aeList = EncuestaDAO.analisisEmpleabilidad();
                    int ord = 1;
                    for (AreaEmpleabilidad a : aeList) {
                        sb.append(csv(a.getArea())).append(",").append(a.getDemandaEmpleador()).append(",").append(a.getInteresTitulados()).append(",").append(a.getEmpleabilidad()).append(",").append(ord++).append("\r\n");
                    }
                    sb.append("\r\n");

                    // Razones críticas
                    sb.append("=== OFERTA CON POCA DEMANDA - RAZONES CRÍTICAS (p28) ===\r\n");
                    sb.append("RAZON,TOTAL,%\r\n");
                    List<ConteoRespuesta> crit = razonesCriticas();
                    int Ncrit = 0;
                    for (ConteoRespuesta c : crit) Ncrit += c.getTotal();
                    for (ConteoRespuesta c : crit) {
                        double pct = Ncrit == 0 ? 0.0 : (c.getTotal() * 100.0 / Ncrit);
                        sb.append(csv(c.getRespuesta())).append(",").append(c.getTotal()).append(",").append(DF2.format(pct)).append("\r\n");
                    }
                    sb.append("\r\n");

                    // Asignaturas poca utilidad 
                    sb.append("=== ASIGNATURAS CON POCA UTILIDAD EN EL CAMPO LABORAL (p44) - TOP ===\r\n");
                    sb.append("ASIGNATURA,TOTAL,%\r\n");
                    List<ConteoRespuesta> asig44 = asignaturasPocaTop(15);
                    int Na44 = 0;
                    for (ConteoRespuesta c : asig44) Na44 += c.getTotal();
                    for (ConteoRespuesta c : asig44) {
                        double pct = Na44 == 0 ? 0.0 : (c.getTotal() * 100.0 / Na44);
                        sb.append(csv(c.getRespuesta())).append(",").append(c.getTotal()).append(",").append(DF2.format(pct)).append("\r\n");
                    }
                    sb.append("\r\n");
                    sb.append("=== RUBROS DE INVERSIÓN / REQUERIMIENTOS (p53) ===\r\n");
                    sb.append("OPCIÓN,TOTAL,%\r\n");
                    List<ConteoRespuesta> p53 = EncuestaDAO.contarRespuestas(53);
                    int N53 = 0;
                    for (ConteoRespuesta c : p53) N53 += c.getTotal();
                    for (ConteoRespuesta c : p53) {
                        double pct = N53 == 0 ? 0.0 : (c.getTotal() * 100.0 / N53);
                        sb.append(csv(c.getRespuesta())).append(",").append(c.getTotal()).append(",").append(DF2.format(pct)).append("\r\n");
                    }
                    sb.append("\r\n");
                    sb.append("=== POSIBILIDAD DE CONTRATACIÓN DE TITULADOS (p55) ===\r\n");
                    sb.append("OPCIÓN,TOTAL,%\r\n");
                    List<ConteoRespuesta> p55 = EncuestaDAO.contarRespuestas(55);
                    int N55 = 0;
                    for (ConteoRespuesta c : p55) N55 += c.getTotal();
                    for (ConteoRespuesta c : p55) {
                        double pct = N55 == 0 ? 0.0 : (c.getTotal() * 100.0 / N55);
                        sb.append(csv(c.getRespuesta())).append(",").append(c.getTotal()).append(",").append(DF2.format(pct)).append("\r\n");
                    }
                }
                case "cruce" -> {
                    sb.append("=== CRUCE DE VARIABLES (MATRIZ DE FRECUENCIA CRUZADA) ===\r\n");
                    sb.append("NOTA: Este reporte se genera dinámicamente en la interfaz. Exportación tabular por defecto:\r\n\r\n");
                    sb.append("MATRIZ GENÉRICA (X × Y),VALOR_X,VALOR_Y,TOTAL\r\n");
                }
                case "ubicacion" -> {
                    sb.append("=== PERTINENCIA CURRICULAR, GEOGRAFÍA Y FUGA DE TALENTOS (HU11) ===\r\n\r\n");

                    // KPIs
                    int[] k = kpisUbicacion();
                    sb.append("=== INDICADORES CLAVE (p19: departamento de trabajo) ===\r\n");
                    sb.append("INDICADOR,VALOR,%\r\n");
                    sb.append("Titulados analizados,").append(k[0]).append(",").append(DF2.format(100.0)).append("\r\n");
                    sb.append(csv("Trabajan en Cochabamba")).append(",").append(k[1]).append(",").append(DF2.format(pctDe(k[1], k[0]))).append("\r\n");
                    sb.append(csv("Fuera del departamento base (fuga)")).append(",").append(k[2]).append(",").append(DF2.format(pctDe(k[2], k[0]))).append("\r\n");
                    sb.append(csv("En el extranjero")).append(",").append(k[3]).append(",").append(DF2.format(pctDe(k[3], k[0]))).append("\r\n");
                    sb.append("\r\n");

                    // Dónde trabajan
                    sb.append("=== ¿DÓNDE TRABAJAN LOS TITULADOS? (p19, opciones) ===\r\n");
                    sb.append("DEPARTAMENTO,TOTAL,%\r\n");
                    for (ConteoRespuesta c : AnalisisDAO.frecuencias(U_DEPTO, false, new FiltroEnvio())) {
                        sb.append(csv(c.getRespuesta())).append(",").append(c.getTotal()).append(",")
                          .append(DF2.format(c.getPorcentaje())).append("\r\n");
                    }
                    sb.append("\r\n");

                    // Promedio salario por departamento
                    appendPromedioCsv(sb, AnalisisDAO.promedioPorGrupo(U_SALARIO, U_DEPTO, new FiltroEnvio()),
                            "=== SALARIO PROMEDIO MENSUAL POR DEPARTAMENTO (p27 × p19) ===");

                    // Promedio pertinencia por cargo
                    appendPromedioCsv(sb, AnalisisDAO.promedioPorGrupo(U_PERT, U_CARGO, new FiltroEnvio()),
                            "=== PERTINENCIA PROMEDIO POR CARGO (p24 × p22) ===");

                    // Cruces con χ²
                    ResultadoChiCuadrado c1 = chi2auto(U_DEPTO, U_SALARIO);
                    sb.append("=== CRUCE: UBICACIÓN (p19) × NIVEL SALARIAL (p27) ===\r\n");
                    appendChiCsv(sb, c1);
                    appendChiMatrizCsv(sb, c1);

                    ResultadoChiCuadrado c2 = chi2auto(U_CARGO, U_PERT);
                    sb.append("=== CRUCE: CARGO (p22) × PERTINENCIA (p24) ===\r\n");
                    appendChiCsv(sb, c2);
                    appendChiMatrizCsv(sb, c2);

                    ResultadoChiCuadrado c3 = chi2auto(U_DEPTO, U_PERT);
                    sb.append("=== CRUCE: UBICACIÓN (p19) × PERTINENCIA (p24) ===\r\n");
                    appendChiCsv(sb, c3);
                    appendChiMatrizCsv(sb, c3);
                }
                case "hu12" -> {
                    sb.append("=== DEMANDA EMPRESARIAL Y RED ALUMNO (HU12) ===\r\n\r\n");
                    sb.append("=== GRUPO 1: DEMANDA EMPRESARIAL (encuesta 2) ===\r\n\r\n");
                    for (int pid : HU12_DEMANDA) appendHu12Csv(sb, pid);
                    sb.append("=== GRUPO 2: PERCEPCIÓN DE LA FORMACIÓN (encuesta 2) ===\r\n\r\n");
                    for (int pid : HU12_PERCEPCION) appendHu12Csv(sb, pid);

                    // Red alumno (resumen de consentimiento, p46)
                    Map<String, Object> red = ReporteHu12DAO.obteneralumno();
                    sb.append("=== GRUPO 3: RED ALUMNO - CONSENTIMIENTO DE CONTACTO (p46) ===\r\n");
                    sb.append("INDICADOR,VALOR\r\n");
                    sb.append("Total de titulados,").append(red.get("totalTitulados")).append("\r\n");
                    sb.append(csv("Aceptaron ser contactados (p46 = Sí)")).append(",").append(red.get("aceptaron")).append("\r\n");
                    sb.append("Con correo,").append(red.get("conCorreo")).append("\r\n");
                    sb.append("Con teléfono,").append(red.get("conTelefono")).append("\r\n");
                    sb.append("Con LinkedIn,").append(red.get("conLinkedin")).append("\r\n");
                    sb.append("\r\nNOTA: El listado de contactos (correo/teléfono/LinkedIn) solo se muestra en la interfaz a usuarios autorizados.\r\n");
                }
                default -> sb.append("TIPO DE REPORTE NO SOPORTADO: ").append(csv(tipo)).append("\r\n");
            }
        } catch (Exception e) {
            sb.append("\r\nERROR AL EXPORTAR: ").append(csv(e.getMessage())).append("\r\n");
        }
        return sb.toString();
    }

    private static void appendSeccionEmpCsv(StringBuilder sb, int pid, String titulo, Map<Integer, List<ConteoRespuesta>> porPreg) {
        List<ConteoRespuesta> lista = porPreg.getOrDefault(pid, new ArrayList<>());
        int total = 0;
        for (ConteoRespuesta cp : lista) total += cp.getTotal();
        sb.append("=== ").append(titulo).append(" ===\r\n");
        sb.append("RESPUESTA,TOTAL,%\r\n");
        for (ConteoRespuesta cp : lista) {
            double pct = total == 0 ? 0.0 : (cp.getTotal() * 100.0 / (double) total);
            sb.append(csv(cp.getRespuesta())).append(",").append(cp.getTotal()).append(",").append(DF2.format(pct)).append("\r\n");
        }
        sb.append("\r\n");
    }

    private static String csv(String s) {
        if (s == null) return "\"\"";
        String t = s.replace("\"", "\"\"");
        return "\"" + t + "\"";
    }

    private static int getCelda(List<CeldaCruce> celdas, String f, String c) {
        for (CeldaCruce cc : celdas) {
            if (Objects.equals(cc.getValorX(), f) && Objects.equals(cc.getValorY(), c)) return cc.getTotal();
        }
        return 0;
    }

    /** Razones críticas de poca demanda - misma lógica que ReporteController.reporteEmpleabilidad. */
    private static List<ConteoRespuesta> razonesCriticas() throws Exception {
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
        return criticas;
    }

    /** Asignaturas con poca utilidad, top N. */
    private static List<ConteoRespuesta> asignaturasPocaTop(int n) throws Exception {
        List<ConteoRespuesta> lista = EncuestaDAO.asignaturasFrecuencia(44);
        if (lista.size() > n) return new ArrayList<>(lista.subList(0, n));
        return lista;
    }

    // ===== HU11 (Pertinencia/Geografía/Fuga de Talentos) y HU12 (Demanda empresarial y Red alumno) =====

    /** Títulos de las preguntas de la HU12 (mismos que reporte-hu12.js). */
    private static final Map<Integer, String> HU12_TITULOS = new LinkedHashMap<>();
    static {
        HU12_TITULOS.put(54, "Contratación de ingenieros en los últimos 5 años");
        HU12_TITULOS.put(55, "Posibilidad de futuras contrataciones");
        HU12_TITULOS.put(56, "Formación académica requerida");
        HU12_TITULOS.put(57, "Medios de convocatoria");
        HU12_TITULOS.put(58, "Cargos desempeñados por los titulados");
        HU12_TITULOS.put(59, "Áreas de conocimiento y tendencias emergentes");
        HU12_TITULOS.put(60, "Herramientas tecnológicas requeridas");
        HU12_TITULOS.put(61, "Habilidades y competencias fundamentales");
        HU12_TITULOS.put(62, "Confianza en la formación profesional");
        HU12_TITULOS.put(63, "Consistencia del título con las competencias");
        HU12_TITULOS.put(64, "Consulta sobre el perfil profesional");
        HU12_TITULOS.put(65, "Conocimiento del perfil de egreso");
        HU12_TITULOS.put(66, "Coherencia del perfil con el desempeño laboral");
        HU12_TITULOS.put(67, "Formación y desempeño profesional integral");
        HU12_TITULOS.put(68, "Incorporación de necesidades del sector");
        HU12_TITULOS.put(69, "Comunicación, análisis y trabajo en equipo");
        HU12_TITULOS.put(70, "Ética, responsabilidad social e inclusión");
        HU12_TITULOS.put(71, "Participación en evaluación de titulados");
        HU12_TITULOS.put(72, "Vínculos con organizaciones y oportunidades laborales");
        HU12_TITULOS.put(73, "Actividades de vinculación laboral y tecnológica");
        HU12_TITULOS.put(74, "Difusión de oportunidades laborales y pasantías");
        HU12_TITULOS.put(75, "Encuentros entre empleadores y comunidad académica");
        HU12_TITULOS.put(76, "Retroalimentación sobre el desempeño profesional");
        HU12_TITULOS.put(77, "Consulta sobre la pertinencia de la formación");
        HU12_TITULOS.put(78, "La UMSS como fuente de talento profesional");
        HU12_TITULOS.put(79, "Participación en autoevaluación y mejora continua");
    }
    private static final List<Integer> HU12_DEMANDA = List.of(54, 55, 56, 57, 58, 59, 60, 61);
    private static final List<Integer> HU12_PERCEPCION = List.of(62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79);

    /** Preguntas clave del HU11 (encuesta de titulados). */
    private static final int U_DEPTO = 19, U_CARGO = 22, U_PERT = 24, U_SALARIO = 27;

    /** KPIs del HU11: {total, enCochabamba, fuga, enExtranjero} desde la p19 agrupada. */
    private static int[] kpisUbicacion() throws Exception {
        List<ConteoRespuesta> g = AnalisisDAO.frecuencias(U_DEPTO, true, new FiltroEnvio());
        int total = 0, base = 0, ext = 0;
        for (ConteoRespuesta c : g) {
            total += c.getTotal();
            if ("Cochabamba".equals(c.getRespuesta())) base = c.getTotal();
            if ("Extranjero".equals(c.getRespuesta())) ext = c.getTotal();
        }
        return new int[]{total, base, total - base, ext};
    }

    /** Prueba χ² con el mismo modo "auto" del AnalisisController (completa → agrupada si no cumple supuestos). */
    private static ResultadoChiCuadrado chi2auto(int x, int y) throws Exception {
        FiltroEnvio f = new FiltroEnvio();
        ResultadoChiCuadrado r = PruebaChiCuadrado.calcular(
                AnalisisDAO.contingencia(x, y, false, f),
                AnalisisDAO.ordenEtiquetas(x, false),
                AnalisisDAO.ordenEtiquetas(y, false)).conModo("COMPLETA", null);
        if (!r.cumpleSupuestos() && (AnalisisDAO.tieneAgrupacion(x) || AnalisisDAO.tieneAgrupacion(y))) {
            r = PruebaChiCuadrado.calcular(
                    AnalisisDAO.contingencia(x, y, true, f),
                    AnalisisDAO.ordenEtiquetas(x, true),
                    AnalisisDAO.ordenEtiquetas(y, true))
                    .conModo("AGRUPADA",
                            "Se aplicó la agrupación de categorías porque la tabla completa no cumplía los supuestos.");
        }
        return r;
    }

    private static String fmtPval(Double p) {
        if (p == null) return "no calculable";
        if (p < 0.0001) return "< 0.0001";
        return DF3.format(p);
    }

    /** Encabezado χ² (modo, N, gl, χ², crítico, p, decisión) como líneas clave,valor. */
    private static void appendChiCsv(StringBuilder sb, ResultadoChiCuadrado r) {
        sb.append("MODO,").append(csv(r.modoAplicado())).append("\r\n");
        sb.append("N,").append(r.n()).append("\r\n");
        sb.append("GRADOS_DE_LIBERTAD,").append(r.gradosLibertad()).append("\r\n");
        sb.append("CHI2,").append(r.chiCuadrado() == null ? "no calculable" : DF3.format(r.chiCuadrado())).append("\r\n");
        sb.append("VALOR_CRITICO,").append(r.valorCritico() == null ? "no calculable" : DF3.format(r.valorCritico())).append("\r\n");
        sb.append("P_VALOR,").append(fmtPval(r.pValor())).append("\r\n");
        sb.append("RECHAZA_INDEPENDENCIA,").append(r.rechazaIndependencia() == null ? "no calculable"
                : (r.rechazaIndependencia() ? "SÍ" : "NO")).append("\r\n");
        for (String a : r.advertencias()) sb.append("AVISO,").append(csv(a)).append("\r\n");
        sb.append("\r\n");
    }

    /** Matriz observada/esperada/contribución en formato largo (una fila por celda) + totales. */
    private static void appendChiMatrizCsv(StringBuilder sb, ResultadoChiCuadrado r) {
        sb.append("FILA,COLUMNA,OBSERVADA,ESPERADA,CONTRIBUCION\r\n");
        for (int i = 0; i < r.etiquetasFila().size(); i++)
            for (int j = 0; j < r.etiquetasColumna().size(); j++)
                sb.append(csv(r.etiquetasFila().get(i))).append(",")
                  .append(csv(r.etiquetasColumna().get(j))).append(",")
                  .append(r.observada()[i][j]).append(",")
                  .append(DF2.format(r.esperada()[i][j])).append(",")
                  .append(DF3.format(r.contribucion()[i][j])).append("\r\n");
        sb.append("\r\nTOTALES_FILA_OBSERVADA\r\n");
        for (int i = 0; i < r.etiquetasFila().size(); i++)
            sb.append(csv(r.etiquetasFila().get(i))).append(",").append(r.totalesFila()[i]).append("\r\n");
        sb.append("TOTALES_COLUMNA_OBSERVADA\r\n");
        for (int j = 0; j < r.etiquetasColumna().size(); j++)
            sb.append(csv(r.etiquetasColumna().get(j))).append(",").append(r.totalesColumna()[j]).append("\r\n");
        sb.append("\r\n");
    }

    /** Tabla de promedios por grupo (salario/pertinencia) en CSV. */
    private static void appendPromedioCsv(StringBuilder sb, ResultadoPromedio r, String cabecera) {
        sb.append(cabecera).append("\r\n");
        sb.append("GRUPO,n,SUMA,MEDIA,DESV_EST,MUESTRA_SUFICIENTE\r\n");
        for (ResumenGrupo g : r.grupos()) {
            sb.append(csv(g.grupo())).append(",").append(g.n()).append(",")
              .append(DF2.format(g.suma())).append(",")
              .append(g.media() == null ? "" : DF2.format(g.media())).append(",")
              .append(g.desviacion() == null ? "" : DF2.format(g.desviacion())).append(",")
              .append(g.muestraSuficiente() ? "SÍ" : "NO (n < 5)").append("\r\n");
        }
        sb.append("GENERAL,").append(r.n()).append(",").append(DF2.format(r.suma())).append(",")
          .append(r.mediaGlobal() == null ? "" : DF2.format(r.mediaGlobal())).append(",,\r\n\r\n");
    }

    /** Frecuencias de una pregunta HU12 (encuesta 2) en CSV. */
    private static void appendHu12Csv(StringBuilder sb, int pid) throws Exception {
        Map<String, Object> f = ReporteHu12DAO.obtenerFrecuencias(2, pid);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cats = (List<Map<String, Object>>) f.get("categorias");
        sb.append("=== P").append(pid).append(": ").append(csv(HU12_TITULOS.getOrDefault(pid, "")))
          .append(" (N=").append(f.get("total")).append(") ===\r\n");
        sb.append("RESPUESTA,TOTAL,%\r\n");
        if (cats != null) {
            for (Map<String, Object> c : cats) {
                sb.append(csv(String.valueOf(c.get("respuesta")))).append(",")
                  .append(c.get("total")).append(",")
                  .append(DF2.format(((Number) c.get("porcentaje")).doubleValue())).append("\r\n");
            }
        }
        sb.append("\r\n");
    }

    /** Bloque χ² para Excel: estadísticos + matriz larga. Devuelve la próxima fila libre. */
    private static int addChiExcel(Sheet sh, int ri, CellStyle hs, String titulo, ResultadoChiCuadrado r) {
        Row rt = sh.createRow(ri++);
        rt.createCell(0).setCellValue(titulo);
        styleRow(rt, hs);
        addKV(sh, ri++, "Modo", r.modoAplicado());
        addKV(sh, ri++, "N", r.n());
        addKV(sh, ri++, "Grados de libertad", r.gradosLibertad());
        addKV(sh, ri++, "χ²", r.chiCuadrado() == null ? "no calculable" : DF3.format(r.chiCuadrado()));
        addKV(sh, ri++, "Valor crítico", r.valorCritico() == null ? "no calculable" : DF3.format(r.valorCritico()));
        addKV(sh, ri++, "Valor p", fmtPval(r.pValor()));
        addKV(sh, ri++, "Rechaza independencia", r.rechazaIndependencia() == null ? "no calculable"
                : (r.rechazaIndependencia() ? "SÍ" : "NO"));
        for (String a : r.advertencias()) addKV(sh, ri++, "Aviso", a);
        Row rh = sh.createRow(ri++);
        rh.createCell(0).setCellValue("FILA");
        rh.createCell(1).setCellValue("COLUMNA");
        rh.createCell(2).setCellValue("OBSERVADA");
        rh.createCell(3).setCellValue("ESPERADA");
        rh.createCell(4).setCellValue("CONTRIBUCIÓN");
        styleRow(rh, hs);
        for (int i = 0; i < r.etiquetasFila().size(); i++)
            for (int j = 0; j < r.etiquetasColumna().size(); j++) {
                Row rr = sh.createRow(ri++);
                rr.createCell(0).setCellValue(r.etiquetasFila().get(i));
                rr.createCell(1).setCellValue(r.etiquetasColumna().get(j));
                rr.createCell(2).setCellValue(r.observada()[i][j]);
                rr.createCell(3).setCellValue(r.esperada()[i][j]);
                rr.createCell(4).setCellValue(r.contribucion()[i][j]);
            }
        return ri + 1;
    }

    /** Tabla de promedios por grupo para Excel. Devuelve la próxima fila libre. */
    private static int addPromedioExcel(Sheet sh, int ri, CellStyle hs, String titulo, ResultadoPromedio r) {
        Row rt = sh.createRow(ri++);
        rt.createCell(0).setCellValue(titulo);
        styleRow(rt, hs);
        Row rh = sh.createRow(ri++);
        rh.createCell(0).setCellValue("GRUPO");
        rh.createCell(1).setCellValue("n");
        rh.createCell(2).setCellValue("SUMA");
        rh.createCell(3).setCellValue("MEDIA");
        rh.createCell(4).setCellValue("DESV. EST.");
        rh.createCell(5).setCellValue("MUESTRA SUFICIENTE");
        styleRow(rh, hs);
        for (ResumenGrupo g : r.grupos()) {
            Row rr = sh.createRow(ri++);
            rr.createCell(0).setCellValue(g.grupo());
            rr.createCell(1).setCellValue(g.n());
            rr.createCell(2).setCellValue(g.suma());
            rr.createCell(3).setCellValue(g.media() == null ? 0.0 : g.media());
            rr.createCell(4).setCellValue(g.desviacion() == null ? 0.0 : g.desviacion());
            rr.createCell(5).setCellValue(g.muestraSuficiente() ? "SÍ" : "NO (n < 5)");
        }
        Row rg = sh.createRow(ri++);
        rg.createCell(0).setCellValue("GENERAL");
        rg.createCell(1).setCellValue(r.n());
        rg.createCell(2).setCellValue(r.suma());
        rg.createCell(3).setCellValue(r.mediaGlobal() == null ? 0.0 : r.mediaGlobal());
        styleRow(rg, hs);
        return ri + 1;
    }

    /** Bloque de una pregunta HU12 en Excel (encuesta 2). Devuelve la próxima fila libre. */
    private static int addHu12Block(Sheet sh, int ri, CellStyle hs, int pid) throws Exception {
        Map<String, Object> f = ReporteHu12DAO.obtenerFrecuencias(2, pid);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cats = (List<Map<String, Object>>) f.get("categorias");
        Row rt = sh.createRow(ri++);
        rt.createCell(0).setCellValue("P" + pid + ": " + HU12_TITULOS.getOrDefault(pid, "")
                + " (N=" + f.get("total") + ")");
        styleRow(rt, hs);
        Row rh = sh.createRow(ri++);
        rh.createCell(0).setCellValue("RESPUESTA");
        rh.createCell(1).setCellValue("TOTAL");
        rh.createCell(2).setCellValue("%");
        styleRow(rh, hs);
        if (cats != null) {
            for (Map<String, Object> c : cats) {
                Row rr = sh.createRow(ri++);
                rr.createCell(0).setCellValue(String.valueOf(c.get("respuesta")));
                rr.createCell(1).setCellValue(((Number) c.get("total")).intValue());
                rr.createCell(2).setCellValue(((Number) c.get("porcentaje")).doubleValue());
            }
        }
        return ri + 1;
    }

    /** Tabla χ² para PDF: estadísticos + matriz observada/esperada/contribución + totales. */
    private static void htmlChi(StringBuilder sb, ResultadoChiCuadrado r) {
        sb.append("<table><tr><th>MODO</th><th>N</th><th>GL</th><th class='num'>χ²</th><th class='num'>VALOR CRÍTICO</th><th class='num'>VALOR P</th><th>RECHAZA INDEPENDENCIA</th></tr>");
        sb.append("<tr><td>").append(esc(r.modoAplicado())).append("</td><td>").append(r.n())
          .append("</td><td>").append(r.gradosLibertad())
          .append("</td><td class='num'>").append(r.chiCuadrado() == null ? "no calculable" : DF3.format(r.chiCuadrado()))
          .append("</td><td class='num'>").append(r.valorCritico() == null ? "no calculable" : DF3.format(r.valorCritico()))
          .append("</td><td class='num'>").append(fmtPval(r.pValor()))
          .append("</td><td>").append(r.rechazaIndependencia() == null ? "no calculable"
                  : (r.rechazaIndependencia() ? "SÍ" : "NO")).append("</td></tr></table>");
        for (String a : r.advertencias())
            sb.append("<div class='alert'><strong>Aviso:</strong> ").append(esc(a)).append("</div>");
        sb.append("<h4>Frecuencias observadas (O)</h4>");
        htmlChiMatriz(sb, r, false);
        sb.append("<h4>Frecuencias esperadas (E)</h4>");
        htmlChiMatriz(sb, r, true);
    }

    private static void htmlChiMatriz(StringBuilder sb, ResultadoChiCuadrado r, boolean esperada) {
        sb.append("<table><tr><th>FILA \\ COLUMNA</th>");
        for (String c : r.etiquetasColumna()) sb.append("<th class='num'>").append(esc(c)).append("</th>");
        sb.append("<th class='num'>TOTAL</th></tr>");
        for (int i = 0; i < r.etiquetasFila().size(); i++) {
            sb.append("<tr><th>").append(esc(r.etiquetasFila().get(i))).append("</th>");
            for (int j = 0; j < r.etiquetasColumna().size(); j++)
                sb.append("<td class='num'>").append(esperada
                        ? DF2.format(r.esperada()[i][j]) : r.observada()[i][j]).append("</td>");
            sb.append("<td class='num fw-bold'>").append(r.totalesFila()[i]).append("</td></tr>");
        }
        sb.append("<tr class='table-secondary fw-bold'><th>Total</th>");
        for (int j = 0; j < r.etiquetasColumna().size(); j++)
            sb.append("<td class='num'>").append(r.totalesColumna()[j]).append("</td>");
        sb.append("<td class='num'>").append(r.n()).append("</td></tr></table>");
    }

    /** Tabla de promedios por grupo para PDF. */
    private static void htmlPromedio(StringBuilder sb, ResultadoPromedio r) {
        sb.append("<table><tr><th>GRUPO</th><th class='num'>n</th><th class='num'>SUMA (Σ)</th><th class='num'>MEDIA</th><th class='num'>DESV. EST.</th><th>OBSERVACIÓN</th></tr>");
        for (ResumenGrupo g : r.grupos()) {
            sb.append("<tr").append(g.muestraSuficiente() ? "" : " style='background:#fff3cd'").append("><td>")
              .append(esc(g.grupo())).append("</td><td class='num'>").append(g.n())
              .append("</td><td class='num'>").append(DF2.format(g.suma()))
              .append("</td><td class='num fw-bold'>").append(g.media() == null ? "—" : DF2.format(g.media()))
              .append("</td><td class='num'>").append(g.desviacion() == null ? "—" : DF2.format(g.desviacion()))
              .append("</td><td class='small'>").append(g.muestraSuficiente() ? "" : "n &lt; 5: poco confiable")
              .append(g.sinCodificar() > 0 ? " · " + g.sinCodificar() + " sin valor asignado" : "")
              .append("</td></tr>");
        }
        sb.append("<tr class='table-secondary fw-bold'><th>General</th><td class='num'>").append(r.n())
          .append("</td><td class='num'>").append(DF2.format(r.suma()))
          .append("</td><td class='num'>").append(r.mediaGlobal() == null ? "—" : DF2.format(r.mediaGlobal()))
          .append("</td><td></td><td></td></tr></table>");
    }

    /** Tabla de una pregunta HU12 para PDF (encuesta 2). */
    private static void htmlHu12(StringBuilder sb, int pid) throws Exception {
        Map<String, Object> f = ReporteHu12DAO.obtenerFrecuencias(2, pid);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cats = (List<Map<String, Object>>) f.get("categorias");
        sb.append("<h3>P").append(pid).append(": ").append(esc(HU12_TITULOS.getOrDefault(pid, "")))
          .append(" <span class='meta'>(N = ").append(f.get("total")).append(")</span></h3>");
        if (cats == null || cats.isEmpty()) {
            sb.append("<p class='meta'>Sin respuestas para esta pregunta.</p>");
            return;
        }
        sb.append("<table><tr><th>RESPUESTA</th><th class='num'>TOTAL</th><th class='num'>%</th></tr>");
        for (Map<String, Object> c : cats)
            sb.append("<tr><td>").append(esc(String.valueOf(c.get("respuesta")))).append("</td><td class='num'>")
              .append(c.get("total")).append("</td><td class='num'>")
              .append(DF2.format(((Number) c.get("porcentaje")).doubleValue())).append("</td></tr>");
        sb.append("</table>");
    }

    /** Gráfico de barras de una pregunta HU12 (top 10 para texto abierto, como en la interfaz). */
    private static byte[] hu12Chart(int pid) throws Exception {
        Map<String, Object> f = ReporteHu12DAO.obtenerFrecuencias(2, pid);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cats = new ArrayList<>((List<Map<String, Object>>) f.get("categorias"));
        if (cats.isEmpty()) return new byte[0];
        boolean textoAbierto = pid == 59 || pid == 60 || pid == 61;
        if (textoAbierto) {
            cats.sort((a, b) -> ((Number) b.get("total")).intValue() - ((Number) a.get("total")).intValue());
            if (cats.size() > 10) cats = new ArrayList<>(cats.subList(0, 10));
        }
        List<String> etiquetas = new ArrayList<>();
        List<Double> valores = new ArrayList<>();
        for (Map<String, Object> c : cats) {
            String et = String.valueOf(c.get("respuesta"));
            if (et.length() > 45) et = et.substring(0, 42) + "...";
            etiquetas.add(et);
            valores.add(((Number) c.get("total")).doubleValue());
        }
        return chartBar("P" + pid + ": " + HU12_TITULOS.getOrDefault(pid, ""), "Respuesta", "Total", etiquetas, valores, "Total");
    }

    // Resumen de Ecosistema de Emprendimiento e Innovación (mismo que la interfaz)
    private static double pctDe(long g, long t) { return t == 0 ? 0 : (g * 100.0 / t); }

    private static List<String> resumenEmprendimientoBullets(Map<Integer, List<ConteoRespuesta>> porPreg) {
        List<String> out = new ArrayList<>();
        List<ConteoRespuesta> d30 = porPreg.getOrDefault(30, new ArrayList<>());
        long t30 = 0; for (ConteoRespuesta c : d30) t30 += c.getTotal();
        long spin = 0, start = 0, idea = 0;
        for (ConteoRespuesta c : d30) {
            String r = c.getRespuesta() == null ? "" : c.getRespuesta().toLowerCase();
            if (r.contains("spin")) spin = c.getTotal();
            if (r.contains("start")) start = c.getTotal();
            if (r.contains("idea propia")) idea = c.getTotal();
        }
        List<ConteoRespuesta> d31 = porPreg.getOrDefault(31, new ArrayList<>());
        long t31 = 0; for (ConteoRespuesta c : d31) t31 += c.getTotal();
        ConteoRespuesta top31 = null;
        for (ConteoRespuesta c : d31) if (top31 == null || c.getTotal() > top31.getTotal()) top31 = c;

        List<ConteoRespuesta> d32 = porPreg.getOrDefault(32, new ArrayList<>());
        long t32 = 0, externo = 0;
        for (ConteoRespuesta c : d32) {
            t32 += c.getTotal();
            String r = c.getRespuesta() == null ? "" : c.getRespuesta().trim().toLowerCase();
            if (r.startsWith("si") || r.startsWith("sí")) externo += c.getTotal();
        }
        long propio = t32 - externo;

        List<ConteoRespuesta> d33 = porPreg.getOrDefault(33, new ArrayList<>());
        long t33 = 0, alta33 = 0;
        double sum33 = 0;
        for (ConteoRespuesta c : d33) {
            t33 += c.getTotal();
            int v = 0; try { v = Integer.parseInt(c.getRespuesta() == null ? "" : c.getRespuesta().trim()); } catch (Exception ignored) {}
            sum33 += v * c.getTotal();
            if (v >= 4) alta33 += c.getTotal();
        }
        double prom33 = t33 == 0 ? 0 : sum33 / t33;

        List<ConteoRespuesta> d34 = porPreg.getOrDefault(34, new ArrayList<>());
        long t34 = 0, alta34 = 0;
        for (ConteoRespuesta c : d34) {
            t34 += c.getTotal();
            int v = 0; try { v = Integer.parseInt(c.getRespuesta() == null ? "" : c.getRespuesta().trim()); } catch (Exception ignored) {}
            if (v >= 4) alta34 += c.getTotal();
        }

        if (t30 > 0) out.add("Universo analizado: " + t30 + " emprendimiento(s) registrados por los titulados.");
        if (t30 > 0) {
            StringBuilder sb = new StringBuilder("Origen: ");
            if (spin > 0) sb.append("Spin Off ").append(DF2.format(pctDe(spin, t30))).append("% · ");
            if (idea > 0) sb.append("Idea propia ").append(DF2.format(pctDe(idea, t30))).append("% · ");
            if (start > 0) sb.append("StartUp ").append(DF2.format(pctDe(start, t30))).append("%");
            String s = sb.toString().replaceAll(" · $", "");
            sb = new StringBuilder(s);
            sb.append(". ").append(spin >= start ? "Predomina la iniciativa derivada de proyectos universitarios (Spin Off)." : "Predomina la StartUp.");
            out.add(sb.toString());
        }
        if (top31 != null) out.add("Tipo de entregable predominante: " + top31.getRespuesta() + " (" + top31.getTotal() + " de " + t31 + ", " + DF2.format(pctDe(top31.getTotal(), t31)) + "%).");
        if (t32 > 0) out.add("Financiamiento externo: " + DF2.format(pctDe(externo, t32)) + "% accedió a financiamiento (inversores/banca) y " + DF2.format(pctDe(propio, t32)) + "% trabajó con recursos propios.");
        if (t33 > 0) out.add("Satisfacción con el rendimiento: promedio " + DF2.format(prom33) + "/5; " + DF2.format(pctDe(alta33, t33)) + "% calificó con 4 o 5.");
        if (t34 > 0) out.add("Importancia de la formación: " + DF2.format(pctDe(alta34, t34)) + "% la valoró con 4 o 5, lo que respalda el fortalecimiento de competencias emprendedoras.");
        return out;
    }

    private static List<String> resumenEmprendimientoRecomendaciones(Map<Integer, List<ConteoRespuesta>> porPreg) {
        List<String> out = new ArrayList<>();
        List<ConteoRespuesta> d30 = porPreg.getOrDefault(30, new ArrayList<>());
        boolean haySpin = false, hayStart = false;
        for (ConteoRespuesta c : d30) {
            String r = c.getRespuesta() == null ? "" : c.getRespuesta().toLowerCase();
            if (r.contains("spin")) haySpin = true;
            if (r.contains("start")) hayStart = true;
        }
        List<ConteoRespuesta> d32 = porPreg.getOrDefault(32, new ArrayList<>());
        long t32 = 0, externo = 0;
        for (ConteoRespuesta c : d32) {
            t32 += c.getTotal();
            String r = c.getRespuesta() == null ? "" : c.getRespuesta().trim().toLowerCase();
            if (r.startsWith("si") || r.startsWith("sí")) externo += c.getTotal();
        }
        List<ConteoRespuesta> d33 = porPreg.getOrDefault(33, new ArrayList<>());
        long t33 = 0, alta33 = 0;
        for (ConteoRespuesta c : d33) {
            t33 += c.getTotal();
            int v = 0; try { v = Integer.parseInt(c.getRespuesta() == null ? "" : c.getRespuesta().trim()); } catch (Exception ignored) {}
            if (v >= 4) alta33 += c.getTotal();
        }
        boolean hay34 = !porPreg.getOrDefault(34, new ArrayList<>()).isEmpty();

        if (t32 > 0 && (t32 - externo) > externo) {
            out.add("Ampliar el acceso a fuentes de financiamiento externo (inversores ángeles, fondos y banca) ya que la mayoría depende de recursos propios.");
        }
        if (haySpin) out.add("Consolidar la vinculación universidad–emprendimiento para escalar los Spin Off surgidos de proyectos de la carrera.");
        if (hayStart) out.add("Reforzar el acompañamiento (mentoría e incubación) a las StartUps en etapas tempranas.");
        if (t33 > 0 && alta33 * 2 < t33) out.add("Atender los casos de baja satisfacción con el rendimiento mediante seguimiento individualizado.");
        if (hay34) out.add("Incorporar formación práctica en emprendimiento e innovación en el plan de estudios.");
        return out;
    }

    // EXCEL

    public static void exportExcel(Context ctx) throws IOException {
        String tipo = ctx.queryParam("tipo");
        if (tipo == null) tipo = "brechas";
        Workbook wb = generarExcel(tipo);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        wb.write(baos);
        wb.close();
        String filename = "reporte_" + tipo + "_" + System.currentTimeMillis() + ".xlsx";
        ctx.res().setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        ctx.res().setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        ctx.result(new java.io.ByteArrayInputStream(baos.toByteArray()));
    }

    private static Workbook generarExcel(String tipo) throws IOException {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("Reporte_" + tipo.toUpperCase());
        int rowIdx = 0;

        // Estilos
        CellStyle titleStyle = wb.createCellStyle();
        Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 12);
        titleStyle.setFont(titleFont);

        CellStyle headerStyle = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        CellStyle wrapStyle = wb.createCellStyle();
        wrapStyle.setWrapText(true);

        // Encabezado
        Row rh0 = sh.createRow(rowIdx++);
        Cell ch0 = rh0.createCell(0);
        ch0.setCellValue("REPORTE UMSS - " + tipo.toUpperCase());
        ch0.setCellStyle(titleStyle);
        rh0.createCell(1).setCellValue(DF.format(new Date()));

        try {
            switch (tipo) {
                case "brechas" -> {
                    rowIdx++;
                    // Radar
                    Row rH = sh.createRow(rowIdx++);
                    rH.createCell(0).setCellValue("DIMENSIÓN");
                    rH.createCell(1).setCellValue("OBJETIVO(%)");
                    rH.createCell(2).setCellValue("ACTUAL(%)");
                    rH.createCell(3).setCellValue("BRECHA(%)");
                    styleRow(rH, headerStyle);
                    for (DimensionRadar d : EncuestaDAO.radarBrechas()) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(d.getDimension());
                        rr.createCell(1).setCellValue(d.getObjetivo());
                        rr.createCell(2).setCellValue(d.getActual());
                        rr.createCell(3).setCellValue(d.getBrecha());
                    }
                    rowIdx++;

                    Row rhA = sh.createRow(rowIdx++);
                    rhA.createCell(0).setCellValue("ASIGNATURA (p42)");
                    rhA.createCell(1).setCellValue("TOTAL");
                    rhA.createCell(2).setCellValue("% TITULADOS");
                    styleRow(rhA, headerStyle);
                    List<ConteoRespuesta> asig42 = EncuestaDAO.asignaturasFrecuencia(42);
                    for (ConteoRespuesta c : asig42) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(c.getRespuesta());
                        rr.createCell(1).setCellValue(c.getTotal());
                        rr.createCell(2).setCellValue(c.getPorcentaje());
                    }
                    rowIdx++;
                    // Cruce
                    Row rhC = sh.createRow(rowIdx++);
                    rhC.createCell(0).setCellValue("ASIGNATURA (p42)");
                    rhC.createCell(1).setCellValue("SITUACIÓN LABORAL (p14)");
                    rhC.createCell(2).setCellValue("TOTAL");
                    styleRow(rhC, headerStyle);
                    for (CeldaCruce cc : EncuestaDAO.cruceAsignaturaVariable(42, 14)) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(cc.getValorX());
                        rr.createCell(1).setCellValue(cc.getValorY());
                        rr.createCell(2).setCellValue(cc.getTotal());
                    }
                    rowIdx++;

                    // Asignaturas de poca utilidad (p44)
                    Row rhB = sh.createRow(rowIdx++);
                    rhB.createCell(0).setCellValue("ASIGNATURA (p44) - POCA UTILIDAD");
                    rhB.createCell(1).setCellValue("TOTAL");
                    rhB.createCell(2).setCellValue("% TITULADOS");
                    styleRow(rhB, headerStyle);
                    List<ConteoRespuesta> asig44 = EncuestaDAO.asignaturasFrecuencia(44);
                    for (ConteoRespuesta c : asig44) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(c.getRespuesta());
                        rr.createCell(1).setCellValue(c.getTotal());
                        rr.createCell(2).setCellValue(c.getPorcentaje());
                    }
                    rowIdx++;
                    rowIdx = escribirCruceExcel(sh, rowIdx, headerStyle,
                            "ASIGNATURA (p44) × SITUACIÓN LABORAL (p14)", EncuestaDAO.cruceAsignaturaVariable(44, 14));
                    rowIdx = escribirCruceExcel(sh, rowIdx, headerStyle,
                            "ASIGNATURA (p44) × RAZÓN DE INACTIVIDAD (p28)", EncuestaDAO.cruceAsignaturaVariable(44, 28));
                }
                case "emprendimiento" -> {
                    @SuppressWarnings("unchecked")
                    List<ConteoRespuesta> filasEmp = EncuestaDAO.obtenerConteoEmprendimiento();
                    if (filasEmp == null) filasEmp = Collections.emptyList();
                    Map<Integer, List<ConteoRespuesta>> porPreg = new LinkedHashMap<>();
                    for (ConteoRespuesta f : filasEmp) porPreg.computeIfAbsent(f.getPreguntaId(), k -> new ArrayList<>()).add(f);
                    rowIdx++;
                    addEmpSheetBlock(sh, rowIdx, 30, "Origen del emprendimiento (p30)", porPreg, headerStyle);
                    rowIdx += countBlock(porPreg.getOrDefault(30, new ArrayList<>())) + 2;
                    addEmpSheetBlock(sh, rowIdx, 31, "Tipo de entregable (p31)", porPreg, headerStyle);
                    rowIdx += countBlock(porPreg.getOrDefault(31, new ArrayList<>())) + 2;
                    addEmpSheetBlock(sh, rowIdx, 32, "Financiamiento externo (p32)", porPreg, headerStyle);
                    rowIdx += countBlock(porPreg.getOrDefault(32, new ArrayList<>())) + 2;
                    addEmpSheetBlock(sh, rowIdx, 33, "Satisfacción con el rendimiento (p33)", porPreg, headerStyle);
                    rowIdx += countBlock(porPreg.getOrDefault(33, new ArrayList<>())) + 2;
                    addEmpSheetBlock(sh, rowIdx, 34, "Importancia de la formación (p34)", porPreg, headerStyle);
                    rowIdx += countBlock(porPreg.getOrDefault(34, new ArrayList<>())) + 2;
                    // Comparativo
                    List<ConteoRespuesta> d30 = porPreg.getOrDefault(30, new ArrayList<>());
                    ConteoRespuesta spin = null, start = null;
                    for (ConteoRespuesta cp : d30) {
                        String r = cp.getRespuesta() == null ? "" : cp.getRespuesta().toLowerCase();
                        if (r.contains("spin")) spin = cp;
                        if (r.contains("start")) start = cp;
                    }
                    Row rhComp = sh.createRow(rowIdx++);
                    rhComp.createCell(0).setCellValue("COMPARATIVO: SPIN-OFFS VS STARTUPS (p30)");
                    styleRow(rhComp, headerStyle);
                    Row rhc2 = sh.createRow(rowIdx++);
                    rhc2.createCell(0).setCellValue("CATEGORÍA");
                    rhc2.createCell(1).setCellValue("TOTAL");
                    styleRow(rhc2, headerStyle);
                    Row r1c = sh.createRow(rowIdx++);
                    r1c.createCell(0).setCellValue("Spin Off");
                    r1c.createCell(1).setCellValue(spin == null ? 0 : spin.getTotal());
                    Row r2c = sh.createRow(rowIdx++);
                    r2c.createCell(0).setCellValue("StartUp");
                    r2c.createCell(1).setCellValue(start == null ? 0 : start.getTotal());
                    rowIdx++;
                    // Resumen y recomendaciones
                    Row rhRes = sh.createRow(rowIdx++);
                    rhRes.createCell(0).setCellValue("RESUMEN Y RECOMENDACIÓN");
                    styleRow(rhRes, headerStyle);
                    for (String b : resumenEmprendimientoBullets(porPreg)) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(b);
                    }
                    Row rhRec = sh.createRow(rowIdx++);
                    rhRec.createCell(0).setCellValue("RECOMENDACIONES PARA EL PLAN DE DESARROLLO ESTRATÉGICO");
                    styleRow(rhRec, headerStyle);
                    for (String r : resumenEmprendimientoRecomendaciones(porPreg)) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(r);
                    }
                }
                case "desempleo" -> {
                    rowIdx++;
                    Row rh1 = sh.createRow(rowIdx++);
                    rh1.createCell(0).setCellValue("RAZÓN PARA CONTINUAR DESEMPLEADO (p28)");
                    rh1.createCell(1).setCellValue("EXPERIENCIA ANTERIOR (p29)");
                    rh1.createCell(2).setCellValue("TOTAL");
                    styleRow(rh1, headerStyle);
                    for (CeldaCruce cc : EncuestaDAO.obtenerCruce(28, 29)) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(cc.getValorX());
                        rr.createCell(1).setCellValue(cc.getValorY());
                        rr.createCell(2).setCellValue(cc.getTotal());
                    }
                    rowIdx++;
                    Row rh2 = sh.createRow(rowIdx++);
                    rh2.createCell(0).setCellValue("TIEMPO HASTA PRIMER EMPLEO (p36)");
                    rh2.createCell(1).setCellValue("NÚMERO DE EMPLEOS (p37)");
                    rh2.createCell(2).setCellValue("TOTAL");
                    styleRow(rh2, headerStyle);
                    for (CeldaCruce cc : EncuestaDAO.obtenerCruce(36, 37)) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(cc.getValorX());
                        rr.createCell(1).setCellValue(cc.getValorY());
                        rr.createCell(2).setCellValue(cc.getTotal());
                    }
                }
                case "formacion" -> {
                    rowIdx++;
                    Row rhp9 = sh.createRow(rowIdx++);
                    rhp9.createCell(0).setCellValue("ÁREA DE INTERÉS DE POSGRADO (p9)");
                    rhp9.createCell(1).setCellValue("n");
                    rhp9.createCell(2).setCellValue("f_i (%)");
                    styleRow(rhp9, headerStyle);
                    List<AreaInteresPosgrado> ap9 = EncuestaDAO.areasInteresPosgrado();
                    for (AreaInteresPosgrado a : ap9) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(a.getArea());
                        rr.createCell(1).setCellValue(a.getN());
                        rr.createCell(2).setCellValue(a.getPorcentaje());
                    }
                    rowIdx++;
                    // p4
                    Row rhp4 = sh.createRow(rowIdx++);
                    rhp4.createCell(0).setCellValue("NIVEL DE POSGRADO DESEADO (p4)");
                    rhp4.createCell(1).setCellValue("TOTAL");
                    rhp4.createCell(2).setCellValue("%");
                    styleRow(rhp4, headerStyle);
                    List<ConteoRespuesta> np4 = EncuestaDAO.nivelesFormacionDeseados();
                    int tn4 = 0;
                    for (ConteoRespuesta c : np4) tn4 += c.getTotal();
                    for (ConteoRespuesta c : np4) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(c.getRespuesta());
                        rr.createCell(1).setCellValue(c.getTotal());
                        rr.createCell(2).setCellValue(tn4 == 0 ? 0.0 : (c.getTotal() * 100.0 / tn4));
                    }
                    rowIdx++;
                    // matriz
                    Map<String, Object> fin = EncuestaDAO.tablaFinanciamiento();
                    @SuppressWarnings("unchecked") List<String> filasF = (List<String>) fin.get("filas");
                    @SuppressWarnings("unchecked") List<String> colsF = (List<String>) fin.get("columnas");
                    @SuppressWarnings("unchecked") List<CeldaCruce> celdasF = (List<CeldaCruce>) fin.get("celdas");
                    Row rhm = sh.createRow(rowIdx++);
                    rhm.createCell(0).setCellValue("NIVEL (p4) / FUENTE FINANCIAMIENTO (p6)");
                    int kc = 1;
                    for (String c : colsF) rhm.createCell(kc++).setCellValue(c);
                    rhm.createCell(kc).setCellValue("TOTAL_FILA");
                    styleRow(rhm, headerStyle);
                    for (String f : filasF) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(f);
                        int kk = 1;
                        for (String c : colsF) rr.createCell(kk++).setCellValue(getCelda(celdasF, f, c));
                        rr.createCell(kk).setCellValue(getTotalFila(celdasF, f));
                    }
                    Row rtot = sh.createRow(rowIdx++);
                    rtot.createCell(0).setCellValue("TOTAL_COLUMNA");
                    int kkt = 1;
                    long NTf = getTotalGeneral(celdasF);
                    for (String c : colsF) rtot.createCell(kkt++).setCellValue(getTotalCol(celdasF, c));
                    rtot.createCell(kkt).setCellValue(NTf);
                    styleRow(rtot, headerStyle);
                    rowIdx++;
                    // chi2
                    ResultadoChi2 rchi = (ResultadoChi2) fin.get("chi2");
                    Boolean tieneF = (Boolean) fin.get("tieneDatos");
                    Row rhc = sh.createRow(rowIdx++);
                    rhc.createCell(0).setCellValue("DIAGNÓSTICO χ²");
                    styleRow(rhc, headerStyle);
                    if (rchi != null) {
                        addKV(sh, rowIdx++, "χ²", rchi.getChiCuadrado());
                        addKV(sh, rowIdx++, "gl", rchi.getGradosLibertad());
                        addKV(sh, rowIdx++, "p-valor", rchi.getValorP());
                        addKV(sh, rowIdx++, "Independiente (p≥0.05)", rchi.isIndependiente() ? "SÍ" : "NO");
                        addKV(sh, rowIdx++, "Interpretación", rchi.getInterpretacion());
                        addKV(sh, rowIdx++, "Datos suficientes", (tieneF != null && tieneF) ? "SÍ" : "NO");
                    }
                }
                case "empleabilidad" -> {
                    rowIdx++;
                    // AE
                    Row rhAe = sh.createRow(rowIdx++);
                    rhAe.createCell(0).setCellValue("ÁREA");
                    rhAe.createCell(1).setCellValue("DEMANDA_EMPLEADOR(%)");
                    rhAe.createCell(2).setCellValue("INTERÉS_TITULADOS(%)");
                    rhAe.createCell(3).setCellValue("EMPLEABILIDAD(%)");
                    styleRow(rhAe, headerStyle);
                    List<AreaEmpleabilidad> aeL = EncuestaDAO.analisisEmpleabilidad();
                    for (AreaEmpleabilidad a : aeL) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(a.getArea());
                        rr.createCell(1).setCellValue(a.getDemandaEmpleador());
                        rr.createCell(2).setCellValue(a.getInteresTitulados());
                        rr.createCell(3).setCellValue(a.getEmpleabilidad());
                    }
                    rowIdx++;
                    // críticas
                    addSimpleSection(sh, rowIdx, "RAZONES CRÍTICAS (p28)", razonesCriticas(), headerStyle);
                    rowIdx += getSimpleCount(razonesCriticas()) + 2;
                    addSimpleSection(sh, rowIdx, "ASIGNATURAS POCA UTILIDAD (p44) - TOP", asignaturasPocaTop(20), headerStyle);
                    rowIdx += getSimpleCount(asignaturasPocaTop(20)) + 2;
                    addSimpleSection(sh, rowIdx, "RUBROS DE INVERSIÓN (p53)", EncuestaDAO.contarRespuestas(53), headerStyle);
                    rowIdx += getSimpleCount(EncuestaDAO.contarRespuestas(53)) + 2;
                    addSimpleSection(sh, rowIdx, "POSIBILIDAD DE CONTRATACIÓN (p55)", EncuestaDAO.contarRespuestas(55), headerStyle);
                }
                case "cruce" -> {
                    Row rr = sh.createRow(rowIdx++);
                    rr.createCell(0).setCellValue("Reporte de cruce dinámico. Use la interfaz para generar matrices específicas.");
                }
                case "ubicacion" -> {
                    rowIdx++;
                    // KPIs
                    int[] k = kpisUbicacion();
                    Row rhK = sh.createRow(rowIdx++);
                    rhK.createCell(0).setCellValue("INDICADOR");
                    rhK.createCell(1).setCellValue("VALOR");
                    rhK.createCell(2).setCellValue("%");
                    styleRow(rhK, headerStyle);
                    Row r0 = sh.createRow(rowIdx++);
                    r0.createCell(0).setCellValue("Titulados analizados");
                    r0.createCell(1).setCellValue(k[0]);
                    r0.createCell(2).setCellValue(100.0);
                    Row r1 = sh.createRow(rowIdx++);
                    r1.createCell(0).setCellValue("Trabajan en Cochabamba");
                    r1.createCell(1).setCellValue(k[1]);
                    r1.createCell(2).setCellValue(pctDe(k[1], k[0]));
                    Row r2 = sh.createRow(rowIdx++);
                    r2.createCell(0).setCellValue("Fuera del departamento base (fuga)");
                    r2.createCell(1).setCellValue(k[2]);
                    r2.createCell(2).setCellValue(pctDe(k[2], k[0]));
                    Row r3 = sh.createRow(rowIdx++);
                    r3.createCell(0).setCellValue("En el extranjero");
                    r3.createCell(1).setCellValue(k[3]);
                    r3.createCell(2).setCellValue(pctDe(k[3], k[0]));
                    rowIdx++;

                    // Dónde trabajan
                    Row rhD = sh.createRow(rowIdx++);
                    rhD.createCell(0).setCellValue("¿DÓNDE TRABAJAN LOS TITULADOS? (p19)");
                    rhD.createCell(1).setCellValue("TOTAL");
                    rhD.createCell(2).setCellValue("%");
                    styleRow(rhD, headerStyle);
                    for (ConteoRespuesta c : AnalisisDAO.frecuencias(U_DEPTO, false, new FiltroEnvio())) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(c.getRespuesta());
                        rr.createCell(1).setCellValue(c.getTotal());
                        rr.createCell(2).setCellValue(c.getPorcentaje());
                    }
                    rowIdx++;

                    // Promedios
                    rowIdx = addPromedioExcel(sh, rowIdx, headerStyle,
                            "SALARIO PROMEDIO MENSUAL POR DEPARTAMENTO (p27 × p19)",
                            AnalisisDAO.promedioPorGrupo(U_SALARIO, U_DEPTO, new FiltroEnvio())) + 1;
                    rowIdx = addPromedioExcel(sh, rowIdx, headerStyle,
                            "PERTINENCIA PROMEDIO POR CARGO (p24 × p22)",
                            AnalisisDAO.promedioPorGrupo(U_PERT, U_CARGO, new FiltroEnvio())) + 1;

                    // Cruces χ²
                    rowIdx = addChiExcel(sh, rowIdx, headerStyle,
                            "CRUCE: UBICACIÓN (p19) × NIVEL SALARIAL (p27)", chi2auto(U_DEPTO, U_SALARIO)) + 1;
                    rowIdx = addChiExcel(sh, rowIdx, headerStyle,
                            "CRUCE: CARGO (p22) × PERTINENCIA (p24)", chi2auto(U_CARGO, U_PERT)) + 1;
                    rowIdx = addChiExcel(sh, rowIdx, headerStyle,
                            "CRUCE: UBICACIÓN (p19) × PERTINENCIA (p24)", chi2auto(U_DEPTO, U_PERT)) + 1;
                }
                case "hu12" -> {
                    rowIdx++;
                    Row rh1 = sh.createRow(rowIdx++);
                    rh1.createCell(0).setCellValue("GRUPO 1: DEMANDA EMPRESARIAL (encuesta 2)");
                    styleRow(rh1, headerStyle);
                    for (int pid : HU12_DEMANDA) rowIdx = addHu12Block(sh, rowIdx, headerStyle, pid) + 1;
                    Row rh2 = sh.createRow(rowIdx++);
                    rh2.createCell(0).setCellValue("GRUPO 2: PERCEPCIÓN DE LA FORMACIÓN (encuesta 2)");
                    styleRow(rh2, headerStyle);
                    for (int pid : HU12_PERCEPCION) rowIdx = addHu12Block(sh, rowIdx, headerStyle, pid) + 1;

                    // Red alumno
                    Map<String, Object> red = ReporteHu12DAO.obteneralumno();
                    Row rh3 = sh.createRow(rowIdx++);
                    rh3.createCell(0).setCellValue("GRUPO 3: RED ALUMNO - CONSENTIMIENTO DE CONTACTO (p46)");
                    styleRow(rh3, headerStyle);
                    addKV(sh, rowIdx++, "Total de titulados", red.get("totalTitulados"));
                    addKV(sh, rowIdx++, "Aceptaron ser contactados (p46 = Sí)", red.get("aceptaron"));
                    addKV(sh, rowIdx++, "Con correo", red.get("conCorreo"));
                    addKV(sh, rowIdx++, "Con teléfono", red.get("conTelefono"));
                    addKV(sh, rowIdx++, "Con LinkedIn", red.get("conLinkedin"));
                    Row rNot = sh.createRow(rowIdx++);
                    rNot.createCell(0).setCellValue("NOTA: El listado de contactos solo se muestra en la interfaz a usuarios autorizados.");
                }
                default -> {
                    Row rr = sh.createRow(rowIdx++);
                    rr.createCell(0).setCellValue("TIPO NO IMPLEMENTADO: " + tipo);
                }
            }
        } catch (Exception e) {
            Row rr = sh.createRow(rowIdx++);
            rr.createCell(0).setCellValue("ERROR: " + e.getMessage());
        }

        // Gráficos embebidos (según el reporte) 
        try { agregarGraficosExcel(wb, sh, tipo); } catch (Exception ignored) {}

        // AutoSize
        for (int i = 0; i < 20; i++) {
            try { sh.autoSizeColumn(i); } catch (Exception ignored) {}
        }
        return wb;
    }

    /** Devuelve los primeros n elementos de una lista. */
    private static List<ConteoRespuesta> list(List<ConteoRespuesta> l) { return l == null ? new ArrayList<>() : l; }

    private static void agregarGraficosExcel(Workbook wb, Sheet sh, String tipo) throws Exception {
        final int COL = 8;          // columna I (deja libres las columnas de datos)
        final int ANCHO_COLS = 6;   // columnas que abarca el gráfico
        int row = 1;

        switch (tipo) {
            case "brechas" -> {
                List<DimensionRadar> radar = EncuestaDAO.radarBrechas();
                List<String> cats = new ArrayList<>();
                List<Double> obj = new ArrayList<>();
                List<Double> act = new ArrayList<>();
                for (DimensionRadar d : radar) { cats.add(d.getDimension()); obj.add(d.getObjetivo()); act.add(d.getActual()); }
                insertarImagen(wb, sh, chartRadar("Brechas: objetivo (empleador) vs actual (titulado)", cats, Arrays.asList("Objetivo", "Actual"), Arrays.asList(obj, act)), COL, row, COL + ANCHO_COLS, row + 25);
                row += 26;

                List<ConteoRespuesta> asig = list(EncuestaDAO.asignaturasFrecuencia(42));
                if (asig.size() > 10) asig = asig.subList(0, 10);
                insertarImagen(wb, sh, chartBar("Asignaturas de mayor ventaja (p42)", "Asignatura", "Frecuencia",
                        asig.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                        asig.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()), "Total"),
                        COL, row, COL + ANCHO_COLS, row + 23);
                row += 24;

                List<ConteoRespuesta> asig44 = list(EncuestaDAO.asignaturasFrecuencia(44));
                if (asig44.size() > 10) asig44 = asig44.subList(0, 10);
                insertarImagen(wb, sh, chartBar("Asignaturas de poca utilidad (p44)", "Asignatura", "Frecuencia",
                        asig44.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                        asig44.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()), "Total"),
                        COL, row, COL + ANCHO_COLS, row + 23);
            }
            case "emprendimiento" -> {
                List<ConteoRespuesta> filas = list(EncuestaDAO.obtenerConteoEmprendimiento());
                Map<Integer, List<ConteoRespuesta>> porPreg = new LinkedHashMap<>();
                for (ConteoRespuesta f : filas) porPreg.computeIfAbsent(f.getPreguntaId(), k -> new ArrayList<>()).add(f);
                int[][] meta = { {30, 0}, {31, 0}, {32, 0}, {33, 1}, {34, 1} }; // 0=pie, 1=bar
                String[] titulos = {
                        "Origen del emprendimiento (p30)",
                        "Tipo de entregable (p31)",
                        "Financiamiento externo (p32)",
                        "Satisfacción con el rendimiento (p33)",
                        "Importancia de la formación (p34)"
                };
                for (int k = 0; k < 5; k++) {
                    List<ConteoRespuesta> lista = list(porPreg.getOrDefault(new int[]{30,31,32,33,34}[k], new ArrayList<>()));
                    List<String> c = lista.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList());
                    List<Double> v = lista.stream().map(x -> (double) x.getTotal()).collect(java.util.stream.Collectors.toList());
                    byte[] png = chartPie(titulos[k], c, v);
                    insertarImagen(wb, sh, png, COL, row, COL + ANCHO_COLS, row + 22);
                    row += 23;
                }
                // comparativo
                List<ConteoRespuesta> d30 = list(porPreg.getOrDefault(30, new ArrayList<>()));
                double spin = 0, start = 0;
                for (ConteoRespuesta cp : d30) {
                    String r = cp.getRespuesta() == null ? "" : cp.getRespuesta().toLowerCase();
                    if (r.contains("spin")) spin = cp.getTotal();
                    if (r.contains("start")) start = cp.getTotal();
                }
                insertarImagen(wb, sh, chartBar("Comparativo: Spin-offs vs Startups (p30)", "Categoría", "Frecuencia",
                        Arrays.asList("Spin Off", "StartUp"), Arrays.asList(spin, start), "Total"), COL, row, COL + ANCHO_COLS, row + 22);
            }
            case "desempleo" -> {
                List<ConteoRespuesta> razones = list(EncuestaDAO.contarRespuestas(28));
                insertarImagen(wb, sh, chartBar("Razones del desempleo (p28)", "Razón", "Frecuencia",
                        razones.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                        razones.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()), "Total"), COL, row, COL + ANCHO_COLS, row + 24);
                row += 25;
                List<ConteoRespuesta> tiempos = list(EncuestaDAO.contarRespuestas(36));
                insertarImagen(wb, sh, chartBar("Tiempo hasta el primer empleo (p36)", "Tiempo", "Frecuencia",
                        tiempos.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                        tiempos.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()), "Total"), COL, row, COL + ANCHO_COLS, row + 24);
            }
            case "formacion" -> {
                List<AreaInteresPosgrado> areas = EncuestaDAO.areasInteresPosgrado();
                insertarImagen(wb, sh, chartBar("Áreas de interés de posgrado (p9)", "Área", "n",
                        areas.stream().map(AreaInteresPosgrado::getArea).collect(java.util.stream.Collectors.toList()),
                        areas.stream().map(a -> (double) a.getN()).collect(java.util.stream.Collectors.toList()), "n"), COL, row, COL + ANCHO_COLS, row + 24);
                row += 25;

                List<ConteoRespuesta> niv = list(EncuestaDAO.nivelesFormacionDeseados());
                insertarImagen(wb, sh, chartPie("Nivel de posgrado deseado (p4)",
                        niv.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                        niv.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList())), COL, row, COL + ANCHO_COLS, row + 24);
                row += 25;

                Map<String, Object> fin = EncuestaDAO.tablaFinanciamiento();
                @SuppressWarnings("unchecked") List<String> filas = (List<String>) fin.get("filas");
                @SuppressWarnings("unchecked") List<CeldaCruce> celdas = (List<CeldaCruce>) fin.get("celdas");
                if (filas != null && !filas.isEmpty()) {
                    List<Double> tot = new ArrayList<>();
                    List<String> cats = new ArrayList<>();
                    for (String f : filas) { cats.add(f); tot.add((double) getTotalFila(celdas, f)); }
                    insertarImagen(wb, sh, chartBar("Matriz p4 × p6: total por nivel de posgrado", "Nivel", "Titulados", cats, tot, "Total"), COL, row, COL + ANCHO_COLS, row + 24);
                }
            }
            case "empleabilidad" -> {
                List<AreaEmpleabilidad> ae = EncuestaDAO.analisisEmpleabilidad();
                List<String> cats = ae.stream().map(AreaEmpleabilidad::getArea).collect(java.util.stream.Collectors.toList());
                List<List<Double>> series = Arrays.asList(
                        ae.stream().map(a -> (double) a.getDemandaEmpleador()).collect(java.util.stream.Collectors.toList()),
                        ae.stream().map(a -> (double) a.getInteresTitulados()).collect(java.util.stream.Collectors.toList()),
                        ae.stream().map(AreaEmpleabilidad::getEmpleabilidad).collect(java.util.stream.Collectors.toList()));
                insertarImagen(wb, sh, chartBarMulti("Empleabilidad por área (%)", "Área", "Porcentaje",
                        cats, Arrays.asList("Demanda empleador", "Interés titulados", "Empleabilidad"), series), COL, row, COL + ANCHO_COLS, row + 24);
                row += 25;

                List<ConteoRespuesta> criticas = razonesCriticas();
                insertarImagen(wb, sh, chartBar("Oferta con poca demanda: razones críticas (p28)", "Razón", "Frecuencia",
                        criticas.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                        criticas.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()), "Total"), COL, row, COL + ANCHO_COLS, row + 24);
            }
            case "ubicacion" -> {
                // Dónde trabajan
                List<ConteoRespuesta> deptos = list(AnalisisDAO.frecuencias(U_DEPTO, false, new FiltroEnvio()));
                insertarImagen(wb, sh, chartBar("¿Dónde trabajan los titulados? (p19)", "Departamento", "Titulados",
                        deptos.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                        deptos.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()), "Titulados"),
                        COL, row, COL + ANCHO_COLS, row + 24);
                row += 25;

                // Salario promedio por departamento
                ResultadoPromedio sal = AnalisisDAO.promedioPorGrupo(U_SALARIO, U_DEPTO, new FiltroEnvio());
                List<String> catsS = new ArrayList<>();
                List<Double> valsS = new ArrayList<>();
                for (ResumenGrupo g : sal.grupos()) { catsS.add(g.grupo()); valsS.add(g.media() == null ? 0.0 : g.media()); }
                insertarImagen(wb, sh, chartBar("Salario promedio mensual por departamento (Bs.)", "Departamento", "Media (Bs.)",
                        catsS, valsS, "Media"), COL, row, COL + ANCHO_COLS, row + 24);
                row += 25;

                // Pertinencia promedio por cargo
                ResultadoPromedio pert = AnalisisDAO.promedioPorGrupo(U_PERT, U_CARGO, new FiltroEnvio());
                List<String> catsP = new ArrayList<>();
                List<Double> valsP = new ArrayList<>();
                for (ResumenGrupo g : pert.grupos()) { catsP.add(g.grupo()); valsP.add(g.media() == null ? 0.0 : g.media()); }
                insertarImagen(wb, sh, chartBar("Pertinencia promedio por cargo (1-4)", "Cargo", "Media",
                        catsP, valsP, "Media"), COL, row, COL + ANCHO_COLS, row + 24);
            }
            case "hu12" -> {
                // Un gráfico por pregunta de demanda empresarial (encuesta 2).
                for (int pid : HU12_DEMANDA) {
                    insertarImagen(wb, sh, hu12Chart(pid), COL, row, COL + ANCHO_COLS, row + 22);
                    row += 23;
                }
            }
            default -> { /* sin gráficos */ }
        }
    }

    private static void styleRow(Row r, CellStyle s) {
        // Capturar el límite ANTES de crear celdas 
        int fin = r.getLastCellNum();
        if (fin < 0) return;
        for (int i = 0; i < fin; i++) {
            Cell c = r.getCell(i);
            if (c == null) c = r.createCell(i);
            c.setCellStyle(s);
        }
    }

    private static int countBlock(List<ConteoRespuesta> l) { return l == null ? 0 : l.size(); }
    private static int getSimpleCount(List<ConteoRespuesta> l) { return l == null ? 0 : l.size(); }

    /** Escribe una tabla de frecuencia cruzada (asignatura × variable) en la hoja Excel. Devuelve la próxima fila libre. */
    private static int escribirCruceExcel(Sheet sh, int rowIdx, CellStyle headerStyle, String titulo, List<CeldaCruce> celdas) {
        int ri = rowIdx;
        Row rhT = sh.createRow(ri++);
        rhT.createCell(0).setCellValue(titulo);
        styleRow(rhT, headerStyle);

        Row rh = sh.createRow(ri++);
        rh.createCell(0).setCellValue("ASIGNATURA");
        rh.createCell(1).setCellValue("VARIABLE");
        rh.createCell(2).setCellValue("TOTAL");
        styleRow(rh, headerStyle);

        for (CeldaCruce cc : celdas) {
            Row rr = sh.createRow(ri++);
            rr.createCell(0).setCellValue(cc.getValorX());
            rr.createCell(1).setCellValue(cc.getValorY());
            rr.createCell(2).setCellValue(cc.getTotal());
        }
        return ri + 1;
    }

    private static int addEmpSheetBlock(Sheet sh, int rowIdxStart, int pid, String titulo, Map<Integer, List<ConteoRespuesta>> porPreg, CellStyle headerStyle) {
        int ri = rowIdxStart;
        Row rh = sh.createRow(ri++);
        rh.createCell(0).setCellValue(titulo);
        styleRow(rh, headerStyle);
        Row rh2 = sh.createRow(ri++);
        rh2.createCell(0).setCellValue("RESPUESTA");
        rh2.createCell(1).setCellValue("TOTAL");
        rh2.createCell(2).setCellValue("%");
        styleRow(rh2, headerStyle);
        List<ConteoRespuesta> lista = porPreg.getOrDefault(pid, new ArrayList<>());
        int total = 0;
        for (ConteoRespuesta cp : lista) total += cp.getTotal();
        for (ConteoRespuesta cp : lista) {
            Row rr = sh.createRow(ri++);
            rr.createCell(0).setCellValue(cp.getRespuesta());
            rr.createCell(1).setCellValue(cp.getTotal());
            rr.createCell(2).setCellValue(total == 0 ? 0.0 : (cp.getTotal() * 100.0 / (double) total));
        }
        return ri - rowIdxStart;
    }

    private static void addSimpleSection(Sheet sh, int rowIdx, String titulo, List<ConteoRespuesta> lista, CellStyle headerStyle) {
        int ri = rowIdx;
        Row rh = sh.createRow(ri++);
        rh.createCell(0).setCellValue(titulo);
        styleRow(rh, headerStyle);
        Row rh2 = sh.createRow(ri++);
        rh2.createCell(0).setCellValue("OPCIÓN/RESPUESTA");
        rh2.createCell(1).setCellValue("TOTAL");
        rh2.createCell(2).setCellValue("%");
        styleRow(rh2, headerStyle);
        int total = 0;
        for (ConteoRespuesta c : lista) total += c.getTotal();
        for (ConteoRespuesta c : lista) {
            Row rr = sh.createRow(ri++);
            rr.createCell(0).setCellValue(c.getRespuesta());
            rr.createCell(1).setCellValue(c.getTotal());
            rr.createCell(2).setCellValue(total == 0 ? 0.0 : (c.getTotal() * 100.0 / (double) total));
        }
    }

    private static void addKV(Sheet sh, int ri, String k, Object v) {
        Row rr = sh.createRow(ri);
        rr.createCell(0).setCellValue(k);
        if (v instanceof Number) rr.createCell(1).setCellValue(((Number) v).doubleValue());
        else rr.createCell(1).setCellValue(v == null ? "" : v.toString());
    }

    private static long getTotalFila(List<CeldaCruce> celdas, String f) {
        long s = 0;
        for (CeldaCruce cc : celdas) if (Objects.equals(cc.getValorX(), f)) s += cc.getTotal();
        return s;
    }
    private static long getTotalCol(List<CeldaCruce> celdas, String c) {
        long s = 0;
        for (CeldaCruce cc : celdas) if (Objects.equals(cc.getValorY(), c)) s += cc.getTotal();
        return s;
    }
    private static long getTotalGeneral(List<CeldaCruce> celdas) {
        long s = 0;
        for (CeldaCruce cc : celdas) s += cc.getTotal();
        return s;
    }

    // PDF 

    public static void exportPdf(Context ctx) {
        String tipo = ctx.queryParam("tipo");
        if (tipo == null) tipo = "brechas";
        String html = generarHtml(tipo);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();
            String filename = "reporte_" + tipo + "_" + System.currentTimeMillis() + ".pdf";
            ctx.res().setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            ctx.res().setContentType("application/pdf");
            ctx.result(new java.io.ByteArrayInputStream(baos.toByteArray()));
        } catch (Exception e) {
            ctx.status(500).result("Error al generar PDF: " + e.getMessage());
        }
    }

    private static String generarHtml(String tipo) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"utf-8\"/><style>");
        sb.append("body{font-family:Arial,sans-serif;margin:20px;color:#222;font-size:9pt}");
        sb.append("h1{font-size:14pt;color:#0d6efd;margin:0 0 4px 0}");
        sb.append("h2{font-size:11pt;color:#0d6efd;margin:16px 0 6px 0;border-bottom:1px solid #e5e5e5;padding-bottom:2px}");
        sb.append("h3{font-size:10pt;margin:10px 0 4px 0}");
        sb.append(".meta{color:#666;font-size:8pt;margin-bottom:10px}");
        sb.append("table{border-collapse:collapse;width:100%;margin-bottom:8px}");
        sb.append("td,th{border:1px solid #ddd;padding:4px 6px;vertical-align:top}");
        sb.append("th{background:#f5f5f5;font-weight:bold;text-align:left}");
        sb.append(".num{text-align:right}");
        sb.append(".alert{background:#fff3cd;border:1px solid #ffe69c;padding:6px 8px;border-radius:4px;margin:6px 0}");
        sb.append("</style></head><body>");
        sb.append("<h1>REPORTE UMSS - ").append(esc(tipo.toUpperCase())).append("</h1>");
        sb.append("<div class='meta'>Generado: ").append(DF.format(new Date())).append("</div>");
        try {
            switch (tipo) {
                case "brechas" -> {
                    sb.append("<h2>1. Evaluación de Brechas y Competencias</h2>");
                    sb.append("<h3>Matriz Radar (Dimensión × Objetivo/Actual/Brecha)</h3>");
                    sb.append("<table><tr><th>DIMENSIÓN</th><th class='num'>OBJETIVO(%)</th><th class='num'>ACTUAL(%)</th><th class='num'>BRECHA(%)</th></tr>");
                    for (DimensionRadar d : EncuestaDAO.radarBrechas()) {
                        sb.append("<tr><td>").append(esc(d.getDimension())).append("</td><td class='num'>").append(d.getObjetivo()).append("</td><td class='num'>").append(d.getActual()).append("</td><td class='num'>").append(d.getBrecha()).append("</td></tr>");
                    }
                    sb.append("</table>");
                    sb.append("<h3>Asignaturas con mayor ventaja para el desempeño (p42)</h3>");
                    List<ConteoRespuesta> as42 = EncuestaDAO.asignaturasFrecuencia(42);
                    sb.append("<table><tr><th>ASIGNATURA</th><th class='num'>TOTAL</th><th class='num'>% TITULADOS</th></tr>");
                    for (ConteoRespuesta c : as42) {
                        sb.append("<tr><td>").append(esc(c.getRespuesta())).append("</td><td class='num'>").append(c.getTotal()).append("</td><td class='num'>").append(DF2.format(c.getPorcentaje())).append("</td></tr>");
                    }
                    sb.append("</table>");
                    sb.append("<h3>Cruce: Asignaturas de ventaja (p42) × Situación laboral actual (p14)</h3>");
                    sb.append("<table><tr><th>ASIGNATURA (p42)</th><th>SITUACIÓN LABORAL (p14)</th><th class='num'>TOTAL</th></tr>");
                    for (CeldaCruce cc : EncuestaDAO.cruceAsignaturaVariable(42,14)) {
                        sb.append("<tr><td>").append(esc(cc.getValorX())).append("</td><td>").append(esc(cc.getValorY())).append("</td><td class='num'>").append(cc.getTotal()).append("</td></tr>");
                    }
                    sb.append("</table>");

                    List<ConteoRespuesta> as44 = EncuestaDAO.asignaturasFrecuencia(44);
                    sb.append("<h3>Asignaturas de poca utilidad profesional (p44)</h3>");
                    sb.append("<table><tr><th>ASIGNATURA</th><th class='num'>TOTAL</th><th class='num'>% TITULADOS</th></tr>");
                    for (ConteoRespuesta c : as44) {
                        sb.append("<tr><td>").append(esc(c.getRespuesta())).append("</td><td class='num'>").append(c.getTotal()).append("</td><td class='num'>").append(DF2.format(c.getPorcentaje())).append("</td></tr>");
                    }
                    sb.append("</table>");

                    sb.append("<h3>Cruce: Asignaturas de poca utilidad (p44) × Situación laboral (p14)</h3>");
                    sb.append("<table><tr><th>ASIGNATURA (p44)</th><th>SITUACIÓN LABORAL (p14)</th><th class='num'>TOTAL</th></tr>");
                    for (CeldaCruce cc : EncuestaDAO.cruceAsignaturaVariable(44,14)) {
                        sb.append("<tr><td>").append(esc(cc.getValorX())).append("</td><td>").append(esc(cc.getValorY())).append("</td><td class='num'>").append(cc.getTotal()).append("</td></tr>");
                    }
                    sb.append("</table>");

                    sb.append("<h3>Cruce: Asignaturas de poca utilidad (p44) × Razón de inactividad laboral (p28)</h3>");
                    sb.append("<table><tr><th>ASIGNATURA (p44)</th><th>RAZÓN (p28)</th><th class='num'>TOTAL</th></tr>");
                    for (CeldaCruce cc : EncuestaDAO.cruceAsignaturaVariable(44,28)) {
                        sb.append("<tr><td>").append(esc(cc.getValorX())).append("</td><td>").append(esc(cc.getValorY())).append("</td><td class='num'>").append(cc.getTotal()).append("</td></tr>");
                    }
                    sb.append("</table>");
                    // Gráficos
                    List<DimensionRadar> radarH = EncuestaDAO.radarBrechas();
                    List<String> catsR = new ArrayList<>(); List<Double> objR = new ArrayList<>(); List<Double> actR = new ArrayList<>();
                    for (DimensionRadar d : radarH) { catsR.add(d.getDimension()); objR.add(d.getObjetivo()); actR.add(d.getActual()); }
                    sb.append(htmlChart("Gráfico Radar: Objetivo (empleador) vs Actual (titulado)", chartRadar("Estado objetivo vs actual", catsR, Arrays.asList("Objetivo", "Actual"), Arrays.asList(objR, actR))));
                    List<ConteoRespuesta> as42top = list(EncuestaDAO.asignaturasFrecuencia(42));
                    if (as42top.size() > 10) as42top = as42top.subList(0, 10);
                    sb.append(htmlChart("Asignaturas de mayor ventaja (p42)", chartBar("Asignaturas de mayor ventaja", "Asignatura", "Frecuencia",
                            as42top.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                            as42top.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()), "Total")));
                    List<ConteoRespuesta> as44top = as44;
                    if (as44top.size() > 10) as44top = as44top.subList(0, 10);
                    sb.append(htmlChart("Asignaturas de poca utilidad (p44)", chartBar("Asignaturas de poca utilidad", "Asignatura", "Frecuencia",
                            as44top.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                            as44top.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()), "Total")));
                }
                case "emprendimiento" -> {
                    sb.append("<h2>2. Ecosistema de Emprendimiento e Innovación</h2>");
                    @SuppressWarnings("unchecked")
                    List<ConteoRespuesta> fe = EncuestaDAO.obtenerConteoEmprendimiento();
                    if (fe == null) fe = Collections.emptyList();
                    Map<Integer,List<ConteoRespuesta>> mp = new LinkedHashMap<>();
                    for (ConteoRespuesta f : fe) mp.computeIfAbsent(f.getPreguntaId(), k->new ArrayList<>()).add(f);
                    htmlEmpBlock(sb,"Origen del emprendimiento (p30)", mp.getOrDefault(30,new ArrayList<>()));
                    htmlEmpBlock(sb,"Tipo de entregable (p31)", mp.getOrDefault(31,new ArrayList<>()));
                    htmlEmpBlock(sb,"Financiamiento externo (p32)", mp.getOrDefault(32,new ArrayList<>()));
                    htmlEmpBlock(sb,"Satisfacción con el rendimiento (p33)", mp.getOrDefault(33,new ArrayList<>()));
                    htmlEmpBlock(sb,"Importancia de la formación (p34)", mp.getOrDefault(34,new ArrayList<>()));
                    List<ConteoRespuesta> d30h = mp.getOrDefault(30,new ArrayList<>());
                    ConteoRespuesta sph=null,sth=null;
                    for (ConteoRespuesta cp:d30h){ String r=cp.getRespuesta()==null?"":cp.getRespuesta().toLowerCase(); if(r.contains("spin")) sph=cp; if(r.contains("start")) sth=cp;}
                    sb.append("<h3>Comparativo: Spin-offs vs Startups (p30)</h3><table><tr><th>CATEGORÍA</th><th class='num'>TOTAL</th></tr>");
                    sb.append("<tr><td>Spin Off</td><td class='num'>").append(sph==null?0:sph.getTotal()).append("</td></tr>");
                    sb.append("<tr><td>Spin Off</td><td class='num'>").append(sph==null?0:sph.getTotal()).append("</td></tr>");
                    sb.append("<tr><td>StartUp</td><td class='num'>").append(sth==null?0:sth.getTotal()).append("</td></tr></table>");
                    // Resumen y recomendaciones
                    sb.append("<h3>Resumen y recomendación</h3><ul>");
                    for (String b : resumenEmprendimientoBullets(mp)) sb.append("<li>").append(esc(b)).append("</li>");
                    sb.append("</ul><h3>Recomendaciones para el Plan de Desarrollo Estratégico</h3><ul>");
                    for (String r : resumenEmprendimientoRecomendaciones(mp)) sb.append("<li>").append(esc(r)).append("</li>");
                    sb.append("</ul>");
                    // Gráficos
                    int[] pids = {30,31,32,33,34};
                    String[] titulos = {"Origen del emprendimiento (p30)","Tipo de entregable (p31)","Financiamiento externo (p32)","Satisfacción con el rendimiento (p33)","Importancia de la formación (p34)"};
                    for (int k = 0; k < pids.length; k++) {
                        List<ConteoRespuesta> lk = list(mp.getOrDefault(pids[k], new ArrayList<>()));
                        sb.append(htmlChart(titulos[k], chartPie(titulos[k],
                                lk.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                                lk.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()))));
                    }
                    sb.append(htmlChart("Comparativo: Spin-offs vs Startups (p30)", chartBar("Spin-offs vs Startups", "Categoría", "Frecuencia",
                            Arrays.asList("Spin Off", "StartUp"),
                            Arrays.asList(sph==null?0.0:(double)sph.getTotal(), sth==null?0.0:(double)sth.getTotal()), "Total")));
                }
                case "desempleo" -> {
                    sb.append("<h2>3. Desempleo y Barreras</h2>");
                    sb.append("<h3>Matriz: Razones para continuar desempleado (p28) × Experiencia anterior (p29)</h3>");
                    sb.append("<table><tr><th>RAZÓN (p28)</th><th>EXPERIENCIA ANTERIOR (p29)</th><th class='num'>TOTAL</th></tr>");
                    for (CeldaCruce cc:EncuestaDAO.obtenerCruce(28,29)) sb.append("<tr><td>").append(esc(cc.getValorX())).append("</td><td>").append(esc(cc.getValorY())).append("</td><td class='num'>").append(cc.getTotal()).append("</td></tr>");
                    sb.append("</table>");
                    sb.append("<h3>Matriz: Tiempo hasta el primer empleo (p36) × Número de empleos (p37)</h3>");
                    sb.append("<table><tr><th>TIEMPO HASTA PRIMER EMPLEO (p36)</th><th>NÚMERO DE EMPLEOS (p37)</th><th class='num'>TOTAL</th></tr>");
                    for (CeldaCruce cc:EncuestaDAO.obtenerCruce(36,37)) sb.append("<tr><td>").append(esc(cc.getValorX())).append("</td><td>").append(esc(cc.getValorY())).append("</td><td class='num'>").append(cc.getTotal()).append("</td></tr>");
                    sb.append("</table>");
                    // Gráficos
                    List<ConteoRespuesta> rz = list(EncuestaDAO.contarRespuestas(28));
                    sb.append(htmlChart("Razones del desempleo (p28)", chartBar("Razones del desempleo", "Razón", "Frecuencia",
                            rz.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                            rz.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()), "Total")));
                    List<ConteoRespuesta> tp = list(EncuestaDAO.contarRespuestas(36));
                    sb.append(htmlChart("Tiempo hasta el primer empleo (p36)", chartBar("Tiempo hasta el primer empleo", "Tiempo", "Frecuencia",
                            tp.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                            tp.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()), "Total")));
                }
                case "formacion" -> {
                    sb.append("<h2>4. Formación continua y Mecanismos de financiamiento</h2>");
                    List<AreaInteresPosgrado> ap9h = EncuestaDAO.areasInteresPosgrado();
                    int Np9h=0; for (AreaInteresPosgrado a:ap9h) Np9h+=a.getN();
                    sb.append("<h3>Áreas de interés de posgrado (p9)</h3><table><tr><th>ÁREA</th><th class='num'>n</th><th class='num'>f_i (%)</th></tr>");
                    for (AreaInteresPosgrado a:ap9h) sb.append("<tr><td>").append(esc(a.getArea())).append("</td><td class='num'>").append(a.getN()).append("</td><td class='num'>").append(DF2.format(a.getPorcentaje())).append("</td></tr>");
                    sb.append("</table>");
                    if (Np9h>0){ long t1=ap9h.isEmpty()?0:ap9h.get(0).getN(); long t2=ap9h.size()<2?t1:t1+ap9h.get(1).getN(); double pt2=t2*100.0/Np9h; sb.append("<div class='alert'><strong>Alerta de Concentración Crítica:</strong> Suma Top 2 = ").append(t2).append("/").append(Np9h).append(" (").append(DF2.format(pt2)).append("%) — Umbral ≥ 70% → ").append(pt2>=70?"<strong>SÍ (CRÍTICA)</strong>":"NO").append("</div>"); }
                    List<ConteoRespuesta> np4h = EncuestaDAO.nivelesFormacionDeseados();
                    int tn4h=0; for (ConteoRespuesta c:np4h) tn4h+=c.getTotal();
                    sb.append("<h3>Nivel de posgrado deseado (p4)</h3><table><tr><th>NIVEL</th><th class='num'>TOTAL</th><th class='num'>%</th></tr>");
                    for (ConteoRespuesta c:np4h){ double p=tn4h==0?0:c.getTotal()*100.0/tn4h; sb.append("<tr><td>").append(esc(c.getRespuesta())).append("</td><td class='num'>").append(c.getTotal()).append("</td><td class='num'>").append(DF2.format(p)).append("</td></tr>"); }
                    sb.append("</table>");
                    Map<String,Object> finh = EncuestaDAO.tablaFinanciamiento();
                    @SuppressWarnings("unchecked") List<String> fh=(List<String>)finh.get("filas");
                    @SuppressWarnings("unchecked") List<String> ch=(List<String>)finh.get("columnas");
                    @SuppressWarnings("unchecked") List<CeldaCruce> ceh=(List<CeldaCruce>)finh.get("celdas");
                    sb.append("<h3>Matriz de Contingencia: p4 × p6 (Nivel × Fuente de financiamiento)</h3><table><tr><th>NIVEL / FUENTE</th>");
                    for (String c:ch) sb.append("<th class='num'>").append(esc(c)).append("</th>");
                    sb.append("<th class='num'>TOTAL_FILA</th></tr>");
                    for (String f:fh){ sb.append("<tr><th>").append(esc(f)).append("</th>"); for (String c:ch) sb.append("<td class='num'>").append(getCelda(ceh,f,c)).append("</td>"); sb.append("<td class='num'>").append(getTotalFila(ceh,f)).append("</td></tr>"); }
                    sb.append("<tr><th>TOTAL_COLUMNA</th>"); for (String c:ch) sb.append("<td class='num'>").append(getTotalCol(ceh,c)).append("</td>"); sb.append("<td class='num'><strong>").append(getTotalGeneral(ceh)).append("</strong></td></tr></table>");
                    ResultadoChi2 rch = (ResultadoChi2)finh.get("chi2");
                    Boolean tfh=(Boolean)finh.get("tieneDatos");
                    if (rch!=null){ sb.append("<h3>Diagnóstico Estadístico (χ² de Independencia)</h3><table><tr><th>ÍNDICE</th><th>VALOR</th></tr>"); sb.append("<tr><td>χ²</td><td>").append(rch.getChiCuadrado()).append("</td></tr>"); sb.append("<tr><td>gl</td><td>").append(rch.getGradosLibertad()).append("</td></tr>"); sb.append("<tr><td>p-valor</td><td>").append(rch.getValorP()).append("</td></tr>"); sb.append("<tr><td>Independiente (p ≥ 0.05)</td><td>").append(rch.isIndependiente()?"SÍ":"NO").append("</td></tr>"); sb.append("<tr><td>Datos suficientes</td><td>").append((tfh!=null&&tfh)?"SÍ":"NO").append("</td></tr>"); sb.append("<tr><td>Interpretación</td><td>").append(esc(rch.getInterpretacion())).append("</td></tr></table>"); }
                    // Gráficos
                    sb.append(htmlChart("Áreas de interés de posgrado (p9)", chartBar("Áreas de interés de posgrado", "Área", "n",
                            ap9h.stream().map(AreaInteresPosgrado::getArea).collect(java.util.stream.Collectors.toList()),
                            ap9h.stream().map(a -> (double) a.getN()).collect(java.util.stream.Collectors.toList()), "n")));
                    sb.append(htmlChart("Nivel de posgrado deseado (p4)", chartPie("Nivel de posgrado deseado (p4)",
                            np4h.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                            np4h.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()))));
                    if (fh != null && !fh.isEmpty()) {
                        List<Double> totF = new ArrayList<>();
                        for (String f : fh) totF.add((double) getTotalFila(ceh, f));
                        sb.append(htmlChart("Matriz p4 × p6: total por nivel de posgrado", chartBar("Total por nivel de posgrado", "Nivel", "Titulados", fh, totF, "Total")));
                    }
                }
                case "empleabilidad" -> {
                    sb.append("<h2>5. Empleabilidad y Ofertas con poca demanda</h2>");
                    sb.append("<h3>Áreas con mayor empleabilidad</h3><table><tr><th>ÁREA</th><th class='num'>DEMANDA EMPL.(%)</th><th class='num'>INTERÉS TIT.(%)</th><th class='num'>EMPLEABILIDAD(%)</th></tr>");
                    for (AreaEmpleabilidad a:EncuestaDAO.analisisEmpleabilidad()) sb.append("<tr><td>").append(esc(a.getArea())).append("</td><td class='num'>").append(a.getDemandaEmpleador()).append("</td><td class='num'>").append(a.getInteresTitulados()).append("</td><td class='num'><strong>").append(a.getEmpleabilidad()).append("</strong></td></tr>");
                    sb.append("</table>");
                    htmlSimpleBlock(sb,"Razones críticas (p28)", razonesCriticas());
                    htmlSimpleBlock(sb,"Asignaturas con poca utilidad (p44) - Top", asignaturasPocaTop(20));
                    htmlSimpleBlock(sb,"Rubros de inversión/requerimientos (p53)", EncuestaDAO.contarRespuestas(53));
                    htmlSimpleBlock(sb,"Posibilidad de contratación de titulados (p55)", EncuestaDAO.contarRespuestas(55));
                    // Gráficos
                    List<AreaEmpleabilidad> aeH = EncuestaDAO.analisisEmpleabilidad();
                    List<String> catsE = aeH.stream().map(AreaEmpleabilidad::getArea).collect(java.util.stream.Collectors.toList());
                    List<List<Double>> serE = Arrays.asList(
                            aeH.stream().map(a -> (double) a.getDemandaEmpleador()).collect(java.util.stream.Collectors.toList()),
                            aeH.stream().map(a -> (double) a.getInteresTitulados()).collect(java.util.stream.Collectors.toList()),
                            aeH.stream().map(AreaEmpleabilidad::getEmpleabilidad).collect(java.util.stream.Collectors.toList()));
                    sb.append(htmlChart("Empleabilidad por área (%)", chartBarMulti("Empleabilidad por área", "Área", "Porcentaje",
                            catsE, Arrays.asList("Demanda empleador", "Interés titulados", "Empleabilidad"), serE)));
                    List<ConteoRespuesta> crH = razonesCriticas();
                    sb.append(htmlChart("Oferta con poca demanda: razones críticas (p28)", chartBar("Razones críticas", "Razón", "Frecuencia",
                            crH.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                            crH.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()), "Total")));
                }
                case "cruce" -> {
                    sb.append("<h2>6. Cruce de Variables</h2>");
                    sb.append("<p>El cruce dinámico (X × Y) se configura en la interfaz gráfica. Esta exportación presenta la estructura de matrices de frecuencia cruzada para referencia.</p>");
                }
                case "ubicacion" -> {
                    sb.append("<h2>7. Pertinencia Curricular, Geografía y Fuga de Talentos (HU11)</h2>");
                    int[] k = kpisUbicacion();
                    sb.append("<h3>Indicadores clave (p19: departamento de trabajo)</h3>");
                    sb.append("<table><tr><th>INDICADOR</th><th class='num'>VALOR</th><th class='num'>%</th></tr>");
                    sb.append("<tr><td>Titulados analizados</td><td class='num'>").append(k[0]).append("</td><td class='num'>")
                      .append(DF2.format(100.0)).append("</td></tr>");
                    sb.append("<tr><td>Trabajan en Cochabamba</td><td class='num'>").append(k[1]).append("</td><td class='num'>")
                      .append(DF2.format(pctDe(k[1], k[0]))).append("</td></tr>");
                    sb.append("<tr><td>Fuera del departamento base (fuga)</td><td class='num'>").append(k[2]).append("</td><td class='num'>")
                      .append(DF2.format(pctDe(k[2], k[0]))).append("</td></tr>");
                    sb.append("<tr><td>En el extranjero</td><td class='num'>").append(k[3]).append("</td><td class='num'>")
                      .append(DF2.format(pctDe(k[3], k[0]))).append("</td></tr></table>");

                    sb.append("<h3>¿Dónde trabajan los titulados? (p19)</h3>");
                    List<ConteoRespuesta> deptosH = AnalisisDAO.frecuencias(U_DEPTO, false, new FiltroEnvio());
                    sb.append("<table><tr><th>DEPARTAMENTO</th><th class='num'>TOTAL</th><th class='num'>%</th></tr>");
                    for (ConteoRespuesta c : deptosH)
                        sb.append("<tr><td>").append(esc(c.getRespuesta())).append("</td><td class='num'>")
                          .append(c.getTotal()).append("</td><td class='num'>").append(DF2.format(c.getPorcentaje())).append("</td></tr>");
                    sb.append("</table>");

                    sb.append("<h3>Salario promedio mensual por departamento (p27 × p19)</h3>");
                    htmlPromedio(sb, AnalisisDAO.promedioPorGrupo(U_SALARIO, U_DEPTO, new FiltroEnvio()));

                    sb.append("<h3>Pertinencia promedio por cargo (p24 × p22)</h3>");
                    htmlPromedio(sb, AnalisisDAO.promedioPorGrupo(U_PERT, U_CARGO, new FiltroEnvio()));

                    sb.append("<h3>Cruce: Ubicación (p19) × Nivel salarial (p27)</h3>");
                    htmlChi(sb, chi2auto(U_DEPTO, U_SALARIO));
                    sb.append("<h3>Cruce: Cargo (p22) × Pertinencia (p24)</h3>");
                    htmlChi(sb, chi2auto(U_CARGO, U_PERT));
                    sb.append("<h3>Cruce: Ubicación (p19) × Pertinencia (p24)</h3>");
                    htmlChi(sb, chi2auto(U_DEPTO, U_PERT));

                    // Gráficos
                    List<String> catsD = deptosH.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList());
                    List<Double> valsD = deptosH.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList());
                    sb.append(htmlChart("¿Dónde trabajan los titulados? (p19)", chartBar("¿Dónde trabajan los titulados?", "Departamento", "Titulados", catsD, valsD, "Titulados")));
                    ResultadoPromedio salH = AnalisisDAO.promedioPorGrupo(U_SALARIO, U_DEPTO, new FiltroEnvio());
                    List<String> catsS = new ArrayList<>(); List<Double> valsS = new ArrayList<>();
                    for (ResumenGrupo g : salH.grupos()) { catsS.add(g.grupo()); valsS.add(g.media() == null ? 0.0 : g.media()); }
                    sb.append(htmlChart("Salario promedio mensual por departamento (Bs.)", chartBar("Salario promedio por departamento", "Departamento", "Media (Bs.)", catsS, valsS, "Media")));
                    ResultadoPromedio pertH = AnalisisDAO.promedioPorGrupo(U_PERT, U_CARGO, new FiltroEnvio());
                    List<String> catsP = new ArrayList<>(); List<Double> valsP = new ArrayList<>();
                    for (ResumenGrupo g : pertH.grupos()) { catsP.add(g.grupo()); valsP.add(g.media() == null ? 0.0 : g.media()); }
                    sb.append(htmlChart("Pertinencia promedio por cargo (1-4)", chartBar("Pertinencia promedio por cargo", "Cargo", "Media", catsP, valsP, "Media")));
                }
                case "hu12" -> {
                    sb.append("<h2>8. Demanda Empresarial y Red Alumno (HU12)</h2>");
                    sb.append("<h3>Grupo 1: Demanda empresarial (encuesta 2)</h3>");
                    for (int pid : HU12_DEMANDA) htmlHu12(sb, pid);
                    sb.append("<h3>Grupo 2: Percepción de la formación (encuesta 2)</h3>");
                    for (int pid : HU12_PERCEPCION) htmlHu12(sb, pid);

                    Map<String, Object> red = ReporteHu12DAO.obteneralumno();
                    sb.append("<h3>Grupo 3: Red Alumno - Consentimiento de contacto (p46)</h3>");
                    sb.append("<table><tr><th>INDICADOR</th><th class='num'>VALOR</th></tr>");
                    sb.append("<tr><td>Total de titulados</td><td class='num'>").append(red.get("totalTitulados")).append("</td></tr>");
                    sb.append("<tr><td>Aceptaron ser contactados (p46 = Sí)</td><td class='num'>").append(red.get("aceptaron")).append("</td></tr>");
                    sb.append("<tr><td>Con correo</td><td class='num'>").append(red.get("conCorreo")).append("</td></tr>");
                    sb.append("<tr><td>Con teléfono</td><td class='num'>").append(red.get("conTelefono")).append("</td></tr>");
                    sb.append("<tr><td>Con LinkedIn</td><td class='num'>").append(red.get("conLinkedin")).append("</td></tr></table>");
                    sb.append("<p class='meta'>El listado de contactos (correo/teléfono/LinkedIn) solo se muestra en la interfaz a usuarios autorizados.</p>");

                    // Gráficos: demanda empresarial
                    for (int pid : HU12_DEMANDA) sb.append(htmlChart("P" + pid + ": " + HU12_TITULOS.getOrDefault(pid, ""), hu12Chart(pid)));
                }
                default -> sb.append("<p>Tipo de reporte no soportado para exportación completa.</p>");
            }
        } catch (Exception e) {
            sb.append("<p style='color:#dc3545'><strong>ERROR:</strong> ").append(esc(e.getMessage())).append("</p>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static void htmlEmpBlock(StringBuilder sb, String titulo, List<ConteoRespuesta> lista) {
        int t=0; for (ConteoRespuesta cp:lista) t+=cp.getTotal();
        sb.append("<h3>").append(esc(titulo)).append("</h3><table><tr><th>RESPUESTA</th><th class='num'>TOTAL</th><th class='num'>%</th></tr>");
        for (ConteoRespuesta cp:lista){ double p=t==0?0:cp.getTotal()*100.0/t; sb.append("<tr><td>").append(esc(cp.getRespuesta())).append("</td><td class='num'>").append(cp.getTotal()).append("</td><td class='num'>").append(DF2.format(p)).append("</td></tr>"); }
        sb.append("</table>");
    }
    private static void htmlSimpleBlock(StringBuilder sb, String titulo, List<ConteoRespuesta> lista) {
        int t=0; for (ConteoRespuesta c:lista) t+=c.getTotal();
        sb.append("<h3>").append(esc(titulo)).append("</h3><table><tr><th>OPCIÓN/RESPUESTA</th><th class='num'>TOTAL</th><th class='num'>%</th></tr>");
        for (ConteoRespuesta c:lista){ double p=t==0?0:c.getTotal()*100.0/t; sb.append("<tr><td>").append(esc(c.getRespuesta())).append("</td><td class='num'>").append(c.getTotal()).append("</td><td class='num'>").append(DF2.format(p)).append("</td></tr>"); }
        sb.append("</table>");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
