package com.agrotech;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.apache.camel.Header;

public class ServicioAnalitica {

  private final DataSource ds;

  public ServicioAnalitica(DataSource ds) {
    this.ds = ds;
  }

  public String getUltimoValor(@Header("id_sensor") String id) throws Exception {
    String sql = """
      SELECT id_sensor, fecha, humedad, temperatura
      FROM lecturas
      WHERE id_sensor = ?
      ORDER BY fecha DESC
      LIMIT 1
    """;
    try (Connection con = ds.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {
      ps.setString(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          String sensor = rs.getString("id_sensor");
          String fecha  = rs.getString("fecha");
          double hum    = rs.getDouble("humedad");
          double temp   = rs.getDouble("temperatura");
          return """
            {"id":"%s","humedad":%s,"temperatura":%s,"fecha":"%s"}
            """.formatted(sensor, hum, temp, fecha);
        } else {
          return """
            {"id":"%s","mensaje":"sin lecturas registradas"}
            """.formatted(id);
        }
      }
    }
  }
}
