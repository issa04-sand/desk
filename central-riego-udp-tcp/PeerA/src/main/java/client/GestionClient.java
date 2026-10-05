package client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import controllers.dtos.Request;
import controllers.dtos.Response;

/**
 * CLIENTE TCP de la app de gestión (PeerA).
 *
 * Conexión LARGA: se conecta una vez con conectar() y manda varias peticiones
 * por el mismo socket, hasta cerrar con close().
 *
 * Compara con FieldClient (UDP), que abre y cierra un socket por mensaje:
 * ahí es barato porque no hay conexión que negociar; aquí el handshake de
 * tres pasos cuesta un viaje de ida y vuelta, así que el socket se reutiliza.
 */
public class GestionClient implements Closeable {

    private final String host;
    private final int port;

    private final Gson gson = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public GestionClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Abre la conexión. Aquí ocurre el three-way handshake.
     */
    public void conectar() throws IOException {
        this.socket = new Socket(this.host, this.port);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    public Response listar() throws IOException {
        return enviar("LISTAR", new JsonObject());
    }

    public Response consultar(String sectorId) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("sectorId", sectorId);
        return enviar("CONSULTAR", data);
    }

    public Response programar(String sectorId, int minutos) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("sectorId", sectorId);
        data.addProperty("minutos", minutos);
        return enviar("PROGRAMAR", data);
    }

    public Response cancelar(String sectorId) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("sectorId", sectorId);
        return enviar("CANCELAR", data);
    }

    /**
     * Manda una petición por el socket ya abierto y espera una línea de respuesta.
     */
    public Response enviar(String command, JsonObject data) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("El cliente no está conectado: llama a conectar() primero.");
        }

        writer.write(gson.toJson(new Request(command, data)));
        writer.newLine();
        writer.flush();

        String line = reader.readLine();        // BLOQUEA esperando la respuesta
        return line == null ? null : gson.fromJson(line, Response.class);
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
