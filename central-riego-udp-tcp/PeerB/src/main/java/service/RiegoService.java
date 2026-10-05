package service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import model.SectorData;

/**
 * ESTADO COMPARTIDO. No sabe nada de sockets ni de protocolos.
 *
 * Lo usan los DOS canales: el UDP (RiegoProcessor) y el TCP (GestionController).
 * Por eso es thread-safe: lo tocan el hilo del servidor UDP y los hilos del
 * ThreadPool del servidor TCP al mismo tiempo.
 */
public class RiegoService {

    private final Map<String, SectorData> ultimasLecturas = new ConcurrentHashMap<>();
    private final Map<String, Integer> riegosProgramados = new ConcurrentHashMap<>();

    public void registrarLectura(String sectorId, String tipo, double valor) {
        ultimasLecturas.put(sectorId, new SectorData(sectorId, tipo, valor));
    }

    public SectorData getUltimaLectura(String sectorId) {
        return ultimasLecturas.get(sectorId);
    }

    public boolean existe(String sectorId) {
        return sectorId != null && ultimasLecturas.containsKey(sectorId);
    }

    public int contarSectores() {
        return ultimasLecturas.size();
    }

    public List<SectorData> listarSectores() {
        List<SectorData> lista = new ArrayList<>(ultimasLecturas.values());
        lista.sort((a, b) -> a.getSectorId().compareTo(b.getSectorId()));
        return lista;
    }

    /**
     * Operación de leer-decidir-escribir: va synchronized para que dos clientes
     * concurrentes no programen riego sobre el mismo sector a la vez.
     */
    public synchronized boolean programarRiego(String sectorId, int minutos) {
        if (!existe(sectorId) || riegosProgramados.containsKey(sectorId)) {
            return false;
        }
        riegosProgramados.put(sectorId, minutos);
        return true;
    }

    public synchronized boolean cancelarRiego(String sectorId) {
        return riegosProgramados.remove(sectorId) != null;
    }

    public Integer getRiego(String sectorId) {
        return riegosProgramados.get(sectorId);
    }

    public void clear() {
        ultimasLecturas.clear();
        riegosProgramados.clear();
    }
}
