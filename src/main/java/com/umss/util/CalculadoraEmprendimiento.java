package com.umss.util;

import com.umss.model.ConteoRespuesta;
import com.umss.model.RegistroEmprendimiento;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

// Cálculos del reporte "Ecosistema de Emprendimiento e Innovación" 
public final class CalculadoraEmprendimiento {

    public static final int P_ORIGEN = 30;
    public static final int P_ENTREGABLE = 31;
    public static final int P_FINANCIAMIENTO = 32;
    public static final int P_SATISFACCION = 33;
    public static final int P_IMPORTANCIA = 34;

    /** Valor de la escala a partir del cual una respuesta se considera "alta" (4 o 5). */
    public static final int UMBRAL_ALTO = 4;

    /**
     * Mapeo documentado de las etiquetas verbales de la pregunta 33 (ESCALA_1_5) a valores 1–5.
     * Las respuestas se importaron como texto y no tienen en la BD.
     */
    public static final Map<String, Integer> MAPA_SATISFACCION;

    //Mapeo documentado de las etiquetas verbales de la pregunta 34 (ESCALA_1_5) a valores 1–5.
    public static final Map<String, Integer> MAPA_IMPORTANCIA;

    static {
        Map<String, Integer> satisfaccion = new LinkedHashMap<>();
        satisfaccion.put("muy insatisfecho", 1);
        satisfaccion.put("insatisfecho", 2);
        satisfaccion.put("algo satisfecho", 3);
        satisfaccion.put("satisfecho", 4);
        satisfaccion.put("muy satisfecho", 5);
        MAPA_SATISFACCION = Collections.unmodifiableMap(satisfaccion);

        Map<String, Integer> importancia = new LinkedHashMap<>();
        importancia.put("nada importante", 1);
        importancia.put("poco importante", 2);
        importancia.put("importante", 4);
        importancia.put("muy importante", 5);
        MAPA_IMPORTANCIA = Collections.unmodifiableMap(importancia);
    }

    /** Categorías que siempre se muestran en las tablas, aunque tengan 0 casos. */
    private static final List<String> ORIGENES_FIJOS = List.of(
            "Idea propia", "Idea conjunta", "Spin Off", "StartUp", "Otro");
    private static final List<String> ENTREGABLES_FIJOS = List.of(
            "Producto", "Servicio", "Ambos");
    private static final List<String> FINANCIAMIENTOS_FIJOS = List.of(
            "No, financiamiento propio", "Sí, con inversores", "Sí, con préstamos bancarios");

    private CalculadoraEmprendimiento() {
    }

    public record Metricas(
            int universo,
            long spinOff,
            long startUp,
            long ideaPropia,
            String topOrigen,  
            long topOrigenTotal,
            String topEntregable,
            long topEntregableTotal,
            int totalEntregable,
            long financiamientoExterno,
            long financiamientoPropio,
            int totalFinanciamiento,
            double promedioSatisfaccion,
            long altaSatisfaccion,
            int nSatisfaccion,
            int sinCodificarSatisfaccion,
            double promedioImportancia,
            long altaImportancia,
            int nImportancia,
            int sinCodificarImportancia) {
    }

    private record Escala(double promedio, long altos, int n, int sinCodificar) {
    }

    public record Frecuencia(String etiqueta, int total, double porcentaje) {
    }

    public record TablaCruce(
            List<String> filas,
            List<String> columnas,
            List<List<Integer>> celdas,
            List<Integer> totalesFila,
            List<Integer> totalesColumna,
            int total) {
    }

    public record BloqueFinanciamiento(String financiamiento, int total, TablaCruce tabla) {
    }

    public record AnalisisExploratorio(
            int total,
            int completos,
            int incompletos,
            List<Frecuencia> origen,
            List<Frecuencia> entregable,
            List<Frecuencia> financiamiento,
            TablaCruce origenEntregable,
            TablaCruce origenFinanciamiento,
            TablaCruce entregableFinanciamiento,
            List<BloqueFinanciamiento> bloques,
            List<String> resumen) {
    }

    // cálculo
    public static Metricas metricas(List<ConteoRespuesta> filas) {
        List<ConteoRespuesta> datos = filas == null ? List.of() : filas;

        List<ConteoRespuesta> d30 = porPregunta(datos, P_ORIGEN);
        List<ConteoRespuesta> d31 = porPregunta(datos, P_ENTREGABLE);
        List<ConteoRespuesta> d32 = porPregunta(datos, P_FINANCIAMIENTO);
        List<ConteoRespuesta> d33 = porPregunta(datos, P_SATISFACCION);
        List<ConteoRespuesta> d34 = porPregunta(datos, P_IMPORTANCIA);

        int universo = suma(d30);                                 
        long spinOff = totalSiContiene(d30, "spin");
        long startUp = totalSiContiene(d30, "start");
        long ideaPropia = totalSiContiene(d30, "idea propia");

        ConteoRespuesta top31 = null;
        for (ConteoRespuesta c : d31) {
            if (top31 == null || c.getTotal() > top31.getTotal()) {
                top31 = c;
            }
        }
        int totalEntregable = suma(d31);

                // Buscar la opción con mayor conteo absoluto en p30 (Origen)
        ConteoRespuesta top30 = null;
        for (ConteoRespuesta c : d30) {
            if (top30 == null || c.getTotal() > top30.getTotal()) {
                top30 = c;
            }
        }
        int totalFinanciamiento = suma(d32);
        long externo = 0;
        for (ConteoRespuesta c : d32) {
            if (normalizar(c.getRespuesta()).startsWith("si")) {
                externo += c.getTotal();
            }
        }
        long propio = totalFinanciamiento - externo;

        Escala satisfaccion = escala(d33, MAPA_SATISFACCION);
        Escala importancia = escala(d34, MAPA_IMPORTANCIA);

        return new Metricas(
                universo,
                spinOff, startUp, ideaPropia,
                top30 == null ? null : top30.getRespuesta(), 
                top30 == null ? 0 : top30.getTotal(),         
                top31 == null ? null : top31.getRespuesta(),
                top31 == null ? 0 : top31.getTotal(),
                totalEntregable,
                externo, propio, totalFinanciamiento,
                satisfaccion.promedio(), satisfaccion.altos(), satisfaccion.n(), satisfaccion.sinCodificar(),
                importancia.promedio(), importancia.altos(), importancia.n(), importancia.sinCodificar());
    }

    /**
     * Análisis exploratorio de las tres variables categóricas:
     * origen (p30), tipo de entregable (p31) y financiamiento (p32).
     */
    public static AnalisisExploratorio analisisExploratorio(List<RegistroEmprendimiento> registros) {
        List<RegistroEmprendimiento> datos = registros == null ? List.of() : registros;
        List<RegistroEmprendimiento> completos = new ArrayList<>();
        for (RegistroEmprendimiento r : datos) {
            if (r != null && r.completo()) {
                completos.add(r);
            }
        }

        int total = datos.size();
        int n = completos.size();
        List<String> origenes = categoriasTabla(completos, r -> origen(r.origen()), ORIGENES_FIJOS);
        List<String> entregables = categoriasTabla(completos, r -> entregable(r.entregable()), ENTREGABLES_FIJOS);
        List<String> financiamientos = categoriasTabla(completos, r -> financiamiento(r.financiamiento()), FINANCIAMIENTOS_FIJOS);

        List<Frecuencia> fOrigen = frecuencias(completos, r -> origen(r.origen()), ORIGENES_FIJOS, n);
        List<Frecuencia> fEntregable = frecuencias(completos, r -> entregable(r.entregable()), ENTREGABLES_FIJOS, n);
        List<Frecuencia> fFinanciamiento = frecuencias(completos, r -> financiamiento(r.financiamiento()), FINANCIAMIENTOS_FIJOS, n);

        TablaCruce origenEntregable = tabla(completos, origenes, entregables,
                r -> origen(r.origen()), r -> entregable(r.entregable()));
        TablaCruce origenFinanciamiento = tabla(completos, origenes, financiamientos,
                r -> origen(r.origen()), r -> financiamiento(r.financiamiento()));
        TablaCruce entregableFinanciamiento = tabla(completos, entregables, financiamientos,
                r -> entregable(r.entregable()), r -> financiamiento(r.financiamiento()));

        List<BloqueFinanciamiento> bloques = new ArrayList<>();
        for (String bloqueFin : financiamientos) {
            List<RegistroEmprendimiento> sub = new ArrayList<>();
            for (RegistroEmprendimiento r : completos) {
                if (bloqueFin.equals(financiamiento(r.financiamiento()))) {
                    sub.add(r);
                }
            }
            bloques.add(new BloqueFinanciamiento(
                    bloqueFin,
                    sub.size(),
                    tabla(sub, origenes, entregables,
                            r -> origen(r.origen()), r -> entregable(r.entregable()))));
        }

        return new AnalisisExploratorio(
                total, n, total - n,
                fOrigen, fEntregable, fFinanciamiento,
                origenEntregable, origenFinanciamiento, entregableFinanciamiento,
                bloques,
                resumenExploratorio(n, fOrigen, fEntregable, fFinanciamiento));
    }

    /** Tres líneas con los hallazgos principales, para pantalla y exportaciones. */
    private static List<String> resumenExploratorio(
            int completos,
            List<Frecuencia> origen,
            List<Frecuencia> entregable,
            List<Frecuencia> financiamiento) {
        List<String> out = new ArrayList<>();
        if (completos <= 0) {
            return out;
        }

        out.add(resumenCategoria("Origen más frecuente", origen, completos));
        out.add(resumenCategoria("Tipo de entregable más frecuente", entregable, completos));

        int propio = 0;
        for (Frecuencia f : financiamiento) {
            if (FINANCIAMIENTOS_FIJOS.get(0).equals(f.etiqueta())) {
                propio += f.total();
            }
        }
        int externo = completos - propio;
        StringBuilder texto = new StringBuilder("Financiamiento: propio ")
                .append(propio).append(" (").append(pct(propio, completos)).append("%)");
        if (externo > 0) {
            texto.append("; externo ").append(externo)
                    .append(" (").append(pct(externo, completos)).append("%)");
        } else {
            texto.append("; sin financiamiento externo");
        }
        out.add(texto.append(".").toString());
        return out;
    }

    private static String resumenCategoria(String prefijo, List<Frecuencia> lista, int total) {
        if (lista == null || lista.isEmpty()) {
            return prefijo + ": sin datos.";
        }
        int maximo = 0;
        for (Frecuencia f : lista) {
            if (f.total() > maximo) {
                maximo = f.total();
            }
        }
        List<String> etiquetas = new ArrayList<>();
        for (Frecuencia f : lista) {
            if (f.total() == maximo) {
                etiquetas.add(f.etiqueta());
            }
        }
        String detalle = etiquetas.size() == 1
                ? maximo + " de " + total + "; " + pct(maximo, total) + "%"
                : maximo + " cada uno; " + pct(maximo, total) + "%";
        return prefijo + ": " + unirEtiquetas(etiquetas) + " (" + detalle + ").";
    }

    private static String unirEtiquetas(List<String> etiquetas) {
        if (etiquetas.size() <= 2) {
            return String.join(" y ", etiquetas);
        }
        return String.join(", ", etiquetas.subList(0, etiquetas.size() - 1))
                + " y " + etiquetas.get(etiquetas.size() - 1);
    }

    static String origen(String valor) {
        String n = normalizar(valor);
        if (n.contains("idea propia")) return "Idea propia";
        if (n.contains("idea conjunta")) return "Idea conjunta";
        if (n.contains("spin")) return "Spin Off";
        if (n.contains("start")) return "StartUp";
        if (n.contains("otro")) return "Otro";
        return limpiar(valor);
    }

    static String entregable(String valor) {
        String n = normalizar(valor);
        if (n.contains("ambos") || (n.contains("producto") && n.contains("servicio"))) return "Ambos";
        if (n.contains("producto")) return "Producto";
        if (n.contains("servicio")) return "Servicio";
        return limpiar(valor);
    }

    static String financiamiento(String valor) {
        String n = normalizar(valor);
        if (n.contains("inversor")) return "Sí, con inversores";
        if (n.contains("prestamo") || n.contains("banco") || n.contains("bancari")) {
            return "Sí, con préstamos bancarios";
        }
        if (n.startsWith("no") || n.contains("propio")) return "No, financiamiento propio";
        return limpiar(valor);
    }

    private static String limpiar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static List<String> categoriasTabla(
            List<RegistroEmprendimiento> datos,
            Function<RegistroEmprendimiento, String> extractor,
            List<String> fijas) {
        LinkedHashSet<String> categorias = new LinkedHashSet<>(fijas);
        for (RegistroEmprendimiento r : datos) {
            String valor = extractor.apply(r);
            if (valor != null && !valor.isBlank()) {
                categorias.add(valor);
            }
        }
        return new ArrayList<>(categorias);
    }

    private static List<Frecuencia> frecuencias(
            List<RegistroEmprendimiento> datos,
            Function<RegistroEmprendimiento, String> extractor,
            List<String> fijas,
            int total) {
        LinkedHashMap<String, Integer> conteos = new LinkedHashMap<>();
        for (String categoria : fijas) {
            conteos.put(categoria, 0);
        }
        for (RegistroEmprendimiento r : datos) {
            String valor = extractor.apply(r);
            if (valor != null && !valor.isBlank()) {
                conteos.merge(valor, 1, Integer::sum);
            }
        }

        List<Frecuencia> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : conteos.entrySet()) {
            if (e.getValue() > 0) {
                double porcentaje = total == 0 ? 0.0 : Math.round(e.getValue() * 1000.0 / total) / 10.0;
                out.add(new Frecuencia(e.getKey(), e.getValue(), porcentaje));
            }
        }
        return out;
    }

    private static TablaCruce tabla(
            List<RegistroEmprendimiento> datos,
            List<String> filas,
            List<String> columnas,
            Function<RegistroEmprendimiento, String> extractorFila,
            Function<RegistroEmprendimiento, String> extractorColumna) {
        List<List<Integer>> celdas = new ArrayList<>();
        List<Integer> totalesFila = new ArrayList<>();
        List<Integer> totalesColumna = new ArrayList<>(Collections.nCopies(columnas.size(), 0));

        for (int i = 0; i < filas.size(); i++) {
            celdas.add(new ArrayList<>(Collections.nCopies(columnas.size(), 0)));
            totalesFila.add(0);
        }

        for (RegistroEmprendimiento r : datos) {
            int i = filas.indexOf(extractorFila.apply(r));
            int j = columnas.indexOf(extractorColumna.apply(r));
            if (i < 0 || j < 0) {
                continue;
            }
            celdas.get(i).set(j, celdas.get(i).get(j) + 1);
            totalesFila.set(i, totalesFila.get(i) + 1);
            totalesColumna.set(j, totalesColumna.get(j) + 1);
        }

        return new TablaCruce(filas, columnas, celdas, totalesFila, totalesColumna, datos.size());
    }

    /**
     * Convierte las respuestas de una pregunta ESCALA_1_5 a valores numéricos.
     * Acepta etiquetas verbales (ver mapas) y números ("3", "4.0").
     * Las respuestas no reconocidas se cuentan como sinCodificar y se excluyen
     * del promedio y del porcentaje de respuestas altas.
     */
    private static Escala escala(List<ConteoRespuesta> datos, Map<String, Integer> mapa) {
        long sumaValores = 0;
        long altos = 0;
        int n = 0;
        int sinCodificar = 0;
        for (ConteoRespuesta c : datos) {
            Integer valor = valorEscala(c.getRespuesta(), mapa);
            if (valor == null) {
                sinCodificar += c.getTotal();
                continue;
            }
            n += c.getTotal();
            sumaValores += (long) valor * c.getTotal();
            if (valor >= UMBRAL_ALTO) {
                altos += c.getTotal();
            }
        }
        double promedio = n == 0 ? 0.0 : (double) sumaValores / n;
        return new Escala(promedio, altos, n, sinCodificar);
    }

    //Devuelve el valor 1–5 de una respuesta, o {@code null} si no es codificable.
    public static Integer valorEscala(String respuesta, Map<String, Integer> mapa) {
        String r = normalizar(respuesta);
        if (r.isEmpty()) {
            return null;
        }
        try {
            double numero = Double.parseDouble(r.replace(',', '.'));
            if (numero >= 1 && numero <= 5) {
                return (int) Math.round(numero);
            }
            return null;
        } catch (NumberFormatException ignored) {
            // No es numérica: se busca como etiqueta verbal.
        }
        return mapa.get(r);
    }

    public static List<String> bullets(List<ConteoRespuesta> filas) {
        return bullets(metricas(filas));
    }

    public static List<String> bullets(Metricas m) {
        List<String> out = new ArrayList<>();
        if (m.universo() <= 0) {
            return out;
        }

        out.add("Universo analizado: " + m.universo() + " emprendimiento(s) registrados por los titulados.");

        StringBuilder origen = new StringBuilder();
        if (m.ideaPropia() > 0) {
            origen.append("Idea propia ").append(pct(m.ideaPropia(), m.universo())).append("% · ");
        }
        if (m.spinOff() > 0) {
            origen.append("Spin Off ").append(pct(m.spinOff(), m.universo())).append("% · ");
        }
        if (m.startUp() > 0) {
            origen.append("StartUp ").append(pct(m.startUp(), m.universo())).append("% · ");
        }
        String detalle = origen.toString().replaceAll(" · $", "");
        if (!detalle.isEmpty()) {
            String nombreTop = (m.topOrigen() != null) ? m.topOrigen() : "N/A";
            String predominante = "Predomina: " + nombreTop + " (" + m.topOrigenTotal() + " de " + m.universo() + ", " + pct(m.topOrigenTotal(), m.universo()) + "%).";
            out.add("Origen: " + detalle + ". " + predominante);
        }

        if (m.topEntregable() != null) {
            out.add("Tipo de entregable predominante: " + m.topEntregable() + " ("
                    + m.topEntregableTotal() + " de " + m.totalEntregable() + ", "
                    + pct(m.topEntregableTotal(), m.totalEntregable()) + "%).");
        }

        if (m.totalFinanciamiento() > 0) {
            out.add("Financiamiento externo: " + pct(m.financiamientoExterno(), m.totalFinanciamiento())
                    + "% accedió a financiamiento (inversores/banca) y "
                    + pct(m.financiamientoPropio(), m.totalFinanciamiento())
                    + "% trabajó con recursos propios.");
        }

        if (m.nSatisfaccion() > 0) {
            out.add("Satisfacción con el rendimiento: promedio " + decimal(m.promedioSatisfaccion())
                    + "/5; " + pct(m.altaSatisfaccion(), m.nSatisfaccion()) + "% calificó con 4 o 5.");
        }

        if (m.nImportancia() > 0) {
            out.add("Importancia de la formación: promedio " + decimal(m.promedioImportancia())
                    + "/5; " + pct(m.altaImportancia(), m.nImportancia())
                    + "% la valoró con 4 o 5, lo que respalda el fortalecimiento de competencias emprendedoras.");
        }

        if (m.sinCodificarSatisfaccion() > 0) {
            out.add("Nota: " + m.sinCodificarSatisfaccion()
                    + " respuesta(s) de satisfacción no tienen un valor 1–5 reconocible y se excluyeron del promedio.");
        }
        if (m.sinCodificarImportancia() > 0) {
            out.add("Nota: " + m.sinCodificarImportancia()
                    + " respuesta(s) de importancia no tienen un valor 1–5 reconocible y se excluyeron del promedio.");
        }
        return out;
    }

    public static List<String> recomendaciones(List<ConteoRespuesta> filas) {
        return recomendaciones(metricas(filas));
    }

    public static List<String> recomendaciones(Metricas m) {
        List<String> out = new ArrayList<>();
        if (m.totalFinanciamiento() > 0 && m.financiamientoPropio() > m.financiamientoExterno()) {
            out.add("Ampliar el acceso a fuentes de financiamiento externo (inversores, fondos y banca) "
                    + "ya que la mayoría depende de recursos propios.");
        }
        if (m.spinOff() > 0) {
            out.add("Consolidar la vinculación universidad – emprendimiento para escalar los Spin Off "
                    + "surgidos de proyectos de la carrera.");
        }
        if (m.startUp() > 0) {
            out.add("Reforzar el acompañamiento (mentoría e incubación) a las StartUps en etapas tempranas.");
        }
        if (m.nSatisfaccion() > 0 && m.altaSatisfaccion() * 2 < m.nSatisfaccion()) {
            out.add("Atender los casos de baja satisfacción con el rendimiento mediante seguimiento individualizado.");
        }
        if (m.nImportancia() > 0) {
            out.add("Incorporar formación práctica en emprendimiento e innovación en el plan de estudios.");
        }
        return out;
    }

    //Estructura que consumen la interfaz y los exportadores.
    public static Map<String, Object> resumen(List<ConteoRespuesta> filas) {
        Metricas m = metricas(filas);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("metricas", m);
        r.put("bullets", bullets(m));
        r.put("recomendaciones", recomendaciones(m));
        return r;
    }

    private static List<ConteoRespuesta> porPregunta(List<ConteoRespuesta> filas, int preguntaId) {
        List<ConteoRespuesta> out = new ArrayList<>();
        for (ConteoRespuesta c : filas) {
            if (c.getPreguntaId() == preguntaId) {
                out.add(c);
            }
        }
        return out;
    }

    private static int suma(List<ConteoRespuesta> lista) {
        int total = 0;
        for (ConteoRespuesta c : lista) {
            total += c.getTotal();
        }
        return total;
    }

    private static long totalSiContiene(List<ConteoRespuesta> lista, String texto) {
        for (ConteoRespuesta c : lista) {
            if (normalizar(c.getRespuesta()).contains(texto)) {
                return c.getTotal();
            }
        }
        return 0;
    }

    /** Minúsculas, sin tildes y con espacios colapsados. */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String pct(long parte, long total) {
        if (total == 0) {
            return "0.0";
        }
        return String.format(Locale.US, "%.1f", parte * 100.0 / total);
    }

    private static String decimal(double valor) {
        return String.format(Locale.US, "%.1f", valor);
    }
}
