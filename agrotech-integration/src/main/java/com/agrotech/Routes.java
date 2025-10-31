package com.agrotech;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

public class Routes extends RouteBuilder {

  private final String base;

  public Routes(String baseDir) {
    this.base = baseDir.replace("\\", "/");
  }

  @Override
  public void configure() {

    from("file:" + base + "/input"
        + "?noop=true"
        + "&idempotent=true"
        + "&idempotentKey=${file:name}-${file:size}-${date:header.CamelFileLastModified:yyyyMMddHHmmss}"
        + "&include=(?i).*\\.csv$"
        + "&readLock=none"
        + "&initialDelay=0"
        + "&delay=1s"
        + "&startingDirectoryMustExist=true"
        + "&bridgeErrorHandler=true")
      .routeId("sensdata-filetransfer")
      .log("[FILE] Detectado ${header.CamelFileName}")
      .toD("file:" + base + "/output?fileName=${file:name.noext}_${date:now:yyyyMMddHHmmss}.csv")
      .unmarshal().csv()
      .process(exchange -> {
        @SuppressWarnings("unchecked")
        List<List<String>> rows = exchange.getMessage().getBody(List.class);
        if (rows == null || rows.isEmpty()) {
          exchange.getMessage().setBody(List.of());
          exchange.getMessage().setHeader("insert_count", 0);
          return;
        }
        List<String> headerRaw = rows.get(0);
        List<String> header = new java.util.ArrayList<>();
        for (String h : headerRaw) {
          if (h == null) h = "";
          h = h.replace("\uFEFF", "").trim().toLowerCase();
          header.add(h);
        }
        int idxId = header.indexOf("id_sensor");
        int idxFecha = header.indexOf("fecha");
        int idxHum = header.indexOf("humedad");
        int idxTemp = header.indexOf("temperatura");
        if (idxId < 0 || idxFecha < 0 || idxHum < 0 || idxTemp < 0) {
          throw new IllegalArgumentException("Encabezados CSV no válidos. Esperados: id_sensor, fecha, humedad, temperatura. Recibidos: " + header);
        }
        List<Map<String, Object>> lecturas = new java.util.ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
          List<String> r = rows.get(i);
          if (r == null) continue;
          int maxIdx = Math.max(Math.max(idxId, idxFecha), Math.max(idxHum, idxTemp));
          if (r.size() <= maxIdx) continue;
          String humStr = r.get(idxHum);
          String tempStr = r.get(idxTemp);
          if (humStr == null || tempStr == null) continue;
          humStr = humStr.replace(",", ".").trim();
          tempStr = tempStr.replace(",", ".").trim();
          Map<String, Object> m = new HashMap<>();
          m.put("id_sensor", r.get(idxId));
          m.put("fecha", r.get(idxFecha));
          m.put("humedad", Double.valueOf(humStr));
          m.put("temperatura", Double.valueOf(tempStr));
          lecturas.add(m);
        }
        exchange.getMessage().setHeader("insert_count", lecturas.size());
        exchange.getMessage().setBody(lecturas);
      })
      .marshal().json(JsonLibrary.Jackson)
      .log("[FILE->JSON] Registros: ${header.insert_count}")
      .setProperty("lecturasJson", body())
      .to("direct:agroAnalyzer")
      .setBody(exchangeProperty("lecturasJson"))
      .setHeader(Exchange.FILE_NAME, constant("lecturas.json"))
      .to("file:" + base + "/output?fileExist=Override")
      .log("[OUTPUT] JSON generado en /output/lecturas.json");

    from("direct:agroAnalyzer")
      .routeId("agroanalyzer-persist")
      .unmarshal().json(JsonLibrary.Jackson, List.class)
      .split(body()).streaming()
        .process(ex -> {
          @SuppressWarnings("unchecked")
          Map<String, Object> m = ex.getMessage().getBody(Map.class);
          ex.getMessage().setHeader("id_sensor", m.get("id_sensor"));
          ex.getMessage().setHeader("fecha", m.get("fecha"));
          ex.getMessage().setHeader("humedad", m.get("humedad"));
          ex.getMessage().setHeader("temperatura", m.get("temperatura"));
        })
        .to("sql:INSERT INTO lecturas (id_sensor, fecha, humedad, temperatura) VALUES (:#id_sensor, :#fecha, :#humedad, :#temperatura)?dataSource=#dataSource")
        .log("[DB] Insert ${header.id_sensor} ${header.fecha} h=${header.humedad} t=${header.temperatura}")
      .end();

    from("direct:solicitarLectura")
      .routeId("rpc-cliente")
      .setHeader("id_sensor", simple("${body}"))
      .log("[CLIENTE] Solicitando lectura del sensor ${header.id_sensor}")
      .to("direct:rpc.obtenerUltimo")
      .log("[CLIENTE] Respuesta recibida: ${body}");

    from("direct:rpc.obtenerUltimo")
      .routeId("rpc-servidor")
      .log("[SERVIDOR] Solicitud recibida para sensor ${header.id_sensor}")
      .bean("servicioAnalitica", "getUltimoValor");

    from("timer:demoRpc?period=5s&delay=5s")
      .setBody(constant("S001"))
      .to("direct:solicitarLectura");
  }
}
