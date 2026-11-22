package com.umss.util;

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

    /** Categorías que siempre se muestran en las tablas, aunque tengan 0 casos. */
    private static final List<String> ORIGENES_FIJOS = List.of(
            "Idea propia", "Idea conjunta", "Spin Off", "StartUp", "Otro");
    private static final List<String> ENTREGABLES_FIJOS = List.of(
            "Producto", "Servicio", "Ambos");
    private static final List<String> FINANCIAMIENTOS_FIJOS = List.of(
            "No, financiamiento propio", "Sí, con inversores", "Sí, con préstamos bancarios");

    private CalculadoraEmprendimiento() {
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

    /**
     * Análisis exploratorio de las tres variables categóricas del reporte:
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
        List<String> financiamientos = categoriasTabla(
                completos, r -> financiamiento(r.financiamiento()), FINANCIAMIENTOS_FIJOS);

        List<Frecuencia> fOrigen = frecuencias(completos, r -> origen(r.origen()), ORIGENES_FIJOS, n);
        List<Frecuencia> fEntregable = frecuencias(
                completos, r -> entregable(r.entregable()), ENTREGABLES_FIJOS, n);
        List<Frecuencia> fFinanciamiento = frecuencias(
                completos, r -> financiamiento(r.financiamiento()), FINANCIAMIENTOS_FIJOS, n);

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
}
