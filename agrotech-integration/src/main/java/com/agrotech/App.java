package com.agrotech;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.stream.Collectors;
import org.apache.camel.main.Main;
import com.zaxxer.hikari.HikariDataSource;

public class App {

  public static void main(String[] args) throws Exception {
    String base = Path.of("").toAbsolutePath().normalize().toString().replace("\\", "/");
    String dbPath = base + "/database/agrotech.db";

    Files.createDirectories(Path.of(base + "/database"));
    Files.createDirectories(Path.of(base + "/logs"));
    Files.createDirectories(Path.of(base + "/input"));
    Files.createDirectories(Path.of(base + "/output"));

    System.setProperty("LOG_DIR", base + "/logs");

    String listing = Files.list(Path.of(base + "/input"))
      .map(p -> p.getFileName().toString())
      .collect(Collectors.joining(", "));
    System.out.println("[BOOT] BASE=" + base);
    System.out.println("[BOOT] INPUT LIST=" + listing);

    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl("jdbc:sqlite:" + dbPath);
    ds.setMaximumPoolSize(3);

    try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
      st.execute("""
        CREATE TABLE IF NOT EXISTS lecturas (
          id_sensor VARCHAR(10),
          fecha TEXT,
          humedad DOUBLE,
          temperatura DOUBLE
        );
      """);
      st.execute("""
        CREATE INDEX IF NOT EXISTS idx_lecturas_sensor_fecha
        ON lecturas(id_sensor, fecha);
      """);
    }

    Main main = new Main();
    main.bind("dataSource", ds);
    main.bind("servicioAnalitica", new ServicioAnalitica(ds));
    main.configure().addRoutesBuilder(new Routes(base));
    main.run(args);
  }
}
