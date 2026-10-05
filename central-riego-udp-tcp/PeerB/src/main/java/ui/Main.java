package ui;

import controllers.GestionController;
import service.CentralServer;
import service.RiegoProcessor;
import service.RiegoService;

/**
 * Central de Riego (PeerB): levanta LOS DOS canales sobre el MISMO estado.
 *
 * Un solo proceso puede escuchar un puerto UDP y uno TCP a la vez porque son
 * sockets distintos y espacios de puertos distintos. Lo que comparten es el
 * RiegoService; los protocolos no se comparten.
 */
public class Main {

    public static final int PUERTO_UDP = 5000;
    public static final int PUERTO_TCP = 6000;

    public static void main(String[] args) throws Exception {

        int puertoUdp = args.length >= 1 ? Integer.parseInt(args[0]) : PUERTO_UDP;
        int puertoTcp = args.length >= 2 ? Integer.parseInt(args[1]) : PUERTO_TCP;

        // 1. Estado compartido.
        RiegoService service = new RiegoService();

        // 2. Canal UDP: estaciones de campo.
        CentralServer udp = new CentralServer(puertoUdp, new RiegoProcessor(service));

        // 3. Canal TCP: app de gestión.
        GestionController tcp = new GestionController(puertoTcp, service);

        System.out.println("==================================================");
        System.out.println("   CENTRAL DE RIEGO - PeerB");
        System.out.println("==================================================");

        udp.start();
        tcp.start();

        System.out.println(" UDP (sensores) escuchando en " + udp.getPort());
        System.out.println(" TCP (gestión)  escuchando en " + tcp.getPort());
        System.out.println(" Ctrl+C para detener.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nDeteniendo servicios...");
            udp.stop();
            tcp.stop();
        }));

        Thread.currentThread().join();
    }
}
