package controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.google.gson.JsonObject;

import controllers.dtos.Request;
import controllers.dtos.Response;
import service.RiegoService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas UNITARIAS de la lógica del canal TCP. No abren ningún socket.
 */
public class GestionControllerTest {

    private RiegoService service;
    private GestionController controller;

    @BeforeEach
    void setUp() {
        service = new RiegoService();
        controller = new GestionController(0, service);
        service.registrarLectura("S-1", "HUMEDAD", 40.0);
        service.registrarLectura("S-2", "CAUDAL", 10.0);
    }

    private Request req(String command, Object... kv) {
        JsonObject data = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v instanceof Number) {
                data.addProperty((String) kv[i], (Number) v);
            } else {
                data.addProperty((String) kv[i], String.valueOf(v));
            }
        }
        return new Request(command, data);
    }

    @Test
    @DisplayName("CP-10: LISTAR devuelve todos los sectores y el total")
    void testListar() {
        Response r = controller.handleRequest(req("LISTAR"));
        assertEquals("OK", r.getStatus());
        assertEquals(2, ((List<?>) r.getData().get("sectores")).size());
        assertEquals(2, r.getData().get("total"));
    }

    @Test
    @DisplayName("CP-11: CONSULTAR devuelve el sector o SECTOR_NO_ENCONTRADO")
    void testConsultar() {
        Response ok = controller.handleRequest(req("CONSULTAR", "sectorId", "S-1"));
        assertEquals("OK", ok.getStatus());
        assertNotNull(ok.getData().get("sector"));

        Response ko = controller.handleRequest(req("CONSULTAR", "sectorId", "Z-9"));
        assertEquals("ERROR", ko.getStatus());
        assertEquals("SECTOR_NO_ENCONTRADO", ko.getData().get("message"));
    }

    @Test
    @DisplayName("CP-12: PROGRAMAR valida sector, rango y duplicado")
    void testProgramar() {
        Response ok = controller.handleRequest(req("PROGRAMAR", "sectorId", "S-1", "minutos", 20));
        assertEquals("OK", ok.getStatus());
        assertEquals(20, ok.getData().get("minutos"));

        Response dup = controller.handleRequest(req("PROGRAMAR", "sectorId", "S-1", "minutos", 20));
        assertEquals("RIEGO_YA_PROGRAMADO", dup.getData().get("message"));

        Response rango = controller.handleRequest(req("PROGRAMAR", "sectorId", "S-2", "minutos", 99));
        assertEquals("DURACION_INVALIDA", rango.getData().get("message"));

        Response raro = controller.handleRequest(req("PROGRAMAR", "sectorId", "Z-9", "minutos", 10));
        assertEquals("SECTOR_NO_ENCONTRADO", raro.getData().get("message"));
    }

    @Test
    @DisplayName("CP-13: CANCELAR libera el riego y falla la segunda vez")
    void testCancelar() {
        controller.handleRequest(req("PROGRAMAR", "sectorId", "S-2", "minutos", 15));

        assertEquals("OK", controller.handleRequest(req("CANCELAR", "sectorId", "S-2")).getStatus());
        assertEquals("SIN_RIEGO_PROGRAMADO",
                controller.handleRequest(req("CANCELAR", "sectorId", "S-2")).getData().get("message"));
    }

    @Test
    @DisplayName("CP-14: comando desconocido y petición nula")
    void testErrores() {
        assertEquals("COMANDO_DESCONOCIDO",
                controller.handleRequest(req("VOLAR")).getData().get("message"));
        assertEquals("PETICION_INVALIDA",
                controller.handleRequest(null).getData().get("message"));
    }
}
