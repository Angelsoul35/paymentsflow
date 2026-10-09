# 04 — Bloqueo optimista y concurrencia

## El problema de la actualización perdida

Dos transferencias que tocan la misma cuenta pueden entrelazarse:

1. La transferencia A lee el saldo = 100.
2. La transferencia B lee el saldo = 100.
3. A escribe 100 − 30 = 70.
4. B escribe 100 + 50 = 150.

El efecto de A se **pierde**: el resultado debería ser 120, no 150. En un libro
mayor, una actualización perdida es dinero perdido.

## La solución: bloqueo optimista con `@Version`

Cada `Account` tiene una columna `@Version`. Al escribir un saldo, Hibernate ejecuta:

```sql
UPDATE account SET balance_minor = ?, version = ? WHERE id = ? AND version = ?
```

Si otra transacción ya incrementó la versión, la condición `version = ?` no coincide,
se actualizan **cero filas**, y Hibernate lanza una excepción de bloqueo optimista.
Nadie sobrescribe en silencio a nadie.

"Optimista" significa que **no** bloqueamos la fila por adelantado: dejamos que las
transferencias avancen y solo la que choca de verdad paga el coste. Es lo contrario
del bloqueo pesimista, que serializaría *todas* las transferencias aunque no entren
en conflicto.

## Reintento, y si no, 409

`TransferService` envuelve cada intento en un **bucle de reintento** (por defecto 8
intentos, configurable con `paymentsflow.transfer.max-retries`). Ante un conflicto,
reintenta en una transacción nueva con una pequeña espera aleatoria; si agota el
presupuesto, devuelve **409 Conflict** para que el cliente reintente.

Un detalle clave de diseño: la unidad de trabajo transaccional vive en un bean aparte,
`TransferExecutor`, y `TransferService` **no** es transaccional. Así cada reintento
corre en su propia transacción con su propio contexto de persistencia. Si el bucle
compartiera una sola transacción con el executor, un intento fallido la dejaría
marcada para rollback y el reintento no podría confirmar.

## Cómo se prueba

El test `TransferConcurrencyIntegrationTest` lanza 10 transferencias simultáneas
contra el mismo par de cuentas y comprueba que:

- todas se confirman (gracias al reintento),
- el saldo final es exactamente el de una ejecución secuencial, y
- los dos saldos siguen sumando el total financiado (no se perdió nada).

## El límite medible del enfoque

Bajo mucha contención sobre la **misma** cuenta, los reintentos añaden latencia y un
punto caliente persistente puede agotar el presupuesto y devolver 409. Es el precio
justo del enfoque optimista. Una carga con esos puntos calientes podría fragmentar la
cuenta caliente o agrupar sus asientos. Como todas las transferencias bloquean las
cuentas en el mismo orden (origen y luego destino dentro de un mismo flush), este
diseño no introduce interbloqueos (deadlocks).
