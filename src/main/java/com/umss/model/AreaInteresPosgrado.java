package com.umss.model;

public class AreaInteresPosgrado {
    private String area;
    private int n;
    private double porcentaje;

    public AreaInteresPosgrado() {}

    public AreaInteresPosgrado(String area, int n, double porcentaje) {
        this.area = area;
        this.n = n;
        this.porcentaje = porcentaje;
    }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public int getN() { return n; }
    public void setN(int n) { this.n = n; }

    public double getPorcentaje() { return porcentaje; }
    public void setPorcentaje(double porcentaje) { this.porcentaje = porcentaje; }
}