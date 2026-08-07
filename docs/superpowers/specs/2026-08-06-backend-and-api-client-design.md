# Backend + API client layer — design

Date: 2026-08-06
Scope: full Spring Boot API for `specs.md`, plus the typed API client layer in `ui/`.
Out of scope this pass: UI screens and the CSS rewrite (next pass).

## Context

`api/` is an empty Spring Boot 4.1 skeleton — one `@SpringBootApplication` class and a
three-line `application.yaml`. Everything below is new.

`ui/` has four screens written entirely in Tailwind utility classes, but Tailwind is not
installed and `index.css` is still the Vite template. Every route except `/transparencia`
is commented out in `App.tsx`. This pass adds only the API client layer; the screens and
the plain-CSS rewrite come next.

## Decisions taken

| Decision | Choice | Reason |
|---|---|---|
| Money | `NUMERIC(12,2)` / `BigDecimal`, BRL | Matches the UI's `number` types with no cents-conversion layer. Never `double`. |
| Payment | Provider-agnostic registry, `fake` + `mercadopago` | Provider is swappable by config; the fake one makes the flow runnable with no credentials. |
| Product images | Port of the `sator` `api/internal/image` module | Proven pattern: dedup by hash, storage rollback, reference-guarded delete. |
| Image URLs | Public bucket, stored public URL | Catalog and dashboard are public by definition; presigning would add a round trip and defeat caching. |
| Object storage | One S3-compatible implementation | MinIO locally via compose, Cloudflare R2 in prod. Same code path. |
| Dev database | Postgres 17 via podman compose | Closest to production. |
| Test database | H2 in PostgreSQL mode | Runs with no container. Forces migrations to stay portable — see Risks. |

## Data model

Flyway `V1__initial_schema.sql`. Portable SQL only: no `jsonb`, no `gen_random_uuid()`,
UUIDs generated in Java. `TIMESTAMP WITH TIME ZONE` throughout; the API works in UTC and
formats for `America/Sao_Paulo` only at the dashboard-aggregation boundary.

**`image`** — `id`, `storage_key` unique, `mime_type`, `width`, `height`, `hash` (SHA-256
hex, unique), `created_at`.

**`admin`** — `id`, `email` unique (lowercased), `password_hash` (BCrypt), `active`,
`created_at`.

**`product`** — `id`, `name`, `price NUMERIC(12,2) CHECK (price > 0)`, `image_id` FK →
`image` null, `active`, `created_at`, `updated_at`.

**`orders`** — `id`, `status`, `total NUMERIC(12,2)`, `payment_provider`,
`provider_payment_id`, `payment_payload`, `payment_qr_base64`, `payment_checkout_url`,
`last_status_check_at`, `created_at`, `expires_at`, `paid_at`,
`paid_manually_by` FK → `admin` null.
The payment columns are named generically rather than `pix_*`, so a future non-PIX
provider stores its charge in the same place.
Status is `PENDING | PAID | EXPIRED | CANCELED`.

**`order_item`** — `order_id` FK, `product_id` FK, `product_name`, `unit_price`,
`quantity CHECK (quantity > 0)`.
Name and price are **snapshots**, not joins. A price change must never rewrite the
recorded value of a past sale — that is the core transparency requirement.

**`expense`** — `id`, `description`, `amount NUMERIC(12,2) CHECK (amount > 0)`,
`incurred_on DATE`, `created_by` FK → `admin`, `created_at`.

**`settings`** — single row enforced by `id SMALLINT PRIMARY KEY CHECK (id = 1)`:
`goal_target`, `crowdfunding_url`, `updated_at`. Seeded by the migration.

**`webhook_event`** — `id`, `provider`, `provider_event_id` **unique**, `payload TEXT`,
`received_at`, `processed_at`, `error`. The unique constraint is the idempotency
mechanism: a duplicate delivery hits it and is acknowledged without reprocessing.

## Payment provider abstraction

```java
public interface PaymentProvider {
    String name();
    PaymentCharge createCharge(ChargeRequest request);
    PaymentStatus fetchStatus(String providerPaymentId);
    Optional<WebhookNotification> parseAndVerify(Map<String, String> headers, String rawBody);
}
```

`PaymentCharge` is deliberately not PIX-specific: `providerPaymentId`, `payload`
(the copy-and-paste string), `qrImageBase64`, `checkoutUrl` (nullable), `expiresAt`.
A future card or boleto provider fits without changing the interface.

`PaymentProviderRegistry` receives `List<PaymentProvider>` from Spring and indexes by
`name()`. The active provider for new charges comes from `app.payments.active-provider`.

`orders.payment_provider` records the provider that created the charge. Status polling
and reconciliation resolve the provider **from the order**, not from current config — so
switching providers never strands in-flight orders.

### MercadoPagoPaymentProvider

Plain `RestClient` against `https://api.mercadopago.com/v1/payments`, not the Mercado
Pago Java SDK — fewer transitive dependencies and the SDK trails the REST API. Sends
`X-Idempotency-Key` derived from the order id. Reads the QR from
`point_of_interaction.transaction_data.{qr_code, qr_code_base64}`.

Webhook verification: parse `x-signature` into its `ts` and `v1` parts, rebuild the
manifest `id:{data.id};request-id:{x-request-id};ts:{ts};`, HMAC-SHA256 it with
`app.payments.mercadopago.webhook-secret`, and compare with `MessageDigest.isEqual`
(constant time). Missing, malformed, or mismatched signature → 401 and **nothing is
written**. Stale `ts` beyond a five-minute window → 401, to blunt replay.

### FakePaymentProvider

Active when registered under the name `fake`. Emits a correctly-shaped EMV payload and a
real scannable QR PNG (via `zxing`), then reports `PAID` after
`app.payments.fake.auto-confirm-after`. Lets the whole customer flow run end to end with
no credentials.

## Product images

Port of `sator/api/internal/image`, adapted to Spring and to public-read storage.

```java
public interface StorageProvider {
    void upload(String key, InputStream body, long size, String mimeType);
    void delete(String key);
    String publicUrl(String key);
    String presignDownloadUrl(String key, Duration expires);
}
```

`presignDownloadUrl` is declared but unused today. It exists so moving to a private
bucket later is a configuration change rather than an interface change.

`S3CompatibleStorageProvider` uses AWS SDK v2 with `endpointOverride`, static
credentials, and a path-style toggle — MinIO locally, Cloudflare R2 in production.

`ImageProcessor.process(InputStream, maxDim)`:

1. Read the bytes, compute SHA-256 hex.
2. **Peek dimensions before decoding**, via `ImageIO.getImageReaders` →
   `reader.getWidth(0)/getHeight(0)`. Reject above `maxSourceDim = 8192`. This is the
   decompression-bomb guard: a small, highly compressed file claiming an enormous pixel
   count would otherwise force a huge allocation during the full decode.
3. Decode, then fit within `maxDim × maxDim` (1024) preserving aspect ratio, via
   Thumbnailator.
4. Re-encode: **JPEG at quality 0.85 if the source has no alpha channel, PNG if it
   does.** Sator forces PNG because logos need transparency for PDF watermarks; that
   reason does not apply to food photos, and JPEG is far smaller. The alpha branch avoids
   flattening a transparent upload onto a black background.

`ImageService.uploadAndSave` mirrors sator exactly: look up by hash and return the
existing row if found; otherwise upload to `products/{uuid}.{ext}`, insert the row, and
on insert failure delete the uploaded object. If the failure is a unique violation on
`hash`, another request won the race — re-read and return that row instead of failing.

`ImageService.delete` refuses when any `product.image_id` still references the image,
deletes the row, then deletes the object. An object left orphaned by a storage failure
after the row is gone is logged at WARN, not raised — the DB is the source of truth.

Endpoints: `POST /api/admin/images` (multipart, admin only) and
`DELETE /api/admin/images/{id}`. There is no public image endpoint; product DTOs carry
`imageUrl` built from `publicUrl(storage_key)`, plus `imageWidth`/`imageHeight` so the UI
can reserve layout space.

Upload limits: 8 MB request cap, and an allowlist of `image/jpeg`, `image/png`,
`image/webp`, `image/gif` checked against the **decoded** format, not the client's
`Content-Type`.

## HTTP API

### Public — no authentication

- `GET /api/public/products` — active products only.
- `POST /api/public/orders` — body is `{ items: [{ productId, quantity }] }` and nothing
  more. The total is computed server-side from current DB prices; any price in the
  request is ignored. Rejects inactive or unknown products, empty carts, and quantities
  outside 1..99. Creates the order, calls the active provider, returns
  `{ orderId, payload, qrImageBase64, total, expiresAt }`.
- `GET /api/public/orders/{id}/status` — returns `{ status, paidAt }`. If the order is
  `PENDING` and was last checked more than 10 seconds ago, it queries the provider
  inline before answering. This is the first webhook fallback.
- `GET /api/public/dashboard?page=&size=` — see below.

### Webhooks

- `POST /api/webhooks/{provider}` — resolves the provider from the registry, calls
  `parseAndVerify`, and returns 401 on failure. Records the event, then applies it inside
  a transaction. Always returns 200 for a duplicate `provider_event_id` so the provider
  stops retrying.

### Admin — JWT

- `POST /api/auth/login` → `{ token, email, expiresAt }`.
- `GET /api/admin/me`
- `GET|POST /api/admin/products`, `PUT|DELETE /api/admin/products/{id}`.
  Delete is a soft delete (`active = false`) when the product appears in any order —
  history must stay intact.
- `GET /api/admin/orders?status=&page=`, `POST /api/admin/orders/{id}/mark-paid`,
  `POST /api/admin/orders/{id}/sync`, `POST /api/admin/orders/{id}/cancel`.
- `GET|POST /api/admin/expenses`, `DELETE /api/admin/expenses/{id}`.
- `GET|PUT /api/admin/settings`.
- `GET|POST /api/admin/admins`, `DELETE /api/admin/admins/{id}`.
- `POST /api/admin/images`, `DELETE /api/admin/images/{id}`.

Errors are RFC 7807 `application/problem+json` via `@RestControllerAdvice`, with a stable
`type` slug per failure class. Validation failures list the offending fields.

## Payment confirmation — three independent paths

The spec calls out webhook failure as the top risk, so nothing depends on the webhook
alone:

1. **Webhook** — the fast path, signature-verified and idempotent.
2. **Scheduled reconciler** — every 60 seconds, loads `PENDING` orders that are not yet
   expired, asks each order's originating provider for status, and applies the result.
   The same pass marks orders past `expires_at` as `EXPIRED`.
3. **Manual** — an admin can force a provider check (`/sync`) or mark an order paid
   outright (`/mark-paid`), which records `paid_manually_by` so the public record still
   shows how the payment was confirmed.

All three funnel through one `OrderService.markPaid` guarded by a row-level lock and an
idempotency check on `status`, so concurrent confirmation from two paths settles once.

## Dashboard

Computed from native aggregate queries against `orders WHERE status = 'PAID'` on every
request, with **no caching** — the spec requires it, because a stale transparency figure
is worse than a slow one.

```
kpis:  totalRaised (gross paid), totalExpenses, netBalance,
       totalOrders, averageTicket, topProduct,
       soldToday, soldThisWeek, soldThisMonth
goal:  { current: netBalance, target, crowdfundingUrl }
chartData:    last 7 days, one bucket per day, zero-filled
transactions: page of paid orders; productNames rendered "2x Café, 1x Bolo"
```

`goal.current` is **netBalance**, not gross, because the spec defines the caixa as
arrecadado − despesas and the progress bar should reflect actual money on hand. Gross
stays visible as its own KPI so nothing is hidden.

`topProduct` aggregates `order_item.product_name` — the snapshot — so a renamed product
does not retroactively rewrite history.

Day bucketing uses `America/Sao_Paulo`, otherwise an evening sale lands on the wrong day.

## Security

Stateless `SecurityFilterChain`. `JwtAuthenticationFilter` before
`UsernamePasswordAuthenticationFilter`. HS256, secret from `APP_JWT_SECRET`, refused at
startup if under 32 bytes. BCrypt for passwords. CSRF disabled (no cookies; the token is
a bearer header). CORS origins from `app.cors.allowed-origins`.

`/api/public/**`, `/api/auth/login` and `/api/webhooks/**` are `permitAll`;
`/api/admin/**` requires authentication; everything else denies.

The first admin is created by an `ApplicationRunner` from `APP_BOOTSTRAP_ADMIN_EMAIL` and
`APP_BOOTSTRAP_ADMIN_PASSWORD`, and only when the `admin` table is empty — BCrypt cannot
be produced inside a migration. It logs loudly and skips if the table is populated.

Admin management guards: an admin cannot delete themselves, and the last active admin
cannot be removed. Both would lock everyone out of a system whose whole point is that the
role rotates.

Order creation is rate-limited per IP (a bucket in memory, 10/minute) so an open,
unauthenticated endpoint cannot be used to hammer the payment provider.

## Frontend — this pass only

- `src/api/client.ts` — fetch wrapper over `VITE_API_URL`, bearer token from
  localStorage, parses `problem+json` into a typed `ApiError`, 401 clears the token.
- `src/api/public.ts`, `src/api/admin.ts`, `src/api/auth.ts` — one function per endpoint.
- `src/types.ts` realigned to the actual DTOs, including the new KPI fields.
- Vite dev proxy for `/api`, and `.env.example`.
- `PixPayment` currently calls `/api/orders/{id}/status`, which will not exist; the
  client layer uses the correct `/api/public/orders/{id}/status`.

## Testing

TDD, test first. H2 in PostgreSQL mode with Flyway running the real migrations, so schema
drift is caught.

- `ImageProcessor` — bomb guard rejects oversized declared dimensions; alpha source stays
  PNG; opaque source becomes JPEG; hash is stable across identical bytes.
- `ImageService` — dedup returns the existing row without re-uploading; a DB failure
  deletes the uploaded object; delete is refused while a product references the image.
- `OrderService` — total is computed from DB prices and ignores anything client-sent;
  inactive products are rejected; `markPaid` is idempotent across the three paths.
- `MercadoPagoPaymentProvider` — a known payload and secret produce a signature that
  verifies; a tampered body does not; a stale timestamp is refused.
- Webhook controller — unsigned request gets 401 and writes nothing; duplicate event id
  returns 200 without double-crediting.
- Dashboard — figures are computed from paid orders only, expenses subtract from
  `netBalance`, chart days are zero-filled, `topProduct` uses the snapshot name.
- Security — `/api/admin/**` is 401 without a token; expired and tampered tokens fail;
  the last-admin and self-delete guards hold.

## Infrastructure

`compose.yaml` for podman: Postgres 17 and MinIO, with a one-shot init container that
creates the bucket and sets a public-read policy. Production repoints the same S3
configuration at Cloudflare R2 — free tier, no egress charges.

`CLAUDE.md` at the repo root, listed in `.gitignore` as requested.

README currently documents `backend/` and `frontend/`; the real directories are `api/`
and `ui/`. Fixed as part of this work.

## Risks

**Tests run on H2, dev and production run on Postgres.** A Postgres-specific SQL bug can
pass the suite. Mitigated by keeping migrations to portable SQL and by running the same
Flyway migrations in tests, but it is a real gap. Testcontainers would close it and is the
natural upgrade once a working podman socket is confirmed.

**The fake payment provider must never be reachable in production.** The active provider
is read from configuration, and the application refuses to start if
`app.payments.active-provider` resolves to `fake` while the `prod` profile is active.

**Anonymous orders have no stock control**, by design. Delivery depends on the customer
showing the confirmation screen. Nothing in this design changes that, and adding stock
later means one column plus a check at order creation.
