package com.umss.model;

public class Pregunta {
    private int id;
    private int encuestaId;
    private String textoPregunta;
    private String tipoPregunta;
    private String opciones;

    public Pregunta() {}

    public Pregunta(int id, int encuestaId, String textoPregunta, String tipoPregunta, String opciones) {
        this.id = id;
        this.encuestaId = encuestaId;
        this.textoPregunta = textoPregunta;
        this.tipoPregunta = tipoPregunta;
        this.opciones = opciones;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getEncuestaId() { return encuestaId; }
    public void setEncuestaId(int encuestaId) { this.encuestaId = encuestaId; }

    public String getTextoPregunta() { return textoPregunta; }
    public void setTextoPregunta(String textoPregunta) { this.textoPregunta = textoPregunta; }

    public String getTipoPregunta() { return tipoPregunta; }
    public void setTipoPregunta(String tipoPregunta) { this.tipoPregunta = tipoPregunta; }

    public String getOpciones() { return opciones; }
    public void setOpciones(String opciones) { this.opciones = opciones; }
}
