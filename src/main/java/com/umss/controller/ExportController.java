package com.umss.controller;

import com.umss.dao.AnalisisDAO;
import com.umss.dao.EncuestaDAO;
import com.umss.dao.ReporteHu12DAO;
import com.umss.model.*;
import com.umss.util.CalculadoraEmprendimiento;
import com.umss.util.CalculadoraDesempleo;
import com.umss.util.PruebaChiCuadrado;
import io.javalin.http.Context;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

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

    /**
     * Inserta un gráfico de barras nativo de Excel vinculado a un rango de celdas.
     */
    private static void crearGraficoBarrasNativo(
            Sheet sh, 
            String titulo, 
            int colCategorias, int colValores, 
            int filaInicio, int filaFin, 
            int colUbicacion, int filaUbicacion, int anchoCols, int altoFilas) {

        try {
            XSSFSheet xssfSheet = (XSSFSheet) sh;
            XSSFDrawing drawing = dibujoHoja(xssfSheet);

            XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
                    colUbicacion, filaUbicacion, colUbicacion + anchoCols, filaUbicacion + altoFilas);

            XSSFChart chart = drawing.createChart(anchor);
            chart.setTitleText(titulo);
            chart.setTitleOverlay(false);

            XDDFChartLegend legend = chart.getOrAddLegend();
            legend.setPosition(LegendPosition.TOP_RIGHT);

            XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
            bottomAxis.setTitle("Categoría");
            XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
            leftAxis.setTitle("Valor");
            leftAxis.setCrossBetween(AxisCrossBetween.BETWEEN);
            XDDFDataSource<String> xs = XDDFDataSourcesFactory.fromStringCellRange(
                    xssfSheet, new CellRangeAddress(filaInicio, filaFin, colCategorias, colCategorias));

            XDDFNumericalDataSource<Double> ys = XDDFDataSourcesFactory.fromNumericCellRange(
                    xssfSheet, new CellRangeAddress(filaInicio, filaFin, colValores, colValores));

            XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
            data.setBarDirection(BarDirection.COL);

            XDDFBarChartData.Series series = (XDDFBarChartData.Series) data.addSeries(xs, ys);
            series.setTitle("Total", null);

            chart.plot(data);
        } catch (RuntimeException ex) {
            // Un gráfico defectuoso no debe invalidar todo el libro: se omite y queda registrado.
            System.err.println("Aviso: no se pudo crear el gráfico '" + titulo + "': " + ex);
        }
    }

    /**
     * Devuelve el dibujo (drawing) único de la hoja. Llamar a createDrawingPatriarch()
     * por cada gráfico puede crear/reemplazar partes de dibujo y romper el .xlsx al escribir.
     */
    private static XSSFDrawing dibujoHoja(XSSFSheet hoja) {
        XSSFDrawing drawing = hoja.getDrawingPatriarch();
        return drawing != null ? drawing : hoja.createDrawingPatriarch();
    }

    /**
     * Inserta un gráfico de pastel nativo de Excel (Pie Chart) vinculado a un rango de celdas.
     */
    private static void crearGraficoPastelNativo(
            Sheet sh, 
            String titulo, 
            int colCategorias, int colValores, 
            int filaInicio, int filaFin, 
            int colUbicacion, int filaUbicacion, int anchoCols, int altoFilas) {

        try {
            XSSFSheet xssfSheet = (XSSFSheet) sh;
            XSSFDrawing drawing = dibujoHoja(xssfSheet);

            XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
                    colUbicacion, filaUbicacion, colUbicacion + anchoCols, filaUbicacion + altoFilas);

            XSSFChart chart = drawing.createChart(anchor);
            chart.setTitleText(titulo);
            chart.setTitleOverlay(false);

            // Leyenda a la derecha
            XDDFChartLegend legend = chart.getOrAddLegend();
            legend.setPosition(LegendPosition.RIGHT);

            // Rango de datos (Los gráficos de pastel no requieren declarar ejes Category/Value Axis)
            XDDFDataSource<String> xs = XDDFDataSourcesFactory.fromStringCellRange(
                    xssfSheet, new CellRangeAddress(filaInicio, filaFin, colCategorias, colCategorias));

            XDDFNumericalDataSource<Double> ys = XDDFDataSourcesFactory.fromNumericCellRange(
                    xssfSheet, new CellRangeAddress(filaInicio, filaFin, colValores, colValores));

            XDDFPieChartData data = (XDDFPieChartData) chart.createData(ChartTypes.PIE, null, null);
            data.setVaryColors(true); // Asigna colores variados a cada tajada

            XDDFPieChartData.Series series = (XDDFPieChartData.Series) data.addSeries(xs, ys);
            series.setTitle(titulo, null);

            chart.plot(data);
        } catch (RuntimeException ex) {
            System.err.println("Aviso: no se pudo crear el gráfico '" + titulo + "': " + ex);
        }
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
        XSSFDrawing drawing = dibujoHoja((org.apache.poi.xssf.usermodel.XSSFSheet) sh);
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
            // Reemplaza tu método exportCsv por este:
        public static void exportCsv(Context ctx) {
            String tipo = ctx.queryParam("tipo");
            if (tipo == null || tipo.isBlank()) tipo = "brechas";
            try {
                // 1. Generar CSV
                String csvOriginal = reformatearCsv(generarCsv(tipo));

                // 2. CORRECCIÓN AUTOMÁTICA DE CORRUPCIÓN DE ENCODING (MOJIBAKE)
                String csvCorregido = repararEncoding(csvOriginal);

                String filename = "reporte_" + tipo + "_" + System.currentTimeMillis() + ".csv";
                ctx.res().setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                ctx.res().setContentType("text/csv; charset=UTF-8");
                ctx.res().setCharacterEncoding("UTF-8");

                // BOM UTF-8 para Excel
                byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
                ctx.result(new java.io.ByteArrayInputStream(concat(bom, csvCorregido.getBytes(StandardCharsets.UTF_8))));
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Error al generar CSV: " + mensajeError(e));
            }
        }

    static String repararEncoding(String texto) {
            if (texto == null) return "";
            String limpio = texto
                .replace("Ã“", "O").replace("Ã³", "o")
                .replace("Ã\u0081", "A").replace("Ã¡", "a")
                .replace("Ã‰", "E").replace("Ã©", "e")
                .replace("Ã\u008D", "I").replace("Ã*", "I").replace("Ã­", "i")
                .replace("Ãš", "U").replace("Ãº", "u")
                .replace("Ã‘", "N").replace("Ã±", "n")
                .replace("Ã", "I")
                .replace("Í³", "o").replace("Í\u0081", "A").replace("Í¡", "a")
                .replace("Í‰", "E").replace("Í©", "e").replace("Í*", "I").replace("Í­", "i")
                .replace("Íš", "U").replace("Íº", "u").replace("Í‘", "N").replace("Í±", "n")
                .replace("ïˆò", "2").replace("ï†²", "2")
                .replace("χ²", "Chi2");

            // Eliminar cualquier tilde o diacrítico restante para máxima compatibilidad en Excel CSV
            String sinTildes = java.text.Normalizer.normalize(limpio, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
            return sinTildes;
        }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    /**Adapta el CSV (comas + punto decimal) al formato que Excel espera según el locales*/
    static String reformatearCsv(String csv) {
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
    static String convertirCampo(String f) {
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
                        // Formato Tabular Limpio y Estándar (Sin títulos intermedios que rompan el parseo)
                    sb.append("DIMENSION;OBJETIVO_PCT;ACTUAL_PCT;BRECHA_PCT\r\n");
                    List<DimensionRadar> radar = EncuestaDAO.radarBrechas();
                    for (DimensionRadar d : radar) {
                        // Estandarización de decimales con DF2 (evita mezcla de puntos y comas)
                        sb.append(csv(d.getDimension())).append(";")
                        .append(DF2.format(d.getObjetivo())).append(";")
                        .append(DF2.format(d.getActual())).append(";")
                        .append(DF2.format(d.getBrecha())).append("\r\n");
                    }
                }
                case "emprendimiento" -> appendAnalisisEmprendimientoCsv(sb,
                        CalculadoraEmprendimiento.analisisExploratorio(
                                EncuestaDAO.obtenerRegistrosEmprendimiento()));
                case "desempleo" -> appendAnalisisDesempleoCsv(sb, analisisDesempleo());
                case "formacion" -> {
                    sb.append("=== FORMACIÓN CONTINUA Y MECANISMOS DE FINANCIAMIENTO ===\r\n\r\n");

                    // 1. Tabla cruzada: Área de interés (p9) x Nivel de posgrado deseado (p4)
                    sb.append("=== TABLA CRUZADA: ÁREA DE INTERÉS (p9) x NIVEL DE POSGRADO DESEADO (p4) ===\r\n");
                    var cruceAN = EncuestaDAO.cruceAreaNivel();
                    var nivelesOrden = List.of("DIPLOMADO", "MAESTRIA", "ESPECIALIDAD", "DOCTORADO");
                    var matrizAN = new LinkedHashMap<String, Map<String, Integer>>();
                    var totFilaAN = new LinkedHashMap<String, Integer>();
                    int granTotalAN = 0;

                    for (Object obj : cruceAN) {
                        Map row = (Map) obj;
                        String area = String.valueOf(row.get("area"));
                        String nivel = String.valueOf(row.get("nivel")).toUpperCase();
                        int tot = ((Number) row.get("total")).intValue();
                        matrizAN.computeIfAbsent(area, k -> new LinkedHashMap<>()).put(nivel, tot);
                        totFilaAN.put(area, totFilaAN.getOrDefault(area, 0) + tot);
                        granTotalAN += tot;
                    }

                    var areasOrdenadas = new ArrayList<String>(totFilaAN.keySet());
                    areasOrdenadas.sort((a, b) -> Integer.compare(totFilaAN.get(b), totFilaAN.get(a)));

                    sb.append("AREA_DE_INTERES");
                    for (String niv : nivelesOrden) sb.append(",").append(niv);
                    sb.append(",TOTAL\r\n");

                    int[] totColAN = new int[nivelesOrden.size()];
                    for (String area : areasOrdenadas) {
                        sb.append(csv(area));
                        Map<String, Integer> filaMap = matrizAN.getOrDefault(area, Collections.emptyMap());
                        for (int j = 0; j < nivelesOrden.size(); j++) {
                            int val = filaMap.getOrDefault(nivelesOrden.get(j), 0);
                            totColAN[j] += val;
                            sb.append(",").append(val);
                        }
                        sb.append(",").append(totFilaAN.getOrDefault(area, 0)).append("\r\n");
                    }
                    sb.append("TOTAL_GENERAL");
                    for (int j = 0; j < nivelesOrden.size(); j++) sb.append(",").append(totColAN[j]);
                    sb.append(",").append(granTotalAN).append("\r\n\r\n");

                    // 2. Preferencia institucional: Nivel de Posgrado (p4) x Organización / Institución (p10)
                    sb.append("=== PREFERENCIA INSTITUCIONAL: NIVEL DE POSGRADO (p4) x ORGANIZACIÓN / INSTITUCIÓN (p10) ===\r\n");
                    sb.append("NIVEL_DE_POSGRADO,ORGANIZACION_INSTITUCION,FRECUENCIA,%\r\n");
                    var cruceNO = EncuestaDAO.cruceNivelOrganizacion(10);
                    int totalNO = 0;
                    for (Object obj : cruceNO) {
                        Map row = (Map) obj;
                        int freq = ((Number) row.get("total")).intValue();
                        double pct = ((Number) row.get("porcentaje")).doubleValue();
                        totalNO += freq;
                        sb.append(csv(String.valueOf(row.get("nivel")))).append(",")
                          .append(csv(String.valueOf(row.get("organizacion")))).append(",")
                          .append(freq).append(",")
                          .append(DF2.format(pct)).append("\r\n");
                    }
                    sb.append("TOTAL,,").append(totalNO).append(",100.00\r\n\r\n");

                    // 3. Nivel de formación deseado (p4) - Frecuencias
                    sb.append("=== NIVEL DE FORMACIÓN DESEADO (p4) - FRECUENCIAS ===\r\n");
                    sb.append("NIVEL_DESEADO,TOTAL,%\r\n");
                    var nivP4 = EncuestaDAO.nivelesFormacionDeseados();
                    int Nn4 = 0;
                    for (ConteoRespuesta c : nivP4) Nn4 += c.getTotal();
                    for (ConteoRespuesta c : nivP4) {
                        double pct = Nn4 == 0 ? 0.0 : (c.getTotal() * 100.0 / Nn4);
                        sb.append(csv(c.getRespuesta())).append(",").append(c.getTotal()).append(",").append(DF2.format(pct)).append("\r\n");
                    }
                    sb.append("\r\n");

                    // 4. Mecanismos de financiamiento: Matriz de contingencia (p4 x p6)
                    var fin = EncuestaDAO.tablaFinanciamiento();
                    var filasF = (List) fin.get("filas");
                    var colsF = (List) fin.get("columnas");
                    var celdasF = (List) fin.get("celdas");
                    sb.append("=== MECANISMOS DE FINANCIAMIENTO: MATRIZ DE CONTINGENCIA (p4 x p6) ===\r\n");
                    sb.append("NIVEL/FUENTE_DE_FINANCIAMIENTO");
                    for (Object c : colsF) sb.append(",").append(csv(String.valueOf(c)));
                    sb.append(",TOTAL_FILA\r\n");
                    var tfMap = new LinkedHashMap<String, Integer>();
                    for (Object obj : celdasF) {
                        CeldaCruce cc = (CeldaCruce) obj;
                        tfMap.put(cc.getValorX(), tfMap.getOrDefault(cc.getValorX(), 0) + cc.getTotal());
                    }
                    for (Object fObj : filasF) {
                        String f = String.valueOf(fObj);
                        sb.append(csv(f));
                        for (Object cObj : colsF) {
                            sb.append(",").append(getCelda((List<CeldaCruce>) celdasF, f, String.valueOf(cObj)));
                        }
                        sb.append(",").append(tfMap.getOrDefault(f, 0)).append("\r\n");
                    }
                    sb.append("TOTAL_COLUMNA");
                    var tcMap = new LinkedHashMap<String, Integer>();
                    for (Object obj : celdasF) {
                        CeldaCruce cc = (CeldaCruce) obj;
                        tcMap.put(cc.getValorY(), tcMap.getOrDefault(cc.getValorY(), 0) + cc.getTotal());
                    }
                    long NTotF = 0;
                    for (Integer v : tcMap.values()) NTotF += v;
                    for (Object cObj : colsF) sb.append(",").append(tcMap.getOrDefault(String.valueOf(cObj), 0));
                    sb.append(",").append(NTotF).append("\r\n\r\n");

                    // 5. Diagnóstico estadístico Chi-cuadrado
                    ResultadoChi2 rchi = (ResultadoChi2) fin.get("chi2");
                    Boolean tieneDatosF = (Boolean) fin.get("tieneDatos");
                    if (rchi != null) {
                        sb.append("=== DIAGNÓSTICO ESTADÍSTICO (Chi-cuadrado de Independencia) ===\r\n");
                        sb.append("Chi-cuadrado,").append(rchi.getChiCuadrado()).append("\r\n");
                        sb.append("gl (grados de libertad),").append(rchi.getGradosLibertad()).append("\r\n");
                        sb.append("p-valor,").append(rchi.getValorP()).append("\r\n");
                        sb.append("Independiente (H0),").append(rchi.isIndependiente() ? "SÍ (p >= 0.05)" : "NO (p < 0.05)").append("\r\n");
                        sb.append("Interpretación,").append(csv(rchi.getInterpretacion())).append("\r\n");
                        sb.append("Datos suficientes para el test,").append((tieneDatosF != null && tieneDatosF) ? "SÍ" : "NO").append("\r\n");
                    }
                }
                case "empleabilidad" -> {
                    sb.append("=== ANALISIS DE EMPLEABILIDAD Y BARRERAS DE INSERCION ===\r\n\r\n");

                    // 1. Estadísticos de Antigüedad Profesional (p12)
                    sb.append("=== ESTADISTICOS DE ANTIGUEDAD PROFESIONAL ===\r\n");
                    sb.append("INDICADOR,VALOR\r\n");
                    var respP12 = EncuestaDAO.contarRespuestas(12);
                    var valsAnt = new ArrayList<Double>();
                    var freqAnt = new LinkedHashMap<Double, Integer>();
                    for (ConteoRespuesta c : respP12) {
                        if (c.getRespuesta() == null) continue;
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:[.,]\\d+)?)").matcher(c.getRespuesta());
                        if (m.find()) {
                            double v = Double.parseDouble(m.group(1).replace(',', '.'));
                            int tot = c.getTotal();
                            freqAnt.put(v, freqAnt.getOrDefault(v, 0) + tot);
                            for (int i = 0; i < tot; i++) valsAnt.add(v);
                        }
                    }
                    Collections.sort(valsAnt);
                    int nValid = valsAnt.size();
                    double sumaAnt = 0;
                    for (double v : valsAnt) sumaAnt += v;
                    double mediaAnt = nValid == 0 ? 0.0 : sumaAnt / nValid;
                    double medianaAnt = 0.0;
                    if (nValid > 0) {
                        medianaAnt = (nValid % 2 == 1)
                                ? valsAnt.get(nValid / 2)
                                : (valsAnt.get(nValid / 2 - 1) + valsAnt.get(nValid / 2)) / 2.0;
                    }
                    double modaAnt = 0.0;
                    int maxFreqAnt = -1;
                    for (var entry : freqAnt.entrySet()) {
                        if (entry.getValue() > maxFreqAnt) {
                            maxFreqAnt = entry.getValue();
                            modaAnt = entry.getKey();
                        }
                    }
                    double sumSq = 0;
                    for (double v : valsAnt) sumSq += Math.pow(v - mediaAnt, 2);
                    double desvAnt = nValid > 1 ? Math.sqrt(sumSq / (nValid - 1)) : 0.0;

                    sb.append("Titulados validos,").append(nValid).append("\r\n");
                    sb.append("Media (anios),").append(DF2.format(mediaAnt)).append("\r\n");
                    sb.append("Mediana (anios),").append(DF2.format(medianaAnt)).append("\r\n");
                    sb.append("Moda (anios),").append(DF2.format(modaAnt)).append("\r\n");
                    sb.append("Desv. Estandar (anios),").append(DF2.format(desvAnt)).append("\r\n\r\n");

                    // 2. Demanda de Empresas vs. Interés de Graduados
                    sb.append("=== DEMANDA DE EMPRESAS VS. INTERES DE GRADUADOS ===\r\n");
                    sb.append("AREA,DEMANDA_EMPLEADOR(%),INTERES_TITULADOS(%),EMPLEABILIDAD(%),ORDEN\r\n");
                    var aeList = EncuestaDAO.analisisEmpleabilidad();
                    int ord = 1;
                    for (AreaEmpleabilidad a : aeList) {
                        sb.append(csv(a.getArea())).append(",")
                          .append(DF2.format(a.getDemandaEmpleador())).append(",")
                          .append(DF2.format(a.getInteresTitulados())).append(",")
                          .append(DF2.format(a.getEmpleabilidad())).append(",")
                          .append(ord++).append("\r\n");
                    }
                    sb.append("\r\n");

                    // 3. Motivos Principales de Desempleo (p28 - todas las razones, igual al modal)
                    sb.append("=== MOTIVOS PRINCIPALES DE DESEMPLEO (p28) ===\r\n");
                    sb.append("RAZON,TOTAL,%\r\n");
                    var motivos28 = EncuestaDAO.contarRespuestas(28);
                    int nMot = 0;
                    for (ConteoRespuesta c : motivos28) nMot += c.getTotal();
                    for (ConteoRespuesta c : motivos28) {
                        double pct = nMot == 0 ? 0.0 : (c.getTotal() * 100.0 / nMot);
                        sb.append(csv(c.getRespuesta())).append(",").append(c.getTotal()).append(",").append(DF2.format(pct)).append("\r\n");
                    }
                    sb.append("\r\n");

                    // 4. Materias con Menor Impacto Laboral (p44 - usando % de titulados del DAO)
                    sb.append("=== MATERIAS CON MENOR IMPACTO LABORAL (p44) ===\r\n");
                    sb.append("ASIGNATURA,TOTAL,%_TITULADOS\r\n");
                    var asig44 = EncuestaDAO.asignaturasFrecuencia(44);
                    for (ConteoRespuesta c : asig44) {
                        sb.append(csv(c.getRespuesta())).append(",").append(c.getTotal()).append(",").append(DF2.format(c.getPorcentaje())).append("\r\n");
                    }
                    sb.append("\r\n");

                    // 5. Perfil del Sector Empresarial - Rubro principal (p53)
                    sb.append("=== PERFIL DEL SECTOR EMPRESARIAL: RUBRO PRINCIPAL (p53) ===\r\n");
                    sb.append("RUBRO,TOTAL,%\r\n");
                    var p53 = EncuestaDAO.contarRespuestas(53);
                    int N53 = 0;
                    for (ConteoRespuesta c : p53) N53 += c.getTotal();
                    for (ConteoRespuesta c : p53) {
                        double pct = N53 == 0 ? 0.0 : (c.getTotal() * 100.0 / N53);
                        sb.append(csv(c.getRespuesta())).append(",").append(c.getTotal()).append(",").append(DF2.format(pct)).append("\r\n");
                    }
                    sb.append("\r\n");

                    // 6. Perfil del Sector Empresarial - Posibilidad de incorporar (p55)
                    sb.append("=== PERFIL DEL SECTOR EMPRESARIAL: POSIBILIDAD DE INCORPORAR (p55) ===\r\n");
                    sb.append("OPCION,TOTAL,%\r\n");
                    var p55 = EncuestaDAO.contarRespuestas(55);
                    int N55 = 0;
                    for (ConteoRespuesta c : p55) N55 += c.getTotal();
                    for (ConteoRespuesta c : p55) {
                        double pct = N55 == 0 ? 0.0 : (c.getTotal() * 100.0 / N55);
                        sb.append(csv(c.getRespuesta())).append(",").append(c.getTotal()).append(",").append(DF2.format(pct)).append("\r\n");
                    }
                    sb.append("\r\n");

                    // 7. Resumen y recomendación (igual al modal)
                    sb.append("=== RESUMEN Y RECOMENDACION ===\r\n");
                    StringBuilder topAreas = new StringBuilder();
                    for (int i = 0; i < aeList.size(); i++) {
                        AreaEmpleabilidad a = aeList.get(i);
                        if (i > 0) topAreas.append(", ");
                        topAreas.append(a.getArea()).append(" ").append(DF2.format(a.getEmpleabilidad())).append("%");
                    }
                    sb.append(csv("Areas con mayor empleabilidad: " + topAreas)).append("\r\n");
                    sb.append(csv("Oferta con poca demanda: priorizar revision de las razones criticas, las asignaturas de poca utilidad y la posibilidad real de contratacion, cruzando con las areas de mayor empleabilidad.")).append("\r\n");
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
                    sb.append("=== DEMANDA EMPRESARIAL Y RED ALUMNO  ===\r\n\r\n");
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
            // No se devuelve un CSV con una línea de error: se informa el fallo real.
            throw new RuntimeException("No se pudo construir el reporte '" + tipo + "': " + mensajeError(e), e);
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

    private static void appendAnalisisEmprendimientoCsv(
            StringBuilder sb,
            CalculadoraEmprendimiento.AnalisisExploratorio analisis) {
        sb.append("=== ECOSISTEMA DE EMPRENDIMIENTO E INNOVACIÓN ===\r\n");
        sb.append("=== ANÁLISIS EXPLORATORIO ===\r\n\r\n");
        sb.append("CASOS CON LAS TRES RESPUESTAS,").append(analisis.completos()).append("\r\n");
        sb.append("CASOS INCOMPLETOS,").append(analisis.incompletos()).append("\r\n\r\n");

        appendFrecuenciaEmpCsv(sb, "ORIGEN DEL EMPRENDIMIENTO (p30)", analisis.origen());
        appendFrecuenciaEmpCsv(sb, "TIPO DE ENTREGABLE (p31)", analisis.entregable());
        appendFrecuenciaEmpCsv(sb, "FINANCIAMIENTO (p32)", analisis.financiamiento());

        appendTablaCruceEmpCsv(sb, "ORIGEN × TIPO DE ENTREGABLE",
                analisis.origenEntregable(), "ORIGEN", "TIPO DE ENTREGABLE", true);
        appendTablaCruceEmpCsv(sb, "ORIGEN × FINANCIAMIENTO",
                analisis.origenFinanciamiento(), "ORIGEN", "FINANCIAMIENTO", true);
        appendTablaCruceEmpCsv(sb, "TIPO DE ENTREGABLE × FINANCIAMIENTO",
                analisis.entregableFinanciamiento(), "TIPO DE ENTREGABLE", "FINANCIAMIENTO", true);

        for (CalculadoraEmprendimiento.BloqueFinanciamiento bloque : analisis.bloques()) {
            appendTablaCruceEmpCsv(sb,
                    "ORIGEN × TIPO DE ENTREGABLE | " + bloque.financiamiento(),
                    bloque.tabla(), "ORIGEN", "TIPO DE ENTREGABLE", false);
        }

        if (!analisis.resumen().isEmpty()) {
            sb.append("=== RESUMEN ===\r\n");
            for (String linea : analisis.resumen()) {
                sb.append(csv(linea)).append("\r\n");
            }
            sb.append("\r\n");
        }
    }

    private static void appendAnalisisDesempleoCsv(
            StringBuilder sb,
            CalculadoraDesempleo.AnalisisDesempleo analisis) {
        sb.append("=== DESEMPLEO Y BARRERAS DE INSERCIÓN LABORAL ===\r\n");
        sb.append("=== ANÁLISIS EXPLORATORIO ===\r\n\r\n");
        sb.append("ENVÍOS,").append(analisis.total()).append("\r\n");
        sb.append("CASOS CON LAS TRES VARIABLES,").append(analisis.conTresVariables()).append("\r\n\r\n");

        appendFrecuenciaDesempCsv(sb, "SITUACIÓN LABORAL ACTUAL (p14)", analisis.situacion());
        appendFrecuenciaDesempCsv(sb, "TIEMPO TOTAL DE DESEMPLEO EN AÑOS (p13)", analisis.tiempoDesempleo());
        appendFrecuenciaDesempCsv(sb, "RAZÓN POR LA QUE NO TRABAJA (p28)", analisis.razon());
        appendFrecuenciaDesempCsv(sb, "AÑOS HASTA EL PRIMER EMPLEO (p36)", analisis.primerEmpleo());
        appendFrecuenciaDesempCsv(sb, "AÑOS DE VIDA PROFESIONAL (p12)", analisis.vidaProfesional());

        appendTablaCruceDesempCsv(sb, "SITUACIÓN LABORAL × TIEMPO DE DESEMPLEO",
                analisis.situacionTiempo(), "SITUACIÓN LABORAL", "TIEMPO DE DESEMPLEO", true);
        appendTablaCruceDesempCsv(sb, "MATRIZ DE BARRERAS: RAZÓN DE NO TRABAJAR × EXPERIENCIA PREVIA",
                analisis.razonExperiencia(), "RAZÓN", "EXPERIENCIA PREVIA", true);
        appendTablaCruceDesempCsv(sb, "RAZÓN DE NO TRABAJAR × AÑOS HASTA EL PRIMER EMPLEO",
                analisis.razonPrimerEmpleo(), "RAZÓN", "AÑOS HASTA EL PRIMER EMPLEO", true);
        appendTablaCruceDesempCsv(sb, "RAZÓN DE NO TRABAJAR × AÑOS DE VIDA PROFESIONAL",
                analisis.razonVidaProfesional(), "RAZÓN", "AÑOS DE VIDA PROFESIONAL", true);

        for (CalculadoraDesempleo.BloqueRazon bloque : analisis.bloques()) {
            appendTablaCruceDesempCsv(sb,
                    "ORIGEN DEL BLOQUE: " + bloque.razon(),
                    bloque.tabla(), "AÑOS HASTA EL PRIMER EMPLEO", "AÑOS DE VIDA PROFESIONAL", false);
        }

        for (CalculadoraDesempleo.DemandaEmpleador demanda : analisis.demandas()) {
            sb.append("=== DEMANDA DEL EMPLEADOR: ").append(demanda.titulo().toUpperCase()).append(" (n=")
                    .append(demanda.total()).append(") ===\r\n");
            sb.append("PALABRA_CLAVE,n,%\r\n");
            for (ConteoPalabra p : demanda.palabras()) {
                sb.append(csv(p.getPalabra())).append(",").append(p.getTotal()).append(",")
                        .append(DF2.format(p.getPorcentaje())).append("\r\n");
            }
            sb.append("\r\n");
        }

        if (!analisis.resumen().isEmpty()) {
            sb.append("=== RESUMEN ===\r\n");
            for (String linea : analisis.resumen()) {
                sb.append(csv(linea)).append("\r\n");
            }
            sb.append("\r\n");
        }
    }

    private static void appendFrecuenciaDesempCsv(
            StringBuilder sb,
            String titulo,
            List<CalculadoraDesempleo.Frecuencia> lista) {
        int total = 0;
        for (CalculadoraDesempleo.Frecuencia f : lista) total += f.total();
        sb.append("=== ").append(titulo).append(" (n=").append(total).append(") ===\r\n");
        sb.append("RESPUESTA,n,%\r\n");
        for (CalculadoraDesempleo.Frecuencia f : lista) {
            sb.append(csv(f.etiqueta())).append(",").append(f.total()).append(",")
                    .append(DF2.format(f.porcentaje())).append("\r\n");
        }
        sb.append(csv("Total")).append(",").append(total).append(",100.0\r\n\r\n");
    }

    private static void appendTablaCruceDesempCsv(
            StringBuilder sb,
            String titulo,
            CalculadoraDesempleo.TablaCruce tabla,
            String etiquetaFila,
            String etiquetaColumna,
            boolean porcentajeFila) {
        sb.append("=== ").append(titulo).append(" (n=").append(tabla.total()).append(") ===\r\n");
        sb.append(csv(etiquetaFila)).append(",").append(csv(etiquetaColumna))
                .append(",n,%\r\n");
        for (int i = 0; i < tabla.filas().size(); i++) {
            for (int j = 0; j < tabla.columnas().size(); j++) {
                int valor = tabla.celdas().get(i).get(j);
                int denominador = porcentajeFila ? tabla.totalesFila().get(i) : tabla.total();
                sb.append(csv(tabla.filas().get(i))).append(",")
                        .append(csv(tabla.columnas().get(j))).append(",")
                        .append(valor).append(",")
                        .append(DF2.format(pctDe(valor, denominador))).append("\r\n");
            }
        }
        sb.append("\r\n");
    }

    private static CalculadoraDesempleo.AnalisisDesempleo analisisDesempleo() throws Exception {
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

        return CalculadoraDesempleo.analisis(
                EncuestaDAO.obtenerRegistrosDesempleo(), demandas);
    }

    private static void appendFrecuenciaEmpCsv(
            StringBuilder sb,
            String titulo,
            List<CalculadoraEmprendimiento.Frecuencia> lista) {
        sb.append("=== ").append(titulo).append(" ===\r\n");
        sb.append("RESPUESTA,TOTAL,%\r\n");
        for (CalculadoraEmprendimiento.Frecuencia f : lista) {
            sb.append(csv(f.etiqueta())).append(",").append(f.total()).append(",")
                    .append(DF2.format(f.porcentaje())).append("\r\n");
        }
        sb.append("\r\n");
    }

    private static void appendTablaCruceEmpCsv(
            StringBuilder sb,
            String titulo,
            CalculadoraEmprendimiento.TablaCruce tabla,
            String etiquetaFila,
            String etiquetaColumna,
            boolean porcentajeFila) {
        sb.append("=== ").append(titulo).append(" ===\r\n");
        sb.append(csv(etiquetaFila)).append(",").append(csv(etiquetaColumna))
                .append(",TOTAL,%\r\n");
        for (int i = 0; i < tabla.filas().size(); i++) {
            for (int j = 0; j < tabla.columnas().size(); j++) {
                int valor = tabla.celdas().get(i).get(j);
                int denominador = porcentajeFila ? tabla.totalesFila().get(i) : tabla.total();
                sb.append(csv(tabla.filas().get(i))).append(",")
                        .append(csv(tabla.columnas().get(j))).append(",")
                        .append(valor).append(",")
                        .append(DF2.format(pctDe(valor, denominador))).append("\r\n");
            }
        }
        sb.append("\r\n");
    }

    private static String csv(String s) {
        if (s == null) return "\"\"";
        // Limpia espacios no romponibles (\u00A0) antes de escapar las comillas
        String limpio = s.replace('\u00A0', ' ').trim();
        String t = limpio.replace("\"", "\"\"");
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
        // CAMBIO: Se cambió "χ²" por "CHI2" para evitar que el PDF renderice "#²"
        sb.append("<table><tr><th>MODO</th><th>N</th><th>GL</th><th class='num'>CHI2</th><th class='num'>VALOR CRÍTICO</th><th class='num'>VALOR P</th><th>RECHAZA INDEPENDENCIA</th></tr>");
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
            // CAMBIO: Se quitó el carácter griego Σ que generaba "SUMA (#)"
            sb.append("<table><tr><th>GRUPO</th><th class='num'>n</th><th class='num'>SUMA</th><th class='num'>MEDIA</th><th class='num'>DESV. EST.</th><th>OBSERVACIÓN</th></tr>");
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

    // La lógica vive en CalculadoraEmprendimiento para que interfaz, exportadores y pruebas
    // compartan exactamente las mismas fórmulas (ver docs/emprendimiento-calculos.md).
    private static List<String> resumenEmprendimientoBullets(Map<Integer, List<ConteoRespuesta>> porPreg) {
        return CalculadoraEmprendimiento.bullets(aplanar(porPreg));
    }

    private static List<String> resumenEmprendimientoRecomendaciones(Map<Integer, List<ConteoRespuesta>> porPreg) {
        return CalculadoraEmprendimiento.recomendaciones(aplanar(porPreg));
    }

    private static List<ConteoRespuesta> aplanar(Map<Integer, List<ConteoRespuesta>> porPreg) {
        List<ConteoRespuesta> out = new ArrayList<>();
        for (List<ConteoRespuesta> lista : porPreg.values()) {
            out.addAll(lista);
        }
        return out;
    }

    // EXCEL

    public static void exportExcel(Context ctx) {
        String tipo = ctx.queryParam("tipo");
        if (tipo == null || tipo.isBlank()) tipo = "brechas";
        try (Workbook wb = generarExcel(tipo);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            wb.write(baos);
            String filename = "reporte_" + tipo + "_" + System.currentTimeMillis() + ".xlsx";
            ctx.res().setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            ctx.res().setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            ctx.result(new java.io.ByteArrayInputStream(baos.toByteArray()));
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Error al generar Excel: " + mensajeError(e));
        }
    }

    /** Devuelve el mensaje de la causa raíz para poder mostrarlo en la interfaz. */
    static String mensajeError(Throwable e) {
        Throwable causa = e;
        while (causa.getCause() != null && causa.getCause() != causa) causa = causa.getCause();
        String msg = causa.getMessage();
        return (msg == null || msg.isBlank()) ? causa.getClass().getSimpleName() : msg;
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
                    CalculadoraEmprendimiento.AnalisisExploratorio analisis =
                            CalculadoraEmprendimiento.analisisExploratorio(
                                    EncuestaDAO.obtenerRegistrosEmprendimiento());
                    LayoutEmprendimientoExcel layout = layoutEmprendimientoExcel(analisis);

                    Row titulo = sh.createRow(2);
                    titulo.createCell(0).setCellValue(
                            "ANÁLISIS EXPLORATORIO - " + analisis.completos() + " casos con las tres respuestas");
                    styleRow(titulo, headerStyle);

                    Row rFrec = sh.createRow(3);
                    rFrec.createCell(0).setCellValue("1. FRECUENCIAS");
                    styleRow(rFrec, headerStyle);

                    escribirFrecuenciaExcel(sh, layout.origenTitulo(), layout.origenHeader(),
                            layout.origenInicio(), "Origen del emprendimiento (p30)",
                            analisis.origen(), headerStyle);
                    escribirFrecuenciaExcel(sh, layout.entregableTitulo(), layout.entregableHeader(),
                            layout.entregableInicio(), "Tipo de entregable (p31)",
                            analisis.entregable(), headerStyle);
                    escribirFrecuenciaExcel(sh, layout.financiamientoTitulo(), layout.financiamientoHeader(),
                            layout.financiamientoInicio(), "Financiamiento (p32)",
                            analisis.financiamiento(), headerStyle);

                    Row rCruces = sh.createRow(layout.inicioCruces());
                    rCruces.createCell(0).setCellValue("2. CRUCES DE DOS VARIABLES");
                    styleRow(rCruces, headerStyle);

                    int fila = layout.inicioCruces() + 1;
                    fila = escribirTablaCruceExcel(sh, fila, headerStyle,
                            "ORIGEN × TIPO DE ENTREGABLE",
                            analisis.origenEntregable(), "ORIGEN", true) + 1;
                    fila = escribirTablaCruceExcel(sh, fila, headerStyle,
                            "ORIGEN × FINANCIAMIENTO",
                            analisis.origenFinanciamiento(), "ORIGEN", true) + 1;
                    fila = escribirTablaCruceExcel(sh, fila, headerStyle,
                            "TIPO DE ENTREGABLE × FINANCIAMIENTO",
                            analisis.entregableFinanciamiento(), "TIPO DE ENTREGABLE", true) + 1;

                    Row rTres = sh.createRow(fila++);
                    rTres.createCell(0).setCellValue("3. CRUCE DE TRES VARIABLES");
                    styleRow(rTres, headerStyle);
                    for (CalculadoraEmprendimiento.BloqueFinanciamiento bloque : analisis.bloques()) {
                        fila = escribirTablaCruceExcel(sh, fila, headerStyle,
                                "ORIGEN × TIPO DE ENTREGABLE | " + bloque.financiamiento()
                                        + " (n=" + bloque.total() + ")",
                                bloque.tabla(), "ORIGEN", false) + 1;
                    }

                    if (!analisis.resumen().isEmpty()) {
                        Row rResumen = sh.createRow(fila++);
                        rResumen.createCell(0).setCellValue("4. RESUMEN");
                        styleRow(rResumen, headerStyle);
                        CellStyle resumenStyle = sh.getWorkbook().createCellStyle();
                        resumenStyle.setWrapText(true);
                        resumenStyle.setVerticalAlignment(VerticalAlignment.TOP);
                        for (String linea : analisis.resumen()) {
                            Row rr = sh.createRow(fila++);
                            Cell celda = rr.createCell(0);
                            celda.setCellValue(linea);
                            celda.setCellStyle(resumenStyle);
                            rr.setHeightInPoints(28);
                            sh.addMergedRegion(new CellRangeAddress(rr.getRowNum(), rr.getRowNum(), 0, 7));
                        }
                    }
                }
                case "desempleo" -> {
                    rowIdx = escribirAnalisisDesempleoExcel(
                            sh, rowIdx, headerStyle, analisisDesempleo());
                }
                case "formacion" -> {
                    final int COL_GRAFICO = 8;
                    final int ANCHO_GRAFICO = 6;
                    int filaGrafico = 2;
                    rowIdx++;

                    // =====================================================================
                    // 1. TABLA CRUZADA: ÁREA DE INTERÉS (p9) x NIVEL DE POSGRADO (p4)
                    // =====================================================================
                    Row rTit1 = sh.createRow(rowIdx++);
                    rTit1.createCell(0).setCellValue("1. TABLA CRUZADA: ÁREA DE INTERÉS (p9) × NIVEL DE POSGRADO DESEADO (p4)");
                    styleRow(rTit1, headerStyle);

                    var cruceAN = EncuestaDAO.cruceAreaNivel();
                    var nivelesOrden = List.of("DIPLOMADO", "MAESTRIA", "ESPECIALIDAD", "DOCTORADO");
                    var matrizAN = new LinkedHashMap<String, Map<String, Integer>>();
                    var totFilaAN = new LinkedHashMap<String, Integer>();
                    int granTotalAN = 0;

                    for (Object obj : cruceAN) {
                        Map row = (Map) obj;
                        String area = String.valueOf(row.get("area"));
                        String nivel = String.valueOf(row.get("nivel")).toUpperCase();
                        int tot = ((Number) row.get("total")).intValue();
                        matrizAN.computeIfAbsent(area, k -> new LinkedHashMap<>()).put(nivel, tot);
                        totFilaAN.put(area, totFilaAN.getOrDefault(area, 0) + tot);
                        granTotalAN += tot;
                    }

                    var areasOrdenadas = new ArrayList<String>(totFilaAN.keySet());
                    areasOrdenadas.sort((a, b) -> Integer.compare(totFilaAN.get(b), totFilaAN.get(a)));

                    Row rHead1 = sh.createRow(rowIdx++);
                    rHead1.createCell(0).setCellValue("ÁREA DE INTERÉS");
                    for (int j = 0; j < nivelesOrden.size(); j++) {
                        rHead1.createCell(j + 1).setCellValue(nivelesOrden.get(j));
                    }
                    rHead1.createCell(nivelesOrden.size() + 1).setCellValue("TOTAL");
                    styleRow(rHead1, headerStyle);

                    int inicioTabla1 = rowIdx;
                    int[] totColAN = new int[nivelesOrden.size()];
                    for (String area : areasOrdenadas) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(area);
                        Map<String, Integer> filaMap = matrizAN.getOrDefault(area, Collections.emptyMap());
                        for (int j = 0; j < nivelesOrden.size(); j++) {
                            int val = filaMap.getOrDefault(nivelesOrden.get(j), 0);
                            totColAN[j] += val;
                            rr.createCell(j + 1).setCellValue(val);
                        }
                        rr.createCell(nivelesOrden.size() + 1).setCellValue(totFilaAN.getOrDefault(area, 0));
                    }
                    int finTabla1 = rowIdx - 1;

                    Row rTot1 = sh.createRow(rowIdx++);
                    rTot1.createCell(0).setCellValue("TOTAL GENERAL");
                    for (int j = 0; j < nivelesOrden.size(); j++) {
                        rTot1.createCell(j + 1).setCellValue(totColAN[j]);
                    }
                    rTot1.createCell(nivelesOrden.size() + 1).setCellValue(granTotalAN);
                    styleRow(rTot1, headerStyle);

                    if (finTabla1 >= inicioTabla1) {
                        crearGraficoBarrasNativo(sh, "Total de titulados por Área de Interés (p9 × p4)",
                                0, nivelesOrden.size() + 1,
                                inicioTabla1, finTabla1,
                                COL_GRAFICO, filaGrafico, ANCHO_GRAFICO, 18);
                        filaGrafico += 20;
                    }
                    rowIdx++;

                    // =====================================================================
                    // 2. PREFERENCIA INSTITUCIONAL: NIVEL (p4) x ORGANIZACIÓN (p10)
                    // =====================================================================
                    Row rTit2 = sh.createRow(rowIdx++);
                    rTit2.createCell(0).setCellValue("2. PREFERENCIA INSTITUCIONAL: NIVEL DE POSGRADO (p4) × ORGANIZACIÓN (p10)");
                    styleRow(rTit2, headerStyle);

                    Row rHead2 = sh.createRow(rowIdx++);
                    rHead2.createCell(0).setCellValue("NIVEL DE POSGRADO");
                    rHead2.createCell(1).setCellValue("ORGANIZACIÓN / INSTITUCIÓN");
                    rHead2.createCell(2).setCellValue("FRECUENCIA (ni)");
                    rHead2.createCell(3).setCellValue("%");
                    styleRow(rHead2, headerStyle);

                    var cruceNO = EncuestaDAO.cruceNivelOrganizacion(10);
                    int totalNO = 0;
                    for (Object obj : cruceNO) {
                        Map row = (Map) obj;
                        int freq = ((Number) row.get("total")).intValue();
                        double pct = ((Number) row.get("porcentaje")).doubleValue();
                        totalNO += freq;
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(String.valueOf(row.get("nivel")));
                        rr.createCell(1).setCellValue(String.valueOf(row.get("organizacion")));
                        rr.createCell(2).setCellValue(freq);
                        rr.createCell(3).setCellValue(pct);
                    }
                    Row rTot2 = sh.createRow(rowIdx++);
                    rTot2.createCell(0).setCellValue("TOTAL");
                    rTot2.createCell(1).setCellValue("");
                    rTot2.createCell(2).setCellValue(totalNO);
                    rTot2.createCell(3).setCellValue(100.0);
                    styleRow(rTot2, headerStyle);
                    rowIdx++;

                    // =====================================================================
                    // 3. NIVEL DE FORMACIÓN DESEADO (p4) - FRECUENCIAS
                    // =====================================================================
                    Row rTit3 = sh.createRow(rowIdx++);
                    rTit3.createCell(0).setCellValue("3. NIVEL DE FORMACIÓN DESEADO (p4) - FRECUENCIAS");
                    styleRow(rTit3, headerStyle);

                    Row rHead3 = sh.createRow(rowIdx++);
                    rHead3.createCell(0).setCellValue("NIVEL DESEADO");
                    rHead3.createCell(1).setCellValue("TOTAL");
                    rHead3.createCell(2).setCellValue("%");
                    styleRow(rHead3, headerStyle);

                    var np4 = EncuestaDAO.nivelesFormacionDeseados();
                    int tn4 = 0;
                    for (ConteoRespuesta c : np4) tn4 += c.getTotal();

                    int inicioTabla3 = rowIdx;
                    for (ConteoRespuesta c : np4) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(c.getRespuesta());
                        rr.createCell(1).setCellValue(c.getTotal());
                        rr.createCell(2).setCellValue(tn4 == 0 ? 0.0 : (c.getTotal() * 100.0 / tn4));
                    }
                    int finTabla3 = rowIdx - 1;

                    if (finTabla3 >= inicioTabla3) {
                        crearGraficoPastelNativo(sh, "Nivel de formación deseado (p4)",
                                0, 1,
                                inicioTabla3, finTabla3,
                                COL_GRAFICO, filaGrafico, ANCHO_GRAFICO, 18);
                        filaGrafico += 20;
                    }
                    rowIdx++;

                    // =====================================================================
                    // 4. MECANISMOS DE FINANCIAMIENTO: MATRIZ DE CONTINGENCIA (p4 x p6)
                    // =====================================================================
                    Row rTit4 = sh.createRow(rowIdx++);
                    rTit4.createCell(0).setCellValue("4. MECANISMOS DE FINANCIAMIENTO: MATRIZ DE CONTINGENCIA (p4 × p6)");
                    styleRow(rTit4, headerStyle);

                    var fin = EncuestaDAO.tablaFinanciamiento();
                    var filasF = (List) fin.get("filas");
                    var colsF = (List) fin.get("columnas");
                    var celdasF = (List) fin.get("celdas");

                    Row rhm = sh.createRow(rowIdx++);
                    rhm.createCell(0).setCellValue("NIVEL / FINANCIAMIENTO");
                    int kc = 1;
                    for (Object c : colsF) rhm.createCell(kc++).setCellValue(String.valueOf(c));
                    rhm.createCell(kc).setCellValue("TOTAL_FILA");
                    styleRow(rhm, headerStyle);

                    int inicioTabla4 = rowIdx;
                    for (Object fObj : filasF) {
                        String f = String.valueOf(fObj);
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(f);
                        int kk = 1;
                        for (Object cObj : colsF) {
                            rr.createCell(kk++).setCellValue(getCelda((List<CeldaCruce>) celdasF, f, String.valueOf(cObj)));
                        }
                        rr.createCell(kk).setCellValue(getTotalFila((List<CeldaCruce>) celdasF, f));
                    }
                    int finTabla4 = rowIdx - 1;

                    Row rtot = sh.createRow(rowIdx++);
                    rtot.createCell(0).setCellValue("TOTAL_COLUMNA");
                    int kkt = 1;
                    long NTf = getTotalGeneral((List<CeldaCruce>) celdasF);
                    for (Object cObj : colsF) {
                        rtot.createCell(kkt++).setCellValue(getTotalCol((List<CeldaCruce>) celdasF, String.valueOf(cObj)));
                    }
                    rtot.createCell(kkt).setCellValue(NTf);
                    styleRow(rtot, headerStyle);

                    if (finTabla4 >= inicioTabla4) {
                        crearGraficoBarrasNativo(sh, "Matriz p4 × p6: Total por Nivel",
                                0, colsF.size() + 1,
                                inicioTabla4, finTabla4,
                                COL_GRAFICO, filaGrafico, ANCHO_GRAFICO, 18);
                    }
                    rowIdx++;

                    // =====================================================================
                    // 5. DIAGNÓSTICO ESTADÍSTICO (Chi-cuadrado de Independencia)
                    // =====================================================================
                    ResultadoChi2 rchi = (ResultadoChi2) fin.get("chi2");
                    Boolean tieneF = (Boolean) fin.get("tieneDatos");
                    Row rhc = sh.createRow(rowIdx++);
                    rhc.createCell(0).setCellValue("DIAGNÓSTICO ESTADÍSTICO (Chi-cuadrado)");
                    styleRow(rhc, headerStyle);
                    if (rchi != null) {
                        addKV(sh, rowIdx++, "Chi-cuadrado", rchi.getChiCuadrado());
                        addKV(sh, rowIdx++, "gl", rchi.getGradosLibertad());
                        addKV(sh, rowIdx++, "p-valor", rchi.getValorP());
                        addKV(sh, rowIdx++, "Independiente (p >= 0.05)", rchi.isIndependiente() ? "SÍ" : "NO");
                        addKV(sh, rowIdx++, "Interpretación", rchi.getInterpretacion());
                        addKV(sh, rowIdx++, "Datos suficientes", (tieneF != null && tieneF) ? "SÍ" : "NO");
                    }
                }
                case "empleabilidad" -> {
                    final int COL_GRAFICO = 6;
                    final int ANCHO_GRAFICO = 6;
                    int filaGrafico = 2;
                    rowIdx++;

                    // =====================================================================
                    // 1. ESTADÍSTICOS DE ANTIGÜEDAD PROFESIONAL (p12)
                    // =====================================================================
                    Row rTit1 = sh.createRow(rowIdx++);
                    rTit1.createCell(0).setCellValue("1. ESTADÍSTICOS DE ANTIGÜEDAD PROFESIONAL (p12)");
                    styleRow(rTit1, headerStyle);

                    Row rHead1 = sh.createRow(rowIdx++);
                    rHead1.createCell(0).setCellValue("INDICADOR");
                    rHead1.createCell(1).setCellValue("VALOR");
                    styleRow(rHead1, headerStyle);

                    var respP12 = EncuestaDAO.contarRespuestas(12);
                    var valsAnt = new ArrayList<Double>();
                    var freqAnt = new LinkedHashMap<Double, Integer>();
                    for (ConteoRespuesta c : respP12) {
                        if (c.getRespuesta() == null) continue;
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:[.,]\\d+)?)").matcher(c.getRespuesta());
                        if (m.find()) {
                            double v = Double.parseDouble(m.group(1).replace(',', '.'));
                            int tot = c.getTotal();
                            freqAnt.put(v, freqAnt.getOrDefault(v, 0) + tot);
                            for (int i = 0; i < tot; i++) valsAnt.add(v);
                        }
                    }
                    Collections.sort(valsAnt);
                    int nValid = valsAnt.size();
                    double sumaAnt = 0;
                    for (double v : valsAnt) sumaAnt += v;
                    double mediaAnt = nValid == 0 ? 0.0 : sumaAnt / nValid;
                    double medianaAnt = 0.0;
                    if (nValid > 0) {
                        medianaAnt = (nValid % 2 == 1)
                                ? valsAnt.get(nValid / 2)
                                : (valsAnt.get(nValid / 2 - 1) + valsAnt.get(nValid / 2)) / 2.0;
                    }
                    double modaAnt = 0.0;
                    int maxFreqAnt = -1;
                    for (var entry : freqAnt.entrySet()) {
                        if (entry.getValue() > maxFreqAnt) {
                            maxFreqAnt = entry.getValue();
                            modaAnt = entry.getKey();
                        }
                    }
                    double sumSq = 0;
                    for (double v : valsAnt) sumSq += Math.pow(v - mediaAnt, 2);
                    double desvAnt = nValid > 1 ? Math.sqrt(sumSq / (nValid - 1)) : 0.0;

                    addKV(sh, rowIdx++, "Titulados válidos", nValid);
                    addKV(sh, rowIdx++, "Media (años)", Double.parseDouble(DF2.format(mediaAnt)));
                    addKV(sh, rowIdx++, "Mediana (años)", Double.parseDouble(DF2.format(medianaAnt)));
                    addKV(sh, rowIdx++, "Moda (años)", Double.parseDouble(DF2.format(modaAnt)));
                    addKV(sh, rowIdx++, "Desv. Estándar (años)", Double.parseDouble(DF2.format(desvAnt)));
                    rowIdx++;

                    // =====================================================================
                    // 2. DEMANDA DE EMPRESAS VS. INTERÉS DE GRADUADOS
                    // =====================================================================
                    Row rTit2 = sh.createRow(rowIdx++);
                    rTit2.createCell(0).setCellValue("2. DEMANDA DE EMPRESAS VS. INTERÉS DE GRADUADOS");
                    styleRow(rTit2, headerStyle);

                    Row rhAe = sh.createRow(rowIdx++);
                    rhAe.createCell(0).setCellValue("ÁREA");
                    rhAe.createCell(1).setCellValue("DEMANDA_EMPLEADOR(%)");
                    rhAe.createCell(2).setCellValue("INTERÉS_TITULADOS(%)");
                    rhAe.createCell(3).setCellValue("EMPLEABILIDAD(%)");
                    styleRow(rhAe, headerStyle);

                    var aeL = EncuestaDAO.analisisEmpleabilidad();
                    int inicioAe = rowIdx;
                    for (AreaEmpleabilidad a : aeL) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(a.getArea());
                        rr.createCell(1).setCellValue(a.getDemandaEmpleador());
                        rr.createCell(2).setCellValue(a.getInteresTitulados());
                        rr.createCell(3).setCellValue(a.getEmpleabilidad());
                    }
                    int finAe = rowIdx - 1;
                    if (finAe >= inicioAe) {
                        crearGraficoBarrasNativo(sh, "Índice de Empleabilidad por Área (%)",
                                0, 3,
                                inicioAe, finAe,
                                COL_GRAFICO, filaGrafico, ANCHO_GRAFICO, 16);
                        filaGrafico += 18;
                    }
                    rowIdx++;

                    // =====================================================================
                    // 3. MOTIVOS PRINCIPALES DE DESEMPLEO (p28 - TODAS LAS RAZONES)
                    // =====================================================================
                    Row rTit3 = sh.createRow(rowIdx++);
                    rTit3.createCell(0).setCellValue("3. MOTIVOS PRINCIPALES DE DESEMPLEO (p28)");
                    styleRow(rTit3, headerStyle);

                    Row rHead3 = sh.createRow(rowIdx++);
                    rHead3.createCell(0).setCellValue("RAZÓN");
                    rHead3.createCell(1).setCellValue("TOTAL");
                    rHead3.createCell(2).setCellValue("%");
                    styleRow(rHead3, headerStyle);

                    var motivos28 = EncuestaDAO.contarRespuestas(28);
                    int nMot = 0;
                    for (ConteoRespuesta c : motivos28) nMot += c.getTotal();
                    int inicioMot = rowIdx;
                    for (ConteoRespuesta c : motivos28) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(c.getRespuesta());
                        rr.createCell(1).setCellValue(c.getTotal());
                        rr.createCell(2).setCellValue(nMot == 0 ? 0.0 : (c.getTotal() * 100.0 / nMot));
                    }
                    int finMot = rowIdx - 1;
                    if (finMot >= inicioMot) {
                        crearGraficoBarrasNativo(sh, "Motivos Principales de Desempleo (p28)",
                                0, 1,
                                inicioMot, finMot,
                                COL_GRAFICO, filaGrafico, ANCHO_GRAFICO, 16);
                        filaGrafico += 18;
                    }
                    rowIdx++;

                    // =====================================================================
                    // 4. MATERIAS CON MENOR IMPACTO LABORAL (p44 - % SOBRE TITULADOS)
                    // =====================================================================
                    Row rTit4 = sh.createRow(rowIdx++);
                    rTit4.createCell(0).setCellValue("4. MATERIAS CON MENOR IMPACTO LABORAL (p44)");
                    styleRow(rTit4, headerStyle);

                    Row rHead4 = sh.createRow(rowIdx++);
                    rHead4.createCell(0).setCellValue("ASIGNATURA");
                    rHead4.createCell(1).setCellValue("TOTAL");
                    rHead4.createCell(2).setCellValue("% TITULADOS");
                    styleRow(rHead4, headerStyle);

                    var asig44 = EncuestaDAO.asignaturasFrecuencia(44);
                    int inicioAsig = rowIdx;
                    for (ConteoRespuesta c : asig44) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(c.getRespuesta());
                        rr.createCell(1).setCellValue(c.getTotal());
                        rr.createCell(2).setCellValue(c.getPorcentaje());
                    }
                    int finAsig = rowIdx - 1;
                    if (finAsig >= inicioAsig) {
                        crearGraficoBarrasNativo(sh, "Materias con Menor Impacto Laboral (p44)",
                                0, 1,
                                inicioAsig, finAsig,
                                COL_GRAFICO, filaGrafico, ANCHO_GRAFICO, 18);
                        filaGrafico += 20;
                    }
                    rowIdx++;

                    // =====================================================================
                    // 5. PERFIL DEL SECTOR EMPRESARIAL: RUBRO PRINCIPAL (p53)
                    // =====================================================================
                    Row rTit5 = sh.createRow(rowIdx++);
                    rTit5.createCell(0).setCellValue("5. PERFIL DEL SECTOR EMPRESARIAL: RUBRO PRINCIPAL (p53)");
                    styleRow(rTit5, headerStyle);

                    Row rHead5 = sh.createRow(rowIdx++);
                    rHead5.createCell(0).setCellValue("RUBRO");
                    rHead5.createCell(1).setCellValue("TOTAL");
                    rHead5.createCell(2).setCellValue("%");
                    styleRow(rHead5, headerStyle);

                    var p53 = EncuestaDAO.contarRespuestas(53);
                    int N53 = 0;
                    for (ConteoRespuesta c : p53) N53 += c.getTotal();
                    for (ConteoRespuesta c : p53) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(c.getRespuesta());
                        rr.createCell(1).setCellValue(c.getTotal());
                        rr.createCell(2).setCellValue(N53 == 0 ? 0.0 : (c.getTotal() * 100.0 / N53));
                    }
                    rowIdx++;

                    // =====================================================================
                    // 6. PERFIL DEL SECTOR EMPRESARIAL: POSIBILIDAD DE INCORPORAR (p55)
                    // =====================================================================
                    Row rTit6 = sh.createRow(rowIdx++);
                    rTit6.createCell(0).setCellValue("6. PERFIL DEL SECTOR EMPRESARIAL: POSIBILIDAD DE INCORPORAR (p55)");
                    styleRow(rTit6, headerStyle);

                    Row rHead6 = sh.createRow(rowIdx++);
                    rHead6.createCell(0).setCellValue("OPCIÓN");
                    rHead6.createCell(1).setCellValue("TOTAL");
                    rHead6.createCell(2).setCellValue("%");
                    styleRow(rHead6, headerStyle);

                    var p55 = EncuestaDAO.contarRespuestas(55);
                    int N55 = 0;
                    for (ConteoRespuesta c : p55) N55 += c.getTotal();
                    int inicioP55 = rowIdx;
                    for (ConteoRespuesta c : p55) {
                        Row rr = sh.createRow(rowIdx++);
                        rr.createCell(0).setCellValue(c.getRespuesta());
                        rr.createCell(1).setCellValue(c.getTotal());
                        rr.createCell(2).setCellValue(N55 == 0 ? 0.0 : (c.getTotal() * 100.0 / N55));
                    }
                    int finP55 = rowIdx - 1;
                    if (finP55 >= inicioP55) {
                        crearGraficoPastelNativo(sh, "Posibilidad de Incorporar Titulados (p55)",
                                0, 1,
                                inicioP55, finP55,
                                COL_GRAFICO, filaGrafico, ANCHO_GRAFICO, 16);
                    }
                    rowIdx++;

                    // =====================================================================
                    // 7. RESUMEN Y RECOMENDACIÓN
                    // =====================================================================
                    Row rTit7 = sh.createRow(rowIdx++);
                    rTit7.createCell(0).setCellValue("7. RESUMEN Y RECOMENDACIÓN");
                    styleRow(rTit7, headerStyle);

                    StringBuilder topAreas = new StringBuilder();
                    for (int i = 0; i < aeL.size(); i++) {
                        AreaEmpleabilidad a = aeL.get(i);
                        if (i > 0) topAreas.append(", ");
                        topAreas.append(a.getArea()).append(" ").append(DF2.format(a.getEmpleabilidad())).append("%");
                    }
                    Row rRes1 = sh.createRow(rowIdx++);
                    rRes1.createCell(0).setCellValue("Áreas con mayor empleabilidad: " + topAreas);
                    Row rRes2 = sh.createRow(rowIdx++);
                    rRes2.createCell(0).setCellValue("Oferta con poca demanda: priorizar revisión de las razones críticas, las asignaturas de poca utilidad y la posibilidad real de contratación, cruzando con las áreas de mayor empleabilidad.");
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
            // Antes se escribía "ERROR: ..." dentro de la hoja y se devolvía 200 con un
            // archivo incompleto. Ahora se propaga para informar el fallo real.
            try { wb.close(); } catch (Exception ignored) {}
            throw new IOException("No se pudo construir el reporte '" + tipo + "': " + mensajeError(e), e);
        }

        // Gráficos embebidos (según el reporte)
        try {
            agregarGraficosExcel(wb, sh, tipo);
        } catch (Exception ex) {
            System.err.println("Aviso: no se pudieron agregar los gráficos del reporte '" + tipo + "': " + ex);
        }

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
                    int inicioP42 = 22; // Fila 23 de Excel (Base de Datos)
                    int finP42 = 30;    // Fila 31 de Excel (Básicas: Física, Cálculo...)

                    crearGraficoBarrasNativo(sh, "Asignaturas de mayor ventaja (p42)", 
                                            0, 1, 
                                            inicioP42, finP42, 
                                            COL, row, ANCHO_COLS, 22);
                    row += 24;

                    int inicioP44 = 62; // Fila 63 de Excel (Aplic. Interactivas para Televisión Digital)
                    int finP44 = 72;    // Fila 73 de Excel (Programación Funcional)

                    crearGraficoBarrasNativo(sh, "Asignaturas de poca utilidad (p44)", 
                                            0, 1, 
                                            inicioP44, finP44, 
                                            COL, row, ANCHO_COLS, 22);
                }
                case "emprendimiento" -> {
                    CalculadoraEmprendimiento.AnalisisExploratorio analisis =
                            CalculadoraEmprendimiento.analisisExploratorio(
                                    EncuestaDAO.obtenerRegistrosEmprendimiento());
                    LayoutEmprendimientoExcel layout = layoutEmprendimientoExcel(analisis);
                    int filaGrafico = layout.origenInicio();

                    if (!analisis.origen().isEmpty()) {
                        crearGraficoBarrasNativo(sh, "Origen del emprendimiento (p30)",
                                0, 1,
                                layout.origenInicio(), layout.origenFin(),
                                COL, filaGrafico, ANCHO_COLS, 18);
                    }
                    if (!analisis.entregable().isEmpty()) {
                        crearGraficoBarrasNativo(sh, "Tipo de entregable (p31)",
                                0, 1,
                                layout.entregableInicio(), layout.entregableFin(),
                                COL, filaGrafico + 20, ANCHO_COLS, 18);
                    }
                    if (!analisis.financiamiento().isEmpty()) {
                        crearGraficoBarrasNativo(sh, "Financiamiento (p32)",
                                0, 1,
                                layout.financiamientoInicio(), layout.financiamientoFin(),
                                COL, filaGrafico + 40, ANCHO_COLS, 18);
                    }
                }
                case "desempleo" -> {
                    // Las frecuencias ya se escriben con su tabla y total n en la hoja.
                }
                case "formacion" -> {
                    // Los gráficos ya se generan dinámicamente en generarExcel
                }
                case "empleabilidad" -> {
                    // Los gráficos nativos de empleabilidad se generan dinámicamente en generarExcel
                }
                case "ubicacion" -> {
                    // 1. Gráfico Nativo de Barras - ¿Dónde trabajan los titulados? (p19) -> Filas 9 a 13 (Índices 9 a 13)
                    // Toma categorías en Columna A (índice 0) y valores de Total en Columna B (índice 1)
                    crearGraficoBarrasNativo(sh, "¿Dónde trabajan los titulados? (p19)", 
                                            0, 1, 
                                            9, 13, 
                                            COL, 3, ANCHO_COLS, 18);

                    // 2. Gráfico Nativo de Barras - Salario promedio mensual por departamento (p27 x p19) -> Filas 17 a 21 (Índices 17 a 21)
                    // Toma categorías en Columna A (índice 0) y valores de Media en Columna D (índice 3)
                    crearGraficoBarrasNativo(sh, "Salario promedio mensual por departamento (Bs.)", 
                                            0, 3, 
                                            17, 21, 
                                            COL, 23, ANCHO_COLS, 18);

                    // 3. Gráfico Nativo de Barras - Pertinencia promedio por cargo (1-4) -> Filas 27 a 30 (Índices 27 a 30)
                    // Toma categorías en Columna A (índice 0) y valores de Media en Columna D (índice 3)
                    crearGraficoBarrasNativo(sh, "Pertinencia promedio por cargo (Escala 1-4)", 
                                            0, 3, 
                                            27, 30, 
                                            COL, 43, ANCHO_COLS, 18);
                }
                case "hu12" -> {
                    int proximaFilaLibreGrafico = 3;

                    // Agrupamos las listas de preguntas (HU12_DEMANDA y HU12_PERCEPCION son de tipo List<Integer>)
                    List<List<Integer>> grupos = List.of(HU12_DEMANDA, HU12_PERCEPCION);

                    for (List<Integer> grupo : grupos) {
                        for (int pid : grupo) {
                            Map<String, Object> f = ReporteHu12DAO.obtenerFrecuencias(2, pid);
                            var cats = (List<?>) f.get("categorias");
                            int numFilasData = (cats != null) ? cats.size() : 0;

                            if (numFilasData > 0 && numFilasData <= 12) {
                                // 1. Escanear la hoja para encontrar la fila del título exacto de la pregunta (ej: "P54:")
                                String prefijoBusqueda = "P" + pid + ":";
                                int filaTituloPoi = -1;

                                for (int r = 0; r <= sh.getLastRowNum(); r++) {
                                    Row fila = sh.getRow(r);
                                    if (fila != null && fila.getCell(0) != null) {
                                        String valCell = fila.getCell(0).getStringCellValue();
                                        if (valCell != null && valCell.startsWith(prefijoBusqueda)) {
                                            filaTituloPoi = r; // Fila exacta del título en índice POI (base 0)
                                            break;
                                        }
                                    }
                                }

                                // 2. Si la pregunta se encontró en la hoja, calculamos el rango exacto de sus datos
                                if (filaTituloPoi != -1) {
                                    // Fila del título = filaTituloPoi
                                    // Fila del encabezado "RESPUESTA" = filaTituloPoi + 1
                                    // Primera fila de datos reales = filaTituloPoi + 2
                                    int inicioDataPoi = filaTituloPoi + 2;
                                    int finDataPoi = inicioDataPoi + numFilasData - 1;

                                    // Posicionar el gráfico alineado a la tabla sin solapar los anteriores
                                    int filaPosicionGrafico = Math.max(filaTituloPoi, proximaFilaLibreGrafico);

                                    String tituloGrafico = "P" + pid + ": " + HU12_TITULOS.getOrDefault(pid, "");
                                    if (tituloGrafico.length() > 45) {
                                        tituloGrafico = tituloGrafico.substring(0, 42) + "...";
                                    }

                                    // Generar gráfico nativo vinculado ÚNICAMENTE a las respuestas reales (sin "RESPUESTA")
                                    crearGraficoBarrasNativo(sh, tituloGrafico,
                                                            0, 1, // Categorías en Columna A (0), Valores en Columna B (1)
                                                            inicioDataPoi, finDataPoi,
                                                            COL, filaPosicionGrafico, ANCHO_COLS, 18);

                                    proximaFilaLibreGrafico = filaPosicionGrafico + 19;
                                }
                            }
                        }
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

    private record LayoutEmprendimientoExcel(
            int origenTitulo, int origenHeader, int origenInicio, int origenFin,
            int entregableTitulo, int entregableHeader, int entregableInicio, int entregableFin,
            int financiamientoTitulo, int financiamientoHeader, int financiamientoInicio, int financiamientoFin,
            int inicioCruces) {
    }

    private static LayoutEmprendimientoExcel layoutEmprendimientoExcel(
            CalculadoraEmprendimiento.AnalisisExploratorio analisis) {
        int row = 4;

        int origenTitulo = row++;
        int origenHeader = row++;
        int origenInicio = row;
        row += Math.max(1, analisis.origen().size());
        int origenFin = row - 1;
        row += 2;

        int entregableTitulo = row++;
        int entregableHeader = row++;
        int entregableInicio = row;
        row += Math.max(1, analisis.entregable().size());
        int entregableFin = row - 1;
        row += 2;

        int financiamientoTitulo = row++;
        int financiamientoHeader = row++;
        int financiamientoInicio = row;
        row += Math.max(1, analisis.financiamiento().size());
        int financiamientoFin = row - 1;
        row += 2;

        return new LayoutEmprendimientoExcel(
                origenTitulo, origenHeader, origenInicio, origenFin,
                entregableTitulo, entregableHeader, entregableInicio, entregableFin,
                financiamientoTitulo, financiamientoHeader, financiamientoInicio, financiamientoFin,
                row);
    }

    private static void escribirFrecuenciaExcel(
            Sheet sh, int tituloRow, int headerRow, int inicio,
            String titulo, List<CalculadoraEmprendimiento.Frecuencia> lista,
            CellStyle headerStyle) {
        Row rt = sh.createRow(tituloRow);
        rt.createCell(0).setCellValue(titulo);
        styleRow(rt, headerStyle);

        Row rh = sh.createRow(headerRow);
        rh.createCell(0).setCellValue("CATEGORÍA");
        rh.createCell(1).setCellValue("TOTAL");
        rh.createCell(2).setCellValue("%");
        styleRow(rh, headerStyle);

        int fila = inicio;
        if (lista == null || lista.isEmpty()) {
            sh.createRow(fila).createCell(0).setCellValue("Sin datos");
            return;
        }
        for (CalculadoraEmprendimiento.Frecuencia f : lista) {
            Row rr = sh.createRow(fila++);
            rr.createCell(0).setCellValue(f.etiqueta());
            rr.createCell(1).setCellValue(f.total());
            rr.createCell(2).setCellValue(DF2.format(f.porcentaje()));
        }
    }

    private static int escribirTablaCruceExcel(
            Sheet sh, int rowIdx, CellStyle headerStyle, String titulo,
            CalculadoraEmprendimiento.TablaCruce tabla,
            String etiquetaFila, boolean porcentajeFila) {
        int ri = rowIdx;

        Row rt = sh.createRow(ri++);
        rt.createCell(0).setCellValue(titulo);
        styleRow(rt, headerStyle);

        Row rh = sh.createRow(ri++);
        int col = 0;
        rh.createCell(col++).setCellValue(etiquetaFila);
        String etiquetaPct = porcentajeFila ? " (% fila)" : " (% bloque)";
        for (String columna : tabla.columnas()) {
            rh.createCell(col++).setCellValue(columna + " (n)");
            rh.createCell(col++).setCellValue(columna + etiquetaPct);
        }
        rh.createCell(col).setCellValue("Total");
        styleRow(rh, headerStyle);

        for (int i = 0; i < tabla.filas().size(); i++) {
            Row rr = sh.createRow(ri++);
            col = 0;
            rr.createCell(col++).setCellValue(tabla.filas().get(i));
            for (int j = 0; j < tabla.columnas().size(); j++) {
                int valor = tabla.celdas().get(i).get(j);
                int denominador = porcentajeFila
                        ? tabla.totalesFila().get(i)
                        : tabla.total();
                rr.createCell(col++).setCellValue(valor);
                rr.createCell(col++).setCellValue(DF2.format(pctDe(valor, denominador)));
            }
            rr.createCell(col).setCellValue(tabla.totalesFila().get(i));
        }

        Row tr = sh.createRow(ri++);
        col = 0;
        tr.createCell(col++).setCellValue("Total");
        for (int j = 0; j < tabla.columnas().size(); j++) {
            tr.createCell(col++).setCellValue(tabla.totalesColumna().get(j));
            tr.createCell(col++).setCellValue("");
        }
        tr.createCell(col).setCellValue(tabla.total());
        styleRow(tr, headerStyle);
        return ri;
    }

    private static int escribirAnalisisDesempleoExcel(
            Sheet sh, int rowIdx, CellStyle headerStyle,
            CalculadoraDesempleo.AnalisisDesempleo analisis) {
        int ri = rowIdx;

        Row titulo = sh.createRow(ri++);
        titulo.createCell(0).setCellValue("ANÁLISIS EXPLORATORIO - DESEMPLEO Y BARRERAS");
        styleRow(titulo, headerStyle);

        Row meta = sh.createRow(ri++);
        meta.createCell(0).setCellValue("Envíos: " + analisis.total()
                + " · Casos con las tres variables: " + analisis.conTresVariables());
        ri++;

        Row rFrec = sh.createRow(ri++);
        rFrec.createCell(0).setCellValue("1. FRECUENCIAS");
        styleRow(rFrec, headerStyle);

        ri = escribirFrecuenciaDesempExcel(sh, ri, headerStyle,
                "Situación laboral actual (p14)", analisis.situacion()) + 1;
        ri = escribirFrecuenciaDesempExcel(sh, ri, headerStyle,
                "Tiempo total de desempleo en años (p13)", analisis.tiempoDesempleo()) + 1;
        ri = escribirFrecuenciaDesempExcel(sh, ri, headerStyle,
                "Razón por la que no trabaja (p28)", analisis.razon()) + 1;
        ri = escribirFrecuenciaDesempExcel(sh, ri, headerStyle,
                "Años hasta el primer empleo (p36)", analisis.primerEmpleo()) + 1;
        ri = escribirFrecuenciaDesempExcel(sh, ri, headerStyle,
                "Años de vida profesional (p12)", analisis.vidaProfesional()) + 1;

        Row rCruces = sh.createRow(ri++);
        rCruces.createCell(0).setCellValue("2. CRUCES DE DOS VARIABLES");
        styleRow(rCruces, headerStyle);

        ri = escribirTablaCruceDesempExcel(sh, ri, headerStyle,
                "SITUACIÓN LABORAL × TIEMPO DE DESEMPLEO",
                analisis.situacionTiempo(), "SITUACIÓN LABORAL", true) + 1;
        ri = escribirTablaCruceDesempExcel(sh, ri, headerStyle,
                "MATRIZ DE BARRERAS: RAZÓN DE NO TRABAJAR × EXPERIENCIA PREVIA",
                analisis.razonExperiencia(), "RAZÓN", true) + 1;
        ri = escribirTablaCruceDesempExcel(sh, ri, headerStyle,
                "RAZÓN DE NO TRABAJAR × AÑOS HASTA EL PRIMER EMPLEO",
                analisis.razonPrimerEmpleo(), "RAZÓN", true) + 1;
        ri = escribirTablaCruceDesempExcel(sh, ri, headerStyle,
                "RAZÓN DE NO TRABAJAR × AÑOS DE VIDA PROFESIONAL",
                analisis.razonVidaProfesional(), "RAZÓN", true) + 1;

        Row rTres = sh.createRow(ri++);
        rTres.createCell(0).setCellValue("3. CRUCE DE TRES VARIABLES");
        styleRow(rTres, headerStyle);

        for (CalculadoraDesempleo.BloqueRazon bloque : analisis.bloques()) {
            ri = escribirTablaCruceDesempExcel(sh, ri, headerStyle,
                    "AÑOS HASTA EL PRIMER EMPLEO × AÑOS DE VIDA PROFESIONAL | "
                            + bloque.razon(),
                    bloque.tabla(), "AÑOS HASTA EL PRIMER EMPLEO", false) + 1;
        }

        Row rDemandas = sh.createRow(ri++);
        rDemandas.createCell(0).setCellValue("4. DEMANDAS DEL EMPLEADOR");
        styleRow(rDemandas, headerStyle);

        for (CalculadoraDesempleo.DemandaEmpleador demanda : analisis.demandas()) {
            Row rt = sh.createRow(ri++);
            rt.createCell(0).setCellValue(demanda.titulo() + " (n=" + demanda.total() + ")");
            styleRow(rt, headerStyle);
            Row rh = sh.createRow(ri++);
            rh.createCell(0).setCellValue("PALABRA CLAVE");
            rh.createCell(1).setCellValue("n");
            rh.createCell(2).setCellValue("%");
            styleRow(rh, headerStyle);
            for (ConteoPalabra p : demanda.palabras()) {
                Row rr = sh.createRow(ri++);
                rr.createCell(0).setCellValue(p.getPalabra());
                rr.createCell(1).setCellValue(p.getTotal());
                rr.createCell(2).setCellValue(DF2.format(p.getPorcentaje()));
            }
            ri++;
        }

        if (!analisis.resumen().isEmpty()) {
            Row rResumen = sh.createRow(ri++);
            rResumen.createCell(0).setCellValue("5. RESUMEN");
            styleRow(rResumen, headerStyle);
            CellStyle resumenStyle = sh.getWorkbook().createCellStyle();
            resumenStyle.setWrapText(true);
            resumenStyle.setVerticalAlignment(VerticalAlignment.TOP);
            for (String linea : analisis.resumen()) {
                Row rr = sh.createRow(ri++);
                Cell celda = rr.createCell(0);
                celda.setCellValue(linea);
                celda.setCellStyle(resumenStyle);
                rr.setHeightInPoints(30);
                sh.addMergedRegion(new CellRangeAddress(rr.getRowNum(), rr.getRowNum(), 0, 7));
            }
        }
        return ri;
    }

    private static int escribirFrecuenciaDesempExcel(
            Sheet sh, int rowIdx, CellStyle headerStyle, String titulo,
            List<CalculadoraDesempleo.Frecuencia> lista) {
        int ri = rowIdx;
        int total = 0;
        for (CalculadoraDesempleo.Frecuencia f : lista) total += f.total();

        Row rt = sh.createRow(ri++);
        rt.createCell(0).setCellValue(titulo + " (n=" + total + ")");
        styleRow(rt, headerStyle);

        Row rh = sh.createRow(ri++);
        rh.createCell(0).setCellValue("CATEGORÍA");
        rh.createCell(1).setCellValue("n");
        rh.createCell(2).setCellValue("%");
        styleRow(rh, headerStyle);

        for (CalculadoraDesempleo.Frecuencia f : lista) {
            Row rr = sh.createRow(ri++);
            rr.createCell(0).setCellValue(f.etiqueta());
            rr.createCell(1).setCellValue(f.total());
            rr.createCell(2).setCellValue(DF2.format(f.porcentaje()));
        }
        Row rt2 = sh.createRow(ri++);
        rt2.createCell(0).setCellValue("Total");
        rt2.createCell(1).setCellValue(total);
        rt2.createCell(2).setCellValue("100.0");
        styleRow(rt2, headerStyle);
        return ri;
    }

    private static int escribirTablaCruceDesempExcel(
            Sheet sh, int rowIdx, CellStyle headerStyle, String titulo,
            CalculadoraDesempleo.TablaCruce tabla,
            String etiquetaFila, boolean porcentajeFila) {
        int ri = rowIdx;

        Row rt = sh.createRow(ri++);
        rt.createCell(0).setCellValue(titulo + " (n=" + tabla.total() + ")");
        styleRow(rt, headerStyle);

        Row rh = sh.createRow(ri++);
        int col = 0;
        rh.createCell(col++).setCellValue(etiquetaFila);
        for (String columna : tabla.columnas()) {
            rh.createCell(col++).setCellValue(columna + " (n)");
            rh.createCell(col++).setCellValue(columna + (porcentajeFila ? " (% fila)" : " (% bloque)"));
        }
        rh.createCell(col).setCellValue("Total");
        styleRow(rh, headerStyle);

        for (int i = 0; i < tabla.filas().size(); i++) {
            Row rr = sh.createRow(ri++);
            col = 0;
            rr.createCell(col++).setCellValue(tabla.filas().get(i));
            for (int j = 0; j < tabla.columnas().size(); j++) {
                int valor = tabla.celdas().get(i).get(j);
                int denominador = porcentajeFila ? tabla.totalesFila().get(i) : tabla.total();
                rr.createCell(col++).setCellValue(valor);
                rr.createCell(col++).setCellValue(DF2.format(pctDe(valor, denominador)));
            }
            rr.createCell(col).setCellValue(tabla.totalesFila().get(i));
        }

        Row tr = sh.createRow(ri++);
        col = 0;
        tr.createCell(col++).setCellValue("Total");
        for (int j = 0; j < tabla.columnas().size(); j++) {
            tr.createCell(col++).setCellValue(tabla.totalesColumna().get(j));
            tr.createCell(col++).setCellValue("");
        }
        tr.createCell(col).setCellValue(tabla.total());
        styleRow(tr, headerStyle);
        return ri;
    }

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

            /**
         * Aplica un formato condicional tipo Mapa de Calor (Escala de Colores) a un rango de celdas.
         */
        private static void aplicarMapaDeCalor(Sheet sh, int filaInicio, int filaFin, int colInicio, int colFin) {
            SheetConditionalFormatting conditionalFormatting = sh.getSheetConditionalFormatting();
            CellRangeAddress[] regions = { new CellRangeAddress(filaInicio, filaFin, colInicio, colFin) };

            // 1. Creación correcta de la regla usando el método nativo de Apache POI
            org.apache.poi.ss.usermodel.ConditionalFormattingRule rule = 
                    conditionalFormatting.createConditionalFormattingColorScaleRule();

            // Si tu versión de POI es más antigua y da error en la línea anterior, usa esta alternativa:
            // org.apache.poi.ss.usermodel.ColorScaleFormatting colorScale = rule.getColorScaleFormatting();

            // 2. Configuración de la regla de color (Blanco a Rojo claro)
            org.apache.poi.ss.usermodel.ColorScaleFormatting colorScale = rule.getColorScaleFormatting();
            if (colorScale != null) {
                colorScale.setNumControlPoints(2);
                colorScale.getColors()[0] = new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null);
                colorScale.getColors()[1] = new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte) 240, (byte) 128, (byte) 128}, null);
            }

            conditionalFormatting.addConditionalFormatting(regions, rule);
        }

    // PDF 

    public static void exportPdf(Context ctx) {
        String tipo = ctx.queryParam("tipo");
        if (tipo == null || tipo.isBlank()) tipo = "brechas";
        try {
            String html = generarHtml(tipo)
                    .replace("χ²", "Chi2")
                    .replace("χ", "Chi")
                    .replace("H₀", "H0")
                    .replace("₀", "0")
                    .replace("≥", ">=")
                    .replace("≤", "<=")
                    .replace("nᵢ", "ni")
                    .replace("ᵢ", "i");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
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
            e.printStackTrace();
            ctx.status(500).result("Error al generar PDF: " + mensajeError(e));
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
        if ("hu12".equalsIgnoreCase(tipo)) {
            sb.append("<h1>REPORTE UMSS - DEMANDA EMPRESARIAL Y RED ALUMNO</h1>");
        } else {
            sb.append("<h1>REPORTE UMSS - ").append(esc(tipo.toUpperCase())).append("</h1>");
        }
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
                    CalculadoraEmprendimiento.AnalisisExploratorio analisis =
                            CalculadoraEmprendimiento.analisisExploratorio(
                                    EncuestaDAO.obtenerRegistrosEmprendimiento());
                    sb.append("<h2>2. Ecosistema de Emprendimiento e Innovación</h2>");
                    sb.append("<p class='meta'>Análisis exploratorio · ")
                            .append(analisis.completos())
                            .append(" casos con las tres respuestas");
                    if (analisis.incompletos() > 0) {
                        sb.append(" · ").append(analisis.incompletos()).append(" incompleto(s)");
                    }
                    sb.append("</p>");

                    appendFrecuenciaHtml(sb, "Origen del emprendimiento (p30)", analisis.origen(), "Origen");
                    appendFrecuenciaHtml(sb, "Tipo de entregable (p31)", analisis.entregable(), "Tipo de entregable");
                    appendFrecuenciaHtml(sb, "Financiamiento (p32)", analisis.financiamiento(), "Financiamiento");

                    sb.append("<h2>2.1 Cruces de dos variables</h2>");
                    appendTablaCruceHtml(sb, "Origen × tipo de entregable",
                            analisis.origenEntregable(), "ORIGEN", true);
                    appendTablaCruceHtml(sb, "Origen × financiamiento",
                            analisis.origenFinanciamiento(), "ORIGEN", true);
                    appendTablaCruceHtml(sb, "Tipo de entregable × financiamiento",
                            analisis.entregableFinanciamiento(), "TIPO DE ENTREGABLE", true);

                    sb.append("<h2>2.2 Cruce de tres variables</h2>");
                    for (CalculadoraEmprendimiento.BloqueFinanciamiento bloque : analisis.bloques()) {
                        appendTablaCruceHtml(sb,
                                "Origen × tipo de entregable | " + bloque.financiamiento()
                                        + " (n=" + bloque.total() + ")",
                                bloque.tabla(), "ORIGEN", false);
                    }

                    sb.append("<h2>2.3 Resumen</h2>");
                    if (analisis.resumen().isEmpty()) {
                        sb.append("<p>Sin datos.</p>");
                    } else {
                        sb.append("<ul>");
                        for (String linea : analisis.resumen()) {
                            sb.append("<li>").append(esc(linea)).append("</li>");
                        }
                        sb.append("</ul>");
                    }
                }
                case "desempleo" -> appendAnalisisDesempleoHtml(sb, analisisDesempleo());
                case "formacion" -> {
                    sb.append("<h2>4. Formación continua y Mecanismos de financiamiento</h2>");

                    // =========================================================================
                    // 1. TABLA CRUZADA: ÁREA DE INTERÉS (p9) x NIVEL DE POSGRADO DESEADO (p4)
                    // =========================================================================
                    sb.append("<h3>Tabla cruzada · Área de interés (p9) × Nivel de posgrado deseado (p4)</h3>");
                    var cruceAN = EncuestaDAO.cruceAreaNivel();
                    var nivelesOrden = List.of("DIPLOMADO", "MAESTRIA", "ESPECIALIDAD", "DOCTORADO");
                    var matrizAN = new LinkedHashMap<String, Map<String, Integer>>();
                    var totFilaAN = new LinkedHashMap<String, Integer>();
                    int granTotalAN = 0;

                    for (Object obj : cruceAN) {
                        Map row = (Map) obj;
                        String area = String.valueOf(row.get("area"));
                        String nivel = String.valueOf(row.get("nivel")).toUpperCase();
                        int tot = ((Number) row.get("total")).intValue();
                        matrizAN.computeIfAbsent(area, k -> new LinkedHashMap<>()).put(nivel, tot);
                        totFilaAN.put(area, totFilaAN.getOrDefault(area, 0) + tot);
                        granTotalAN += tot;
                    }

                    var areasOrdenadas = new ArrayList<String>(totFilaAN.keySet());
                    areasOrdenadas.sort((a, b) -> Integer.compare(totFilaAN.get(b), totFilaAN.get(a)));

                    sb.append("<table><tr><th>Área de interés</th>");
                    for (String niv : nivelesOrden) {
                        sb.append("<th class='num'>").append(esc(niv)).append("</th>");
                    }
                    sb.append("<th class='num'>Total</th></tr>");

                    int[] totColAN = new int[nivelesOrden.size()];
                    DefaultCategoryDataset dsAreaNivel = new DefaultCategoryDataset();
                    for (String area : areasOrdenadas) {
                        sb.append("<tr><td>").append(esc(area)).append("</td>");
                        Map<String, Integer> filaMap = matrizAN.getOrDefault(area, Collections.emptyMap());
                        for (int j = 0; j < nivelesOrden.size(); j++) {
                            String niv = nivelesOrden.get(j);
                            int val = filaMap.getOrDefault(niv, 0);
                            totColAN[j] += val;
                            sb.append("<td class='num'>").append(val).append("</td>");
                            dsAreaNivel.addValue(val, niv, area);
                        }
                        sb.append("<td class='num'><b>").append(totFilaAN.getOrDefault(area, 0)).append("</b></td></tr>");
                    }
                    sb.append("<tr class='total'><td>Total general</td>");
                    for (int j = 0; j < nivelesOrden.size(); j++) {
                        sb.append("<td class='num'>").append(totColAN[j]).append("</td>");
                    }
                    sb.append("<td class='num'>").append(granTotalAN).append("</td></tr></table>");

                    // Gráfico agrupado Área x Nivel
                    JFreeChart chAN = ChartFactory.createBarChart(
                            "Áreas de interés × Nivel de posgrado deseado (N = " + granTotalAN + ")",
                            "Área de interés", "Titulados", dsAreaNivel,
                            PlotOrientation.VERTICAL, true, false, false);
                    CategoryPlot plotAN = chAN.getCategoryPlot();
                    plotAN.setBackgroundPaint(java.awt.Color.WHITE);
                    plotAN.setRangeGridlinePaint(java.awt.Color.LIGHT_GRAY);
                    BarRenderer rAN = (BarRenderer) plotAN.getRenderer();
                    rAN.setDrawBarOutline(false);
                    rAN.setSeriesPaint(0, new java.awt.Color(0x0D, 0x6E, 0xFD));
                    rAN.setSeriesPaint(1, new java.awt.Color(0x00, 0xC2, 0xE0));
                    rAN.setSeriesPaint(2, new java.awt.Color(0x19, 0x87, 0x54));
                    rAN.setSeriesPaint(3, new java.awt.Color(0xFF, 0xC1, 0x07));
                    sb.append(htmlChart("Áreas de interés × Nivel de posgrado deseado", renderPng(chAN, 760, 340)));

                    // =========================================================================
                    // 2. PREFERENCIA INSTITUCIONAL: NIVEL (p4) x ORGANIZACIÓN (p10)
                    // =========================================================================
                    sb.append("<h3>Preferencia institucional · Nivel de Posgrado (p4) × Organización (p10)</h3>");
                    var cruceNO = EncuestaDAO.cruceNivelOrganizacion(10);
                    sb.append("<table><tr><th>Nivel de Posgrado</th><th>Organización / Institución</th><th class='num'>Frecuencia (nᵢ)</th><th class='num'>%</th></tr>");

                    int totalNO = 0;
                    var orgOrden = new LinkedHashSet<String>();
                    var mapaOrgNivel = new LinkedHashMap<String, Map<String, Integer>>();

                    for (Object obj : cruceNO) {
                        Map row = (Map) obj;
                        String niv = String.valueOf(row.get("nivel")).toUpperCase();
                        String org = String.valueOf(row.get("organizacion"));
                        int freq = ((Number) row.get("total")).intValue();
                        double pct = ((Number) row.get("porcentaje")).doubleValue();
                        totalNO += freq;

                        orgOrden.add(org);
                        mapaOrgNivel.computeIfAbsent(org, k -> new LinkedHashMap<>()).put(niv, freq);

                        sb.append("<tr><td><b>").append(esc(niv)).append("</b></td><td>")
                        .append(esc(org)).append("</td><td class='num'>")
                        .append(freq).append("</td><td class='num'>")
                        .append(DF2.format(pct)).append("%</td></tr>");
                    }
                    sb.append("<tr class='total'><td colspan='2'>Total</td><td class='num'>")
                    .append(totalNO).append("</td><td class='num'>100.00%</td></tr></table>");

                    // Gráfico agrupado Organización x Nivel
                    DefaultCategoryDataset dsOrgNivel = new DefaultCategoryDataset();
                    for (String org : orgOrden) {
                        Map<String, Integer> mapN = mapaOrgNivel.getOrDefault(org, Collections.emptyMap());
                        String orgCorta = org.length() > 22 ? org.substring(0, 22) + "..." : org;
                        for (String niv : nivelesOrden) {
                            dsOrgNivel.addValue(mapN.getOrDefault(niv, 0), niv, orgCorta);
                        }
                    }
                    JFreeChart chON = ChartFactory.createBarChart(
                            "Preferencia institucional · Nivel × Organización",
                            "Organización / Institución", "Titulados", dsOrgNivel,
                            PlotOrientation.VERTICAL, true, false, false);
                    CategoryPlot plotON = chON.getCategoryPlot();
                    plotON.setBackgroundPaint(java.awt.Color.WHITE);
                    plotON.setRangeGridlinePaint(java.awt.Color.LIGHT_GRAY);
                    BarRenderer rON = (BarRenderer) plotON.getRenderer();
                    rON.setDrawBarOutline(false);
                    rON.setSeriesPaint(0, new java.awt.Color(0x0D, 0x6E, 0xFD));
                    rON.setSeriesPaint(1, new java.awt.Color(0x00, 0xC2, 0xE0));
                    rON.setSeriesPaint(2, new java.awt.Color(0x19, 0x87, 0x54));
                    rON.setSeriesPaint(3, new java.awt.Color(0xFF, 0xC1, 0x07));
                    sb.append(htmlChart("Preferencia institucional · Nivel × Organización", renderPng(chON, 760, 350)));

                    // =========================================================================
                    // 3. NIVEL DE FORMACIÓN DESEADO (p4) - FRECUENCIAS
                    // =========================================================================
                    sb.append("<h3>Nivel de formación deseado (p4) - Frecuencias</h3>");
                    var np4 = EncuestaDAO.nivelesFormacionDeseados();
                    int tn4 = 0;
                    for (ConteoRespuesta c : np4) tn4 += c.getTotal();

                    sb.append("<table><tr><th>Nivel</th><th class='num'>Total</th><th class='num'>%</th></tr>");
                    DefaultPieDataset<String> dp4 = new DefaultPieDataset<>();
                    for (ConteoRespuesta c : np4) {
                        double pct = tn4 == 0 ? 0.0 : (c.getTotal() * 100.0 / tn4);
                        sb.append("<tr><td>").append(esc(c.getRespuesta())).append("</td><td class='num'>")
                        .append(c.getTotal()).append("</td><td class='num'>")
                        .append(DF2.format(pct)).append("%</td></tr>");
                        dp4.setValue(c.getRespuesta(), c.getTotal());
                    }
                    sb.append("</table>");

                    JFreeChart chP4 = ChartFactory.createPieChart("Nivel de formación deseado (p4)", dp4, true, false, false);
                    PiePlot plotP4 = (PiePlot) chP4.getPlot();
                    plotP4.setBackgroundPaint(java.awt.Color.WHITE);
                    plotP4.setLabelGenerator(new StandardPieSectionLabelGenerator("{0}: {1} ({2})"));
                    sb.append(htmlChart("Nivel de formación deseado (p4)", renderPng(chP4, 720, 360)));

                    // =========================================================================
                    // 4. MECANISMOS DE FINANCIAMIENTO: MATRIZ DE CONTINGENCIA (p4 x p6)
                    // =========================================================================
                    var fin = EncuestaDAO.tablaFinanciamiento();
                    var filasF = (List) fin.get("filas");
                    var colsF = (List) fin.get("columnas");
                    var celdasF = (List) fin.get("celdas");

                    sb.append("<h3>Mecanismos de financiamiento · Matriz de contingencia (Nivel × Fuente)</h3>");
                    sb.append("<table><tr><th>Nivel / Financiamiento</th>");
                    for (Object c : colsF) sb.append("<th class='num'>").append(esc(String.valueOf(c))).append("</th>");
                    sb.append("<th class='num'>Total fila</th></tr>");

                    for (Object fObj : filasF) {
                        String f = String.valueOf(fObj);
                        sb.append("<tr><td><b>").append(esc(f)).append("</b></td>");
                        for (Object cObj : colsF) {
                            sb.append("<td class='num'>").append(getCelda((List<CeldaCruce>) celdasF, f, String.valueOf(cObj))).append("</td>");
                        }
                        sb.append("<td class='num'><b>").append(getTotalFila((List<CeldaCruce>) celdasF, f)).append("</b></td></tr>");
                    }
                    long NTf = getTotalGeneral((List<CeldaCruce>) celdasF);
                    sb.append("<tr class='total'><td>Total columna</td>");
                    for (Object cObj : colsF) {
                        sb.append("<td class='num'>").append(getTotalCol((List<CeldaCruce>) celdasF, String.valueOf(cObj))).append("</td>");
                    }
                    sb.append("<td class='num'>").append(NTf).append("</td></tr></table>");

                    // =========================================================================
                    // 5. DIAGNÓSTICO ESTADÍSTICO (Chi-cuadrado de Independencia)
                    // =========================================================================
                    ResultadoChi2 rchi = (ResultadoChi2) fin.get("chi2");
                    Boolean tieneF = (Boolean) fin.get("tieneDatos");
                    sb.append("<h3>Diagnóstico estadístico · Chi-cuadrado (χ²)</h3>");
                    if (rchi != null) {
                        sb.append("<table><tr><th>Índice</th><th>Valor</th></tr>");
                        sb.append("<tr><td>Chi-cuadrado (χ²)</td><td>").append(rchi.getChiCuadrado()).append("</td></tr>");
                        sb.append("<tr><td>Grados de libertad (gl)</td><td>").append(rchi.getGradosLibertad()).append("</td></tr>");
                        sb.append("<tr><td>Valor p (p-value)</td><td>").append(rchi.getValorP()).append("</td></tr>");
                        sb.append("<tr><td>Resultado de independencia</td><td><b>").append(rchi.isIndependiente() ? "No se rechaza H₀ (independiente, p ≥ 0.05)" : "Se rechaza H₀ (asociación significativa, p < 0.05)").append("</b></td></tr>");
                        sb.append("<tr><td>Datos suficientes</td><td>").append((tieneF != null && tieneF) ? "SÍ" : "NO").append("</td></tr>");
                        sb.append("<tr><td>Interpretación</td><td>").append(esc(rchi.getInterpretacion())).append("</td></tr>");
                        sb.append("</table>");
                    }
                }
                case "empleabilidad" -> {
                    sb.append("<h2>5. Análisis de Empleabilidad y Barreras de Inserción</h2>");

                    // =====================================================================
                    // 1. ESTADÍSTICOS DE ANTIGÜEDAD PROFESIONAL (p12)
                    // =====================================================================
                    sb.append("<h3>Estadísticos de Antigüedad Profesional (p12)</h3>");
                    var respP12 = EncuestaDAO.contarRespuestas(12);
                    var valsAnt = new ArrayList<Double>();
                    var freqAnt = new LinkedHashMap<Double, Integer>();
                    for (ConteoRespuesta c : respP12) {
                        if (c.getRespuesta() == null) continue;
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:[.,]\\d+)?)").matcher(c.getRespuesta());
                        if (m.find()) {
                            double v = Double.parseDouble(m.group(1).replace(',', '.'));
                            int tot = c.getTotal();
                            freqAnt.put(v, freqAnt.getOrDefault(v, 0) + tot);
                            for (int i = 0; i < tot; i++) valsAnt.add(v);
                        }
                    }
                    Collections.sort(valsAnt);
                    int nValid = valsAnt.size();
                    double sumaAnt = 0;
                    for (double v : valsAnt) sumaAnt += v;
                    double mediaAnt = nValid == 0 ? 0.0 : sumaAnt / nValid;
                    double medianaAnt = 0.0;
                    if (nValid > 0) {
                        medianaAnt = (nValid % 2 == 1)
                                ? valsAnt.get(nValid / 2)
                                : (valsAnt.get(nValid / 2 - 1) + valsAnt.get(nValid / 2)) / 2.0;
                    }
                    double modaAnt = 0.0;
                    int maxFreqAnt = -1;
                    for (var entry : freqAnt.entrySet()) {
                        if (entry.getValue() > maxFreqAnt) {
                            maxFreqAnt = entry.getValue();
                            modaAnt = entry.getKey();
                        }
                    }
                    double sumSq = 0;
                    for (double v : valsAnt) sumSq += Math.pow(v - mediaAnt, 2);
                    double desvAnt = nValid > 1 ? Math.sqrt(sumSq / (nValid - 1)) : 0.0;

                    sb.append("<table><tr><th>Titulados válidos</th><th>Media</th><th>Mediana</th><th>Moda</th><th>Desv. Estándar</th></tr>");
                    sb.append("<tr><td>").append(nValid).append("</td><td><b>")
                      .append(DF2.format(mediaAnt)).append(" años</b></td><td><b>")
                      .append(DF2.format(medianaAnt)).append(" años</b></td><td><b>")
                      .append(DF2.format(modaAnt)).append(" año(s)</b></td><td><b>")
                      .append(DF2.format(desvAnt)).append(" años</b></td></tr></table>");

                    // =====================================================================
                    // 2. DEMANDA DE EMPRESAS VS. INTERÉS DE GRADUADOS
                    // =====================================================================
                    sb.append("<h3>Demanda de Empresas vs. Interés de Graduados</h3>");
                    var aeH = EncuestaDAO.analisisEmpleabilidad();
                    sb.append("<table><tr><th>ÁREA</th><th class='num'>DEMANDA EMPL. (%)</th><th class='num'>INTERÉS TIT. (%)</th><th class='num'>EMPLEABILIDAD (%)</th></tr>");
                    for (AreaEmpleabilidad a : aeH) {
                        sb.append("<tr><td>").append(esc(a.getArea())).append("</td><td class='num'>")
                          .append(DF2.format(a.getDemandaEmpleador())).append("</td><td class='num'>")
                          .append(DF2.format(a.getInteresTitulados())).append("</td><td class='num'><strong>")
                          .append(DF2.format(a.getEmpleabilidad())).append("</strong></td></tr>");
                    }
                    sb.append("</table>");

                    List<String> catsE = aeH.stream().map(AreaEmpleabilidad::getArea).collect(java.util.stream.Collectors.toList());
                    List<List<Double>> serE = Arrays.asList(
                            aeH.stream().map(a -> (double) a.getDemandaEmpleador()).collect(java.util.stream.Collectors.toList()),
                            aeH.stream().map(a -> (double) a.getInteresTitulados()).collect(java.util.stream.Collectors.toList()),
                            aeH.stream().map(AreaEmpleabilidad::getEmpleabilidad).collect(java.util.stream.Collectors.toList()));
                    sb.append("<div style='margin-top: 15px; margin-bottom: 15px; page-break-inside: avoid;'>")
                      .append(htmlChart("Demanda de Empresas vs. Interés de Graduados (%)", chartBarMulti("", "Área", "Porcentaje",
                              catsE, Arrays.asList("Demanda empleador (%)", "Interés titulados (%)", "Empleabilidad (%)"), serE)))
                      .append("</div>");

                    // =====================================================================
                    // 3. MOTIVOS PRINCIPALES DE DESEMPLEO (p28 - TODAS LAS RAZONES)
                    // =====================================================================
                    var motivos28 = EncuestaDAO.contarRespuestas(28);
                    htmlSimpleBlock(sb, "Motivos Principales de Desempleo (p28)", motivos28);
                    sb.append("<div style='margin-top: 15px; margin-bottom: 15px; page-break-inside: avoid;'>")
                      .append(htmlChart("Motivos Principales de Desempleo (p28)", chartBar("", "Razón", "Total",
                              motivos28.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                              motivos28.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()), "Total")))
                      .append("</div>");

                    // =====================================================================
                    // 4. MATERIAS CON MENOR IMPACTO LABORAL (p44 - % SOBRE TITULADOS)
                    // =====================================================================
                    var asig44 = EncuestaDAO.asignaturasFrecuencia(44);
                    sb.append("<h3>Materias con Menor Impacto Laboral (p44)</h3>");
                    sb.append("<table><tr><th>ASIGNATURA</th><th class='num'>TOTAL</th><th class='num'>% TITULADOS</th></tr>");
                    for (ConteoRespuesta c : asig44) {
                        sb.append("<tr><td>").append(esc(c.getRespuesta())).append("</td><td class='num'>")
                          .append(c.getTotal()).append("</td><td class='num'>")
                          .append(DF2.format(c.getPorcentaje())).append("%</td></tr>");
                    }
                    sb.append("</table>");
                    sb.append("<div style='margin-top: 15px; margin-bottom: 15px; page-break-inside: avoid;'>")
                      .append(htmlChart("Materias con Menor Impacto Laboral (p44)", chartBar("", "Asignatura", "Menciones",
                              asig44.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                              asig44.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()), "Menciones")))
                      .append("</div>");

                    // =====================================================================
                    // 5. PERFIL DEL SECTOR EMPRESARIAL: RUBRO PRINCIPAL (p53)
                    // =====================================================================
                    var r53H = EncuestaDAO.contarRespuestas(53);
                    htmlSimpleBlock(sb, "Perfil del Sector Empresarial · Rubro principal (p53)", r53H);
                    sb.append("<div style='margin-top: 15px; margin-bottom: 15px; page-break-inside: avoid;'>")
                      .append(htmlChart("Rubros del Sector Empresarial (p53)", chartBar("", "Rubro", "Frecuencia",
                              r53H.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                              r53H.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()), "Frecuencia")))
                      .append("</div>");

                    // =====================================================================
                    // 6. PERFIL DEL SECTOR EMPRESARIAL: POSIBILIDAD DE INCORPORAR (p55)
                    // =====================================================================
                    var r55H = EncuestaDAO.contarRespuestas(55);
                    htmlSimpleBlock(sb, "Perfil del Sector Empresarial · Posibilidad de incorporar (p55)", r55H);
                    sb.append("<div style='margin-top: 15px; margin-bottom: 15px; page-break-inside: avoid;'>")
                      .append(htmlChart("Posibilidad de Incorporar Titulados (p55)", chartPie("",
                              r55H.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList()),
                              r55H.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList()))))
                      .append("</div>");

                    // =====================================================================
                    // 7. RESUMEN Y RECOMENDACIÓN
                    // =====================================================================
                    sb.append("<h3>Resumen y recomendación</h3><ul>");
                    StringBuilder topAreas = new StringBuilder();
                    for (int i = 0; i < aeH.size(); i++) {
                        AreaEmpleabilidad a = aeH.get(i);
                        if (i > 0) topAreas.append(", ");
                        topAreas.append(a.getArea()).append(" ").append(DF2.format(a.getEmpleabilidad())).append("%");
                    }
                    sb.append("<li><b>Áreas con mayor empleabilidad:</b> ").append(esc(topAreas.toString())).append(".</li>");
                    sb.append("<li><b>Oferta con poca demanda:</b> priorizar revisión de las razones críticas, las asignaturas de poca utilidad y la posibilidad real de contratación, cruzando con las áreas de mayor empleabilidad.</li>");
                    sb.append("</ul>");
                }
                case "ubicacion" -> {
                    // 1. Título principal limpio (sin HU11)
                    sb.append("<h2>7. Pertinencia Curricular, Geografía y Fuga de Talentos</h2>");

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

                    // ==========================================
                    // GRÁFICOS SIN TÍTULOS DUPLICADOS Y CON PÁGINA ENMARCADA
                    // ==========================================
                    List<String> catsD = deptosH.stream().map(ConteoRespuesta::getRespuesta).collect(java.util.stream.Collectors.toList());
                    List<Double> valsD = deptosH.stream().map(c -> (double) c.getTotal()).collect(java.util.stream.Collectors.toList());
                    sb.append("<div style='margin-top: 25px; margin-bottom: 25px; page-break-inside: avoid;'>")
                    .append(htmlChart("¿Dónde trabajan los titulados? (p19)", chartBar("", "Departamento", "Titulados", catsD, valsD, "Titulados")))
                    .append("</div>");

                    ResultadoPromedio salH = AnalisisDAO.promedioPorGrupo(U_SALARIO, U_DEPTO, new FiltroEnvio());
                    List<String> catsS = new ArrayList<>(); List<Double> valsS = new ArrayList<>();
                    for (ResumenGrupo g : salH.grupos()) { 
                        if (!"GENERAL".equalsIgnoreCase(g.grupo())) {
                            catsS.add(g.grupo()); 
                            valsS.add(g.media() == null ? 0.0 : g.media()); 
                        }
                    }
                    sb.append("<div style='margin-top: 25px; margin-bottom: 25px; page-break-inside: avoid;'>")
                    .append(htmlChart("Salario promedio mensual por departamento (Bs.)", chartBar("", "Departamento", "Media (Bs.)", catsS, valsS, "Media")))
                    .append("</div>");

                    ResultadoPromedio pertH = AnalisisDAO.promedioPorGrupo(U_PERT, U_CARGO, new FiltroEnvio());
                    List<String> catsP = new ArrayList<>(); List<Double> valsP = new ArrayList<>();
                    for (ResumenGrupo g : pertH.grupos()) { 
                        if (!"GENERAL".equalsIgnoreCase(g.grupo())) {
                            catsP.add(g.grupo()); 
                            valsP.add(g.media() == null ? 0.0 : g.media()); 
                        }
                    }
                    sb.append("<div style='margin-top: 25px; margin-bottom: 25px; page-break-inside: avoid;'>")
                    .append(htmlChart("Pertinencia promedio por cargo (Escala 1-4)", chartBar("", "Cargo", "Media", catsP, valsP, "Media")))
                    .append("</div>");
                }
                case "hu12" -> {
                    sb.append("<h2>8. Demanda Empresarial y Red Alumno</h2>");
                    sb.append("<h3>Grupo 1: Demanda empresarial (encuesta 2)</h3>");
                    
                    // 1. Grupo 1: Imprime Tabla + Gráfico de forma entrelazada (solo si tiene <= 12 opciones)
                    for (int pid : HU12_DEMANDA) {
                        htmlHu12(sb, pid); // Imprime la tabla HTML
                        
                        Map<String, Object> f = ReporteHu12DAO.obtenerFrecuencias(2, pid);
                        List<?> cats = (List<?>) f.get("categorias");
                        int numFilas = (cats != null) ? cats.size() : 0;
                        
                        // Filtro: Solo genera e inserta gráfico si no es de texto libre / tiene <= 12 categorías
                        if (numFilas > 0 && numFilas <= 12) {
                            sb.append(htmlChart("P" + pid + ": " + HU12_TITULOS.getOrDefault(pid, ""), hu12Chart(pid)));
                        }
                    }
                    
                    sb.append("<h3>Grupo 2: Percepción de la formación (encuesta 2)</h3>");
                    for (int pid : HU12_PERCEPCION) {
                        htmlHu12(sb, pid);
                        
                        Map<String, Object> f = ReporteHu12DAO.obtenerFrecuencias(2, pid);
                        List<?> cats = (List<?>) f.get("categorias");
                        int numFilas = (cats != null) ? cats.size() : 0;
                        
                        if (numFilas > 0 && numFilas <= 12) {
                            sb.append(htmlChart("P" + pid + ": " + HU12_TITULOS.getOrDefault(pid, ""), hu12Chart(pid)));
                        }
                    }

                    // Red Alumno
                    Map<String, Object> red = ReporteHu12DAO.obteneralumno();
                    sb.append("<h3>Grupo 3: Red Alumno - Consentimiento de contacto (p46)</h3>");
                    sb.append("<table><tr><th>INDICADOR</th><th class='num'>VALOR</th></tr>");
                    sb.append("<tr><td>Total de titulados</td><td class='num'>").append(red.get("totalTitulados")).append("</td></tr>");
                    sb.append("<tr><td>Aceptaron ser contactados (p46 = Sí)</td><td class='num'>").append(red.get("aceptaron")).append("</td></tr>");
                    sb.append("<tr><td>Con correo</td><td class='num'>").append(red.get("conCorreo")).append("</td></tr>");
                    sb.append("<tr><td>Con teléfono</td><td class='num'>").append(red.get("conTelefono")).append("</td></tr>");
                    sb.append("<tr><td>Con LinkedIn</td><td class='num'>").append(red.get("conLinkedin")).append("</td></tr></table>");
                    sb.append("<p class='meta'>El listado de contactos (correo/teléfono/LinkedIn) solo se muestra en la interfaz a usuarios autorizados.</p>");
                    
                    // (SE ELIMINÓ LA LÍNEA 2131 QUE REPETÍA Y AMONTONABA LOS GRÁFICOS AL FINAL)
                }
                default -> sb.append("<p>Tipo de reporte no soportado para exportación completa.</p>");
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudo construir el reporte '" + tipo + "': " + mensajeError(e), e);
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static void appendAnalisisDesempleoHtml(
            StringBuilder sb,
            CalculadoraDesempleo.AnalisisDesempleo analisis) {
        sb.append("<h2>3. Desempleo y Barreras</h2>");
        sb.append("<p class='meta'>").append(analisis.total()).append(" envíos · ")
                .append(analisis.conTresVariables()).append(" casos con las tres variables</p>");

        sb.append("<h2>3.1 Frecuencias</h2>");
        appendFrecuenciaDesempHtml(sb, "Situación laboral actual (p14)", analisis.situacion(), "Situación");
        appendFrecuenciaDesempHtml(sb, "Tiempo total de desempleo en años (p13)", analisis.tiempoDesempleo(), "Años");
        appendFrecuenciaDesempHtml(sb, "Razón por la que no trabaja (p28)", analisis.razon(), "Razón");
        appendFrecuenciaDesempHtml(sb, "Años hasta el primer empleo (p36)", analisis.primerEmpleo(), "Años");
        appendFrecuenciaDesempHtml(sb, "Años de vida profesional (p12)", analisis.vidaProfesional(), "Años");

        sb.append("<h2>3.2 Cruces de dos variables</h2>");
        appendTablaCruceDesempHtml(sb, "Situación laboral × tiempo de desempleo",
                analisis.situacionTiempo(), "SITUACIÓN LABORAL", true);
        appendTablaCruceDesempHtml(sb, "Matriz de barreras: razón de no trabajar × experiencia previa",
                analisis.razonExperiencia(), "RAZÓN", true);
        appendTablaCruceDesempHtml(sb, "Razón de no trabajar × años hasta el primer empleo",
                analisis.razonPrimerEmpleo(), "RAZÓN", true);
        appendTablaCruceDesempHtml(sb, "Razón de no trabajar × años de vida profesional",
                analisis.razonVidaProfesional(), "RAZÓN", true);

        sb.append("<h2>3.3 Cruce de tres variables</h2>");
        if (analisis.bloques().isEmpty()) {
            sb.append("<p>Sin casos completos para el cruce de tres variables.</p>");
        } else {
            for (CalculadoraDesempleo.BloqueRazon bloque : analisis.bloques()) {
                appendTablaCruceDesempHtml(sb,
                        "Años hasta el primer empleo × años de vida profesional | "
                                + bloque.razon() + " (n=" + bloque.total() + ")",
                        bloque.tabla(), "AÑOS HASTA EL PRIMER EMPLEO", false);
            }
        }

        sb.append("<h2>3.4 Demandas del empleador</h2>");
        if (analisis.demandas().isEmpty()) {
            sb.append("<p>Sin respuestas de empleadores.</p>");
        } else {
            for (CalculadoraDesempleo.DemandaEmpleador demanda : analisis.demandas()) {
                sb.append("<h3>").append(esc(demanda.titulo()))
                        .append(" (n=").append(demanda.total()).append(")</h3>");
                sb.append("<table><tr><th>PALABRA CLAVE</th><th class='num'>n</th><th class='num'>%</th></tr>");
                for (ConteoPalabra p : demanda.palabras()) {
                    sb.append("<tr><td>").append(esc(p.getPalabra()))
                            .append("</td><td class='num'>").append(p.getTotal())
                            .append("</td><td class='num'>").append(DF2.format(p.getPorcentaje()))
                            .append("</td></tr>");
                }
                sb.append("</table>");
            }
        }

        sb.append("<h2>3.5 Resumen</h2>");
        if (analisis.resumen().isEmpty()) {
            sb.append("<p>Sin datos.</p>");
        } else {
            sb.append("<ul>");
            for (String linea : analisis.resumen()) {
                sb.append("<li>").append(esc(linea)).append("</li>");
            }
            sb.append("</ul>");
        }
    }

    private static void appendFrecuenciaDesempHtml(
            StringBuilder sb, String titulo,
            List<CalculadoraDesempleo.Frecuencia> lista,
            String ejeCategoria) {
        int total = 0;
        for (CalculadoraDesempleo.Frecuencia f : lista) total += f.total();
        sb.append("<h3>").append(esc(titulo)).append(" (n=").append(total).append(")</h3>");
        if (lista.isEmpty()) {
            sb.append("<p>Sin datos.</p>");
            return;
        }
        List<String> categorias = lista.stream()
                .map(CalculadoraDesempleo.Frecuencia::etiqueta)
                .collect(java.util.stream.Collectors.toList());
        List<Double> valores = lista.stream()
                .map(f -> (double) f.total())
                .collect(java.util.stream.Collectors.toList());
        sb.append(htmlChart(titulo, chartBar(titulo, ejeCategoria, "Frecuencia",
                categorias, valores, "Total")));
        sb.append("<table><tr><th>").append(esc(ejeCategoria))
                .append("</th><th class='num'>n</th><th class='num'>%</th></tr>");
        for (CalculadoraDesempleo.Frecuencia f : lista) {
            sb.append("<tr><td>").append(esc(f.etiqueta()))
                    .append("</td><td class='num'>").append(f.total())
                    .append("</td><td class='num'>").append(DF2.format(f.porcentaje()))
                    .append("</td></tr>");
        }
        sb.append("<tr><th>Total</th><th class='num'>").append(total)
                .append("</th><th class='num'>100.0</th></tr></table>");
    }

    private static void appendTablaCruceDesempHtml(
            StringBuilder sb, String titulo,
            CalculadoraDesempleo.TablaCruce tabla,
            String etiquetaFila, boolean porcentajeFila) {
        sb.append("<h3>").append(esc(titulo)).append(" (n=").append(tabla.total()).append(")</h3>");
        sb.append("<table><tr><th>").append(esc(etiquetaFila)).append("</th>");
        for (String columna : tabla.columnas()) {
            sb.append("<th class='num'>").append(esc(columna)).append("</th>");
        }
        sb.append("<th class='num'>TOTAL</th></tr>");

        for (int i = 0; i < tabla.filas().size(); i++) {
            sb.append("<tr><td>").append(esc(tabla.filas().get(i))).append("</td>");
            for (int j = 0; j < tabla.columnas().size(); j++) {
                int valor = tabla.celdas().get(i).get(j);
                int denominador = porcentajeFila ? tabla.totalesFila().get(i) : tabla.total();
                sb.append("<td class='num'>").append(valor)
                        .append("<br/><small>").append(DF2.format(pctDe(valor, denominador)))
                        .append("%</small></td>");
            }
            sb.append("<td class='num'>").append(tabla.totalesFila().get(i)).append("</td></tr>");
        }

        sb.append("<tr><th>Total</th>");
        for (int j = 0; j < tabla.columnas().size(); j++) {
            sb.append("<th class='num'>").append(tabla.totalesColumna().get(j)).append("</th>");
        }
        sb.append("<th class='num'>").append(tabla.total()).append("</th></tr></table>");
    }

    private static void appendFrecuenciaHtml(
            StringBuilder sb, String titulo,
            List<CalculadoraEmprendimiento.Frecuencia> lista,
            String ejeCategoria) {
        if (lista == null || lista.isEmpty()) {
            sb.append("<h3>").append(esc(titulo)).append("</h3>");
            sb.append("<p>Sin datos.</p>");
            return;
        }
        List<String> categorias = lista.stream()
                .map(CalculadoraEmprendimiento.Frecuencia::etiqueta)
                .collect(java.util.stream.Collectors.toList());
        List<Double> valores = lista.stream()
                .map(f -> (double) f.total())
                .collect(java.util.stream.Collectors.toList());
        sb.append(htmlChart(titulo, chartBar(titulo, ejeCategoria, "Frecuencia",
                categorias, valores, "Total")));
        sb.append("<table><tr><th>").append(esc(ejeCategoria))
                .append("</th><th class='num'>TOTAL</th><th class='num'>%</th></tr>");
        for (CalculadoraEmprendimiento.Frecuencia f : lista) {
            sb.append("<tr><td>").append(esc(f.etiqueta()))
                    .append("</td><td class='num'>").append(f.total())
                    .append("</td><td class='num'>").append(DF2.format(f.porcentaje()))
                    .append("</td></tr>");
        }
        sb.append("</table>");
    }

    private static void appendTablaCruceHtml(
            StringBuilder sb, String titulo,
            CalculadoraEmprendimiento.TablaCruce tabla,
            String etiquetaFila, boolean porcentajeFila) {
        sb.append("<h3>").append(esc(titulo)).append("</h3>");
        sb.append("<p class='meta'>Conteo y porcentaje ")
                .append(porcentajeFila ? "por fila" : "del bloque")
                .append("</p>");
        sb.append("<table><tr><th>").append(esc(etiquetaFila)).append("</th>");
        for (String columna : tabla.columnas()) {
            sb.append("<th class='num'>").append(esc(columna)).append("</th>");
        }
        sb.append("<th class='num'>TOTAL</th></tr>");

        for (int i = 0; i < tabla.filas().size(); i++) {
            sb.append("<tr><td>").append(esc(tabla.filas().get(i))).append("</td>");
            for (int j = 0; j < tabla.columnas().size(); j++) {
                int valor = tabla.celdas().get(i).get(j);
                int denominador = porcentajeFila
                        ? tabla.totalesFila().get(i)
                        : tabla.total();
                sb.append("<td class='num'>").append(valor)
                        .append("<br/><small>").append(DF2.format(pctDe(valor, denominador)))
                        .append("%</small></td>");
            }
            sb.append("<td class='num'>").append(tabla.totalesFila().get(i)).append("</td></tr>");
        }

        sb.append("<tr><th>Total</th>");
        for (int j = 0; j < tabla.columnas().size(); j++) {
            sb.append("<th class='num'>").append(tabla.totalesColumna().get(j)).append("</th>");
        }
        sb.append("<th class='num'>").append(tabla.total()).append("</th></tr></table>");
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
