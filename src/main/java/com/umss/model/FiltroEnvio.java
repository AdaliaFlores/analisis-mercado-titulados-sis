package com.umss.model;

import java.util.ArrayList;
import java.util.List;

/** Condiciones "el envío debe tener esta respuesta en esta pregunta". Cantidad ilimitada. */
public class FiltroEnvio {

    public record Condicion(int preguntaId, String valor) {}

    private final List<Condicion> condiciones = new ArrayList<>();

    public FiltroEnvio agregar(int preguntaId, String valor) {
        condiciones.add(new Condicion(preguntaId, valor));
        return this;
    }

    public List<Condicion> getCondiciones() { return condiciones; }
}
