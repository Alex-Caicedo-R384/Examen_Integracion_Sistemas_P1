# Prototipo de Integración – AgroTech Solutions S.A.

**Patrones:** File Transfer · Shared Database · RPC (simulado)  
**Stack:** Java 17 · Maven · Apache Camel 4.x · SQLite · Logback

---

## Resumen

Se implementó un prototipo funcional de integración entre **SensData**, **AgroAnalyzer** y **FieldControl**. El sistema:

- Detecta automáticamente un CSV con lecturas de sensores (**File Transfer**).
- **Copia** el archivo a una carpeta de salida y **convierte** el contenido a **JSON**.
- **Persiste** todas las lecturas en una **base de datos compartida (SQLite)**.
- Expone una **consulta síncrona (RPC simulado)** para recuperar el **último valor por sensor**.
- **Registra** la operación en un **log centralizado** con rotación.

El resultado cumple el caso de uso: **disponibilidad del último valor** para decisiones de riego y **trazabilidad end-to-end** del flujo.

---

## Arquitectura y patrones explicados

### 1) File Transfer (SensData → AgroAnalyzer)
- Un consumidor de archivos **Camel** detecta `./input/sensores.csv`, **copia** el archivo a `./output/` con timestamp y convierte el contenido del CSV a **JSON** (arreglo de lecturas).
- **Justificación:** patrón sencillo y robusto, alineado con procesos por lotes de exportación horaria.

### 2) Shared Database (AgroAnalyzer ↔ FieldControl)
- AgroAnalyzer **inserta** cada lectura en `./database/agrotech.db` (SQLite), tabla `lecturas(id_sensor, fecha, humedad, temperatura)`.
- FieldControl **consulta** directamente la misma BD para obtener el **último valor** por sensor.
- **Justificación:** repositorio común, **baja latencia** de lectura y **simplicidad** de integración.

### 3) RPC simulado (FieldControl → AgroAnalyzer)
- Llamada **síncrona** modelada con rutas Camel `direct:`; el cliente envía `id_sensor`, el servidor **consulta SQLite** y responde con la lectura **más reciente**.
- **Justificación:** emula toma de decisión en línea (activar/desactivar riego) con **respuesta inmediata**.

### 4) Logging centralizado
- Log a archivo `./logs/agrotech.log` con **rotación diaria y por tamaño** (Logback).

---

## Ejecución

> Ejecuta el JAR desde la **raíz** del proyecto (rutas relativas a `./`).

1. **Compilar/empacar**
   ```bash
   mvn clean package
   ```
2. **Ejecutar**
   ```bash
   java -jar target/agrotech-integration-*-runner.jar
   ```
3. **Colocar CSV** en `./input/sensores.csv` con encabezados exactos:
   ```csv
   id_sensor,fecha,humedad,temperatura
   S001,2025-05-22,45,26.4
   S002,2025-05-22,50,25.1
   S003,2025-05-22,47,27.3
   ```
4. Verificar en `./output`:
   - Copia del CSV con timestamp (p. ej. `sensores_YYYYMMDDHHmmss.csv`)
   - `lecturas.json` con el arreglo completo.
5. Revisar `./logs/agrotech.log`:
   - `[FILE]`, `[FILE->JSON]`, `[DB] Insert`, `[OUTPUT]`, y trazas del **RPC** cada 5s.

---

## Validación rápida

**Logs esperados**
```
[FILE] Detectado sensores.csv
[FILE->JSON] Registros: 3
[DB] Insert S001 2025-05-22 h=45.0 t=26.4
[DB] Insert S002 2025-05-22 h=50.0 t=25.1
[DB] Insert S003 2025-05-22 h=47.0 t=27.3
[OUTPUT] JSON generado en /output/lecturas.json
[CLIENTE] Solicitando lectura del sensor S001
[SERVIDOR] Solicitud recibida para sensor S001
[CLIENTE] Respuesta recibida: {"id":"S001","humedad":45.0,"temperatura":26.4,"fecha":"2025-05-22"}
```

**Consultas SQL útiles (SQLite)**
```sql
-- Tablas
SELECT name FROM sqlite_schema WHERE type = 'table' ORDER BY name;

-- Estructura de 'lecturas'
PRAGMA table_info(lecturas);

-- Total de filas
SELECT COUNT(*) AS total FROM lecturas;

-- Todas las lecturas
SELECT id_sensor, fecha, humedad, temperatura
FROM lecturas
ORDER BY id_sensor, fecha;

-- Último valor de S001
SELECT id_sensor, fecha, humedad, temperatura
FROM lecturas
WHERE id_sensor='S001'
ORDER BY fecha DESC
LIMIT 1;
```

---

## Reflexión individual

**¿Qué patrón aplicaste en cada fase del flujo y por qué?**  
- **File Transfer:** detección de CSV, **copia** y **transformación a JSON**; simple y robusto, emula los procesos por lotes de SensData.  
- **Shared Database:** repositorio común para lecturas; FieldControl lee el último valor con **baja latencia**.  
- **RPC simulado:** solicitud-respuesta **inmediata** para decisiones de riego en tiempo real.

**¿Qué riesgos observas al usar una base de datos compartida?**  
Cuando muchos sistemas usan la misma base de datos, un cambio de esquema puede afectar a todos; con alta concurrencia puede haber **demoras o bloqueos**. Es clave cuidar la **seguridad y gobernanza** (accesos, auditoría, cifrado). Si no se diseña bien, puede convertirse en **punto único de falla** y dificultar la **escalabilidad**. También se debe mantener la **consistencia** de los datos con transacciones adecuadas.

**¿Cómo ayuda el RPC simulado a representar un flujo síncrono?**  
El cliente **espera la respuesta** antes de continuar; se envían datos como el **identificador del sensor** y se obtiene un resultado determinista. Refleja la **decisión inmediata** que toma FieldControl.

**¿Qué limitaciones tienen los patrones clásicos frente a arquitecturas modernas?**  
El envío de archivos puede ser más **lento** que el **event streaming** en tiempo real. Depender de una BD compartida incrementa el **acoplamiento**; con colas/streams (Kafka, RabbitMQ) es más fácil **escalar** y **resistir fallos**. Además, sin buenas **trazas** o **contratos** claros entre sistemas, es más difícil observar y evolucionar.

---

## Estructura sugerida del proyecto

```
/evaluacion-practica-agrotech/
├── input/
├── output/
├── database/
├── logs/
├── src/
├── README.md
└── reflexion.pdf
```

---

## Evidencias

Incluye capturas de:
- El CSV con **3+ filas**.
- `agrotech.log` con `[FILE]`, `[FILE->JSON]`, `[DB] Insert`, `[OUTPUT]`.
- `lecturas.json` en `./output`.
- Tabla `lecturas` y consultas en SQLite (último valor por sensor).
- Trazas del **RPC** cliente/servidor.
