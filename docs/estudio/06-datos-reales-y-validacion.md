# 06 — Datos reales y validación del motor

Los capítulos anteriores explican el *diseño*. Este capítulo demuestra que el
motor **funciona sobre datos reales**: alimentamos transacciones reales a través
del propio motor de paymentsflow y comprobamos que el libro mayor resultante
cumple las invariantes contables, comparándolo con valores de referencia
calculados de forma **independiente**.

## Los datos reales

Usamos el conjunto de datos *Online Retail* (UCI Machine Learning Repository,
DOI 10.24432/C5BW33, licencia CC BY 4.0): las transacciones reales de un
minorista en línea del Reino Unido entre 2010 y 2011. Vendemos un recorte
reproducible de las primeras **1.989** líneas de factura en
`src/test/resources/realdata/online-retail/` (ver su `README.md` para fuente,
licencia y regeneración).

Cada fila es una línea de factura: número de factura, código de producto,
cantidad, precio unitario en libras, identificador de cliente y país. Un número
de factura que empieza por `C` es una **nota de crédito** (una devolución, con
cantidad negativa).

## El mapeo a doble partida

El libro de ventas del minorista se modela con dos clases de cuenta del motor:

- una cuenta de **ingreso** (crédito natural): `revenue:sales`;
- una cuenta de **activo** (débito natural) por cada cliente: su cuenta por
  cobrar, `customer:<id>`.

Cada factura se convierte en **un asiento equilibrado**:

| Caso                       | Transferencia                 | Efecto                                  |
| -------------------------- | ----------------------------- | --------------------------------------- |
| Venta (neto > 0)           | `transfer(revenue → customer)`| ingreso sube, cuenta por cobrar sube    |
| Devolución (neto < 0)      | `transfer(customer → revenue)`| ingreso baja, cuenta por cobrar baja    |

El importe se convierte a **peniques exactos** con aritmética de enteros (nunca
`double`): `2.55 GBP → 255`. Así, los importes que entran al motor son exactos,
igual que el modelo de "unidades menores" del propio motor (capítulo 02).

## Las invariantes, con fórmulas

### 1. Doble partida por asiento

Para cada asiento *e*, la suma de débitos es igual a la suma de créditos:

```
Σ débitos(e) = Σ créditos(e)
```

Como una transferencia debita una cuenta y acredita otra por el mismo importe,
cuadra por construcción; aun así `validateBalanced()` lo comprueba antes de tocar
ningún saldo.

### 2. Recurrencia del saldo

El saldo de una cuenta tras su *k*-ésima línea es el saldo anterior más el efecto
con signo de esa línea:

```
saldo_k = saldo_(k-1) + signedEffect(tipo, dirección, importe)
saldo_0 = 0
```

donde `signedEffect = +importe` si la dirección coincide con el lado natural de la
cuenta, y `−importe` en caso contrario (capítulo 01). Por tanto, el saldo final de
una cuenta es exactamente la suma de los efectos con signo de todas sus líneas.

### 3. Balance de comprobación (compensación / conservación)

Sumando sobre **todas** las cuentas, los saldos de débito natural menos los de
crédito natural dan cero:

```
Σ (saldo de cuentas de débito natural) − Σ (saldo de cuentas de crédito natural) = 0
```

En nuestro mapeo esto significa que **la suma de todas las cuentas por cobrar de
los clientes es igual al saldo de ingresos**. El dinero ni se crea ni se destruye:
cada penique que sube en un lado sube en el otro.

## Un mini-ejemplo numérico con los números reales

La primera factura del recorte es la **536365** del cliente **17850**. Sus líneas,
convertidas a peniques (`cantidad × precio`):

| Producto | Cantidad | Precio | Línea (peniques)      |
| -------- | -------- | ------ | --------------------- |
| 85123A   | 6        | 2.55   | 1530                  |
| 71053    | 6        | 3.39   | 2034                  |
| 84406B   | 8        | 2.75   | 2200                  |
| 84029G   | 6        | 3.39   | 2034                  |
| 84029E   | 6        | 3.39   | 2034                  |
| 22752    | 2        | 7.65   | 1530                  |
| 21730    | 6        | 4.25   | 2550                  |
| **Neto** |          |        | **13 912**            |

Es una venta (neto > 0), así que el motor ejecuta `transfer(revenue → customer:17850)`
por 13 912 peniques:

- `revenue` (ingreso, crédito natural) recibe un **crédito**: su saldo sube a 13 912.
- `customer:17850` (activo, débito natural) recibe un **débito**: su saldo sube a 13 912.

El asiento cuadra: débito 13 912 = crédito 13 912. Aplicando la recurrencia a todas
las facturas del cliente 17850, su cuenta por cobrar termina en **149 934** peniques
(1 499,34 GBP).

Sumando todo el recorte (89 asientos sobre 71 clientes):

| Magnitud                                   | Peniques    |
| ------------------------------------------ | ----------- |
| Ingresos — ventas netas                    | 3 222 693   |
| Suma de cuentas por cobrar de clientes     | 3 222 693   |
| Balance de comprobación (activos − créditos) | **0**     |
| Volumen bruto (Σ débitos = Σ créditos)     | 3 287 739   |

Ingresos y suma de cuentas por cobrar coinciden: la invariante 3 se cumple. El
volumen bruto (3 287 739) es mayor que el neto (3 222 693) porque las 6 notas de
crédito restan: `bruto = Σ |neto|`, `neto = Σ neto`.

## La recomputación independiente

Los valores de arriba no los calcula el motor que estamos validando. Los calcula
`regenerate.py` en Python puro, leyendo el mismo CSV con las mismas reglas de
forma, pero por un **camino de código distinto y en otro lenguaje**. El test de
Java fija esos valores como constantes y comprueba que el motor los reproduce **al
penique**. Que dos implementaciones independientes coincidan es una prueba mucho
más fuerte que comprobar el motor contra sí mismo.

## El control de manipulación (y por qué debe fallar)

Un test que nunca puede fallar no prueba nada. Por eso añadimos tres controles que
**corrompen** la entrada o el libro mayor y exigen que una comprobación concreta
falle:

1. **Quitar una línea.** Construimos un asiento con el importe real de la factura
   536365 pero con una sola línea (se "pierde" el crédito). `validateBalanced()`
   debe lanzar `UnbalancedEntryException` ("at least two postings"). Si no fallara,
   la comprobación de doble partida no tendría dientes.
2. **Falsear un importe.** Inflamos una sola factura en 100 peniques y repetimos el
   proceso. Los ingresos del motor pasan a 3 222 793 (no 3 222 693) y el saldo del
   cliente afectado también se desvía. La conciliación contra la referencia fijada
   **falla**, que es justo lo que debe hacer ante un registro corrupto. (El asiento
   interno sigue cuadrando: por eso la conciliación contra una referencia externa
   captura lo que la comprobación interna no puede.)
3. **Un apunte a un solo lado.** Tras una carga limpia, aplicamos un crédito de 500
   peniques a `revenue` sin la contrapartida. El balance de comprobación global deja
   de ser cero (pasa a −500). Demuestra que la invariante 3 es **sensible**: solo da
   cero porque cada apunte está equilibrado.

## Veredicto

El motor de doble partida de paymentsflow procesa transacciones reales y reproduce,
**al penique**, todos los valores de referencia calculados de forma independiente:
cada asiento cuadra, el balance de comprobación global es exactamente cero, y una
reejecución idempotente no vuelve a asentar nada. Los controles de manipulación
confirman que las comprobaciones detectan errores reales. Es una capacidad real y
validada sobre datos del mundo real, lista para crecer: el mismo arnés escala al
conjunto completo de 541 909 filas, admite una cuenta de compensación para validar
el *netting*, o un segundo flujo real para una extensión multidivisa.
