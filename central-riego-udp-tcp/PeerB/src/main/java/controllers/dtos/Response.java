package controllers.dtos;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.annotations.Expose;

/**
 * DTO de respuesta del canal TCP.
 *
 * data es Map<String,Object> porque lleva textos, números y listas
 * (por ejemplo la lista de sectores), no solo texto.
 *
 * Ejemplo: {"status":"OK","data":{"sectorId":"S-1","minutos":30}}
 */
public class Response {

    @Expose private String status;
    @Expose private Map<String, Object> data = new HashMap<>();

    public Response() {
    }

    public static Response ok() {
        Response r = new Response();
        r.status = "OK";
        return r;
    }

    public static Response error(String mensaje) {
        Response r = new Response();
        r.status = "ERROR";
        r.data.put("message", mensaje);
        return r;
    }

    public Response put(String clave, Object valor) {
        this.data.put(clave, valor);
        return this;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
