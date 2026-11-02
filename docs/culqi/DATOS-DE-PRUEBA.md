# Datos de prueba de Culqi (demostración)

Solo funcionan con las llaves de **integración** (`pk_test_...` / `sk_test_...`). No mueven dinero real.

## Tarjetas

| Caso | Número | Vencimiento | CVV | Resultado |
|---|---|---|---|---|
| Pago aprobado | `4111 1111 1111 1111` | `09/30` | `123` | Pago **APROBADO**: matrícula activa, constancia y correo |
| Pago rechazado (fondos insuficientes) | `4000 0400 0000 0008` | `03/30` | `295` | Pago **RECHAZADO** (`insufficient_funds`): la matrícula queda pendiente y aparece "Volver a pagar" |

- Correo: el modal lo trae prellenado con el de la cuenta; si lo pide, usa cualquiera.
- Al elegir "Tarjeta de crédito o débito" no hay pasos extra: completar los datos y pagar.

## Yape

| Dato | Valor |
|---|---|
| Celular | `900000001` |
| Código de aprobación | `123456` (acepta cualquier código de 6 dígitos) |

Pasos: elegir **Yape** → Continuar → escribir el celular → escribir el código de 6 dígitos → **Yapear**. Resultado: pago **APROBADO**.

## Billeteras digitales (Plin y otras)

- **Están desactivadas en la demostración** (`BILLETERAS_HABILITADAS = false` en `curso-formato.util.ts`); el checkout solo ofrece **tarjeta** y **Yape**.
- Motivo: en el sandbox de Culqi la orden de billetera (PagoEfectivo) se genera con su QR, pero **no pasa a pagada** aunque se confirme desde el panel de Culqi; el pago se queda en `PENDIENTE`.
- Límite de Culqi para billeteras: de S/ 6 a S/ 500.
- No hay datos de prueba de billeteras que den un resultado final, por eso no se usan en la demostración.

## Qué se ve en cada caso

| Caso | Pantalla de resultado | Matrícula |
|---|---|---|
| Tarjeta aprobada / Yape | "¡Pago aprobado!" con constancia | `ACTIVA`, con acceso al curso |
| Tarjeta rechazada | "Culqi rechazó el pago…" y botón "Volver a pagar" | `PENDIENTE_PAGO`, sin acceso |

Después de un rechazo se puede reintentar con otra tarjeta o con Yape sobre el mismo curso.
