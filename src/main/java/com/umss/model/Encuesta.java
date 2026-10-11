package com.umss.model;

public class Encuesta {
    private int id;
    private String titulo;
    private String destinatario;
    private String descripcion;
    private boolean activa;

    public Encuesta() {}

    public Encuesta(int id, String titulo, String destinatario, String descripcion, boolean activa) {
        this.id = id;
        this.titulo = titulo;
        this.destinatario = destinatario;
        this.descripcion = descripcion;
        this.activa = activa;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDestinatario() { return destinatario; }
    public void setDestinatario(String destinatario) { this.destinatario = destinatario; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
}