package com.umss.dao;

/**
 * Regla única de qué respuestas se pueden corregir.
 * Editable = pregunta escrita a mano: tipo TEXTO y sin opciones cargadas.
 * Las preguntas de selección (opción única/múltiple, escalas) nunca se editan.
 * Se usa dentro de consultas SQL con el alias "p" para la tabla preguntas.
 */
final class ReglasEdicion {

    static final String ES_EDITABLE =
        "(p.tipo_pregunta = 'TEXTO' AND (p.opciones IS NULL OR p.opciones IN ('[]'::jsonb, 'null'::jsonb)))";

    private ReglasEdicion() {}
}