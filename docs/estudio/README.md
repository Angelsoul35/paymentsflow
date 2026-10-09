# Guía de estudio — paymentsflow

Una guía en español para entender el proyecto a fondo y poder defenderlo en una
entrevista. El código, los comentarios y la documentación técnica están en inglés
(como en un entorno profesional real); esta guía explica el *porqué* de cada
decisión.

## Orden sugerido

1. [01 — Partida doble](01-partida-doble.md)
   Qué es un asiento, por qué debe cuadrar, y las cinco cuentas.
2. [02 — El dinero en enteros](02-dinero-en-enteros.md)
   Por qué el dinero son céntimos en `long` y nunca `double`.
3. [03 — Idempotencia](03-idempotencia.md)
   Cómo una `Idempotency-Key` evita cobrar dos veces ante un reintento.
4. [04 — Bloqueo optimista y concurrencia](04-bloqueo-optimista-y-concurrencia.md)
   Cómo `@Version` evita que dos transferencias se pisen el saldo.
5. [05 — Preguntas de entrevista](05-preguntas-de-entrevista.md)
   Preguntas típicas y cómo responderlas con este proyecto.
6. [06 — Datos reales y validación del motor](06-datos-reales-y-validacion.md)
   Transacciones reales a través del motor, las invariantes con fórmulas, un
   ejemplo numérico con los números reales, la recomputación independiente, el
   control de manipulación y el veredicto.

## La idea en una frase

Un microservicio de pagos sobre un **libro mayor de doble partida**: cada movimiento
es un asiento equilibrado (débitos = créditos), el dinero son **céntimos enteros**
(nunca coma flotante), las transferencias son **idempotentes** con una clave de
cliente, y la concurrencia se protege con **bloqueo optimista** (`@Version`) para que
ninguna actualización se pierda.
