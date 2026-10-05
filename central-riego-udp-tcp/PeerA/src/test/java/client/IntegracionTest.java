package client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import controllers.GestionController;
import controllers.dtos.Response;
import service.CentralServer;
import service.RiegoProcessor;
import service.RiegoService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de INTEGRACIÓN: levantan los dos servidores reales en puerto 0
 * (el SO asigna uno libre) y hablan con ellos por red.
 */
public class IntegracionTest {

    private RiegoService service;
    private CentralServer udpServer;
    private GestionController tcpServer;
    private FieldClient udp;

    @BeforeEach
    void setUp() throws Exception {
        service = new RiegoService();

        udpServer = new CentralServer(0, new RiegoProcessor(service));
        udpServer.start();

        tcpServer = new GestionController(0, service);
        tcpServer.start();

        udp = new FieldClient("127.0.0.1", udpServer.getPort(), 2000);
    }

    @AfterEach
    void tearDown() {
        if (udpServer != null) udpServer.stop();
        if (tcpServer != null) tcpServer.stop();
    }

    @Test
    @DisplayName("IT-01: el servidor UDP responde al remitente")
    void testUdpRoundTrip() throws Exception {
        assertEquals("PONG", udp.ping());
        assertEquals("ALERTA;SUELO_SECO;12.0", udp.enviarLectura("S-7", "HUMEDAD", 12.0));
        assertEquals("ESTADO_OK;S-7;HUMEDAD;12.0", udp.consultarEstado("S-7"));
    }

    @Test
    @DisplayName("IT-02: el cliente UDP lanza SocketTimeoutException si nadie responde")
    void testUdpTimeout() {
        int puerto = udpServer.getPort();   // guardarlo ANTES de cerrar el socket
        udpServer.stop();

        FieldClient mudo = new FieldClient("127.0.0.1", puerto, 400);
        assertThrows(SocketTimeoutException.class, mudo::ping);
    }

    @Test
    @DisplayName("IT-03: el servidor TCP atiende varias peticiones por el mismo socket")
    void testTcpConexionLarga() throws Exception {
        udp.enviarLectura("S-7", "HUMEDAD", 40.0);

        try (GestionClient tcp = new GestionClient("127.0.0.1", tcpServer.getPort())) {
            tcp.conectar();

            Response lista = tcp.listar();
            assertEquals("OK", lista.getStatus());
            assertEquals(1, ((List<?>) lista.getData().get("sectores")).size());

            assertEquals("OK", tcp.programar("S-7", 25).getStatus());
            assertEquals("RIEGO_YA_PROGRAMADO", tcp.programar("S-7", 25).getData().get("message"));
            assertEquals("OK", tcp.cancelar("S-7").getStatus());
            assertEquals("SIN_RIEGO_PROGRAMADO", tcp.cancelar("S-7").getData().get("message"));
        }
    }

    @Test
    @DisplayName("IT-04: UDP y TCP comparten el MISMO estado")
    void testEstadoCompartido() throws Exception {
        // Entra por UDP...
        assertEquals("OK;HUMEDAD_OK;35.0", udp.enviarLectura("S-9", "HUMEDAD", 35.0));

        try (GestionClient tcp = new GestionClient("127.0.0.1", tcpServer.getPort())) {
            tcp.conectar();

            // ...y se ve por TCP
            Response r = tcp.consultar("S-9");
            assertEquals("OK", r.getStatus());
            Map<?, ?> sector = (Map<?, ?>) r.getData().get("sector");
            assertEquals("HUMEDAD", sector.get("tipo"));

            // Se programa por TCP...
            assertEquals("OK", tcp.programar("S-9", 10).getStatus());
        }

        // ...y el canal UDP ya lo ve ocupado
        assertEquals("ERROR;RIEGO_YA_PROGRAMADO", udp.programarRiego("S-9", 5));
    }

    @Test
    @DisplayName("IT-05: el servidor TCP atiende clientes concurrentes (ThreadPool)")
    void testConcurrencia() throws Exception {
        int n = 5;
        for (int i = 1; i <= n; i++) {
            service.registrarLectura("C-" + i, "CAUDAL", 10.0);
        }

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch listos = new CountDownLatch(n);
        AtomicInteger exitosos = new AtomicInteger();

        for (int i = 1; i <= n; i++) {
            final String id = "C-" + i;
            pool.execute(() -> {
                try (GestionClient cl = new GestionClient("127.0.0.1", tcpServer.getPort())) {
                    cl.conectar();
                    if ("OK".equals(cl.programar(id, 10).getStatus())) {
                        exitosos.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    listos.countDown();
                }
            });
        }

        assertTrue(listos.await(10, TimeUnit.SECONDS), "Los clientes no terminaron a tiempo");
        pool.shutdownNow();
        assertEquals(n, exitosos.get(), "Las 5 peticiones concurrentes debían pasar");
    }
}
