# 02 — El dinero en enteros

## El problema de la coma flotante

Los ordenadores no representan `0,1 + 0,2` exactamente en coma flotante binaria: el
resultado es `0,30000000000000004`. Un libro mayor construido sobre `double` o
`float` acumula ese error, y un balance que debería ser exactamente cero no lo es.
En contabilidad eso es inaceptable: un céntimo perdido es un libro que no cuadra.

## La solución: céntimos en `long`

Todos los importes del sistema son un número entero de **unidades menores** (céntimos
para la mayoría de monedas), guardado en un `long`. La suma y la resta de enteros son
exactas, así que un asiento equilibrado cuadra de verdad, hasta el último céntimo.

El tipo `Money` (un `record`) encapsula el par importe + moneda:

```java
public record Money(long minorUnits, String currency) { ... }
```

- `minorUnits` es el valor autoritativo (p. ej. `150000`).
- `currency` es el código ISO-4217 de tres letras.
- `format()` convierte a texto legible (`"1500.00 USD"`) **solo** para mostrarlo.

## El formato vive en el borde

La conversión a decimal ocurre únicamente en la frontera de la API
(`Money.format()`), nunca dentro de la aritmética. Internamente y en la base de datos
todo son enteros. En la API, el saldo se devuelve así:

```json
{ "amount": 150000, "currency": "USD", "formatted": "1500.00 USD" }
```

El cliente envía y recibe enteros: `150000` significa `1500,00`. El DTO rechaza
importes no positivos con un error 400, así que un importe negativo o cero nunca
llega a la lógica de negocio.

## Límites y alcance

Un `long` llega hasta unos ±92 billones (de los largos) de céntimos, muy por encima
de cualquier saldo realista, así que el desbordamiento no es un problema práctico
aquí. Lo que queda **fuera de alcance** son los importes de fracción sub-céntimo
(algunos cambios de divisa, intereses por unidad): eso exigiría un entero escalado o
`BigDecimal` con una política de redondeo explícita en cada frontera. Está anotado
como decisión en el ADR 0001.
