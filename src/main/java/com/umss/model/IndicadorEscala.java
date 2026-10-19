package com.umss.model;

import java.util.List;

public class IndicadorEscala {
    private int pregunta;
    private String etiqueta;
    private int n;
    private double media;
    private double desviacion;
    private List<ConteoRespuesta> distribucion;

    public int getPregunta() { return pregunta; }
    public void setPregunta(int pregunta) { this.pregunta = pregunta; }

    public String getEtiqueta() { return etiqueta; }
    public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }

    public int getN() { return n; }
    public void setN(int n) { this.n = n; }

    public double getMedia() { return media; }
    public void setMedia(double media) { this.media = media; }

    public double getDesviacion() { return desviacion; }
    public void setDesviacion(double desviacion) { this.desviacion = desviacion; }

    public List<ConteoRespuesta> getDistribucion() { return distribucion; }
    public void setDistribucion(List<ConteoRespuesta> distribucion) { this.distribucion = distribucion; }
}
