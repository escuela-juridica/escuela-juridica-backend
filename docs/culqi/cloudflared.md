# cloudflared — URL pública para el webhook de Culqi

Culqi no puede llegar a `localhost`. Para que el webhook funcione en desarrollo hay que exponer el backend con un
túnel. Aquí se usa **cloudflared** (Cloudflare Quick Tunnel): es gratuito y no necesita cuenta.

Solo hace falta para probar **billeteras (Plin)**. Tarjeta y Yape no usan webhook.

## 1. Instalar (una sola vez)

En PowerShell:

```powershell
winget install Cloudflare.cloudflared
```

Cierra la terminal y abre una nueva para que Windows reconozca el comando. Comprueba que quedó instalado:

```powershell
cloudflared --version
```

## 2. Ejecutar cada vez que pruebes

1. Arranca el backend (puerto 8080).
2. Abre **otra terminal** y ejecuta:

   ```powershell
   cloudflared tunnel --url http://localhost:8080
   ```

3. En el log aparece un recuadro como este. La línea con `https://` es tu URL pública:

   ```
   Your quick Tunnel has been created! Visit it at (it may take some time to be reachable):
   https://algo-aleatorio.trycloudflare.com
   ```

4. **No cierres esa terminal.** Mientras esté abierta, el túnel funciona.

## 3. Comprobar que llega al backend

Abre en el navegador (cambiando por tu URL):

```
https://algo-aleatorio.trycloudflare.com/api/health
```

Si responde, el túnel está bien. Si tarda unos segundos al principio, es normal ("it may take some time to be
reachable").

## 4. Usar la URL en Culqi

En CulqiPanel → **Desarrollo → Webhooks** pega la URL completa del webhook:

```
https://algo-aleatorio.trycloudflare.com/api/publico/culqi/webhook
```

El resto del formulario (recurso `order`, acción `status`, resultado `changed`, autenticación) está en
[README.md](README.md), sección 5.3.

## 5. Cada vez que reinicies el túnel

La URL **cambia** cada vez que cierras y vuelves a abrir `cloudflared`. Entonces:

1. Copia la nueva URL del log.
2. En CulqiPanel → Desarrollo → Webhooks, abre el menú de acciones (⋮) del webhook y edita la URL, o crea uno nuevo
   y borra el viejo.

Reiniciar el **backend** no cierra el túnel: la misma URL sigue sirviendo.

## 6. Cerrar

Pulsa `Ctrl+C` en la terminal de cloudflared. Mientras el túnel esté abierto, tu backend es accesible desde internet
con esa URL, así que ciérralo cuando termines de probar.

## Problemas frecuentes

| Síntoma | Solución |
|---|---|
| `cloudflared` no se reconoce como comando | Cierra y abre la terminal después de instalar. |
| La URL no responde justo después de crearla | Espera unos segundos y reintenta. |
| `/api/health` da error 502 o no carga | El backend no está corriendo en el 8080. Arráncalo. |
| Culqi no avisa de los pagos | La URL del webhook en el panel ya no es la del túnel actual, o la terminal de cloudflared se cerró. |
| El Historial del webhook en Culqi muestra 401 | El usuario o la contraseña del panel no coinciden con `esejur.culqi.webhook-user` / `esejur.culqi.webhook-password`. |
| Windows Defender bloquea el programa | cloudflared no suele ser marcado. Si pasa, permite solo ese archivo en Seguridad de Windows → Historial de protección. |
