package com.umss.util;

import com.umss.model.CeldaCruce;
import com.umss.model.ConteoPalabra;
import com.umss.model.ConteoRespuesta;
import com.umss.model.DimensionRadar;
import com.umss.model.IndicadorEscala;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cálculos puros del reporte de Evaluación de Brechas y Competencias.
 *
 * No accede a la base de datos: recibe las respuestas ya leídas. El DAO delega
 * aquí para que el endpoint, las exportaciones y las pruebas usen la misma lógica.
 */
public final class CalculadoraBrechas {

    public static final String[] ETIQUETAS_LIKERT = {
        "Totalmente en desacuerdo", "En desacuerdo", "De acuerdo", "Totalmente de Acuerdo"
    };

    private CalculadoraBrechas() {}

    /** Normalización compartida por el diccionario, el radar y las escalas. */
    public static String normalizar(String s) {
        if (s == null) return "";
        String limpio = s.replace('\u00a0', ' ').replace("\u00A0", " ");
        String t = java.text.Normalizer.normalize(limpio, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return t.toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Separa opciones de una pregunta multiselección. Las comas dentro de
     * paréntesis (por ejemplo "Básicas (Física, Calculo, Algebras)") no separan.
     */
    public static List<String> separarOpciones(String texto) {
        List<String> items = new ArrayList<>();
        if (texto == null) return items;
        int nivel = 0;
        StringBuilder actual = new StringBuilder();
        for (char ch : texto.toCharArray()) {
            if (ch == '(') nivel++;
            if (ch == ')') nivel = Math.max(0, nivel - 1);
            if ((ch == ';' || ch == ',' || ch == '|') && nivel == 0) {
                String t = actual.toString().trim();
                if (!t.isEmpty()) items.add(t);
                actual.setLength(0);
            } else {
                actual.append(ch);
            }
        }
        String t = actual.toString().trim();
        if (!t.isEmpty()) items.add(t);
        return items;
    }

    /** Porcentaje con un decimal, usando el mismo redondeo que el DAO. */
    public static double porcentaje(int parte, int total) {
        return total > 0 ? Math.round(1000.0 * parte / total) / 10.0 : 0.0;
    }

    public static double redondear1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    public static int valorLikert(String etiqueta) {
        if (etiqueta == null) return -1;
        switch (etiqueta) {
            case "Totalmente en desacuerdo": return 1;
            case "En desacuerdo": return 2;
            case "De acuerdo": return 3;
            case "Totalmente de Acuerdo": return 4;
            default: return -1;
        }
    }

    public static ConteoRespuesta nuevoConteo(int pregunta, String respuesta, int total, int denominador) {
        ConteoRespuesta c = new ConteoRespuesta();
        c.setPreguntaId(pregunta);
        c.setRespuesta(respuesta);
        c.setTotal(total);
        c.setPorcentaje(porcentaje(total, denominador));
        return c;
    }

    /** Media, desviación poblacional y distribución de una escala Likert de 4 puntos. */
    public static IndicadorEscala indicador(int pregunta, String etiqueta, List<String> respuestas) {
        IndicadorEscala ind = new IndicadorEscala();
        ind.setPregunta(pregunta);
        ind.setEtiqueta(etiqueta);

        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (String e : ETIQUETAS_LIKERT) conteo.put(e, 0);

        double suma = 0, suma2 = 0;
        int n = 0;
        for (String valor : respuestas) {
            String r = valor == null ? "" : valor.trim();
            if (r.isEmpty()) continue;
            int v = valorLikert(r);
            if (v > 0) {
                suma += v;
                suma2 += (double) v * v;
                n++;
            }
            conteo.merge(r, 1, Integer::sum);
        }

        ind.setN(n);
        if (n > 0) {
            double media = suma / n;
            double var = (suma2 / n) - (media * media);
            ind.setMedia(redondear1(media));
            ind.setDesviacion(redondear1(Math.sqrt(Math.max(var, 0))));
        }

        int total = 0;
        for (int c : conteo.values()) total += c;
        List<ConteoRespuesta> dist = new ArrayList<>();
        for (Map.Entry<String, Integer> e : conteo.entrySet()) {
            if (e.getValue() == 0) continue;
            dist.add(nuevoConteo(pregunta, e.getKey(), e.getValue(), total));
        }
        ind.setDistribucion(dist);
        return ind;
    }

    /** Frecuencia de asignaturas en una pregunta multiselección. */
    public static List<ConteoRespuesta> frecuenciasAsignaturas(int pregunta, List<String> respuestas) {
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (String r : respuestas)
            for (String a : separarOpciones(r)) conteo.merge(a, 1, Integer::sum);

        int total = respuestas.size();
        List<ConteoRespuesta> lista = new ArrayList<>();
        for (Map.Entry<String, Integer> e : conteo.entrySet())
            lista.add(nuevoConteo(pregunta, e.getKey(), e.getValue(), total));
        lista.sort((a, b) -> Integer.compare(b.getTotal(), a.getTotal()));
        return lista;
    }

    /** Celdas de un cruce asignatura × variable para un solo registro. */
    public static List<CeldaCruce> cruceAsignaturaVariable(String asignaturas, String variable) {
        List<CeldaCruce> lista = new ArrayList<>();
        if (asignaturas == null || asignaturas.trim().isEmpty()
                || variable == null || variable.trim().isEmpty()) return lista;
        for (String a : separarOpciones(asignaturas)) {
            CeldaCruce c = new CeldaCruce();
            c.setValorX(a);
            c.setValorY(variable.trim());
            c.setTotal(1);
            lista.add(c);
        }
        return lista;
    }

    private static boolean mencionaAlguna(List<String> textos, String[] claves) {
        for (String t : textos) {
            String norm = " " + normalizar(t) + " ";
            for (String c : claves)
                if (norm.contains(" " + normalizar(c) + " ")) return true;
        }
        return false;
    }

    private static boolean tieneAsignatura(List<String> respuestas, String[] asignaturas) {
        for (String r : respuestas) {
            List<String> seleccion = separarOpciones(r);
            for (String a : asignaturas)
                for (String s : seleccion)
                    if (normalizar(s).equals(normalizar(a))) return true;
        }
        return false;
    }

    /**
     * Radar objetivo (empleadores) vs actual (titulados).
     * Los mapas son: dimensión → palabras clave y dimensión → asignaturas equivalentes.
     */
    public static List<DimensionRadar> radar(
            Map<String, String[]> diccionario,
            Map<String, String[]> asignaturasPorArea,
            Map<String, List<String>> porEmpleador,
            Map<String, List<String>> porTitulado) {
        int totalEmpleadores = porEmpleador.size();
        int totalTitulados = porTitulado.size();
        List<DimensionRadar> lista = new ArrayList<>();

        for (Map.Entry<String, String[]> area : diccionario.entrySet()) {
            int obj = 0;
            for (List<String> resp : porEmpleador.values())
                if (mencionaAlguna(resp, area.getValue())) obj++;

            int act = 0;
            String[] asigs = asignaturasPorArea.get(area.getKey());
            if (asigs != null) {
                for (List<String> resp : porTitulado.values())
                    if (tieneAsignatura(resp, asigs)) act++;
            }

            double objetivo = porcentaje(obj, totalEmpleadores);
            double actual = porcentaje(act, totalTitulados);
            if (objetivo > 0 || actual > 0)
                lista.add(new DimensionRadar(area.getKey(), objetivo, actual));
        }
        return lista;
    }

    /** Conteo de palabras clave por respuesta, no por mención. */
    public static List<ConteoPalabra> contarPalabrasClave(
            Map<String, String[]> diccionario, List<String> respuestas) {
        List<ConteoPalabra> res = new ArrayList<>();
        int total = respuestas.size();
        if (total == 0) return res;

        for (Map.Entry<String, String[]> e : diccionario.entrySet()) {
            int n = 0;
            for (String txt : respuestas) {
                String norm = " " + normalizar(txt) + " ";
                for (String sin : e.getValue()) {
                    if (norm.contains(" " + normalizar(sin) + " ")) {
                        n++;
                        break;
                    }
                }
            }
            if (n > 0) res.add(new ConteoPalabra(e.getKey(), n, porcentaje(n, total)));
        }
        res.sort((a, b) -> Integer.compare(b.getTotal(), a.getTotal()));
        return res;
    }
}
