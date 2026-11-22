package com.umss.util;

import com.umss.model.ConteoPalabra;
import com.umss.model.RegistroDesempleo;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cálculos del reporte "Desempleo y Barreras de Inserción Laboral".
 * El análisis es descriptivo: frecuencias, cruces de dos variables y un cruce
 * de tres variables. No se modifica ni se limpia la base de datos; la
 * normalización se hace en memoria para presentar los resultados.
 */
public final class CalculadoraDesempleo {

    public static final int P_VIDA_PROFESIONAL = 12;
    public static final int P_TIEMPO_DESEMPLEO = 13;
    public static final int P_SITUACION = 14;
    public static final int P_RAZON_NO_TRABAJA = 28;
    public static final int P_EXPERIENCIA_PREVIA = 29;
    public static final int P_PRIMER_EMPLEO = 36;

    private static final Pattern NUMERO = Pattern.compile("(\\d+(?:[.,]\\d+)?)");
    private static final Pattern ANIOS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*anos?");
    private static final Pattern MESES = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*mes(?:es)?");

    private static final List<String> SITUACIONES = List.of(
            "Trabajando en una organización", "Emprendimiento propio", "No trabaja");
    private static final List<String> EXPERIENCIAS = List.of("Sí", "No");
    private static final List<String> TIEMPOS_DESEMPLEO = List.of(
            "0 años", "Hasta 0.5 años", "0.5 a 1 año", "1 a 3 años", "Más de 3 años");
    private static final List<String> TIEMPOS_PRIMER_EMPLEO = List.of(
            "0 años", "Hasta 0.5 años", "0.5 a 1 año", "Más de 1 año");
    private static final List<String> VIDA_PROFESIONAL = List.of(
            "Hasta 1 año", "1 a 5 años", "5 a 10 años", "Más de 10 años");

    private CalculadoraDesempleo() {
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

    public record BloqueRazon(String razon, int total, TablaCruce tabla) {
    }

    public record DemandaEmpleador(String titulo, int total, List<ConteoPalabra> palabras) {
    }

    public record AnalisisDesempleo(
            int total,
            int conTresVariables,
            List<Frecuencia> situacion,
            List<Frecuencia> tiempoDesempleo,
            List<Frecuencia> razon,
            List<Frecuencia> experiencia,
            List<Frecuencia> primerEmpleo,
            List<Frecuencia> vidaProfesional,
            TablaCruce situacionTiempo,
            TablaCruce razonExperiencia,
            TablaCruce razonPrimerEmpleo,
            TablaCruce razonVidaProfesional,
            List<BloqueRazon> bloques,
            List<DemandaEmpleador> demandas,
            List<String> resumen) {
    }

    public static AnalisisDesempleo analisis(
            List<RegistroDesempleo> registros,
            List<DemandaEmpleador> demandas) {
        List<RegistroDesempleo> datos = registros == null ? List.of() : registros;
        List<DemandaEmpleador> demandasSeguras = demandas == null ? List.of() : demandas;

        Function<RegistroDesempleo, String> fSituacion = r -> situacion(r.situacionLaboral());
        Function<RegistroDesempleo, String> fTiempo = r -> grupoTiempoDesempleo(anios(r.tiempoDesempleo()));
        Function<RegistroDesempleo, String> fRazon = r -> razon(r.razonNoTrabaja());
        Function<RegistroDesempleo, String> fExperiencia = r -> experiencia(r.experienciaPrevia());
        Function<RegistroDesempleo, String> fPrimer = r -> grupoPrimerEmpleo(r.tiempoPrimerEmpleo());
        Function<RegistroDesempleo, String> fVida = r -> grupoVidaProfesional(anios(r.aniosVidaProfesional()));

        List<Frecuencia> situacion = frecuencias(datos, fSituacion, SITUACIONES);
        List<Frecuencia> tiempo = frecuencias(datos, fTiempo, TIEMPOS_DESEMPLEO);
        List<Frecuencia> razones = frecuencias(datos, fRazon, null);
        List<Frecuencia> experiencia = frecuencias(datos, fExperiencia, EXPERIENCIAS);
        List<Frecuencia> primer = frecuencias(datos, fPrimer, TIEMPOS_PRIMER_EMPLEO);
        List<Frecuencia> vida = frecuencias(datos, fVida, VIDA_PROFESIONAL);

        List<String> catsSituacion = etiquetas(situacion);
        List<String> catsTiempo = etiquetas(tiempo);
        List<String> catsRazon = etiquetas(razones);
        List<String> catsExperiencia = etiquetas(experiencia);
        List<String> catsPrimer = etiquetas(primer);
        List<String> catsVida = etiquetas(vida);

        TablaCruce situacionTiempo = tabla(datos, catsSituacion, catsTiempo, fSituacion, fTiempo);
        TablaCruce razonExperiencia = tabla(datos, catsRazon, catsExperiencia, fRazon, fExperiencia);
        TablaCruce razonPrimer = tabla(datos, catsRazon, catsPrimer, fRazon, fPrimer);
        TablaCruce razonVida = tabla(datos, catsRazon, catsVida, fRazon, fVida);

        List<RegistroDesempleo> completos = new ArrayList<>();
        for (RegistroDesempleo r : datos) {
            if (r == null) continue;
            if (texto(fRazon.apply(r)) && texto(fPrimer.apply(r)) && texto(fVida.apply(r))) {
                completos.add(r);
            }
        }

        List<BloqueRazon> bloques = new ArrayList<>();
        for (String razon : catsRazon) {
            List<RegistroDesempleo> sub = new ArrayList<>();
            for (RegistroDesempleo r : completos) {
                if (razon.equals(fRazon.apply(r))) {
                    sub.add(r);
                }
            }
            if (!sub.isEmpty()) {
                bloques.add(new BloqueRazon(
                        razon,
                        sub.size(),
                        tabla(sub, catsPrimer, catsVida, fPrimer, fVida)));
            }
        }

        return new AnalisisDesempleo(
                datos.size(),
                completos.size(),
                situacion,
                tiempo,
                razones,
                experiencia,
                primer,
                vida,
                situacionTiempo,
                razonExperiencia,
                razonPrimer,
                razonVida,
                bloques,
                demandasSeguras,
                resumen(situacion, tiempo, razones, experiencia, primer, vida, demandasSeguras));
    }

    /** Convierte "5", "5 años", "6 meses", "Medio año" o "3 meses" a años. */
    public static Double anios(String valor) {
        String n = normalizar(valor);
        if (n.isEmpty()) {
            return null;
        }
        if (n.matches("\\d+(?:[.,]\\d+)?")) {
            return numero(n);
        }
        if (n.contains("ningun") || n.equals("nada") || n.equals("ninguna")
                || n.contains("siempre estuve con trabajo")) {
            return 0.0;
        }

        double total = 0.0;
        boolean encontrado = false;
        if (n.contains("medio ano")) {
            total += 0.5;
            encontrado = true;
        } else if (n.matches(".*\\b(un|una)\\s+ano\\b.*")) {
            total += 1.0;
            encontrado = true;
        }
        if (n.contains("medio mes")) {
            total += 1.0 / 24.0;
            encontrado = true;
        } else if (n.matches(".*\\b(un|una)\\s+mes\\b.*")) {
            total += 1.0 / 12.0;
            encontrado = true;
        }

        Matcher a = ANIOS.matcher(n);
        while (a.find()) {
            Double v = numero(a.group(1));
            if (v != null) {
                total += v;
                encontrado = true;
            }
        }
        Matcher m = MESES.matcher(n);
        while (m.find()) {
            Double v = numero(m.group(1));
            if (v != null) {
                total += v / 12.0;
                encontrado = true;
            }
        }

        if (!encontrado) {
            Matcher suelto = NUMERO.matcher(n);
            if (suelto.find()) {
                Double v = numero(suelto.group(1));
                if (v != null) {
                    return v;
                }
            }
            return null;
        }
        return Math.round(total * 100.0) / 100.0;
    }

    private static Double numero(String valor) {
        try {
            return Double.parseDouble(valor.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String situacion(String valor) {
        String n = normalizar(valor);
        if (n.isEmpty()) return null;
        if (n.contains("no trabajo")) return "No trabaja";
        if (n.contains("emprendimiento")) return "Emprendimiento propio";
        if (n.contains("trabajando")) return "Trabajando en una organización";
        return limpiar(valor);
    }

    static String razon(String valor) {
        String limpio = limpiar(valor);
        if (limpio == null) return null;
        return limpio.replaceAll("[.]+$", "").trim();
    }

    static String experiencia(String valor) {
        String n = normalizar(valor);
        if (n.isEmpty()) return null;
        if (n.startsWith("si")) return "Sí";
        if (n.startsWith("no")) return "No";
        return limpiar(valor);
    }

    static String grupoTiempoDesempleo(Double valor) {
        if (valor == null) return null;
        if (valor <= 0) return "0 años";
        if (valor <= 0.5) return "Hasta 0.5 años";
        if (valor <= 1.0) return "0.5 a 1 año";
        if (valor <= 3.0) return "1 a 3 años";
        return "Más de 3 años";
    }

    static String grupoPrimerEmpleo(String valor) {
        String n = normalizar(valor);
        if (n.isEmpty()) return null;
        if (n.contains("ya trabajaba") || n.contains("antes de titular")) return "0 años";
        if (n.contains("menos de 1 mes")) return "0 años";
        if (n.contains("entre 1") && n.contains("4")) return "Hasta 0.5 años";
        if (n.contains("entre 4") && n.contains("8")) return "Hasta 0.5 años";
        if (n.contains("entre 8") && n.contains("12")) return "0.5 a 1 año";
        if (n.contains("mas de 12")) return "Más de 1 año";

        Double valorAnios = anios(valor);
        if (valorAnios == null) return null;
        if (valorAnios <= 0) return "0 años";
        if (valorAnios <= 0.5) return "Hasta 0.5 años";
        if (valorAnios <= 1.0) return "0.5 a 1 año";
        return "Más de 1 año";
    }

    static String grupoVidaProfesional(Double valor) {
        if (valor == null) return null;
        if (valor <= 1.0) return "Hasta 1 año";
        if (valor <= 5.0) return "1 a 5 años";
        if (valor <= 10.0) return "5 a 10 años";
        return "Más de 10 años";
    }

    private static List<Frecuencia> frecuencias(
            List<RegistroDesempleo> datos,
            Function<RegistroDesempleo, String> extractor,
            List<String> orden) {
        LinkedHashMap<String, Integer> conteos = new LinkedHashMap<>();
        if (orden != null) {
            for (String categoria : orden) {
                conteos.put(categoria, 0);
            }
        }
        for (RegistroDesempleo r : datos) {
            if (r == null) continue;
            String valor = extractor.apply(r);
            if (texto(valor)) {
                conteos.merge(valor, 1, Integer::sum);
            }
        }

        List<Map.Entry<String, Integer>> entradas = new ArrayList<>(conteos.entrySet());
        if (orden == null) {
            entradas.sort((a, b) -> {
                int cmp = Integer.compare(b.getValue(), a.getValue());
                return cmp != 0 ? cmp : a.getKey().compareToIgnoreCase(b.getKey());
            });
            entradas.removeIf(e -> e.getValue() == 0);
        }

        int total = 0;
        for (Map.Entry<String, Integer> e : entradas) {
            total += e.getValue();
        }

        List<Frecuencia> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : entradas) {
            double porcentaje = total == 0 ? 0.0 : Math.round(e.getValue() * 1000.0 / total) / 10.0;
            out.add(new Frecuencia(e.getKey(), e.getValue(), porcentaje));
        }
        return out;
    }

    private static List<String> etiquetas(List<Frecuencia> lista) {
        List<String> out = new ArrayList<>();
        for (Frecuencia f : lista) {
            out.add(f.etiqueta());
        }
        return out;
    }

    private static TablaCruce tabla(
            List<RegistroDesempleo> datos,
            List<String> filas,
            List<String> columnas,
            Function<RegistroDesempleo, String> extractorFila,
            Function<RegistroDesempleo, String> extractorColumna) {
        List<List<Integer>> celdas = new ArrayList<>();
        List<Integer> totalesFila = new ArrayList<>();
        List<Integer> totalesColumna = new ArrayList<>(Collections.nCopies(columnas.size(), 0));

        for (int i = 0; i < filas.size(); i++) {
            celdas.add(new ArrayList<>(Collections.nCopies(columnas.size(), 0)));
            totalesFila.add(0);
        }

        int total = 0;
        for (RegistroDesempleo r : datos) {
            if (r == null) continue;
            String fila = extractorFila.apply(r);
            String columna = extractorColumna.apply(r);
            if (!texto(fila) || !texto(columna)) continue;
            int i = filas.indexOf(fila);
            int j = columnas.indexOf(columna);
            if (i < 0 || j < 0) continue;
            celdas.get(i).set(j, celdas.get(i).get(j) + 1);
            totalesFila.set(i, totalesFila.get(i) + 1);
            totalesColumna.set(j, totalesColumna.get(j) + 1);
            total++;
        }

        return new TablaCruce(filas, columnas, celdas, totalesFila, totalesColumna, total);
    }

    private static List<String> resumen(
            List<Frecuencia> situacion,
            List<Frecuencia> tiempo,
            List<Frecuencia> razon,
            List<Frecuencia> experiencia,
            List<Frecuencia> primer,
            List<Frecuencia> vida,
            List<DemandaEmpleador> demandas) {
        List<String> out = new ArrayList<>();
        if (suma(situacion) <= 0 && suma(razon) <= 0) {
            return out;
        }

        out.add("Situación laboral: " + top(situacion)
                + "; tiempo de desempleo: " + top(tiempo) + ".");

        out.add("Razón para no trabajar: " + top(razon)
                + "; experiencia previa: " + top(experiencia) + ".");

        String empleador = topDemanda(demandas);
        out.add("Trayectoria: primer empleo " + top(primer)
                + "; vida profesional " + top(vida)
                + (empleador == null ? "." : "; demanda del empleador: " + empleador + "."));
        return out;
    }

    private static String topDemanda(List<DemandaEmpleador> demandas) {
        if (demandas == null) return null;
        for (DemandaEmpleador d : demandas) {
            if (d == null || d.palabras() == null || d.palabras().isEmpty()) continue;
            ConteoPalabra p = d.palabras().get(0);
            return p.getPalabra() + " (" + p.getTotal() + " de " + d.total()
                    + "; " + String.format(Locale.US, "%.1f", p.getPorcentaje()) + "%)";
        }
        return null;
    }

    private static String top(List<Frecuencia> lista) {
        if (lista == null || lista.isEmpty()) {
            return "sin datos";
        }
        int maximo = 0;
        int total = 0;
        for (Frecuencia f : lista) {
            maximo = Math.max(maximo, f.total());
            total += f.total();
        }
        if (maximo == 0 || total == 0) {
            return "sin datos";
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
        return unir(etiquetas) + " (" + detalle + ")";
    }

    private static int suma(List<Frecuencia> lista) {
        int total = 0;
        for (Frecuencia f : lista) {
            total += f.total();
        }
        return total;
    }

    private static String unir(List<String> etiquetas) {
        if (etiquetas.size() <= 2) {
            return String.join(" y ", etiquetas);
        }
        return String.join(", ", etiquetas.subList(0, etiquetas.size() - 1))
                + " y " + etiquetas.get(etiquetas.size() - 1);
    }

    private static boolean texto(String valor) {
        return valor != null && !valor.trim().isEmpty();
    }

    private static String limpiar(String valor) {
        return valor == null ? null : valor.replace('\u00A0', ' ').trim();
    }

    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sinTildes.toLowerCase(Locale.ROOT)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String pct(long parte, long total) {
        if (total == 0) {
            return "0.0";
        }
        return String.format(Locale.US, "%.1f", parte * 100.0 / total);
    }
}
