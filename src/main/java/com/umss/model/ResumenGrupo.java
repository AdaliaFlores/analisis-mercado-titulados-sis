package com.umss.model;

/**
 * Estadísticos de un grupo (ej. un departamento).
 * total = respuestas del grupo; n = las que tienen valor numérico asignado;
 * sinCodificar = total - n (texto no reconocido). media y desviación son nulas si no hay datos.
 */
public record ResumenGrupo(String grupo, int total, int n, int sinCodificar,
                           double suma, Double media, Double desviacion,
                           boolean muestraSuficiente) {}