# 05 — Preguntas de entrevista

Preguntas típicas sobre este proyecto y cómo responderlas.

### "¿Por qué partida doble y no sumar y restar el saldo?"
Porque la partida doble convierte la **conservación del dinero** en un invariante
comprobable, no en una convención. Cada movimiento es un asiento con al menos dos
líneas y `débitos == créditos` se valida **antes** de tocar ningún saldo. Así los
libros no pueden descuadrarse, y cualquier saldo es reproducible como la suma de sus
asientos: es auditable.

### "¿Por qué enteros en céntimos y no `double` o `BigDecimal`?"
`double` no representa decimales exactos y acumula error (`0,1 + 0,2 ≠ 0,3`); en
contabilidad eso descuadra los libros. Enteros en céntimos (`long`) dan sumas
exactas. `BigDecimal` también serviría, pero los enteros son más simples y rápidos
para este alcance; lo documento en el ADR 0001 y señalo que los importes sub-céntimo
quedarían fuera.

### "¿Qué hace exactamente la `Idempotency-Key`?"
Evita el doble cobro ante un reintento. La clave se guarda con el asiento en la misma
transacción y es la clave primaria de su tabla. Si la clave ya existe, devuelvo la
transferencia original (200) sin registrar nada; si dos peticiones con la misma clave
compiten, la segunda pierde la inserción única, se deshace y resuelve al original.
Como máximo un asiento por clave, incluso en concurrencia.

### "¿Cómo evitas perder actualizaciones si dos transferencias tocan la misma cuenta?"
Con **bloqueo optimista**: cada cuenta tiene `@Version` y el `UPDATE` lleva
`WHERE version = ?`. Si otra transacción ya cambió la versión, se actualizan cero
filas y salta la excepción de bloqueo. El servicio reintenta en una transacción nueva
y, si agota el presupuesto, devuelve 409. Ninguna escritura pisa a otra en silencio.

### "¿Por qué el `TransferService` no es `@Transactional` y sí lo es el `TransferExecutor`?"
Porque cada reintento necesita su **propia** transacción y su propio contexto de
persistencia. Si el bucle de reintento compartiera transacción con la unidad de
trabajo, un intento fallido la dejaría marcada para rollback y el reintento no podría
confirmar. Por eso la unidad de trabajo está en un bean aparte al que se llama a
través del proxy de Spring: cada llamada abre una transacción nueva.

### "Una transferencia descuadrada, ¿cómo la rechazas desde la API?"
Por construcción, la transferencia debita el destino y acredita el origen por el mismo
importe, así que siempre cuadra. El invariante vive en el dominio
(`JournalEntry.validateBalanced()`) y se prueba de forma directa: un asiento con una
sola línea, con importe no positivo, o con débitos ≠ créditos lanza
`UnbalancedEntryException`. En la API, los casos "inválidos" expresables —importe
negativo (400), cuenta desconocida (422), divisa distinta (422), misma cuenta
origen/destino (422)— se rechazan con una respuesta RFC-7807.

### "¿Por qué H2 en los tests y PostgreSQL en producción?"
Para que `mvn test` corra **sin servicios externos**: H2 en memoria, esquema generado
por Hibernate, Flyway desactivado. En producción, el perfil `postgres` usa una base
real, Flyway dueña del esquema (`V1__init.sql`) y Hibernate en modo `validate`. Los
tests nunca tocan ese perfil, así que el build es rápido y hermético.

### "¿Qué le falta para producción?"
Autenticación y aislamiento multi-inquilino, transferencias multidivisa con cambio de
moneda, retenciones/autorizaciones y liquidación, reversas y cierre de periodos,
caducidad de claves de idempotencia, e importes sub-céntimo. Es una implementación de
referencia de un **núcleo de libro mayor correcto**, no una plataforma bancaria
completa; lo que hace, lo hace bien.
