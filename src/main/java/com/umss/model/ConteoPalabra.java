package com.umss.model;

public class ConteoPalabra {
    private String palabra;
    private int total;
    private double porcentaje;

    public ConteoPalabra() { }

    public ConteoPalabra(String palabra, int total, double porcentaje) {
        this.palabra = palabra;
        this.total = total;
        this.porcentaje = porcentaje;
    }

    public String getPalabra() { return palabra; }
    public void setPalabra(String palabra) { this.palabra = palabra; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public double getPorcentaje() { return porcentaje; }
    public void setPorcentaje(double porcentaje) { this.porcentaje = porcentaje; }
}
