package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Cliente UDP de la estación de campo (PeerA).
 */
public class FieldClient {

    private final String serverHost;
    private final int serverPort;
    private final int timeoutMs;

    public FieldClient(String serverHost, int serverPort, int timeoutMs) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.timeoutMs = timeoutMs;
    }

    public String enviarLectura(String sectorId, String tipo, double valor) throws IOException {
        return sendAndReceive("LECTURA;" + sectorId + ";" + tipo + ";" + valor);
    }

    public String consultarEstado(String sectorId) throws IOException {
        return sendAndReceive("ESTADO;" + sectorId);
    }

    public String programarRiego(String sectorId, int minutos) throws IOException {
        return sendAndReceive("REGAR;" + sectorId + ";" + minutos);
    }

    public String ping() throws IOException {
        return sendAndReceive("PING");
    }

    public String sendAndReceive(String message) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {

            socket.setSoTimeout(this.timeoutMs);

            byte[] datos = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket envio = new DatagramPacket(
                    datos,
                    datos.length,
                    InetAddress.getByName(this.serverHost),
                    this.serverPort);

            socket.send(envio);

            byte[] buffer = new byte[1024];
            DatagramPacket respuesta = new DatagramPacket(buffer, buffer.length);

            socket.receive(respuesta);

            return new String(
                    respuesta.getData(),
                    respuesta.getOffset(),
                    respuesta.getLength(),
                    StandardCharsets.UTF_8).trim();
        }
    }

    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }
}
