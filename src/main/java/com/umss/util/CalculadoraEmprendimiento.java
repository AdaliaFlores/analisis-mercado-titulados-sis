package com.umss.util;

import com.umss.model.ConteoRespuesta;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private CalculadoraEmprendimiento() {
    }

    public record Metricas(
            int universo,
            long spinOff,
            long startUp,
            long ideaPropia,
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
                top31 == null ? null : top31.getRespuesta(),
                top31 == null ? 0 : top31.getTotal(),
                totalEntregable,
                externo, propio, totalFinanciamiento,
                satisfaccion.promedio(), satisfaccion.altos(), satisfaccion.n(), satisfaccion.sinCodificar(),
                importancia.promedio(), importancia.altos(), importancia.n(), importancia.sinCodificar());
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
        if (m.spinOff() > 0) {
            origen.append("Spin Off ").append(pct(m.spinOff(), m.universo())).append("% · ");
        }
        if (m.ideaPropia() > 0) {
            origen.append("Idea propia ").append(pct(m.ideaPropia(), m.universo())).append("% · ");
        }
        if (m.startUp() > 0) {
            origen.append("StartUp ").append(pct(m.startUp(), m.universo())).append("%");
        }
        String detalle = origen.toString().replaceAll(" · $", "");
        if (!detalle.isEmpty()) {
            String predominante = m.spinOff() >= m.startUp()
                    ? "Predomina la iniciativa derivada de proyectos universitarios (Spin Off)."
                    : "Predomina la StartUp.";
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
