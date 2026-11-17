-- Esquema base. Multi-tenant en una sola base con Row Level Security:
-- toda tabla de restaurante lleva tenant_id NOT NULL + ENABLE/FORCE RLS + política por app.tenant_id.
-- El rol de aplicación (NOBYPASSRLS) fija app.tenant_id por transacción; el rol admin (BYPASSRLS) ve todo.

CREATE TABLE plans (
    id            text PRIMARY KEY,
    name          text   NOT NULL,
    price_cents   bigint NOT NULL,
    description   text   NOT NULL DEFAULT '',
    features      jsonb  NOT NULL DEFAULT '[]'::jsonb,
    fee_bps       int    NOT NULL DEFAULT 150,
    highlighted   boolean NOT NULL DEFAULT false,
    max_locations int    NOT NULL DEFAULT 1,
    sort_order    int    NOT NULL DEFAULT 0
);

CREATE TABLE tenants (
    id                uuid PRIMARY KEY,
    slug              text NOT NULL UNIQUE,
    name              text NOT NULL,
    tagline           text NOT NULL DEFAULT '',
    description       text NOT NULL DEFAULT '',
    cuisine           text NOT NULL DEFAULT '',
    theme             jsonb NOT NULL DEFAULT '{}'::jsonb,
    currency          text NOT NULL DEFAULT 'MXN',
    address           text NOT NULL DEFAULT '',
    phone             text NOT NULL DEFAULT '',
    hours             jsonb NOT NULL DEFAULT '[]'::jsonb,
    order_types       jsonb NOT NULL DEFAULT '["DINE_IN","PICKUP","DELIVERY"]'::jsonb,
    eta_minutes       int  NOT NULL DEFAULT 25,
    rating            double precision NOT NULL DEFAULT 4.8,
    custom_domain     text,
    status            text NOT NULL DEFAULT 'TRIAL',
    plan_id           text NOT NULL REFERENCES plans(id),
    mp_preapproval_id text,
    created_at        bigint NOT NULL
);
CREATE INDEX tenants_status_idx ON tenants (status);
CREATE UNIQUE INDEX tenants_custom_domain_idx ON tenants (lower(custom_domain)) WHERE custom_domain IS NOT NULL;

-- Usuarios: tenant_id NULL = operador de la plataforma. El login busca por correo con el rol admin.
CREATE TABLE users (
    id            text PRIMARY KEY,
    tenant_id     uuid REFERENCES tenants(id) ON DELETE CASCADE,
    email         text NOT NULL UNIQUE,
    name          text NOT NULL,
    role          text NOT NULL,
    password_hash text NOT NULL,
    created_at    bigint NOT NULL DEFAULT 0
);
CREATE INDEX users_tenant_idx ON users (tenant_id, role);

CREATE TABLE menu_categories (
    tenant_id   uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    id          text NOT NULL,
    name        text NOT NULL,
    description text NOT NULL DEFAULT '',
    sort_order  int  NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, id)
);

CREATE TABLE menu_items (
    tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    id              text NOT NULL,
    category_id     text NOT NULL,
    name            text NOT NULL,
    description     text NOT NULL DEFAULT '',
    price_cents     bigint NOT NULL,
    image_url       text,
    tags            jsonb NOT NULL DEFAULT '[]'::jsonb,
    available       boolean NOT NULL DEFAULT true,
    modifier_groups jsonb NOT NULL DEFAULT '[]'::jsonb,
    prep_minutes    int NOT NULL DEFAULT 15,
    sort_order      int NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, category_id) REFERENCES menu_categories (tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX menu_items_category_idx ON menu_items (tenant_id, category_id, sort_order);

CREATE TABLE orders (
    id                 text PRIMARY KEY,
    tenant_id          uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    tenant_slug        text NOT NULL DEFAULT '',
    number             text NOT NULL,
    status             text NOT NULL,
    type               text NOT NULL,
    channel            text NOT NULL DEFAULT 'DIRECT',
    lines              jsonb NOT NULL DEFAULT '[]'::jsonb,
    subtotal_cents     bigint NOT NULL DEFAULT 0,
    tip_cents          bigint NOT NULL DEFAULT 0,
    delivery_fee_cents bigint NOT NULL DEFAULT 0,
    total_cents        bigint NOT NULL DEFAULT 0,
    customer_name      text NOT NULL DEFAULT '',
    customer_phone     text NOT NULL DEFAULT '',
    table_label        text NOT NULL DEFAULT '',
    address            text NOT NULL DEFAULT '',
    notes              text NOT NULL DEFAULT '',
    created_at         bigint NOT NULL,
    paid_at            bigint,
    preparing_at       bigint,
    ready_at           bigint,
    completed_at       bigint,
    eta_minutes        int NOT NULL DEFAULT 20,
    payment            jsonb
);
CREATE INDEX orders_tenant_created_idx ON orders (tenant_id, created_at DESC);
CREATE INDEX orders_tenant_status_idx ON orders (tenant_id, status);

CREATE TABLE customers (
    tenant_id         uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    id                text NOT NULL,
    name              text NOT NULL DEFAULT '',
    phone             text NOT NULL,
    orders_count      int NOT NULL DEFAULT 0,
    total_spent_cents bigint NOT NULL DEFAULT 0,
    last_order_at     bigint,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, phone)
);

-- Consecutivo de pedidos por restaurante ("A-042").
CREATE TABLE order_counters (
    tenant_id   uuid PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    last_number int NOT NULL DEFAULT 0
);

-- ───────────────────────── Row Level Security ─────────────────────────
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['menu_categories', 'menu_items', 'orders', 'customers', 'order_counters'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid) WITH CHECK (tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid)',
            t);
    END LOOP;
END
$$;

-- tenants: el rol de aplicación solo ve/edita SU fila. (Listados públicos y operador usan el rol admin.)
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenants FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_self ON tenants
    USING (id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (id = nullif(current_setting('app.tenant_id', true), '')::uuid);

-- users: el rol de aplicación solo ve al personal de su restaurante (nunca al operador).
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON users
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
