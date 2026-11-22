package com.umss.dao;

import com.umss.model.RespuestaDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reglas de validación del registro público de encuestas (HU-1).
 *
 * <p>La validación del navegador es solo una ayuda de usabilidad: estas reglas
 * se aplican también en el backend para que un POST directo no pueda guardar
 * encuestas vacías, correos inválidos, preguntas de otra encuesta o valores
 * fuera de las restricciones del formulario.</p>
 */
public final class ReglasEncuesta {

    private static final Pattern CORREO =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern TELEFONO = Pattern.compile("^[0-9]{6,15}$");
    private static final Pattern TEXTO_ABIERTO =
            Pattern.compile("^[\\p{L}\\p{N}\\s.,;:()¿?¡!\\-]+$");
    private static final Pattern TEXTO_ENLACE =
            Pattern.compile("^[\\p{L}\\p{N}\\s.,;:()¿?¡!\\-/@_%+&=#~]+$");
    private static final Pattern CLAVE_PREGUNTA = Pattern.compile("^p_([0-9]{1,9})$");

    private static final int MAX_TEXTO = 100;
    private static final int MAX_TEXTO_EMPLEADOR = 250;
    private static final int MAX_ENLACE = 200;
    private static final Set<String> OPCIONES_OTRO = Set.of("otro", "otros", "otra", "otras");

    private ReglasEncuesta() {
    }

    /**
     * Valida el payload y devuelve únicamente las respuestas no vacías,
     * indexadas por el id real de la pregunta.
     *
     * @throws IllegalArgumentException cuando el payload no cumple las reglas
     * @throws SQLException cuando falla la consulta de preguntas
     */
    public static Map<Integer, String> validar(Connection conn, RespuestaDTO dto) throws SQLException {
        if (dto == null) {
            throw new IllegalArgumentException("El cuerpo de la solicitud es obligatorio.");
        }

        int encuestaId = dto.getEncuestaId();
        if (encuestaId != 1 && encuestaId != 2) {
            throw new IllegalArgumentException("El tipo de encuesta no es válido.");
        }

        verificarEncuesta(conn, encuestaId);
        Map<Integer, PreguntaBd> preguntas = cargarPreguntas(conn, encuestaId);
        if (preguntas.isEmpty()) {
            throw new IllegalArgumentException("La encuesta no tiene preguntas configuradas.");
        }

        // En la encuesta de empleadores el correo de contacto es obligatorio.
        String contacto = limpiar(dto.getContacto());
        if (encuestaId == 2) {
            if (contacto.isEmpty()) {
                throw new IllegalArgumentException("El correo de contacto es obligatorio.");
            }
            if (!CORREO.matcher(contacto).matches()) {
                throw new IllegalArgumentException(
                        "Ingrese un correo electrónico válido (ejemplo: usuario@dominio.com).");
            }
        }

        Map<String, String> enviadas = dto.getRespuestas();
        if (enviadas == null || enviadas.isEmpty()) {
            throw new IllegalArgumentException("Debe enviar al menos una respuesta.");
        }

        Map<Integer, String> normalizadas = new LinkedHashMap<>();
        for (Map.Entry<String, String> entrada : enviadas.entrySet()) {
            String clave = limpiar(entrada.getKey());
            Matcher matcher = CLAVE_PREGUNTA.matcher(clave);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Formato de pregunta inválido: " + clave);
            }

            int preguntaId = Integer.parseInt(matcher.group(1));
            PreguntaBd pregunta = preguntas.get(preguntaId);
            if (pregunta == null) {
                throw new IllegalArgumentException(
                        "La pregunta " + preguntaId + " no pertenece a la encuesta seleccionada.");
            }

            String valor = limpiar(entrada.getValue());
            if (valor.isEmpty()) {
                // Las preguntas ocultas por los condicionales llegan vacías.
                continue;
            }

            validarRespuesta(encuestaId, pregunta, valor);

            if (normalizadas.put(preguntaId, valor) != null) {
                throw new IllegalArgumentException("La pregunta " + preguntaId + " está repetida.");
            }
        }

        if (normalizadas.isEmpty()) {
            throw new IllegalArgumentException("Debe enviar al menos una respuesta.");
        }
        return normalizadas;
    }

    private static void verificarEncuesta(Connection conn, int encuestaId) throws SQLException {
        String sql = "SELECT 1 FROM encuestas WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, encuestaId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("El tipo de encuesta no existe.");
                }
            }
        }
    }

    private static Map<Integer, PreguntaBd> cargarPreguntas(Connection conn, int encuestaId)
            throws SQLException {
        Map<Integer, PreguntaBd> preguntas = new LinkedHashMap<>();
        String sql = "SELECT id, texto_pregunta, tipo_pregunta, opciones::text AS opciones, formato "
                + "FROM preguntas WHERE encuesta_id = ? ORDER BY id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, encuestaId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    preguntas.put(rs.getInt("id"), new PreguntaBd(
                            rs.getInt("id"),
                            rs.getString("texto_pregunta"),
                            rs.getString("tipo_pregunta"),
                            rs.getString("opciones"),
                            rs.getString("formato")));
                }
            }
        }
        return preguntas;
    }

    private static void validarRespuesta(int encuestaId, PreguntaBd pregunta, String valor) {
        String textoNormalizado = normalizar(pregunta.texto());

        if (esTextoAbierto(pregunta) && esCorreo(textoNormalizado)) {
            if (!CORREO.matcher(valor).matches()) {
                throw new IllegalArgumentException(
                        "Ingrese un correo electrónico válido en la pregunta: " + pregunta.texto());
            }
            return;
        }

        if (esTextoAbierto(pregunta) && esTelefono(textoNormalizado)) {
            if (!TELEFONO.matcher(valor).matches()) {
                throw new IllegalArgumentException(
                        "El teléfono debe contener solo números (6 a 15 dígitos).");
            }
            return;
        }

        if (esTextoAbierto(pregunta) && esEnlace(textoNormalizado)) {
            if (valor.length() > MAX_ENLACE) {
                throw new IllegalArgumentException(
                        "El enlace no puede superar " + MAX_ENLACE + " caracteres.");
            }
            if (!TEXTO_ENLACE.matcher(valor).matches()) {
                throw new IllegalArgumentException(
                        "El enlace contiene caracteres no permitidos.");
            }
            return;
        }

        if (esSeleccion5(textoNormalizado)) {
            int seleccionadas = separarOpciones(valor).size();
            if (seleccionadas != 5) {
                throw new IllegalArgumentException(
                        "Debe seleccionar exactamente 5 opciones en la pregunta: " + pregunta.texto());
            }
            validarOpcionOtro(pregunta, valor);
            return;
        }

        if (esTextoAbierto(pregunta)) {
            int maximo = maximoTexto(encuestaId, textoNormalizado);
            if (valor.length() > maximo) {
                throw new IllegalArgumentException(
                        "La respuesta no puede superar " + maximo + " caracteres.");
            }
            if (!TEXTO_ABIERTO.matcher(valor).matches()) {
                throw new IllegalArgumentException(
                        "La respuesta contiene caracteres no permitidos.");
            }
            return;
        }

        validarOpcionOtro(pregunta, valor);
    }

    private static boolean esTextoAbierto(PreguntaBd pregunta) {
        if (pregunta.tipo() == null || !"TEXTO".equalsIgnoreCase(pregunta.tipo())) {
            return false;
        }
        String opciones = pregunta.opciones();
        return opciones == null
                || opciones.isBlank()
                || "null".equalsIgnoreCase(opciones.trim())
                || "[]".equals(opciones.trim());
    }

    private static boolean esCorreo(String textoNormalizado) {
        return textoNormalizado.contains("correo")
                || textoNormalizado.contains("email")
                || textoNormalizado.contains("e-mail")
                || textoNormalizado.contains("e mail");
    }

    private static boolean esTelefono(String textoNormalizado) {
        return textoNormalizado.contains("telefono")
                || textoNormalizado.contains("telefonico")
                || textoNormalizado.contains("celular")
                || textoNormalizado.contains("whatsapp")
                || textoNormalizado.contains("whatasapp");
    }

    private static boolean esEnlace(String textoNormalizado) {
        return textoNormalizado.contains("linkedin")
                || textoNormalizado.contains("link de")
                || textoNormalizado.contains("url")
                || textoNormalizado.contains("sitio web");
    }

    private static boolean esSeleccion5(String textoNormalizado) {
        return textoNormalizado.contains("indique 5 asignaturas");
    }

    private static int maximoTexto(int encuestaId, String textoNormalizado) {
        if (encuestaId == 2
                && (textoNormalizado.contains("nuevas areas de conocimiento")
                    || textoNormalizado.contains("nuevas herramientas tecnologicas")
                    || textoNormalizado.contains("habilidades, competencias y destrezas"))) {
            return MAX_TEXTO_EMPLEADOR;
        }
        return MAX_TEXTO;
    }

    /**
     * La opción "Otro" debe venir acompañada de su texto (el frontend envía
     * "Otro: detalle"). Las preguntas que tienen una pregunta de seguimiento
     * propia (OTRO RUBRO, OTR@ AREA, OTR@ CARGO) no se ven afectadas.
     */
    private static void validarOpcionOtro(PreguntaBd pregunta, String valor) {
        for (String opcion : separarOpciones(valor)) {
            String limpio = opcion.trim();
            String normalizado = normalizar(limpio);
            if (OPCIONES_OTRO.contains(normalizado)) {
                throw new IllegalArgumentException(
                        "Debe especificar la opción \"Otro\" en la pregunta: " + pregunta.texto());
            }
            int dosPuntos = limpio.indexOf(':');
            if (dosPuntos >= 0
                    && OPCIONES_OTRO.contains(normalizar(limpio.substring(0, dosPuntos)))
                    && limpio.substring(dosPuntos + 1).isBlank()) {
                throw new IllegalArgumentException(
                        "Debe especificar la opción \"Otro\" en la pregunta: " + pregunta.texto());
            }
        }
    }

    /**
     * Separa selecciones múltiples respetando las comas internas de opciones
     * como "Básicas (Física, Calculo, Algebras)".
     */
    private static List<String> separarOpciones(String texto) {
        List<String> items = new ArrayList<>();
        if (texto == null) {
            return items;
        }
        int nivel = 0;
        StringBuilder actual = new StringBuilder();
        for (char ch : texto.toCharArray()) {
            if (ch == '(') nivel++;
            if (ch == ')') nivel = Math.max(0, nivel - 1);
            if ((ch == ';' || ch == ',' || ch == '|') && nivel == 0) {
                String item = actual.toString().trim();
                if (!item.isEmpty()) items.add(item);
                actual.setLength(0);
            } else {
                actual.append(ch);
            }
        }
        String item = actual.toString().trim();
        if (!item.isEmpty()) items.add(item);
        return items;
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static String limpiar(String texto) {
        return texto == null ? "" : texto.trim();
    }

    private record PreguntaBd(int id, String texto, String tipo, String opciones, String formato) {
    }
}
