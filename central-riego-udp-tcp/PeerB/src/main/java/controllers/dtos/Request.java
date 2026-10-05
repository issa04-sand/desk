package controllers.dtos;

import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;

/**
 * DTO de petición del canal TCP.
 *
 * Gson lo llena por reflexión, así que necesita constructor sin argumentos.
 * 'data' es JsonObject (no Map) para que pueda llevar tanto una entidad
 * completa como un par de campos sueltos, sin un DTO por comando.
 *
 * Ejemplo: {"command":"PROGRAMAR","data":{"sectorId":"S-1","minutos":30}}
 */
public class Request {

    @Expose private String command;
    @Expose private JsonObject data;

    public Request() {
    }

    public Request(String command, JsonObject data) {
        this.command = command;
        this.data = data;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public JsonObject getData() {
        return data;
    }

    public void setData(JsonObject data) {
        this.data = data;
    }
}
