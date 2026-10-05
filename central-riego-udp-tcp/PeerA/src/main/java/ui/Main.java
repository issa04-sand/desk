package ui;

import java.net.SocketTimeoutException;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import client.FieldClient;
import client.GestionClient;
import controllers.dtos.Response;

/**
 * Estación de campo (PeerA): habla por los dos canales contra la misma central.
 */
public class Main {

    public static final String HOST = "127.0.0.1";
    public static final int PUERTO_UDP = 5000;
    public static final int PUERTO_TCP = 6000;
    public static final int TIMEOUT_MS = 2000;

    public static void main(String[] args) throws Exception {

        String host = args.length >= 1 ? args[0] : HOST;
        int puertoUdp = args.length >= 2 ? Integer.parseInt(args[1]) : PUERTO_UDP;
        int puertoTcp = args.length >= 3 ? Integer.parseInt(args[2]) : PUERTO_TCP;

        FieldClient udp = new FieldClient(host, puertoUdp, TIMEOUT_MS);
        Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .setPrettyPrinting()
                .create();
        Scanner sc = new Scanner(System.in);

        System.out.println("ESTACION DE CAMPO - PeerA");
        System.out.println("UDP " + puertoUdp + " / TCP " + puertoTcp + "\n");

        try (GestionClient tcp = new GestionClient(host, puertoTcp)) {

            boolean tcpOk = true;
            try {
                tcp.conectar();
            } catch (Exception e) {
                tcpOk = false;
                System.out.println("(canal TCP no disponible: " + e.getMessage() + ")");
            }

            boolean salir = false;
            while (!salir) {
                System.out.println("\n--- SENSORES (UDP) ---");
                System.out.println(" 1. Enviar lectura de HUMEDAD");
                System.out.println(" 2. Enviar lectura de CAUDAL");
                System.out.println(" 3. Enviar lectura de PRESION");
                System.out.println(" 4. Consultar ESTADO de un sector");
                System.out.println(" 5. PING");
                System.out.println("--- GESTION (TCP) ---");
                System.out.println(" 6. LISTAR sectores");
                System.out.println(" 7. CONSULTAR sector");
                System.out.println(" 8. PROGRAMAR riego");
                System.out.println(" 9. CANCELAR riego");
                System.out.println(" 0. Salir");
                System.out.print("Opcion > ");

                String op = sc.nextLine().trim();

                try {
                    switch (op) {
                        case "1": case "2": case "3": {
                            String tipo = op.equals("1") ? "HUMEDAD"
                                        : op.equals("2") ? "CAUDAL" : "PRESION";
                            System.out.print("sectorId (ej. S-1): ");
                            String id = sc.nextLine().trim();
                            System.out.print("valor: ");
                            double v = Double.parseDouble(sc.nextLine().trim());
                            System.out.println("<< " + udp.enviarLectura(id, tipo, v));
                            break;
                        }
                        case "4":
                            System.out.print("sectorId: ");
                            System.out.println("<< " + udp.consultarEstado(sc.nextLine().trim()));
                            break;
                        case "5":
                            System.out.println("<< " + udp.ping());
                            break;
                        case "6":
                            if (!tcpOk) { System.out.println("TCP no conectado."); break; }
                            imprimir(gson, tcp.listar());
                            break;
                        case "7": {
                            if (!tcpOk) { System.out.println("TCP no conectado."); break; }
                            System.out.print("sectorId: ");
                            imprimir(gson, tcp.consultar(sc.nextLine().trim()));
                            break;
                        }
                        case "8": {
                            if (!tcpOk) { System.out.println("TCP no conectado."); break; }
                            System.out.print("sectorId: ");
                            String id = sc.nextLine().trim();
                            System.out.print("minutos (1-60): ");
                            int m = Integer.parseInt(sc.nextLine().trim());
                            imprimir(gson, tcp.programar(id, m));
                            break;
                        }
                        case "9": {
                            if (!tcpOk) { System.out.println("TCP no conectado."); break; }
                            System.out.print("sectorId: ");
                            imprimir(gson, tcp.cancelar(sc.nextLine().trim()));
                            break;
                        }
                        case "0":
                            salir = true;
                            break;
                        default:
                            System.out.println("Opcion invalida.");
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("!! TIMEOUT: la central no respondio en " + TIMEOUT_MS + " ms");
                } catch (Exception e) {
                    System.out.println("!! Error: " + e.getMessage());
                }
            }
        }

        sc.close();
    }

    private static void imprimir(Gson gson, Response r) {
        System.out.println("<< " + gson.toJson(r));
    }
}
