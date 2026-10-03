# Backend

## Administrator credentials

The backend requires `ADMIN_USERNAME` and `ADMIN_PASSWORD` environment variables at startup. Use a unique password of at least 12 characters and configure the same credentials in Railway and your local environment. Do not commit credentials or put them in frontend environment variables.

The admin login validates credentials against the backend. Admin-only API requests use HTTP Basic authentication over HTTPS; the frontend keeps the authorization value only for the current browser tab. Public booking endpoints remain available without administrator credentials.

For local development, set the variables in the terminal before starting the backend:

```powershell
$env:ADMIN_USERNAME = "admin"
$env:ADMIN_PASSWORD = "replace-with-a-unique-password-of-at-least-12-characters"
.\mvnw.cmd spring-boot:run
```

In Railway, add `ADMIN_USERNAME` and `ADMIN_PASSWORD` under the backend service's Variables, then deploy the backend.

## Customer reservation cancellation

Run `sql/20261002_add_reservation_cancel_token.sql` once against the production PostgreSQL database before deploying changes that add token-based customer cancellation. The cancellation token is returned only when a reservation is created; the database stores only its SHA-256 hash. A cancellation deletes the associated pending/cancelled payment and reservation in one transaction. A payment marked as completed cannot be cancelled by the customer.

## Renumber existing customer, reservation, and payment IDs

Run `sql/20261002_renumber_cliente_reserva_pago_ids.sql` once in DBeaver against the intended PostgreSQL database to renumber existing customer, reservation, and payment IDs consecutively from 1 while retaining their current ID order. The transaction first creates timestamped backup tables for those records and customer-management tokens in the `renta_cancha` schema, updates their foreign keys, and aligns the identity sequences so future IDs continue after the current maximum. Keep the backup tables until the renumbered records and private reservation links have been verified. The script stops without applying changes if it detects relationships different from the schema it was written for.

## Insert synthetic booking test data

Run `sql/20261002_insert_demo_clients_reservations_payments.sql` in DBeaver only in a database where these fictitious profiles and bookings are intended. It inserts four synthetic people with ordinary names and unique, randomly generated eight-digit DNI values, two reservations per person, and one pending payment per reservation. It assigns the first eight unreserved court/time slots from tomorrow through the next 365 days, uses each schedule's configured price, and rolls back the entire transaction if it cannot generate unique DNI values or eight slots are unavailable. These generated profiles are not verified real customers. The bookings occupy real availability until they are cancelled or deleted.
