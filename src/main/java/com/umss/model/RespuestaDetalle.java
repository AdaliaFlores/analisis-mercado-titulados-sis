package com.umss.model;

public class RespuestaDetalle {
    private int id;                 // id de la fila en la tabla respuestas
    private int preguntaId;
    private String textoPregunta;
    private String respuesta;
    private String formato;         // LIBRE o NUMERO
    private boolean editable;       // true solo para preguntas escritas a mano

    public RespuestaDetalle() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPreguntaId() { return preguntaId; }
    public void setPreguntaId(int preguntaId) { this.preguntaId = preguntaId; }

    public String getTextoPregunta() { return textoPregunta; }
    public void setTextoPregunta(String textoPregunta) { this.textoPregunta = textoPregunta; }

    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }

    public String getFormato() { return formato; }
    public void setFormato(String formato) { this.formato = formato; }

    public boolean isEditable() { return editable; }
    public void setEditable(boolean editable) { this.editable = editable; }
}