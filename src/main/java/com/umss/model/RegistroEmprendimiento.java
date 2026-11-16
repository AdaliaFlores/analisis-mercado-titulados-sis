package com.umss.model;

/**
 * Una fila del análisis exploratorio de emprendimiento.
 * Los tres campos pertenecen a la misma persona (mismo envío).
 */
public record RegistroEmprendimiento(
        String origen,
        String entregable,
        String financiamiento) {

    public boolean completo() {
        return tieneTexto(origen) && tieneTexto(entregable) && tieneTexto(financiamiento);
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.trim().isEmpty();
    }
}
