# Pagos e integraciones

Código: `backend/src/main/kotlin/server/payments/**` y `server/integrations/**`.
Tablas: `backend/src/main/resources/db/migration/V2__payments.sql` (`mp_connections`, `integration_connections`,
`webhook_events`, `delivery_jobs`; todas con `tenant_id` + RLS).

## Modelo de cobro

Cada restaurante conecta **su propia cuenta de Mercado Pago** por OAuth. El checkout se crea con el token del
restaurante, así que el dinero cae directo en su cuenta. La plataforma cobra su comisión con `marketplace_fee`
(Mercado Pago la separa en cada pago) y cobra la mensualidad con una suscripción (`preapproval`) creada con el
token de la plataforma.

**Modo demostración:** si faltan `MP_CLIENT_ID` o `MP_CLIENT_SECRET` nada sale a la red. "Conectar" marca al
restaurante como conectado (`mpUserId = "demo-seller"`), `checkoutUrl` va en `null` y el pago se aprueba con
`POST /api/public/orders/{id}/simulate-payment`. OXXO y SPEI devuelven una referencia ficticia.

## Variables de entorno

| Variable | Para qué | Default |
|---|---|---|
| `PUBLIC_BASE_URL` | URL pública del servidor (back_urls, webhook, redirect) | `http://localhost:8080` |
| `MP_CLIENT_ID` / `MP_CLIENT_SECRET` | Credenciales de la app de Mercado Pago (OAuth). Sin ellas ⇒ demo | — |
| `MP_ACCESS_TOKEN` | Access token de la **plataforma** (suscripciones y `marketplace_fee`) | — |
| `MP_WEBHOOK_SECRET` | Clave secreta de webhooks de la app. Sin ella no se valida la firma | — |
| `MP_REDIRECT_URI` | Redirect URI registrada en la app | `PUBLIC_BASE_URL/api/oauth/mercadopago/callback` |
| `MP_OAUTH_TEST_TOKEN` | `true` ⇒ el canje OAuth pide credenciales de prueba del vendedor | — |
| `LALAMOVE_API_KEY` / `LALAMOVE_API_SECRET` | Llaves de Lalamove. Sin ellas ⇒ mock | — |
| `LALAMOVE_BASE_URL` | `https://rest.sandbox.lalamove.com` o `https://rest.lalamove.com` | sandbox |
| `REDIS_URL` | Cola `mp-webhooks` y tiempo real | `redis://localhost:6379` |

## Crear la app de Mercado Pago

1. Entra a <https://www.mercadopago.com.mx/developers/panel/app> con la cuenta de la plataforma y crea una aplicación:
   producto **Checkout Pro**, modelo de integración **Marketplace**.
2. En *Credenciales de producción* copia **Client ID** y **Client Secret** → `MP_CLIENT_ID`, `MP_CLIENT_SECRET`.
   El **Access Token** de esa misma cuenta → `MP_ACCESS_TOKEN`.
3. En *Detalles de la aplicación → Redirect URL* registra exactamente `MP_REDIRECT_URI`
   (`https://tu-dominio/api/oauth/mercadopago/callback`). Debe ser https.
4. En *Webhooks* configura la URL `https://tu-dominio/api/webhooks/mercadopago`, eventos **Pagos** y
   **Planes y suscripciones**, y copia la **clave secreta** → `MP_WEBHOOK_SECRET`.

### Usuarios de prueba

En *Cuentas de prueba* del panel crea dos usuarios de México: un **vendedor** (hace de restaurante) y un
**comprador**. Para probar el flujo completo: inicia sesión en Mercado Pago como el vendedor de prueba y desde
el panel del restaurante pulsa *Conectar Mercado Pago*; paga como el comprador de prueba con las
[tarjetas de prueba](https://www.mercadopago.com.mx/developers/es/docs/checkout-pro/additional-content/your-integrations/test/cards)
(p. ej. Mastercard `5474 9254 3267 0366`, CVV `123`, vencimiento `11/30`, titular `APRO` para aprobar).

## OAuth del restaurante

- `POST /api/tenant/payments/mercadopago/connect` (OWNER/MANAGER) devuelve `oauthUrl`:
  `https://auth.mercadopago.com.mx/authorization?client_id=…&response_type=code&platform_id=mp&state=…&redirect_uri=…`.
  `state` = id del tenant + vencimiento (30 min) firmado con HMAC-SHA256 (derivado de `JWT_SECRET`).
- `GET /api/oauth/mercadopago/callback?code=&state=` valida el `state`, canjea el código en
  `POST https://api.mercadopago.com/oauth/token` (`grant_type=authorization_code`), guarda `access_token`,
  `refresh_token`, `public_key`, `user_id`, `live_mode` y `expires_at` en `mp_connections` y redirige a
  `PUBLIC_BASE_URL/#/panel`.
- El token dura 180 días; se renueva solo (`grant_type=refresh_token`) cuando le quedan menos de 7 días.
- `POST …/disconnect` borra la fila. `GET /api/tenant/payments/mercadopago` devuelve el estado y el `feeBps` del plan.

## Checkout y `marketplace_fee`

`Payments.createCheckout` crea una preferencia de Checkout Pro (`POST /checkout/preferences`) con el token del
restaurante:

- `items`: líneas del pedido (precio con modificadores) + envío + propina, para que el total coincida.
- `external_reference` = id del pedido. `statement_descriptor` = nombre del restaurante (ASCII, 22 caracteres).
- `marketplace_fee` = `subtotalCents × feeBps / 10000`, en pesos. `feeBps` sale del plan del restaurante.
- `back_urls` = `PUBLIC_BASE_URL/#/r/<slug>?order=<id>`.
- `notification_url` = `PUBLIC_BASE_URL/api/webhooks/mercadopago?tenant=<id>` y `auto_return=approved`
  **solo si `PUBLIC_BASE_URL` es https** (Mercado Pago rechaza URLs locales).
- Método elegido por el comensal: `CARD` excluye ticket/bank_transfer/atm; `OXXO` deja solo ticket; `SPEI` deja
  solo bank_transfer; `MP_WALLET` usa `purpose=wallet_purchase`; `PAYMENT_LINK` no excluye nada.
- Devuelve `sandbox_init_point` si el token del restaurante es de prueba; si no, `init_point`.

Si el restaurante no ha conectado su cuenta, `checkoutUrl` va en `null`.

## Webhook y firma

`POST /api/webhooks/mercadopago`:

1. Valida `x-signature: ts=<ts>,v1=<hash>` cuando hay `MP_WEBHOOK_SECRET`:
   `v1 = HMAC-SHA256(secret, "id:<data.id>;request-id:<x-request-id>;ts:<ts>;")` (`data.id` del query, en minúsculas).
   Firma inválida ⇒ 401.
2. Guarda el evento en `webhook_events` (único por proveedor + clave ⇒ idempotente).
3. Encola en Redis (`mp-webhooks`) y responde 200 de inmediato.
4. Un worker saca el trabajo y:
   - `payment`: consulta `GET /v1/payments/{id}` con el token del restaurante, verifica que el pedido sea de ese
     restaurante y que el monto cubra el total. `approved` ⇒ `OrderService.markPaid` (el pedido aparece en cocina
     en tiempo real); `refunded` / `charged_back` ⇒ `OrderService.markRefunded`.
   - `subscription_preapproval`: consulta `GET /preapproval/{id}` con el token de la plataforma.
     `authorized` ⇒ tenant `ACTIVE`; `paused` / `cancelled` ⇒ `PAST_DUE` (nunca pisa `SUSPENDED`).
   - Si falla, reintenta hasta 5 veces con espera creciente.

Reembolso: `POST /api/tenant/orders/{id}/refund` llama `Payments.refund` ⇒ `POST /v1/payments/{id}/refunds`
con el token del restaurante (si el pedido no trae el id del pago, se busca por `external_reference`).

## Suscripción de la plataforma (preapproval)

En el registro, `Payments.createSubscriptionCheckout` hace `POST /preapproval` con `MP_ACCESS_TOKEN`:
`reason = "Suscripción <plan>"`, `auto_recurring` mensual en MXN por el precio del plan, `payer_email`,
`external_reference = <tenantId>`, `back_url = PUBLIC_BASE_URL/#/panel`. Guarda el id en
`tenants.mp_preapproval_id` y devuelve el `init_point`. Sin `MP_ACCESS_TOKEN` devuelve `null`.

## Probar en sandbox

```bash
export PUBLIC_BASE_URL=https://<tu-tunel>            # ngrok, cloudflared… (https obligatorio)
export MP_CLIENT_ID=… MP_CLIENT_SECRET=… MP_ACCESS_TOKEN=… MP_WEBHOOK_SECRET=…
export MP_OAUTH_TEST_TOKEN=true                      # opcional: tokens TEST- del vendedor
./gradlew :backend:run
```

1. Entra como dueño, *Pagos → Conectar Mercado Pago*, autoriza con el vendedor de prueba.
2. Haz un pedido como comensal: te manda al checkout de sandbox. Paga con el comprador de prueba.
3. Mercado Pago llama al webhook ⇒ el pedido pasa a `PAID` y aparece en cocina.
4. Reembolsa desde el panel y revisa el pago en la cuenta del vendedor de prueba.
5. Revisa la bitácora: `SELECT event_key, processed_at, result FROM webhook_events ORDER BY id DESC;`

Simular un webhook a mano (sin `MP_WEBHOOK_SECRET`):

```bash
curl -X POST "$PUBLIC_BASE_URL/api/webhooks/mercadopago?tenant=<tenantId>&data.id=<paymentId>&type=payment" \
  -H 'content-type: application/json' -d '{"type":"payment","action":"payment.updated","data":{"id":"<paymentId>"}}'
```

## Integraciones preparadas

`GET /api/tenant/integrations` lista el estado; `POST /api/tenant/integrations/{provider}/toggle` conecta o
desconecta (en demo también Mercado Pago; con credenciales reales Mercado Pago solo se conecta por OAuth).
`POST /api/tenant/integrations/{UBER_EATS|RAPPI|DIDI_FOOD}/simulate-order` arma un pedido con 1 a 3 platillos
del menú y lo manda a cocina con su canal, por el mismo camino que un webhook real.

| Integración | Hoy | Qué falta para producción |
|---|---|---|
| Mercado Pago | Real (sandbox y producción); demo sin credenciales | Credenciales de la app y dominio https. Recomendado: cifrar los tokens de `mp_connections` en reposo |
| Lalamove | `LalamoveClient` completo (v3: cotizar, crear, consultar, cancelar; firma HMAC). Mock sin llaves | `LALAMOVE_API_KEY`/`SECRET` (sandbox desde <https://partnerportal.lalamove.com>), coordenadas del restaurante y del cliente, y conectar el envío al flujo de pedidos a domicilio (guardar en `delivery_jobs`) y su webhook de estado |
| Uber Eats | Interfaz + mock. `UberEatsConnector` con firma de webhook y lectura del pedido; llamadas salientes marcadas como pendientes | App aprobada por Uber (client id/secret, `store_id`), ruta de webhook pública y completar las llamadas HTTP documentadas en el KDoc |
| Rappi | Igual que Uber Eats (`RappiConnector`) | Alta en Rappi Aliados (client id/secret, `store_id`) y completar las llamadas HTTP |
| DiDi Food | Igual (`DidiFoodConnector`). Las rutas son la forma esperada, **sin confirmar** | Acceso al portal de aliados de DiDi (app id/secret, `app_shop_id`), confirmar rutas y firma, completar las llamadas HTTP |
