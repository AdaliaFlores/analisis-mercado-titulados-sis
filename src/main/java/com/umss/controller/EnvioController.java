package com.umss.controller;

import com.umss.dao.EnvioDAO;
import io.javalin.http.Context;

import java.util.UUID;

public class EnvioController {

    private static final int TAMANIO_DEFECTO = 20;
    private static final int TAMANIO_MAXIMO = 100;

    public static void listarEnvios(Context ctx) {
        try {
            int encuestaId = Integer.parseInt(ctx.pathParam("id"));
            int pagina = Math.max(1, entero(ctx.queryParam("pagina"), 1));
            int tamanio = Math.min(TAMANIO_MAXIMO,
                          Math.max(1, entero(ctx.queryParam("tamanio"), TAMANIO_DEFECTO)));
            ctx.json(EnvioDAO.obtenerPagina(encuestaId, pagina, tamanio));
        } catch (IllegalArgumentException e) {
            ctx.status(400).result("Parámetros inválidos");
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Error al obtener las respuestas: " + e.getMessage());
        }
    }

    public static void obtenerDetalle(Context ctx) {
        try {
            String envioId = UUID.fromString(ctx.pathParam("envioId")).toString();
            ctx.json(EnvioDAO.obtenerDetalle(envioId));
        } catch (IllegalArgumentException e) {
            ctx.status(400).result("Identificador de envío inválido");
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Error al obtener el detalle: " + e.getMessage());
        }
    }

    private static int entero(String valor, int defecto) {
        try {
            return valor == null ? defecto : Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            return defecto;
        }
    }
}