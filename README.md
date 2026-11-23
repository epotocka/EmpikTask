# EmpikTask: discount coupon service

A REST service for creating discount coupons and letting users redeem them.

- **Java 25**, **Spring Boot 4.2**, Maven
- **PostgreSQL** stores countries, coupons and completed usages. The schema is managed by **Flyway**.
- **Redis** stores temporary coupon reservations (15 minutes).
- **ip-api.com** works out the user's country from their IP address.

## Running

Requirements: JDK 25 and a running Docker daemon.

```bash
./mvnw spring-boot:run
```

`spring-boot-docker-compose` starts Postgres and Redis from `compose.yaml` and wires the connections automatically. Flyway applies the migrations on startup. The API is served at `http://localhost:8080`.

```bash
./mvnw test
```

The tests start their own Postgres and Redis with Testcontainers, so Docker must be running.

## Design assumptions

- **One active reservation per user.** A user can hold only one coupon reservation at a time, across all coupons. Reserving a different coupon while holding a reservation moves the reservation to the new coupon, and the old coupon's slot is freed immediately for other users. If the new coupon can't be reserved (e.g. it's full or for another country), the request fails and the existing reservation stays unchanged. Reserving the same coupon again just returns the existing reservation.
- **Scalability and concurrent use.** The service is designed to scale horizontally, running as multiple instances or replicas behind a load balancer. It's also designed for many admins and users working at the same time. The application keeps no state in memory between requests. Reservations live in Redis. Every coupon-capacity change and duplicate check is made safe across instances, through Postgres row locks and conditional inserts, or atomic Redis commands for switching a user's reservation (see [Concurrency and race conditions](#concurrency-and-race-conditions)).

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `coupons.reservation.ttl` | `15m` | How long a reservation is held |
| `coupons.geolocation.base-url` | `http://ip-api.com` | Geolocation API base URL |
| `spring.http.clients.connect-timeout` / `read-timeout` | `2s` / `3s` | Timeouts for the geolocation call |
| `server.forward-headers-strategy` | `framework` | The client IP is taken from `X-Forwarded-For` when present |

## Data model

| Table | Columns | Notes |
|---|---|---|
| `country` | `code` (PK, 2 letters, upper case), `name` | Countries a coupon can be assigned to |
| `coupon` | `id` (UUID PK), `coupon_code`, `country_code` (FK → country), `created_at`, `max_usages`, `used_count` | Unique index on `upper(coupon_code)` makes codes **case-insensitively unique**. A check constraint enforces `0 <= used_count <= max_usages`. |
| `used_coupons_by_user` | `id` (UUID PK), `coupon_id` (FK → coupon), `user_id` (UUID), `used_at` | Unique `(coupon_id, user_id)`: a user can use a coupon only once |

Migrations live in `src/main/resources/db/migration` (`V1`–`V3`). Hibernate only validates the schema (`ddl-auto: validate`).

**Indexes.** Every query the application runs is served by an index:

| Query | Index |
|---|---|
| Country by code, including `ON CONFLICT (code)` | `country` primary key |
| Coupon by code, case-insensitive (`upper(coupon_code) = upper(?)`), including `ON CONFLICT ((upper(coupon_code)))` | Unique `ux_coupon_code_case_insensitive ON coupon (upper(coupon_code))` |
| Coupon by ID, including the row lock (`SELECT … FOR NO KEY UPDATE`) | `coupon` primary key |
| Joining coupon to country | `ix_coupon_country_code ON coupon (country_code)` |
| Usage by coupon and user (`coupon_id = ? AND user_id = ?`) | Unique `ux_used_coupons_by_user_coupon_user (coupon_id, user_id)`. It also covers the `coupon_id` foreign key, since `coupon_id` is its first column. |

The case-insensitive lookup is generated with `upper()`, which matches the index expression, so Postgres can use the index. No other indexes are added, because the application doesn't query any other columns, e.g. `user_id` alone, and extra indexes would only slow down inserts.

## API

All endpoints are under `/api/v1`. Errors are returned as RFC 9457 `application/problem+json` with a user-friendly `detail` and a machine-readable `errorCode`:

```json
{
  "title": "Coupon usage limit reached",
  "status": 409,
  "detail": "Sorry, coupon 'SUMMER' has already been used the maximum number of times and is no longer available.",
  "errorCode": "COUPON_USAGE_LIMIT_REACHED"
}
```

Validation errors (`400`, `VALIDATION_FAILED`) also include a `fieldErrors` object, for example `{"code": "must not be blank"}`.

### Flow 1: Country management (`CountryController` → `CountryService`)

| Method | Path | Body | Success |
|---|---|---|---|
| `POST` | `/api/v1/countries` | `{"code": "PL", "name": "Poland"}` | `201` + `Location` header |
| `GET` | `/api/v1/countries` | | `200`, list sorted by code |
| `GET` | `/api/v1/countries/{code}` | | `200` |

Validation:
- `code`: required, exactly two letters (ISO 3166-1 alpha-2). It's stored in upper case, so `pl` becomes `PL`.
- `name`: required, at most 100 characters.

Errors:

| Status | `errorCode` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Invalid body |
| 409 | `COUNTRY_ALREADY_EXISTS` | The code already exists (case-insensitive) |
| 404 | `COUNTRY_NOT_FOUND` | `GET` for an unknown code |

```bash
curl -i -X POST localhost:8080/api/v1/countries \
  -H 'Content-Type: application/json' -d '{"code": "PL", "name": "Poland"}'
```

### Flow 2: Coupon management (`CouponController` → `CouponService`)

| Method | Path | Body | Success |
|---|---|---|---|
| `POST` | `/api/v1/coupons` | `{"code": "SUMMER", "countryCode": "PL", "maxUsages": 100}` | `201` + `Location` header |
| `GET` | `/api/v1/coupons/{code}` | | `200`, looked up case-insensitively |

Response:

```json
{"id": "…", "code": "SUMMER", "countryCode": "PL", "createdAt": "2026-11-22T16:00:00Z", "maxUsages": 100, "usedCount": 0}
```

Validation:
- `code`: required, at most 64 characters, only letters, digits, `-` and `_`. The original casing is kept, but uniqueness ignores case: `summer` conflicts with `SUMMER`.
- `countryCode`: required, two letters, and the country must already exist.
- `maxUsages`: required, at least 1. This is a global limit across all users.

Errors:

| Status | `errorCode` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Invalid body |
| 404 | `COUNTRY_NOT_FOUND` | Unknown country |
| 409 | `COUPON_CODE_ALREADY_EXISTS` | The code already exists (case-insensitive) |
| 404 | `COUPON_NOT_FOUND` | `GET` for an unknown code |

```bash
curl -i -X POST localhost:8080/api/v1/coupons \
  -H 'Content-Type: application/json' -d '{"code": "SUMMER", "countryCode": "PL", "maxUsages": 100}'
```

### Flow 3: Coupon usage (`CouponUsageController` → `CouponUsageService`)

Using a coupon takes two steps:

1. **Reserve.** This holds one of the coupon's remaining uses for the user for 15 minutes. The reservation is kept in Redis.
2. **Use.** This turns the reservation into a permanent usage: it removes the reservation from Redis, increments `used_count` and records the user in `used_coupons_by_user`.

A reservation that isn't used within 15 minutes expires and its slot becomes available to others.

| Method | Path | Body | Success |
|---|---|---|---|
| `POST` | `/api/v1/reservations` | `{"couponCode": "SUMMER", "userId": "<uuid>"}` | `201` (new) or `200` (repeated) `{"couponCode", "userId", "expiresAt"}` |
| `POST` | `/api/v1/coupon-usages` | `{"couponCode": "SUMMER", "userId": "<uuid>"}` | `201` (new) or `200` (repeated) `{"couponCode", "userId", "usedAt"}` |

Validation for both bodies:
- `couponCode`: required, same format as when creating a coupon, looked up case-insensitively.
- `userId`: required UUID.

**Both endpoints are idempotent.** Clients can safely retry them, e.g. after a timeout:

- **Repeating a reservation** while the user's reservation is active returns that reservation with `200` and the original `expiresAt`. It doesn't extend the expiry, and it works even if no free slots are left, because the user already holds one.
- **Repeating a coupon usage** after it succeeded returns the original result (same `usedAt`) with `200` instead of `201`. The coupon isn't used a second time.
- **Parallel duplicates** from the same user get the same result too: exactly one reservation or usage is created.

**One reservation per user.** Reserving a *different* coupon while holding a reservation returns `201` for the new coupon and releases the old one (see [Design assumptions](#design-assumptions)). A later attempt to use the old coupon returns `404 RESERVATION_NOT_FOUND`.

The user's country is determined from the request IP via ip-api.com.

**Client IP and `X-Forwarded-For`.** The address a request arrives from isn't always the user's real IP:

- **Behind a proxy.** In production, the app usually sits behind a load balancer, reverse proxy or CDN. The connection then comes from the proxy, so without extra information every user would appear to have the proxy's IP. The proxy passes the real address in `X-Forwarded-For`. With `server.forward-headers-strategy: framework`, Spring uses the header's first address as the client IP.
- **Local testing.** Locally, every request comes from a loopback address, which can't be placed in any country. Sending e.g. `X-Forwarded-For: 83.0.0.1` lets you test as a user from Poland.

Any client can send this header and claim to be from anywhere, which would bypass the country check. In production, the proxy in front of the app must overwrite the header instead of passing through the value the client sent.

**Reservation checks (fail fast, cheapest first).** Steps 1–4 run before any network call or lock:

| # | Failure | Status | `errorCode` |
|---|---|---|---|
| 1 | The coupon doesn't exist (case-insensitive) | 404 | `COUPON_NOT_FOUND` |
| 2 | The user has already used this coupon | 409 | `COUPON_ALREADY_USED` |
| 3 | The user already has an active reservation. Not an error: it's returned with `200` and the remaining checks are skipped. | 200 | |
| 4a | The coupon has reached its usage limit (`used_count >= max_usages`) | 409 | `COUPON_USAGE_LIMIT_REACHED` ("…has already been used the maximum number of times…") |
| 4b | All remaining uses are reserved (`used_count + active reservations >= max_usages`) | 409 | `COUPON_TEMPORARILY_UNAVAILABLE` ("all remaining uses are currently reserved, try again in a few minutes") |
| 5 | The user's country (from IP) differs from the coupon's country | 403 | `COUPON_NOT_AVAILABLE_IN_COUNTRY` |
| | The IP can't be located (e.g. a private address) | 422 | `COUNTRY_NOT_RESOLVABLE` |
| | The geolocation service is down or times out | 503 | `GEOLOCATION_UNAVAILABLE` |

Checks 2–4 are then repeated under a lock before the reservation is written (see below).

**Coupon usage checks:**

| Failure | Status | `errorCode` |
|---|---|---|
| The coupon doesn't exist | 404 | `COUPON_NOT_FOUND` |
| The user has already used the coupon. Not an error: the original usage is returned with `200`. | 200 | |
| The user has no active reservation, or it has expired | 404 | `RESERVATION_NOT_FOUND` ("…may have expired, please reserve again") |
| An invalid body, e.g. a missing `couponCode` or a malformed `userId` | 400 | `VALIDATION_FAILED` or a plain `400` |

**Example:**

```bash
USER=$(uuidgen)
curl -i -X POST localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' -H 'X-Forwarded-For: 83.0.0.1' \
  -d "{\"couponCode\": \"summer\", \"userId\": \"$USER\"}"

curl -i -X POST localhost:8080/api/v1/coupon-usages \
  -H 'Content-Type: application/json' \
  -d "{\"couponCode\": \"summer\", \"userId\": \"$USER\"}"

curl -s localhost:8080/api/v1/coupons/SUMMER
```

`83.0.0.1` is a Polish address and `85.214.132.117` is a German one. Without `X-Forwarded-For`, a local request comes from a loopback address (`127.0.0.1` or `::1`), which can't be located (`422`).

## Concurrency and race conditions

### Reservations in Redis: why a sorted set

**What the storage must answer.** The reservation logic asks Redis two questions:

1. Does user U have an active reservation for coupon C? This is used for idempotent retries and before the coupon is used.
2. How many active reservations does coupon C have? This is the capacity check, `used_count + active reservations < max_usages`. It runs on every reservation, so it must be both exact and fast.

Each reservation must also disappear by itself after 15 minutes. And because a user can hold only one reservation at a time, the logic must know which coupon a user currently holds, so it can release it when the user switches.

**What a sorted set is.** A Redis sorted set (`ZSET`) is a collection of unique string *members*, where each member has a numeric *score*. Redis keeps the members ordered by score, so it can find, count or delete all members within a score range in O(log N) time. Adding a member that already exists only updates its score.

**How it's used here.** There's one sorted set per coupon, `coupon:reservations:{couponId}`. The member is the user ID, and the score is the moment the reservation expires, in epoch milliseconds:

```
now = 1795364700000 (2026-11-22 16:25:00 UTC)

coupon:reservations:3f38ef58-…   (sorted set)
  member (userId)        score (expiresAt, epoch ms)
  9a0b13f7-…             1795364601550   ← expired at 16:23:21.550
  2341bfa0-…             1795365562892   ← expires at 16:39:22.892
  7c1e44d2-…             1795365610004   ← expires at 16:40:10.004
```

| Operation | Redis command | Cost |
|---|---|---|
| Does the user have an active reservation? | `ZSCORE key userId`. The reservation is active if the score is greater than `now`. | O(1) |
| Count active reservations | `ZCOUNT key (now +inf`, i.e. members whose expiry is in the future | O(log N) |
| Reserve | `ZREMRANGEBYSCORE key -inf now` (clean up expired members), `ZADD key <now + 15 min> userId`, `EXPIRE key 15m` | O(log N) |
| Remove after the coupon is used | `ZREM key userId` | O(log N) |

In Redis score ranges, a leading `(` marks the bound as **exclusive**. It isn't a bracket that needs closing. So `ZCOUNT key (1795364700000 +inf` counts members whose score is strictly greater than `now`, while `ZCOUNT key 1795364700000 +inf` would also count a score equal to `now`. `ReservationStore` sends the equivalent inclusive bound `now + 1`, because scores are whole milliseconds. In the example above, it returns 2: the first member has already expired, so it isn't counted, even though it's still physically in the set.

**Why it's always exact.** Expiry is evaluated at read time, by comparing scores with the current time. An expired reservation is never counted or treated as active, whether or not it has been physically removed yet. Removing expired members (`ZREMRANGEBYSCORE`) and the key-level TTL (`EXPIRE`) are only housekeeping. They keep memory bounded and delete the key of a coupon nobody reserves any more, but correctness never depends on them. Time comes from the application's clock, so application instances should be time-synchronized (NTP).

**What sets or resets a reservation's timer.** Only one place writes a score: the `ZADD` in `ReservationStore.upsertUserReservation`, with `score = now + 15 min`. It runs only when the user has no active reservation in that coupon, which is re-checked under the coupon's row lock. So an active reservation's timer is never reset as a side effect:

| Action | Effect on the user's score | Timer reset? |
|---|---|---|
| Retrying the same coupon while the reservation is active, including parallel retries | No `ZADD`: the existing reservation is returned with its original `expiresAt` | No |
| Other users reserving the same coupon | Only the key-level TTL (`EXPIRE`) is refreshed. It never changes a member's score. | No |
| Reserving again after the reservation expired | Expired members are removed, and the user is added as a new reservation | Yes, as a new reservation |
| Switching to another coupon | The new coupon gets a fresh score, and the user's entry in the old coupon is removed. The time left on the old reservation isn't carried over. | Yes, on the new coupon |
| Using the coupon | The entry is removed (`ZREM`) | — |

Because every switch starts a fresh 15 minutes, a user who alternates between two coupons (A → B → A …) can keep holding one slot for longer than 15 minutes. Each switch frees the previous slot, so the user never holds more than one at a time. Switching back isn't guaranteed either: if someone else took the freed slot and the coupon is now full, the switch fails with `409 COUPON_TEMPORARILY_UNAVAILABLE`, and the user keeps their current reservation.

#### Alternatives considered

**1. A key per reservation with a normal TTL** (`reservation:{couponId}:{userId}`, `EX 900`). Question 1 becomes a simple `EXISTS`, but question 2 can't be answered properly:
- Counting keys by prefix requires `SCAN` with a pattern. It walks the entire Redis keyspace, not just one coupon's reservations, and it doesn't return a consistent snapshot. That's unsuitable for a check that runs on every request.
- A separate per-coupon counter (`INCR` on reserve, `DECR` on use) drifts. When a key expires through TTL, nothing decrements the counter. Redis keyspace notifications for expired keys are fire-and-forget: they're lost if no subscriber is connected, and they can arrive late. Lost decrements would permanently use up coupon slots.

**2. A hash per coupon with per-field TTLs** (`HSET` + `HEXPIRE`, available since Redis 7.4), counted with `HLEN`. This looks like the closest native fit: one field per user, each with its own TTL. It was tested on Redis 8.10.3, with background expiry turned off to show the worst case:

| Step | Result |
|---|---|
| A hash with 3 users, 2 of them given a 100 ms TTL, then a 300 ms wait | |
| `HLEN key` | **3**: the two expired fields are still counted |
| `HKEYS key` | only the non-expired user: listing commands hide expired fields |
| `HLEN key` again | **3**: listing didn't remove them |
| `HEXISTS key <expired user>` | 0, and this physically removes that one field |
| `HLEN key` | 2 |

`HLEN` counts fields whose TTL has passed until Redis physically removes them. Removal happens either lazily, when that exact field is accessed, or through background sampling with a limited time budget. The background sampling has no guaranteed deadline and falls further behind under load. The `HLEN` documentation doesn't promise otherwise. As a result, `HLEN` overcounts. That never lets a coupon go over its limit, but it would wrongly reject valid reservations with `COUPON_TEMPORARILY_UNAVAILABLE`. The only exact count with a hash is listing all fields (`HKEYS`), which is O(N) on every reservation.

The sorted set is the only option that answers both questions exactly, in O(log N), without relying on when Redis removes expired data.

#### The per-user pointer

Next to the per-coupon sorted sets, each user with a reservation has one plain key, `user:reservation:{userId}`. Its value is the ID of the coupon the user holds, and it has the same 15-minute TTL. It's only a pointer used to find the coupon to release when the user switches. The sorted sets remain the source of truth for "is this reservation active" and for counting. A stale pointer, e.g. to a coupon the user has already used, is harmless: releasing a user who isn't in that set does nothing, and the key expires on its own.

### Avoiding race conditions

**Redis is serializable only per command, not for multi-step logic.** Its guarantees are narrower:
- Redis runs commands one at a time on a single thread, so each command is atomic.
- `MULTI`/`EXEC` runs a queued batch without other clients' commands interleaving.
- But commands inside `MULTI` are only queued, so you can't read a value and decide based on it within the transaction. There's no rollback either, and `WATCH` is optimistic (the transaction fails and must be retried).
- So a sequence like "read the count, compare it with the limit, add a reservation" is **not** atomic across separate commands. Two clients can both read `count = 4`, both see that `4 < 5`, and both add a reservation.

Postgres isn't serializable by default either: its default isolation level is `READ COMMITTED`.

**How the service avoids races.** Every operation that changes a coupon's capacity runs inside a Postgres transaction that first takes a **row lock on that coupon** (`SELECT … FOR UPDATE`, via `@Lock(PESSIMISTIC_WRITE)`):

- **Reserve:** lock the coupon → re-check with fresh data: "already used" (error), "already reserved" (return the existing reservation), and `used_count + active reservations < max_usages` → `ZADD` → switch the user's pointer (below) → commit.
- **Use:** lock the coupon → if the user already has a usage row, return it (a parallel duplicate won the race) → re-check that the reservation is still active → `used_count++` and insert into `used_coupons_by_user` → commit → then `ZREM` the reservation.

Why this works:
- Requests for the same coupon are processed one at a time, in the order they get the lock (first come, first served).
- Different coupons never block each other.
- The geolocation call happens before the lock is taken, so a slow external service never holds a lock.
- The reservation is removed from Redis only after the database commit. In the short window between them, the usage is counted both as used and as reserved. That can only make the service briefly *stricter*, never let it go over the limit.

**Switching a reservation.** The coupon row lock doesn't help here, because reserving coupon A and coupon B locks two different rows. So the switch uses an ordering of single atomic Redis commands instead. After the new coupon's capacity check passes:

1. `ZADD` the user into the new coupon's sorted set.
2. `SET user:reservation:{userId} <newCouponId> GET PX 900000`. This atomically replaces the pointer and returns the previous value in one command.
3. If the previous value was a different coupon, `ZREM` the user from that coupon's sorted set.

With parallel requests from the same user for different coupons, both requests add the user to their own coupon *before* swapping the pointer. Whichever request swaps second always receives the other coupon as the previous value. Because the other request had already added the user before its own swap, the `ZREM` reliably removes it. The user always ends up with exactly one reservation. During the brief window between steps 1 and 3, the user counts in both coupons. That can only make capacity checks stricter, never let a coupon exceed its limit.

`CouponUsageConcurrencyIntegrationTest` covers this: one user sends parallel reservations for 5 coupons and ends up holding exactly one. With steps 1 and 2 swapped, the same test fails consistently.

The database constraints (`used_count <= max_usages` and unique `(coupon_id, user_id)`) are a final safety net.

**Duplicate creates.** A separate existence check followed by an insert can race: two parallel requests can both pass the check. So countries and coupons are created with an atomic conditional insert instead:

```sql
INSERT INTO coupon (...) VALUES (...) ON CONFLICT ((upper(coupon_code))) DO NOTHING
```

If the insert affected no rows, the code already exists and the service returns `409 COUNTRY_ALREADY_EXISTS` or `409 COUPON_CODE_ALREADY_EXISTS`. The check and the insert are one statement, so no parallel request can slip between them. The parallel duplicate tests in `CountryControllerIntegrationTest` and `CouponControllerIntegrationTest` verify this.

This assumes two admins can add the same coupon at the same time. Returning an error to the second one is an arbitrary choice. Another completely acceptable solution is to simply return the coupon that was already added. The same applies to countries.

Database constraint violations are never used as validation. A `DataIntegrityViolationException` means the application-level validation has a gap. It's logged at ERROR level and returns a generic `409 DATA_CONFLICT`.

`CouponUsageConcurrencyIntegrationTest` checks this: 40 users race for a coupon with 5 uses, and exactly 5 succeed. With the row lock removed, the same test grants 10–13.

## Simplifications and assumptions

- `X-Forwarded-For` is trusted. In production, the service must sit behind a proxy that overwrites this header.
- The free ip-api.com endpoint is HTTP-only and limited to 45 requests per minute. The base URL is configurable so it can be swapped for a paid endpoint.
- Reservation expiry uses the application's clock. With several application instances, their clocks should be synchronized (NTP).
- There's no authentication. The user is identified only by the UUID sent by the client.

## Tests

All flows are covered by Spring Boot integration tests (`@SpringBootTest` + `MockMvcTester` + AssertJ) against real Postgres and Redis in Testcontainers. The geolocation client is replaced with a Mockito bean, and time is controlled with a `MutableClock` to test expiry.

| Test class | Covers |
|---|---|
| `CountryControllerIntegrationTest` | Create, duplicate, validation, list, get |
| `CouponControllerIntegrationTest` | Create, case-insensitive duplicates (API and DB index), unknown country, validation, get |
| `CouponUsageControllerIntegrationTest` | Reserve and use happy paths, every check in the tables above, retries of both, expiry, switching a reservation to another coupon |
| `CouponUsageConcurrencyIntegrationTest` | Races between users, interleaved reserve and use, parallel reservation and usage retries from one user, parallel reservations of different coupons by one user |
| `IpApiGeolocationClientTest` | The ip-api.com client against `MockRestServiceServer` |
