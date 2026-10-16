package com.umss.model;

public class ConteoRespuesta {
    private int preguntaId;
    private String respuesta;
    private int total;
    private double porcentaje;

    public int getPreguntaId() { return preguntaId; }
    public void setPreguntaId(int preguntaId) { this.preguntaId = preguntaId; }

    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public double getPorcentaje() { return porcentaje; }
    public void setPorcentaje(double porcentaje) { this.porcentaje = porcentaje; }
}
