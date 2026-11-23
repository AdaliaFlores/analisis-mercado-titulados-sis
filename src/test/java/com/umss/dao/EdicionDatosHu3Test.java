package com.umss.dao;

import com.umss.config.Database;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    void cp02CajaBlancaBloqueoSeleccionCerrada() throws SQLException {
        // El ID depende de los datos cargados en cada entorno: se consulta en vez de
        // hardcodear un valor que puede no existir o haber cambiado de pregunta.
        Integer idRespuestaSeleccionCerrada = buscarRespuestaCerrada();
        assertNotNull(idRespuestaSeleccionCerrada,
                "Debe existir al menos una respuesta de selección cerrada en la BD de prueba");

        assertThrows(IllegalArgumentException.class,
                () -> RespuestaDAO.editar(idRespuestaSeleccionCerrada, "Otra opcion", "Intento de cambio"),
                "Las preguntas de selección cerrada no deben ser modificables");
    }

    private static Integer buscarRespuestaCerrada() throws SQLException {
        String sql = """
                SELECT r.id
                FROM respuestas r
                JOIN preguntas p ON p.id = r.pregunta_id
                WHERE NOT %s
                ORDER BY r.id
                LIMIT 1
                """.formatted(ReglasEdicion.ES_EDITABLE);
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : null;
        }
    }

    @Test
    @DisplayName("CP-03 - Caja Blanca: Respuesta inexistente lanza NoSuchElementException (HTTP 404)")
    void cp03CajaBlancaIdInexistente() {
        assertThrows(NoSuchElementException.class,
                () -> RespuestaDAO.editar(99999999, "Texto válido", "Corrección de prueba"),
                "Un ID de respuesta inexistente debe lanzar NoSuchElementException");
    }
}