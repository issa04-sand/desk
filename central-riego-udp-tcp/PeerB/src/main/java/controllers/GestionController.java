package controllers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import controllers.dtos.Request;
import controllers.dtos.Response;
import model.SectorData;
import service.RiegoService;

/**
 * CANAL TCP — app de gestión del sistema de riego.
 *
 * TCP porque programar o cancelar un riego es una transacción: no se puede
 * perder ni llegar a medias. Protocolo JSON, una línea por mensaje.
 *
 * Servidor CONCURRENTE: el hilo del accept() solo acepta y delega al pool.
 * Conexión LARGA: cada cliente manda varias peticiones por el mismo socket.
 *
 * Comandos:
 *   LISTAR    {}                      -> OK, data.sectores (lista), data.total
 *   CONSULTAR {sectorId}              -> OK, data.sector   | ERROR SECTOR_NO_ENCONTRADO
 *   PROGRAMAR {sectorId, minutos}     -> OK, data.sectorId, data.minutos
 *                                      | ERROR SECTOR_NO_ENCONTRADO / DURACION_INVALIDA / RIEGO_YA_PROGRAMADO
 *   CANCELAR  {sectorId}              -> OK, data.sectorId | ERROR SIN_RIEGO_PROGRAMADO
 *   (otro)                            -> ERROR COMANDO_DESCONOCIDO
 */
public class GestionController {

    private final int port;
    private final RiegoService service;

    private final Gson gson = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    private ServerSocket serverSocket;
    private ExecutorService pool;
    private volatile boolean running = false;
    private Thread acceptThread;

    public GestionController(int port, RiegoService service) {
        this.port = port;
        this.service = service;
    }

    /**
     * Abre el ServerSocket, crea el pool y arranca el bucle de accept().
     */
    public synchronized void start() throws Exception {
        if (running) {
            return;
        }

        this.serverSocket = new ServerSocket(this.port);
        this.pool = Executors.newFixedThreadPool(10);
        this.running = true;

        this.acceptThread = new Thread(() -> {
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket cliente = serverSocket.accept();      // BLOQUEA
                    pool.execute(new ClientHandler(cliente));    // delegar, no procesar
                } catch (Exception e) {
                    if (running) {
                        System.err.println("[TCP] Error en accept: " + e.getMessage());
                    }
                }
            }
        }, "GestionController-Accept");

        this.acceptThread.start();
    }

    public synchronized void stop() {
        this.running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {
        }
        if (pool != null) {
            pool.shutdownNow();
        }
        if (acceptThread != null) {
            try {
                acceptThread.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return (serverSocket != null && !serverSocket.isClosed())
                ? serverSocket.getLocalPort()
                : port;
    }

    /**
     * Traduce un Request en un Response. Sin sockets: se prueba sin red.
     */
    public Response handleRequest(Request request) {

        if (request == null || request.getCommand() == null) {
            return Response.error("PETICION_INVALIDA");
        }

        JsonObject data = request.getData() != null ? request.getData() : new JsonObject();

        switch (request.getCommand().toUpperCase()) {

            case "LISTAR":
                return Response.ok()
                        .put("sectores", service.listarSectores())
                        .put("total", service.contarSectores());

            case "CONSULTAR": {
                if (!data.has("sectorId")) {
                    return Response.error("FORMATO_INVALIDO");
                }
                String sectorId = data.get("sectorId").getAsString();

                SectorData sector = service.getUltimaLectura(sectorId);
                if (sector == null) {
                    return Response.error("SECTOR_NO_ENCONTRADO");
                }
                return Response.ok().put("sector", sector);
            }

            case "PROGRAMAR": {
                if (!data.has("sectorId") || !data.has("minutos")) {
                    return Response.error("FORMATO_INVALIDO");
                }
                String sectorId = data.get("sectorId").getAsString();
                int minutos = data.get("minutos").getAsInt();

                if (!service.existe(sectorId)) {
                    return Response.error("SECTOR_NO_ENCONTRADO");
                }
                if (minutos < 1 || minutos > 60) {
                    return Response.error("DURACION_INVALIDA");
                }
                if (!service.programarRiego(sectorId, minutos)) {
                    return Response.error("RIEGO_YA_PROGRAMADO");
                }
                return Response.ok().put("sectorId", sectorId).put("minutos", minutos);
            }

            case "CANCELAR": {
                if (!data.has("sectorId")) {
                    return Response.error("FORMATO_INVALIDO");
                }
                String sectorId = data.get("sectorId").getAsString();

                if (!service.cancelarRiego(sectorId)) {
                    return Response.error("SIN_RIEGO_PROGRAMADO");
                }
                return Response.ok().put("sectorId", sectorId);
            }

            default:
                return Response.error("COMANDO_DESCONOCIDO");
        }
    }

    /**
     * Atiende a UN cliente dentro del ThreadPool, en conexión larga.
     */
    class ClientHandler implements Runnable {

        private final Socket clientSocket;

        ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            // try-with-resources: cierra socket, reader y writer pase lo que pase.
            try (Socket socket = this.clientSocket;
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()));
                 BufferedWriter writer = new BufferedWriter(
                         new OutputStreamWriter(socket.getOutputStream()))) {

                String line;
                // readLine() devuelve null cuando el cliente cierra la conexión.
                while ((line = reader.readLine()) != null) {

                    Request request = gson.fromJson(line, Request.class);
                    Response response = handleRequest(request);

                    writer.write(gson.toJson(response));
                    writer.newLine();      // el cliente lee con readLine(): necesita el \n
                    writer.flush();        // sin flush se quedan esperando los dos
                }

            } catch (Exception e) {
                System.err.println("[TCP] Error atendiendo cliente: " + e.getMessage());
            }
        }
    }
}
