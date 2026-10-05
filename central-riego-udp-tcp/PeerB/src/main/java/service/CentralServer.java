package service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * Servidor UDP de la Central de Riego (PeerB).
 */
public class CentralServer {

    private final int port;
    private final RiegoProcessor processor;

    private DatagramSocket socket;
    private volatile boolean running = false;
    private Thread listenerThread;

    public CentralServer(int port, RiegoProcessor processor) {
        this.port = port;
        this.processor = processor;
    }

    public synchronized void start() throws SocketException {
        if (running) {
            return;
        }

        this.socket = new DatagramSocket(this.port);
        this.running = true;

        this.listenerThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (running && !socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    handlePacket(socket, packet);
                } catch (SocketException e) {
                    if (!running) {
                        break;
                    }
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error procesando datagrama: " + e.getMessage());
                    }
                }
            }
        }, "CentralServer-Thread");

        this.listenerThread.start();
    }

    public void handlePacket(DatagramSocket socket, DatagramPacket packet) throws IOException {
        String mensaje = new String(
                packet.getData(),
                packet.getOffset(),
                packet.getLength(),
                StandardCharsets.UTF_8);

        String respuesta = this.processor.process(mensaje);

        byte[] datos = respuesta.getBytes(StandardCharsets.UTF_8);
        DatagramPacket paqueteRespuesta = new DatagramPacket(
                datos,
                datos.length,
                packet.getAddress(),
                packet.getPort());

        socket.send(paqueteRespuesta);
    }

    public synchronized void stop() {
        this.running = false;
        if (this.socket != null && !this.socket.isClosed()) {
            this.socket.close();
        }
        if (this.listenerThread != null) {
            try {
                this.listenerThread.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return (socket != null && !socket.isClosed()) ? socket.getLocalPort() : port;
    }

    public RiegoProcessor getProcessor() {
        return processor;
    }
}
