package service;

import model.SectorData;

/**
 * CANAL UDP — protocolo de texto de las estaciones de campo.
 *
 * No abre sockets: String entra, String sale. Por eso se prueba sin red.
 * El estado vive en RiegoService, compartido con el canal TCP.
 */
public class RiegoProcessor {

    private final RiegoService service;

    public RiegoProcessor(RiegoService service) {
        this.service = service;
    }

    public String process(String rawMessage) {

        // 1. Validar nulo o vacío.
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return "ERROR;FORMATO_INVALIDO";
        }

        // 2. Separar por ';' y sacar el comando.
        String[] parts = rawMessage.trim().split(";");
        String comando = parts[0].trim().toUpperCase();

        // 3. Comandos sin argumentos.
        if (comando.equals("PING")) {
            if (parts.length != 1) {
                return "ERROR;FORMATO_INVALIDO";
            }
            return "PONG";
        }

        // 4. Consulta de estado: ESTADO;<sectorId>
        if (comando.equals("ESTADO")) {
            if (parts.length != 2 || parts[1].trim().isEmpty()) {
                return "ERROR;FORMATO_INVALIDO";
            }
            String sectorId = parts[1].trim();
            SectorData data = service.getUltimaLectura(sectorId);
            if (data == null) {
                return "ERROR;SECTOR_NO_ENCONTRADO";
            }
            return "ESTADO_OK;" + data.getSectorId() + ";" + data.getTipo() + ";" + data.getValor();
        }

        // 5. Comando con validación de rango: REGAR;<sectorId>;<minutos>
        if (comando.equals("REGAR")) {
            if (parts.length != 3 || parts[1].trim().isEmpty() || parts[2].trim().isEmpty()) {
                return "ERROR;FORMATO_INVALIDO";
            }
            String sectorId = parts[1].trim();

            int minutos;
            try {
                minutos = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException e) {
                return "ERROR;FORMATO_INVALIDO";
            }

            if (!service.existe(sectorId)) {
                return "ERROR;SECTOR_NO_ENCONTRADO";
            }
            if (minutos < 1 || minutos > 60) {
                return "ERROR;DURACION_INVALIDA";
            }
            if (!service.programarRiego(sectorId, minutos)) {
                return "ERROR;RIEGO_YA_PROGRAMADO";
            }
            return "OK;RIEGO_PROGRAMADO;" + sectorId + ";" + minutos;
        }

        // 6. Registro de lectura: LECTURA;<sectorId>;<tipo>;<valor>
        if (comando.equals("LECTURA")) {
            if (parts.length != 4
                    || parts[1].trim().isEmpty()
                    || parts[2].trim().isEmpty()
                    || parts[3].trim().isEmpty()) {
                return "ERROR;FORMATO_INVALIDO";
            }

            String sectorId = parts[1].trim();
            String tipo = parts[2].trim().toUpperCase();

            double valor;
            try {
                valor = Double.parseDouble(parts[3].trim());
            } catch (NumberFormatException e) {
                return "ERROR;FORMATO_INVALIDO";
            }

            if (!tipo.equals("HUMEDAD") && !tipo.equals("CAUDAL") && !tipo.equals("PRESION")) {
                return "ERROR;TIPO_NO_SOPORTADO";
            }

            service.registrarLectura(sectorId, tipo, valor);

            switch (tipo) {
                case "HUMEDAD":
                    if (valor < 25.0) {
                        return "ALERTA;SUELO_SECO;" + valor;
                    } else if (valor > 85.0) {
                        return "ALERTA;ENCHARCAMIENTO;" + valor;
                    }
                    return "OK;HUMEDAD_OK;" + valor;

                case "CAUDAL":
                    if (valor < 5.0) {
                        return "ALERTA;CAUDAL_BAJO;" + valor;
                    }
                    return "OK;CAUDAL_OK;" + valor;

                default: // PRESION
                    if (valor > 80.0) {
                        return "ALERTA;SOBREPRESION;" + valor;
                    }
                    return "OK;PRESION_OK;" + valor;
            }
        }

        // 7. Cualquier otra cosa.
        return "ERROR;COMANDO_DESCONOCIDO";
    }
}
