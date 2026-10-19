package com.umss.model;

import java.util.HashMap;
import java.util.Map;

public class EnvioFila {
    private String envioId;
    private String fecha;
    private Map<Integer, String> respuestas = new HashMap<>();

    public EnvioFila() {}

    public EnvioFila(String envioId, String fecha) {
        this.envioId = envioId;
        this.fecha = fecha;
    }

    public String getEnvioId() { return envioId; }
    public void setEnvioId(String envioId) { this.envioId = envioId; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public Map<Integer, String> getRespuestas() { return respuestas; }
    public void setRespuestas(Map<Integer, String> respuestas) { this.respuestas = respuestas; }
}