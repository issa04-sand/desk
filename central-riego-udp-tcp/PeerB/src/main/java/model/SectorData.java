package model;

import java.time.Instant;

import com.google.gson.annotations.Expose;

/**
 * Última lectura reportada por un sector del cultivo.
 *
 * Los campos llevan @Expose porque el Gson del canal TCP se construye con
 * excludeFieldsWithoutExposeAnnotation(): sin la anotación, el objeto se
 * serializaría como {} vacío. 'timestamp' queda fuera a propósito, porque
 * Gson no sabe serializar Instant sin un adaptador.
 */
public class SectorData {

    @Expose private final String sectorId;
    @Expose private final String tipo;
    @Expose private final double valor;
    private final Instant timestamp;

    public SectorData(String sectorId, String tipo, double valor) {
        this.sectorId = sectorId;
        this.tipo = tipo;
        this.valor = valor;
        this.timestamp = Instant.now();
    }

    public String getSectorId() {
        return sectorId;
    }

    public String getTipo() {
        return tipo;
    }

    public double getValor() {
        return valor;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "SectorData{sectorId='" + sectorId + "', tipo='" + tipo + "', valor=" + valor + "}";
    }
}
