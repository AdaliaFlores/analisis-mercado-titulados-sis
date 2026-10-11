package com.umss.model;

import java.util.Map;

public class RespuestaDTO {
    private int encuestaId;
    private String nombre;
    private String contacto;
    private Map<String, String> respuestas; // Claves "p_1", "p_2", etc.

    public RespuestaDTO() {}

    // Getters y Setters
    public int getEncuestaId() { return encuestaId; }
    public void setEncuestaId(int encuestaId) { this.encuestaId = encuestaId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = contacto; }

    public Map<String, String> getRespuestas() { return respuestas; }
    public void setRespuestas(Map<String, String> respuestas) { this.respuestas = respuestas; }
}