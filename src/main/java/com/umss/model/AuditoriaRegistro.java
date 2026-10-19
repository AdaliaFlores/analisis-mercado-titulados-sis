package com.umss.model;

public class AuditoriaRegistro {
    private int id;
    private int respuestaId;
    private String textoPregunta;
    private String valorAnterior;
    private String valorNuevo;
    private String motivo;
    private String fecha;

    public AuditoriaRegistro() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getRespuestaId() { return respuestaId; }
    public void setRespuestaId(int respuestaId) { this.respuestaId = respuestaId; }

    public String getTextoPregunta() { return textoPregunta; }
    public void setTextoPregunta(String textoPregunta) { this.textoPregunta = textoPregunta; }

    public String getValorAnterior() { return valorAnterior; }
    public void setValorAnterior(String valorAnterior) { this.valorAnterior = valorAnterior; }

    public String getValorNuevo() { return valorNuevo; }
    public void setValorNuevo(String valorNuevo) { this.valorNuevo = valorNuevo; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
}