# Central de Riego — UDP + TCP en un mismo sistema

Repo de **referencia, ya resuelto**, con la misma estructura del lab de telemetría
(`PeerA` / `PeerB`, Gradle multi-módulo, pruebas JUnit 5). Aquí no hay TODOs:
este es el código que hay que saber escribir en el parcial.

```bash
./gradlew test          # 19 pruebas en verde
./gradlew :PeerB:run    # terminal 1 — la central
./gradlew :PeerA:run    # terminal 2 — la estación de campo
```

---

## 1. Por qué dos protocolos

| Quién | Qué necesita | Canal | Por qué |
|---|---|---|---|
| Estaciones de campo | reportar humedad, caudal y presión cada pocos segundos | **UDP** 5000 | mensaje diminuto, altísima frecuencia; perder una lectura no importa, la siguiente llega enseguida |
| App de gestión | programar y cancelar riegos, consultar el estado | **TCP** 6000 | programar un riego es una transacción: no se puede perder ni llegar a medias |

Los dos canales golpean el **mismo** `RiegoService`:

```
  Estaciones --UDP:5000--> CentralServer ──┐
                                            ├──> RiegoService (estado único, thread-safe)
  App        --TCP:6000--> GestionController┘
```

El protocolo cambia; el estado no.

---

## 2. Mapa de archivos

```text
PeerB/  (la central)
  model/SectorData.java              entidad; @Expose para que Gson la serialice
  service/RiegoService.java          ESTADO COMPARTIDO, thread-safe. Sin sockets.
  service/RiegoProcessor.java        protocolo de TEXTO del canal UDP. Sin sockets.
  service/CentralServer.java         socket UDP: recibe, delega, responde al emisor
  controllers/GestionController.java socket TCP: accept + ThreadPool + JSON
  controllers/dtos/Request.java      command + JsonObject
  controllers/dtos/Response.java     status + Map<String,Object>
  ui/Main.java                       levanta los DOS canales sobre un RiegoService

PeerA/  (la estación de campo)
  client/FieldClient.java            cliente UDP con setSoTimeout
  client/GestionClient.java          cliente TCP de conexión larga
  ui/Main.java                       consola que usa los dos
```

La regla que ordena todo: **si una línea menciona `DatagramPacket`, `Socket` o
`byte[]`, va en un controlador; si menciona las reglas del enunciado, va en
`RiegoProcessor` o en `handleRequest`.**

---

## 3. Protocolo UDP — texto, UTF-8, campos separados por `;`

| Petición | Respuesta OK | Errores |
|---|---|---|
| `LECTURA;<sector>;HUMEDAD;<v>` | `<25` → `ALERTA;SUELO_SECO;<v>` · `>85` → `ALERTA;ENCHARCAMIENTO;<v>` · si no `OK;HUMEDAD_OK;<v>` | `ERROR;TIPO_NO_SOPORTADO` |
| `LECTURA;<sector>;CAUDAL;<v>` | `<5` → `ALERTA;CAUDAL_BAJO;<v>` · si no `OK;CAUDAL_OK;<v>` | |
| `LECTURA;<sector>;PRESION;<v>` | `>80` → `ALERTA;SOBREPRESION;<v>` · si no `OK;PRESION_OK;<v>` | |
| `ESTADO;<sector>` | `ESTADO_OK;<sector>;<tipo>;<valor>` | `ERROR;SECTOR_NO_ENCONTRADO` |
| `REGAR;<sector>;<minutos>` | `OK;RIEGO_PROGRAMADO;<sector>;<min>` | `SECTOR_NO_ENCONTRADO`, `DURACION_INVALIDA` (fuera de 1–60), `RIEGO_YA_PROGRAMADO` |
| `PING` | `PONG` | |
| cualquier otra | | `ERROR;COMANDO_DESCONOCIDO` |
| nulo, vacío, campos de más o de menos, valor no numérico | | `ERROR;FORMATO_INVALIDO` |

---

## 4. Protocolo TCP — JSON con Gson, una línea por mensaje

Petición `{"command":"...","data":{...}}` · Respuesta `{"status":"OK|ERROR","data":{...}}`

| `command` | `data` | Respuesta OK | Errores |
|---|---|---|---|
| `LISTAR` | — | `data.sectores` (lista), `data.total` | |
| `CONSULTAR` | `sectorId` | `data.sector` | `SECTOR_NO_ENCONTRADO` |
| `PROGRAMAR` | `sectorId`, `minutos` | `data.sectorId`, `data.minutos` | `SECTOR_NO_ENCONTRADO`, `DURACION_INVALIDA`, `RIEGO_YA_PROGRAMADO` |
| `CANCELAR` | `sectorId` | `data.sectorId` | `SIN_RIEGO_PROGRAMADO` |
| otro | | | `COMANDO_DESCONOCIDO` |

Los errores van como `{"status":"ERROR","data":{"message":"..."}}`.

---

## 5. Las diferencias entre los dos clientes

Esto es lo que más se pregunta, y aquí se ve lado a lado:

| | `FieldClient` (UDP) | `GestionClient` (TCP) |
|---|---|---|
| Socket | uno **nuevo por mensaje** | uno solo, reutilizado (`conectar()` … `close()`) |
| Por qué | no hay conexión que negociar: abrir es gratis | el handshake de 3 pasos cuesta un viaje; se amortiza |
| Timeout | `setSoTimeout()` **obligatorio** | no hace falta: TCP avisa si la conexión se cae |
| Delimitar | no hace falta: el datagrama llega entero | `newLine()` + `flush()`, y el otro lee con `readLine()` |
| Si se pierde | silencio, y salta `SocketTimeoutException` | TCP retransmite solo |

---

## 6. Las pruebas

```bash
./gradlew :PeerB:test                       # 14 unitarias, sin red
./gradlew :PeerA:test                       # 5 de integración, con sockets reales
```

- `RiegoProcessorTest` (CP-01…CP-09) — el protocolo de texto, sin sockets
- `GestionControllerTest` (CP-10…CP-14) — `handleRequest`, sin sockets
- `IntegracionTest` (IT-01…IT-05) — servidores reales en **puerto 0**:
  - IT-01 round-trip UDP
  - IT-02 `SocketTimeoutException` con la central apagada
  - IT-03 varias peticiones por el mismo socket TCP
  - IT-04 **estado compartido**: entra por UDP, se ve por TCP, se programa por TCP, el canal UDP ya lo ve ocupado
  - IT-05 cinco clientes TCP concurrentes contra el ThreadPool

---

## 7. Demo en vivo

Con `:PeerB:run` y `:PeerA:run` en dos terminales:

1. Opción 1 → sector `S-1`, humedad `18` → sale `ALERTA;SUELO_SECO;18.0` (UDP)
2. Opción 6 → `LISTAR` por TCP → ahí está `S-1`, que entró por el otro canal
3. Opción 8 → programar riego 30 min en `S-1` → `OK`
4. Opción 8 otra vez → `RIEGO_YA_PROGRAMADO`
5. Ctrl+C en la central, y opción 5 (`PING`) → timeout a los 2 s
