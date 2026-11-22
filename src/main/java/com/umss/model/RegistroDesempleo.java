package com.umss.model;

/**
 * Una fila del análisis de desempleo y trayectoria laboral.
 * Todos los campos pertenecen al mismo envío de la encuesta de titulados.
 */
public record RegistroDesempleo(
        String situacionLaboral,
        String tiempoDesempleo,
        String razonNoTrabaja,
        String experienciaPrevia,
        String tiempoPrimerEmpleo,
        String aniosVidaProfesional) {
}
