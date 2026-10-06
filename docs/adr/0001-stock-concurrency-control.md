# ADR 0001: Stock Concurrency Control — Optimistic Locking with Bounded Retry

## Status

Accepted

## Context

Order creation (and quantity changes) must decrement `Product.stock` atomically so that two
concurrent requests can never both read the same available stock and both succeed — e.g. 50
concurrent checkout requests against a product with `stock=1` must result in exactly one success
and 49 clean rejections, never an oversell.

Two standard approaches were considered:

1. **Pessimistic row locking** — acquire a `SELECT ... FOR UPDATE` on the `Product` row before
   reading/decrementing stock, serializing all concurrent writers for that row until the
   transaction commits.
2. **Optimistic locking** — add a `@Version` column to `Product` so that a concurrent write that
   raced past the initial read is detected (as an `ObjectOptimisticLockingFailureException`) at
   save time, and retried against a freshly reloaded row.

## Decision

We use **optimistic locking** (`@Version` on `Product`) combined with a bounded explicit retry
loop (default 3 attempts, configurable via `reservation.max-retries`) implemented directly in
`ReservationService`, rather than pessimistic `SELECT ... FOR UPDATE` locking or pulling in a new
retry library/annotation.

Each retry attempt reloads the `Product`, re-checks available stock, and re-applies the stock
delta in its own fresh transaction, so a lost-update race is detected and retried rather than
silently overwritten. If all attempts are exhausted (or stock is genuinely insufficient), the
request fails fast with a `409 Conflict` and an "item no longer available" message rather than a
generic `500`.

## Rationale

- **Optimistic locking scales better under typical checkout traffic.** Most checkout requests for
  different products (or even the same product at low contention) do not actually race each
  other; optimistic locking lets them all proceed without blocking on a row lock, and only pays
  the cost of a retry when a real conflict occurs.
- **Pessimistic locking is simpler but serializes checkout on hot products.** A `SELECT ... FOR
  UPDATE` guarantees correctness too, but forces every concurrent request against the same
  product to queue behind the row lock for the duration of the transaction, which becomes a
  throughput bottleneck for popular/low-stock items — exactly the scenario this system needs to
  handle well (e.g. flash sales, limited-stock drops).
- **The retry logic is explicit and bounded**, not open-ended, so a pathologically hot product
  cannot cause unbounded retries or starve a request; after the bounded number of attempts it
  fails cleanly with a `409` rather than retrying forever or falling through to a `500`.
- We deliberately did **not** add a `spring-retry` dependency/annotation for this, since a 3
  attempt loop is simple enough to implement directly and avoids adding a new dependency for a
  narrowly-scoped need.

## Consequences

- `Product.stock` updates must go through `ReservationService`, which owns the retry loop; direct
  `ProductRepository.save()` calls that mutate `stock` outside of this path bypass the retry
  protection.
- Callers (e.g. `OrderService`) must be prepared to receive `StockUnavailableException` (mapped to
  HTTP 409) from reservation operations and must not assume stock changes always succeed on the
  first attempt.
