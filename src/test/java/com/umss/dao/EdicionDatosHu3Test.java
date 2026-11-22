package com.umss.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class EdicionDatosHu3Test {

    @Test
    @DisplayName("CP-01 - Unitaria / Reglas: Rechazo de valor vacío o motivo ausente")
    void cp01UnitariaValidacionCamposObligatoriosEdicion() {
        // No se debe permitir guardar una corrección sin motivo de auditoría
        assertThrows(IllegalArgumentException.class,
                () -> RespuestaDAO.editar(1, "Texto corregido", ""),
                "Debe rechazar ediciones sin motivo");

        assertThrows(IllegalArgumentException.class,
                () -> RespuestaDAO.editar(1, "   ", "Limpieza de espacios"),
                "Debe rechazar valores en blanco");
    }

    @Test
    @DisplayName("CP-02 - Caja Blanca: Bloqueo de edición sobre preguntas de selección cerrada")
    void cp02CajaBlancaBloqueoSeleccionCerrada() {
        // Ejemplo: pregunta_id de selección cerrada (ej. Situación laboral o Departamento)
        // ReglasEdicion debe impedir modificar respuestas que no sean de texto abierto
        int idRespuestaSeleccionCerrada = 2; // Ajustar al ID real de una respuesta cerrada en BD
        assertThrows(IllegalArgumentException.class,
                () -> RespuestaDAO.editar(idRespuestaSeleccionCerrada, "Otra opcion", "Intento de cambio"),
                "Las preguntas de selección cerrada no deben ser modificables");
    }

    @Test
    @DisplayName("CP-03 - Caja Blanca: Respuesta inexistente lanza NoSuchElementException (HTTP 404)")
    void cp03CajaBlancaIdInexistente() {
        assertThrows(NoSuchElementException.class,
                () -> RespuestaDAO.editar(99999999, "Texto válido", "Corrección de prueba"),
                "Un ID de respuesta inexistente debe lanzar NoSuchElementException");
    }
}