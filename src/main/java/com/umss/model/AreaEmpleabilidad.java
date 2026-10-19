package com.umss.model;

public class AreaEmpleabilidad {
    private String area;
    private double demandaEmpleador; 
    private double interesTitulados; 
    private double empleabilidad;     

    public AreaEmpleabilidad() {}

    public AreaEmpleabilidad(String area, double demandaEmpleador, double interesTitulados) {
        this.area = area;
        this.demandaEmpleador = demandaEmpleador;
        this.interesTitulados = interesTitulados;
        this.empleabilidad = Math.round(((demandaEmpleador + interesTitulados) / 2.0) * 10.0) / 10.0;
    }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public double getDemandaEmpleador() { return demandaEmpleador; }
    public void setDemandaEmpleador(double demandaEmpleador) { this.demandaEmpleador = demandaEmpleador; }

    public double getInteresTitulados() { return interesTitulados; }
    public void setInteresTitulados(double interesTitulados) { this.interesTitulados = interesTitulados; }

    public double getEmpleabilidad() { return empleabilidad; }
    public void setEmpleabilidad(double empleabilidad) { this.empleabilidad = empleabilidad; }
}
