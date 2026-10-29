# Culqi — pagos en línea (HU-047)

Guía para probar la matrícula pagada con **Culqi real** en un entorno de desarrollo: desde crear las llaves y
exponer el backend con `cloudflared` hasta comprobar el resultado.

> Estado: probado contra el ambiente de integración de Culqi con tarjeta aprobada y rechazada (la tarjeta se
> desactivó después del checkout), con Yape aprobado y con la generación del QR de billeteras. Queda sin probar el
> pago real de un QR (billeteras) y su aviso por webhook.

## 1. Cómo funciona

Culqi es el **único** medio de pago: no existe un modo simulado. El backend siempre cobra con la API de Culqi
(`https://api.culqi.com/v2`) y **necesita las llaves para arrancar**. La llave decide el entorno: `pk_test_`/`sk_test_`
es integración (dinero ficticio) y `pk_live_`/`sk_live_` es producción.

Flujo:

1. El alumno elige un curso de pago. El backend crea una **orden** en Culqi (`POST /orders`).
2. El navegador abre **Culqi Checkout** con esa orden. Se ofrecen tarjeta y Yape en todos los cursos. Las billeteras
   digitales (Plin, BBVA, Scotiabank, Ligo, BanBif, Ya, con un solo QR por PagoEfectivo) están
   **desactivadas** con `BILLETERAS_HABILITADAS = false` en `curso-formato.util.ts`, porque en integración el simulador
   de Culqi no marca la orden como pagada en su API. Aceptan de S/ 6 a S/ 500.
3. **Tarjeta y Yape:** Culqi entrega un token y el backend lo cobra (`POST /charges`). El resultado se conoce al
   instante y **no usa webhook**.
4. **Billeteras (Plin):** el pago se confirma después. Culqi avisa por **webhook** y el backend vuelve a consultar
   la orden a la API antes de activar la matrícula (el webhook no va firmado, así que nunca se confía en él solo).

La llave privada nunca sale del servidor; al navegador solo llega la pública.

## 2. Qué necesitas

- Cuenta en Culqi con panel de integración (CulqiPanel). El comercio se valida en unas 24 horas; las llaves de
  prueba ya sirven antes.
- Backend en `http://localhost:8080` y frontend en `http://localhost:4200`.
- Solo para probar billeteras (Plin): una URL pública hacia tu backend (sección 5).

## 3. Obtener las llaves

1. Entra al CulqiPanel → **Desarrollo → API Keys**.
2. Copia la **llave pública** (`pk_test_...`) con el icono de copiar.
3. Pulsa **Mostrar llave** y copia la **llave privada** (`sk_test_...`).

Copia siempre con el botón y no a mano: las letras `O`/`0` y `l`/`I` se confunden.

## 4. Configurar y probar tarjeta y Yape

### 4.1 Configuración

En [`application.properties`](../../src/main/resources/application.properties):

```properties
esejur.culqi.api-url=https://api.culqi.com/v2
esejur.culqi.public-key=pk_test_...
esejur.culqi.private-key=sk_test_...
esejur.culqi.order-validity-hours=24
esejur.culqi.webhook-user=
esejur.culqi.webhook-password=
```

| Propiedad | Significado |
|---|---|
| `api-url` | URL de la API. Es la misma para integración y producción. |
| `public-key` / `private-key` | Llaves del panel. Son obligatorias: si falta una, el backend no arranca. |
| `order-validity-hours` | Horas de vigencia de la orden creada en Culqi. |
| `webhook-user` / `webhook-password` | Autenticación básica del webhook. Vacías = el webhook acepta cualquier llamada. |

> **Seguridad:** `application.properties` se sube a git. No hagas commit con una `sk_live_` ni con llaves reales.
> Una `sk_test_` es solo de pruebas, pero conviene dejar las llaves vacías antes de commitear.

### 4.2 Arrancar

Reinicia el backend. Si arranca sin error, las llaves están presentes.

### 4.3 Probar un pago

1. Inicia sesión como alumno y matricúlate en un curso de pago.
2. Debe abrirse el checkout de Culqi.
3. Paga con una tarjeta de prueba, por ejemplo la Visa `4111 1111 1111 1111`, con cualquier fecha futura y CVV `123`.
   Las demás tarjetas (rechazos, fondos insuficientes) y los datos de prueba de Yape están en la
   documentación de Culqi: <https://docs.culqi.com/es/documentacion/pruebas>.
4. Comprueba el resultado (sección 6).

## 5. Probar billeteras (Plin): URL pública con cloudflared

Culqi no puede llegar a `localhost`, así que para que el webhook funcione hay que exponer el backend con un túnel.
Esta guía usa **cloudflared** (Cloudflare Quick Tunnel): gratuito y sin cuenta.

> ngrok también sirve, pero Windows Defender suele bloquear `ngrok.exe` como "software potencialmente no deseado"
> y las cuentas nuevas exigen una versión reciente del agente (`ngrok update`).

> Guía solo de cloudflared, paso a paso: [cloudflared.md](cloudflared.md).

### 5.1 Instalar cloudflared (una sola vez)

En PowerShell:

```powershell
winget install Cloudflare.cloudflared
```

Cierra y vuelve a abrir la terminal para que reconozca el comando `cloudflared`.

### 5.2 Abrir el túnel

Con el backend corriendo en el 8080, en una **terminal aparte**:

```powershell
cloudflared tunnel --url http://localhost:8080
```

En el log aparece un recuadro con la URL pública, por ejemplo:

```
Your quick Tunnel has been created! Visit it at ...
https://algo-aleatorio.trycloudflare.com
```

Comprueba que llega al backend abriendo en el navegador:

```
https://algo-aleatorio.trycloudflare.com/api/health
```

### 5.3 Registrar el webhook en Culqi

CulqiPanel → **Desarrollo → Webhooks → Crear webhook**:

| Campo | Valor |
|---|---|
| Producto | `CulqiOnline` |
| Recurso | `order` |
| Acción | `status` |
| Resultado | `changed` |
| URL | `https://algo-aleatorio.trycloudflare.com/api/publico/culqi/webhook` |

Eso forma el evento `order.status.changed`, que es el que activa la matrícula cuando una billetera paga.

Opcional: un segundo webhook con recurso `charge`, acción `creation` y resultado `succeeded` como respaldo para
tarjeta y Yape. No es necesario, porque esos cobros ya se confirman en el momento. No registres `token` ni `refund`:
el backend los ignora.

**Autenticación del webhook (opcional en desarrollo):** si activas "Activar autenticación" en el panel, escribe el
mismo usuario y contraseña en `esejur.culqi.webhook-user` y `esejur.culqi.webhook-password`. Son valores que tú
inventas; no los entrega Culqi. Si los dejas vacíos en `application.properties`, no actives la autenticación en el
panel.

### 5.4 Qué tener en cuenta

- **Deja la terminal de cloudflared abierta** mientras pruebes. Si la cierras (o `Ctrl+C`), la URL deja de funcionar.
- **La URL cambia cada vez que reinicias el túnel.** Edita el webhook en el panel con la nueva.
- Reiniciar el backend **no** cae el túnel: la misma URL sigue sirviendo.
- Mientras el túnel está abierto, tu backend es accesible desde internet. Ciérralo al terminar.

## 6. Comprobar el resultado

- **Matrícula:** pasa a `ACTIVA` y el alumno entra al aula.
- **Correo:** llega el aviso de pago aprobado (o el de pago no aprobado, con el detalle informado por Culqi).
- **Reporte de pagos:** Administración → Reportes → **Pagos**. El pago aparece como `APROBADO`; los totales solo
  cuentan los pagos aprobados o registrados manualmente.
- **Log del backend:** los eventos de webhook que no se pueden verificar o no corresponden a ningún pago se
  registran como ignorados (`Evento ... de Culqi ignorado`).
- **Panel de Culqi:** Desarrollo → **API Logs** muestra cada llamada que el backend hizo a la API y su respuesta.

Si un pago llega tarde (por ejemplo, aprueba después de que el cupo se llenó o el curso se canceló), no se pierde:
queda en **Administración → Excepciones de pago** para atenderlo.

## 7. Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| El backend no arranca y dice `Culqi exige esejur.culqi.public-key y esejur.culqi.private-key ...` | Falta `public-key` o `private-key`. Pégalas con el botón de copiar. |
| "No pudimos iniciar el pago con Culqi" | Culqi rechazó la creación de la orden. Revisa las llaves (`sk_test_` con `pk_test_` de la misma cuenta) y el log del backend; el detalle está en **API Logs** del panel. |
| El checkout no abre | El navegador bloqueó el script de `https://js.culqi.com/checkout-js`. Revisa extensiones de bloqueo y la consola. |
| Pagué con Plin y la matrícula no se activa | El webhook no llega: túnel cerrado, URL cambiada en el panel, recurso/acción mal elegidos, o usuario/contraseña distintos entre panel y `application.properties`. |
| El webhook responde 401 | La autenticación del panel no coincide con `webhook-user` / `webhook-password`. |
| `winget` o la terminal no reconocen `cloudflared` | Cierra y abre la terminal tras instalar. |
| ngrok: "agent version too old" (`ERR_NGROK_121`) | Ejecuta `ngrok update`. |
| ngrok: "archivo contiene un virus o software potencialmente no deseado" | Defender lo bloquea. Usa cloudflared, o permite ese archivo en Seguridad de Windows → Historial de protección. |

## 8. Terminar de probar

Cierra el túnel con `Ctrl+C` y, si ya no lo vas a usar, elimina el webhook del panel. El backend sigue cobrando con
Culqi: no hay modo de pruebas local, así que para no hacer cobros hay que usar siempre llaves `pk_test_`/`sk_test_`.

## 9. Producción

- Usa las llaves `pk_live_` / `sk_live_` del panel de producción, nunca en un archivo que se suba a git.
- Registra el webhook con la URL real y HTTPS (`https://<tu-dominio>/api/publico/culqi/webhook`), sin túnel.
- **Define siempre** `webhook-user` y `webhook-password` (contraseña larga y aleatoria) y actívalos en el panel.
  Con ambos vacíos, cualquiera que conozca la URL puede enviar eventos falsos; el backend solo vuelve a consultar el
  pago real, pero es una puerta abierta innecesaria.
- Activa `security.cookie.secure=true`.
