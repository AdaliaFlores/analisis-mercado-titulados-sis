package com.umss.model;

public class ResultadoChi2 {
    private double chiCuadrado;
    private int gradosLibertad;
    private double valorP;
    private boolean independiente; // true si p >= 0.05 (no se rechaza independencia)
    private String interpretacion;

    public double getChiCuadrado() { return chiCuadrado; }
    public void setChiCuadrado(double chiCuadrado) { this.chiCuadrado = chiCuadrado; }

    public int getGradosLibertad() { return gradosLibertad; }
    public void setGradosLibertad(int gradosLibertad) { this.gradosLibertad = gradosLibertad; }

    public double getValorP() { return valorP; }
    public void setValorP(double valorP) { this.valorP = valorP; }

    public boolean isIndependiente() { return independiente; }
    public void setIndependiente(boolean independiente) { this.independiente = independiente; }

    public String getInterpretacion() { return interpretacion; }
    public void setInterpretacion(String interpretacion) { this.interpretacion = interpretacion; }
}