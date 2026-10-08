# 01 — Partida doble

## La regla de oro

En la contabilidad de **doble partida**, cada transacción toca al menos dos cuentas y
la suma de los **débitos** (debe) es igual a la suma de los **créditos** (haber). No
es una convención arbitraria: es una ecuación que se mantiene siempre y que permite
detectar errores. Si un asiento no cuadra, está mal, y el sistema lo rechaza antes de
tocar ningún saldo.

Ejemplo: mover 1.500,00 de la cuenta de Alice a la de Bob.

| Cuenta          | Debe (débito) | Haber (crédito) |
| --------------- | ------------- | --------------- |
| bob:checking    | 150000        |                 |
| alice:checking  |               | 150000          |

Los dos lados suman 150000 (céntimos): el asiento cuadra. El dinero no se crea ni se
destruye, solo se mueve.

## En el código

- Un `JournalEntry` contiene una lista de `Posting` (al menos dos).
- Cada `Posting` tiene una dirección (`DEBIT` o `CREDIT`), un importe positivo y una
  cuenta.
- `JournalEntry.validateBalanced()` comprueba tres cosas **antes** de modificar
  saldos: hay al menos dos líneas, todos los importes son positivos, y
  `totalDebits() == totalCredits()`. Si algo falla, lanza
  `UnbalancedEntryException`.

Una transferencia es el caso concreto de un asiento que **debita el destino y
acredita el origen** por el mismo importe, así que cuadra por construcción; aun así se
valida (defensa en profundidad).

## Las cinco cuentas y su lado "natural"

Cada tipo de cuenta tiene un lado por el que crece:

| Tipo        | Crece por… | Ejemplo                   |
| ----------- | ---------- | ------------------------- |
| Activo      | débito     | Caja, cuentas por cobrar  |
| Gasto       | débito     | Alquiler, nóminas         |
| Pasivo      | crédito    | Cuentas por pagar         |
| Patrimonio  | crédito    | Capital del propietario   |
| Ingreso     | crédito    | Ventas, servicios         |

En el código esto es una sola propiedad: `AccountType.isDebitNormal()`. Activos y
gastos son de "débito natural"; pasivos, patrimonio e ingresos son de "crédito
natural". Esa propiedad decide el signo del efecto de cada línea sobre el saldo
(`Direction.signedEffect`): una línea **aumenta** el saldo cuando su dirección
coincide con el lado natural de la cuenta, y lo **disminuye** en caso contrario.

## Financiar una cuenta también cuadra

¿De dónde sale el dinero inicial de Alice? De un asiento de apertura: se **debita** su
cuenta de activo y se **acredita** una cuenta de **patrimonio** (`owner:equity`) por
el mismo importe. Por eso los ejemplos financian a Alice con una transferencia desde
`owner:equity` en lugar de escribir un saldo "a mano": escribir un saldo sin
contrapartida rompería la doble partida.
