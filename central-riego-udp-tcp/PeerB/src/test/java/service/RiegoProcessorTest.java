package service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas UNITARIAS del protocolo UDP. No abren ningún socket.
 */
public class RiegoProcessorTest {

    private RiegoService service;
    private RiegoProcessor processor;

    @BeforeEach
    void setUp() {
        service = new RiegoService();
        processor = new RiegoProcessor(service);
    }

    @Test
    @DisplayName("CP-01: PING responde PONG")
    void testPing() {
        assertEquals("PONG", processor.process("PING"));
        assertEquals("ERROR;FORMATO_INVALIDO", processor.process("PING;sobra"));
    }

    @Test
    @DisplayName("CP-02: LECTURA de HUMEDAD evalúa los tres rangos")
    void testHumedad() {
        assertEquals("ALERTA;SUELO_SECO;18.0", processor.process("LECTURA;S-1;HUMEDAD;18.0"));
        assertEquals("OK;HUMEDAD_OK;50.0", processor.process("LECTURA;S-1;HUMEDAD;50.0"));
        assertEquals("ALERTA;ENCHARCAMIENTO;92.0", processor.process("LECTURA;S-1;HUMEDAD;92.0"));
    }

    @Test
    @DisplayName("CP-03: LECTURA de CAUDAL y PRESION evalúan su umbral")
    void testCaudalYPresion() {
        assertEquals("ALERTA;CAUDAL_BAJO;3.0", processor.process("LECTURA;S-2;CAUDAL;3.0"));
        assertEquals("OK;CAUDAL_OK;12.0", processor.process("LECTURA;S-2;CAUDAL;12.0"));
        assertEquals("ALERTA;SOBREPRESION;95.0", processor.process("LECTURA;S-3;PRESION;95.0"));
        assertEquals("OK;PRESION_OK;60.0", processor.process("LECTURA;S-3;PRESION;60.0"));
    }

    @Test
    @DisplayName("CP-04: la lectura válida queda guardada en el servicio")
    void testGuardaLectura() {
        processor.process("LECTURA;S-5;HUMEDAD;33.0");
        assertTrue(service.existe("S-5"));
        assertEquals("HUMEDAD", service.getUltimaLectura("S-5").getTipo());
        assertEquals(33.0, service.getUltimaLectura("S-5").getValor());
    }

    @Test
    @DisplayName("CP-05: tipo de sensor no soportado")
    void testTipoNoSoportado() {
        assertEquals("ERROR;TIPO_NO_SOPORTADO", processor.process("LECTURA;S-4;PH;7.0"));
    }

    @Test
    @DisplayName("CP-06: ESTADO devuelve la última lectura o error si no existe")
    void testEstado() {
        assertEquals("ERROR;SECTOR_NO_ENCONTRADO", processor.process("ESTADO;S-1"));
        processor.process("LECTURA;S-1;CAUDAL;8.0");
        assertEquals("ESTADO_OK;S-1;CAUDAL;8.0", processor.process("ESTADO;S-1"));
    }

    @Test
    @DisplayName("CP-07: REGAR valida sector, rango y riego duplicado")
    void testRegar() {
        assertEquals("ERROR;SECTOR_NO_ENCONTRADO", processor.process("REGAR;S-1;30"));

        processor.process("LECTURA;S-1;HUMEDAD;20.0");

        assertEquals("ERROR;DURACION_INVALIDA", processor.process("REGAR;S-1;0"));
        assertEquals("ERROR;DURACION_INVALIDA", processor.process("REGAR;S-1;61"));
        assertEquals("OK;RIEGO_PROGRAMADO;S-1;30", processor.process("REGAR;S-1;30"));
        assertEquals("ERROR;RIEGO_YA_PROGRAMADO", processor.process("REGAR;S-1;10"));
    }

    @Test
    @DisplayName("CP-08: mensajes mal formados devuelven FORMATO_INVALIDO")
    void testFormatoInvalido() {
        assertEquals("ERROR;FORMATO_INVALIDO", processor.process(null));
        assertEquals("ERROR;FORMATO_INVALIDO", processor.process("   "));
        assertEquals("ERROR;FORMATO_INVALIDO", processor.process("LECTURA;S-1;HUMEDAD"));
        assertEquals("ERROR;FORMATO_INVALIDO", processor.process("LECTURA;S-1;HUMEDAD;abc"));
        assertEquals("ERROR;FORMATO_INVALIDO", processor.process("ESTADO"));
        assertEquals("ERROR;FORMATO_INVALIDO", processor.process("REGAR;S-1;abc"));
    }

    @Test
    @DisplayName("CP-09: comando desconocido")
    void testComandoDesconocido() {
        assertEquals("ERROR;COMANDO_DESCONOCIDO", processor.process("BORRAR;S-1"));
    }
}
