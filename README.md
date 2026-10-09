# CanchaVóley

Aplicación web para consultar canchas y horarios, realizar reservas de vóley y administrar la información del negocio. El frontend obtiene los datos mediante una API REST y el backend los persiste en PostgreSQL.

## Integrantes

- David Sangama
- Patrick Freytas
- Esau Pecho
- Leo Pacho

## Aplicación publicada

- **Frontend:** https://cancha-voley.vercel.app/
- **Backend (API):** https://canchavoley-production.up.railway.app
- **Consulta pública de canchas (API):** https://canchavoley-production.up.railway.app/api/canchas
- **Base de datos:** PostgreSQL alojado en Neon

## Tecnologías

- **Frontend:** React 19, Vite y React Router
- **Backend:** Java 21, Spring Boot, Spring MVC, Spring Data JPA y Spring Security
- **Base de datos:** PostgreSQL
- **Despliegue:** Vercel (frontend), Railway (backend) y Neon (base de datos)

## Funcionalidades

- Consulta de canchas, horarios y precios desde el backend.
- Flujo de reserva en línea: selección de fecha, cancha y horario, ingreso de datos y envío de la solicitud.
- Consulta y gestión de reservas del cliente mediante un token privado.
- Panel de administración para gestionar reservas, canchas, clientes, horarios y pagos.
- Autenticación para las operaciones administrativas.
- Persistencia de los datos en PostgreSQL.

## Arquitectura

```text
Navegador
   │
   ▼
React / Vite (Vercel)
   │ solicitudes HTTP (JSON)
   ▼
API REST / Spring Boot (Railway)
   │ Spring Data JPA
   ▼
PostgreSQL (Neon)
```

Los datos comerciales y las reservas se solicitan a la API; los borradores del formulario y el token de gestión del cliente se conservan temporalmente en `sessionStorage`. Los clientes nuevos registran un correo electrónico para poder recuperar el acceso a sus reservas; el correo de clientes ya existentes se agrega desde el panel administrativo.

## Requisitos para ejecutar localmente

- Java 21
- Node.js `20.19+` o `22.12+` y npm (requisito de Vite)
- Acceso a una instancia PostgreSQL con el esquema requerido por la aplicación

## Ejecución local

### 1. Configurar y ejecutar el backend

Desde la carpeta `backend`, define las variables de entorno para conectarte a PostgreSQL:

| Variable | Descripción | Ejemplo local |
|---|---|---|
| `DB_URL` | URL JDBC de PostgreSQL | `jdbc:postgresql://localhost:5432/canchavoley` |
| `DB_USER` | Usuario de PostgreSQL | `postgres` |
| `DB_PASSWORD` | Contraseña de PostgreSQL | Definir localmente; no subirla al repositorio |
| `ADMIN_USERNAME` | Usuario del panel administrativo | `admin` |
| `ADMIN_PASSWORD` | Contraseña del panel; mínimo 12 caracteres | Definir localmente; no subirla al repositorio |
| `RESEND_API_KEY` | Clave secreta de la API de Resend | Definir como secreto |
| `RESEND_FROM_EMAIL` | Remitente autorizado en Resend | `CanchaVoley <reservas@tu-dominio-verificado.com>` |
| `PORT` | Puerto HTTP del backend (opcional) | `6767` |
| `FRONTEND_URL` | Origen permitido por CORS (opcional) | `http://localhost:5173` |

En PowerShell, por ejemplo:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/canchavoley"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "tu-clave-local"
$env:ADMIN_USERNAME = "admin"
$env:ADMIN_PASSWORD = "una-clave-local-de-12-caracteres"
$env:PORT = "6767"
$env:FRONTEND_URL = "http://localhost:5173"
.\mvnw.cmd spring-boot:run
```

En macOS/Linux, desde `backend`:

```bash
export DB_URL="jdbc:postgresql://localhost:5432/canchavoley"
export DB_USER="postgres"
export DB_PASSWORD="tu-clave-local"
export ADMIN_USERNAME="admin"
export ADMIN_PASSWORD="una-clave-local-de-12-caracteres"
export PORT="6767"
export FRONTEND_URL="http://localhost:5173"
./mvnw spring-boot:run
```

El backend queda disponible en `http://localhost:6767`. Configura el esquema `renta_cancha` y las tablas requeridas antes de iniciarlo. `spring.jpa.hibernate.ddl-auto` está en `none`, así que Spring no crea las tablas automáticamente. Este repositorio no incluye un script inicial completo de esquema ni datos de prueba: los archivos en [`backend/sql`](./backend/sql) son cambios incrementales y presuponen que ya existe la base inicial. Para una instalación local desde cero, restaura o crea primero esa estructura en PostgreSQL.

Antes de ejecutar una versión con recuperación por correo, aplica la migración [`20261009_add_cliente_email_recovery.sql`](./backend/sql/20261009_add_cliente_email_recovery.sql) en la base de datos de la aplicación. La migración agrega el correo del cliente y las tablas para códigos de recuperación; los clientes existentes quedan inicialmente sin correo.

Con `psql` instalado, desde la raíz del repositorio puedes aplicarla con:

```powershell
psql -h <host> -p <puerto> -U <usuario> -d <base> -v ON_ERROR_STOP=1 -f backend/sql/20261009_add_cliente_email_recovery.sql
```

La orden solicita la contraseña de PostgreSQL interactivamente. También puedes ejecutar el contenido del archivo desde la consola SQL de tu proveedor, en la base usada por el backend.

### 2. Configurar y ejecutar el frontend

Desde la carpeta `frontend`, crea un archivo `.env.local` con la dirección base de la API:

```dotenv
VITE_API_URL=http://localhost:6767/api
```

Instala las dependencias e inicia Vite:

```bash
npm ci
npm run dev
```

Abre `http://localhost:5173`. El frontend también puede apuntar al backend público usando su URL base como valor de `VITE_API_URL`.

## Pruebas y validaciones

Frontend, desde `frontend`:

```bash
npm run lint
npm run build
```

Backend, desde `backend`:

```powershell
.\mvnw.cmd test
```

En macOS/Linux, usa `./mvnw test`.

Para la prueba de aceptación desplegada, valida el flujo de crear, listar, editar, recargar para confirmar persistencia y eliminar. Conserva capturas o una demostración como evidencia y usa registros de prueba que puedan eliminarse sin afectar datos reales.

## Rutas principales de la API

| Recurso | Ruta base |
|---|---|
| Canchas | `/api/canchas` |
| Horarios | `/api/horarios` |
| Clientes | `/api/clientes` |
| Reservas | `/api/reservas` |
| Pagos | `/api/pagos` |
| Autenticación administrativa | `/api/admin/authenticate` |

La API usa JSON. Las operaciones de escritura utilizan los métodos HTTP correspondientes (`POST`, `PUT`, `PATCH` y `DELETE`); las rutas exactas y sus parámetros se definen en los controladores del backend.

### Activar recuperación por correo con Resend

1. Crea una cuenta en Resend y genera una API key. Guárdala como secreto `RESEND_API_KEY` en las variables del backend; nunca la pongas en el frontend, el repositorio ni el chat.
2. Para enviar códigos a cualquier cliente, agrega y verifica en Resend un dominio que controles y configura los registros DNS que Resend indique. Define `RESEND_FROM_EMAIL` usando una dirección de ese dominio verificado. El remitente de prueba de Resend sirve solo para probar con la dirección permitida por Resend, no para enviar a todos los clientes.
3. Ejecuta la migración de correo indicada en la sección de ejecución local, conectándote a la misma base de datos que utilizará el backend, si aún no se ha aplicado.
4. Para desarrollo local en PowerShell, define las variables en la terminal antes de iniciar el backend:

   ```powershell
   $env:RESEND_API_KEY = "re_xxxxxxxxx"
   $env:RESEND_FROM_EMAIL = "CanchaVoley <reservas@tu-dominio-verificado.com>"
   .\mvnw.cmd spring-boot:run
   ```

5. Reinicia/despliega el backend y prueba solicitar y verificar un código usando una cuenta de correo controlada. Los códigos vencen en 10 minutos; se permiten tres envíos cada 15 minutos y hasta cinco intentos de verificación.
6. Desde el panel de administración, agrega el correo correcto a cada cliente que ya existía antes de esta migración. El correo asociado a un cliente existente no se cambia durante una reserva; así, conocer su DNI no permite reemplazar la dirección usada para recuperar el acceso. Un correo solo puede estar asociado a un cliente.

La solicitud devuelve un mensaje genérico tanto si el correo está registrado como si no. El código se almacena hasheado, es de un solo uso y, al verificarlo, se revocan los enlaces/tokens de gestión anteriores y se entrega uno nuevo. Si falta `RESEND_API_KEY` o `RESEND_FROM_EMAIL`, la aplicación inicia normalmente, pero la recuperación devuelve `503` hasta que se configure el correo.

## Seguridad y configuración

- No publiques contraseñas, tokens ni archivos de entorno con valores secretos.
- `VITE_API_URL` es una variable de configuración del frontend y queda incorporada al bundle al construirlo; no debe contener secretos.
- En producción, define `DB_URL`, `DB_USER`, `DB_PASSWORD`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `PORT` y `FRONTEND_URL` en la configuración del servicio de backend. Configura `RESEND_API_KEY` como secreto y `RESEND_FROM_EMAIL` como variable de configuración para activar la recuperación por correo.
- Para CORS, revisa los orígenes permitidos en `backend/src/main/java/com/canchavoley/backend/config/CorsConfig.java`.
