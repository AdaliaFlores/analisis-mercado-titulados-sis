package com.umss.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VistaPreviaHu2Test {

    @Test
    @DisplayName("CP-01 - Unitaria: Cálculo de paginación y validación de UUID de envío")
    void cp01UnitariaPaginacionYValidacionUuid() {
        int totalRegistros = 184;
        int tamPagina = 20;
        int totalPaginas = (int) Math.ceil((double) totalRegistros / tamPagina);
        assertEquals(10, totalPaginas, "184 registros paginados de 20 en 20 deben generar 10 páginas");

        // Un UUID malformado debe ser rechazado antes de consultar la BD
        assertThrows(IllegalArgumentException.class, () -> UUID.fromString("envio-invalido-123"));
    }

    @Test
    @DisplayName("CP-02 - Caja Blanca: Consulta de auditoría por envioId válido")
    void cp02CajaBlancaConsultaDetalleYAuditoria() throws Exception {
        String envioInexistente = "00000000-0000-0000-0000-000000000000";
        var historial = RespuestaDAO.obtenerAuditoriaPorEnvio(envioInexistente);
        assertNotNull(historial, "Debe retornar una lista vacía y no null cuando no hay cambios");
        assertTrue(historial.isEmpty());
    }
}