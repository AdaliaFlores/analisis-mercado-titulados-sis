package com.umss.model;

public class DimensionRadar {
    private String dimension;
    private double objetivo;
    private double actual;
    private double brecha;

    public DimensionRadar() {}

    public DimensionRadar(String dimension, double objetivo, double actual) {
        this.dimension = dimension;
        this.objetivo = objetivo;
        this.actual = actual;
        this.brecha = Math.round((objetivo - actual) * 10.0) / 10.0;
    }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public double getObjetivo() { return objetivo; }
    public void setObjetivo(double objetivo) { this.objetivo = objetivo; }

    public double getActual() { return actual; }
    public void setActual(double actual) { this.actual = actual; }

    public double getBrecha() { return brecha; }
    public void setBrecha(double brecha) { this.brecha = brecha; }
}
