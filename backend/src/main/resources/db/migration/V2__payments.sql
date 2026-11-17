-- Pagos e integraciones. Mismo patrón que V1: tenant_id + ENABLE/FORCE RLS + política por app.tenant_id.

-- Cuenta de Mercado Pago de cada restaurante (OAuth). Existe fila ⇒ conectado.
CREATE TABLE mp_connections (
    tenant_id     uuid PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    mp_user_id    text NOT NULL,
    access_token  text NOT NULL,
    refresh_token text,
    public_key    text,
    live_mode     boolean NOT NULL DEFAULT false,
    expires_at    timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);

-- Conectores por restaurante (LALAMOVE, UBER_EATS, RAPPI, DIDI_FOOD).
CREATE TABLE integration_connections (
    tenant_id  uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    provider   text NOT NULL,
    connected  boolean NOT NULL DEFAULT false,
    config     jsonb NOT NULL DEFAULT '{}'::jsonb,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, provider)
);

-- Bitácora idempotente de webhooks. tenant_id NULL = evento de plataforma (suscripciones).
CREATE TABLE webhook_events (
    id           bigserial PRIMARY KEY,
    provider     text NOT NULL,
    event_key    text NOT NULL,
    tenant_id    uuid REFERENCES tenants(id) ON DELETE CASCADE,
    topic        text NOT NULL DEFAULT '',
    data_id      text NOT NULL DEFAULT '',
    payload      jsonb NOT NULL DEFAULT '{}'::jsonb,
    received_at  timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    attempts     int NOT NULL DEFAULT 0,
    result       text,
    UNIQUE (provider, event_key)
);
CREATE INDEX webhook_events_tenant_idx ON webhook_events (tenant_id, received_at DESC);

-- Envíos solicitados a un proveedor de reparto (Lalamove o mock).
CREATE TABLE delivery_jobs (
    id           text PRIMARY KEY,
    tenant_id    uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    order_id     text NOT NULL,
    provider     text NOT NULL,
    external_id  text,
    quotation_id text,
    status       text NOT NULL DEFAULT 'ASSIGNING',
    price_cents  bigint NOT NULL DEFAULT 0,
    tracking_url text,
    payload      jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX delivery_jobs_order_idx ON delivery_jobs (tenant_id, order_id);

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['mp_connections', 'integration_connections', 'webhook_events', 'delivery_jobs'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid) WITH CHECK (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid)',
            t);
    END LOOP;
END
$$;
