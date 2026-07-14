# API Design: <resource / capability>

> Owner: backend-lead · Contract-first: frontend work starts from this document, never from code.

## Resource

- Name (plural noun): `orders`
- Base path: `/api/v1/<resource>`
- Bounded context:

## Endpoints

| Method | Path | Purpose | Auth | Success | Errors |
|--------|------|---------|------|---------|--------|
| POST | `/api/v1/orders` | Place order | `ROLE_CUSTOMER` | 201 + Location | 400, 401, 409, 422 |

For **each** endpoint:

### `<METHOD> <path>`

- **Request** (record fields, validation rules per field):
- **Response** (fields, example JSON):
- **Status codes** and when each occurs:
- **Idempotency**: safe to retry? Idempotency-Key required?
- **Pagination/sorting** (collections only): defaults and limits
- **AuthZ rule**: role + ownership check

## Error Contract

RFC 9457 Problem Details. List the `type` slugs this API introduces:

| type | status | when |
|------|--------|------|
| `insufficient-stock` | 409 | |

## Non-functional

- Expected p95 latency:
- Rate limiting needed: yes/no
- Cacheable responses: which, with what headers

## Quality Gates

- [ ] Follows skill `rest-api` rules (nouns, codes, pagination, versioning)
- [ ] OpenAPI spec updated in the same PR
- [ ] Validation on every input field (skill `validation`)
- [ ] Security-engineer reviewed auth rules
- [ ] API tests cover every row of the endpoint table

See `docs/quality-gates.md`.
