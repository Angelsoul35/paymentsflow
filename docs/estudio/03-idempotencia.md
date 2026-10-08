# 03 — Idempotencia

## El problema del reintento

Las redes fallan y los clientes reintentan. Si un `POST /transfers` agota el tiempo
de espera, el cliente no sabe si la transferencia se llegó a registrar o no. Si
reintenta a ciegas y la primera sí había funcionado, se registra **dos veces**: el
clásico doble cobro. El servidor, por sí solo, no puede distinguir un reintento de
una transferencia nueva.

## La solución: una clave de idempotencia

`POST /transfers` acepta una cabecera opcional `Idempotency-Key`. La clave se guarda
en la tabla `idempotency_record`, **cuya clave primaria es la propia clave**, y se
escribe en la **misma transacción** que el asiento que produjo.

- Si llega una petición cuya clave ya está registrada, el servicio devuelve la
  transferencia original (HTTP 200) y **no** registra nada nuevo.
- Si la clave es nueva, se registra el asiento y la clave de forma atómica. Si dos
  peticiones con la misma clave compiten a la vez, la segunda pierde la inserción
  única (`DataIntegrityViolationException`), su transacción se deshace —por lo que no
  hay doble registro— y el servicio resuelve devolviendo el asiento original.

Una petición sin clave se procesa con normalidad: la idempotencia es opcional, como
sugiere el nombre de la cabecera.

## Por qué la misma transacción

Guardar la clave y el asiento juntos es lo que hace la garantía sólida:

- La clave nunca queda registrada para una transferencia que no llegó a confirmarse.
- Una transferencia confirmada siempre tiene su clave.
- La restricción de unicidad convierte un duplicado concurrente en una inserción
  fallida, no en un segundo asiento.

En el transcript del README se ve: dos `POST` con `Idempotency-Key: demo-key-001`
devuelven el **mismo** `id` de transferencia (el primero 201, el segundo 200) y los
saldos reflejan **un solo** movimiento.

## Límites

Las claves se acumulan; en este alcance se conservan indefinidamente. Un sistema en
producción las caducaría (un TTL o un barrido periódico) y podría acotar la clave a
un cliente o a un endpoint en lugar de hacerla global. Además, aquí una misma clave
con un cuerpo distinto devuelve igualmente la transferencia original; validar que el
cuerpo coincide es una política más estricta y razonable que se omitió por
simplicidad (ADR 0003).
