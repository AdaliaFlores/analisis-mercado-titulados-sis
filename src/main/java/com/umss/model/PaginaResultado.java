package com.umss.model;

import java.util.List;

public class PaginaResultado<T> {
    private List<T> items;
    private long total;
    private int pagina;
    private int tamanio;
    private int totalPaginas;

    public PaginaResultado() {}

    public PaginaResultado(List<T> items, long total, int pagina, int tamanio) {
        this.items = items;
        this.total = total;
        this.pagina = pagina;
        this.tamanio = tamanio;
        this.totalPaginas = (int) Math.ceil((double) total / tamanio);
    }

    public List<T> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPagina() { return pagina; }
    public int getTamanio() { return tamanio; }
    public int getTotalPaginas() { return totalPaginas; }
}