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
