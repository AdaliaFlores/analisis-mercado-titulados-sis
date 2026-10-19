package com.umss.controller;

import com.umss.dao.RespuestaDAO;
import com.umss.model.EdicionRespuestaDTO;
import io.javalin.http.Context;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

public class RespuestaController {

    public static void editar(Context ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            EdicionRespuestaDTO dto = ctx.bodyAsClass(EdicionRespuestaDTO.class);
            String guardado = RespuestaDAO.editar(id, dto.getValor(), dto.getMotivo());
            ctx.json(Map.of("id", id, "valor", guardado));
        } catch (NumberFormatException e) {
            ctx.status(400).result("Identificador inválido");
        } catch (IllegalArgumentException e) {
            ctx.status(400).result(e.getMessage());
        } catch (NoSuchElementException e) {
            ctx.status(404).result(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Error al guardar la corrección: " + e.getMessage());
        }
    }

    public static void auditoriaPorEnvio(Context ctx) {
        try {
            String envioId = UUID.fromString(ctx.pathParam("envioId")).toString();
            ctx.json(RespuestaDAO.obtenerAuditoriaPorEnvio(envioId));
        } catch (IllegalArgumentException e) {
            ctx.status(400).result("Identificador de envío inválido");
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).result("Error al obtener el historial: " + e.getMessage());
        }
    }
}
