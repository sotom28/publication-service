# publication-service

Microservicio Spring Boot (Java 21) que administra las **publicaciones** (avisos de venta) del marketplace de PC parts. Vive en su propia base de datos (`db_listings`, MariaDB) y no comparte tablas con los otros microservicios: `sellerId` y `productId` son referencias remotas (a `db_users` y `db_products`), no foreign keys reales, así que se guardan tal cual llegan y no se validan contra esas otras bases.

- **Puerto:** `8083` (`server.port`, o `SERVER_PORT` en prod)
- **Base URL:** `/api/publications`
- **Base de datos:** MariaDB, esquema `db_listings`, tablas `publications` y `publication_images`
- **Perfiles:** `dev` (`application-dev.properties`, conexión local hardcodeada a `localhost:3306`) y `prod` (`application-prod.properties`, todo por variables de entorno + credenciales AWS)

## Modelo de datos

**Publication**
| Campo | Tipo | Notas |
|---|---|---|
| `publicationId` | `CHAR(36)` (UUID) | PK, autogenerado, inmutable |
| `sellerId` | `CHAR(36)` | referencia remota a `db_users.users.user_id` |
| `productId` | `CHAR(36)` | referencia remota a `db_products.master_products.product_id` |
| `title` | `VARCHAR(150)` | obligatorio |
| `description` | `TEXT` | opcional |
| `price` | `Integer` | en CLP, obligatorio, `>= 0` |
| `grade` | enum `GRADE_A / GRADE_B / GRADE_C` | estado de conservación, obligatorio |
| `usageTimeMonths` | `Integer` | opcional, `>= 0` |
| `status` | enum `ACTIVE / RESERVED / SOLD / IN_INSPECTION / WITHDRAWN` | por defecto `ACTIVE` al crear |
| `createdAt` | `LocalDateTime` | autogenerado |
| `images` | lista de `PublicationImage` | `OneToMany`, cascada total |

**PublicationImage**
| Campo | Tipo | Notas |
|---|---|---|
| `imageId` | `CHAR(36)` (UUID) | PK |
| `imageUrl` | `TEXT` | URL del objeto en S3 (se guarda la URL, el servicio no sube el archivo) |
| `isPrimary` | `Boolean` | solo una imagen por publicación puede ser `true` |

> Nota: `application-prod.properties` ya trae variables `aws.access.key.id`, `aws.region`, `AWS_S3_BUCKET`, etc., pero en el código actual no hay ningún cliente S3 ni endpoint de upload — el servicio solo **recibe y guarda la URL** de la imagen (`imageUrl` en el request). La subida real del archivo a S3 se resuelve fuera de este servicio (front u otro servicio) antes de llamar a `POST /images`.

## Endpoints (`PublicationController`)

Todos bajo `/api/publications`.

> **Autenticación simulada por headers** (aún no hay Gateway/Authorizer real): los endpoints de **escritura** exigen `X-User-Id` y `X-User-Role` (`401` si faltan). El `sellerId` de una publicación sale **siempre** del header `X-User-Id` en creación, no del body. Las operaciones de escritura validan **ownership** (`403` si no eres el `sellerId`) y `PATCH /status` exige rol `WORKSHOP_ADMIN` (`403`).

| Método | Path | Body / Params | Qué hace |
|---|---|---|---|
| `POST` | `/` | `PublicationRequest` | Crea una publicación nueva con `status = ACTIVE`. `sellerId` sale del header `X-User-Id`. Devuelve `201`. |
| `GET` | `/{id}` | — | Busca por `publicationId`. `404` si no existe. Público. |
| `GET` | `/` | `?sellerId=`, `?status=`, `?productId=`, `?maxPrice=`, `?grade=`, `?page=`, `?limit=` (combinables) | Listado **paginado** (Page de Spring, `page`/`limit` 1-based). Sin params → todas. Público. |
| `PUT` | `/{id}` | `PublicationRequest` (incluye `status` opcional `ACTIVE/WITHDRAWN`) | Solo dueño. Actualiza `title`, `description`, `price`, `grade`, `usageTimeMonths` y opcionalmente `status`. **No** toca `sellerId` ni `productId`. |
| `PATCH` | `/{id}/status` | `UpdateStatusRequest { status }` | Solo `WORKSHOP_ADMIN`. Cambia el estado del ciclo de vida (`RESERVED`, `SOLD`, etc.). |
| `DELETE` | `/{id}` | — | Borra la publicación (y sus imágenes, por cascada). `204`. |
| `POST` | `/{id}/images` | `PublicationImageRequest { imageUrl, isPrimary }` | Solo dueño. Agrega una imagen. Si `isPrimary=true`, desmarca cualquier otra primaria. `201`. |
| `DELETE` | `/{id}/images/{imageId}` | — | Solo dueño. Quita una imagen puntual. |
| `PATCH` | `/{id}/images/{imageId}/primary` | — | Solo dueño. Marca esa imagen como primaria y desmarca las demás. |

### Validaciones (Bean Validation, `PublicationRequest`)
- `productId`, `title`: `@NotBlank` (el `sellerId` ya no va en el request: sale del header)
- `title`: máx. 150 caracteres
- `price`: `@NotNull`, `@Min(0)`
- `grade`: `@NotNull`
- `usageTimeMonths`: `@Min(0)` si viene
- `status` (solo en `PUT`): restringido a `ACTIVE` / `WITHDRAWN`; cualquier otro valor → `403`
- `PublicationImageRequest.imageUrl`: `@NotBlank`
- `UpdateStatusRequest.status`: `@NotNull`

### Manejo de errores (`GlobalExceptionHandler`)
| Excepción | HTTP | Respuesta |
|---|---|---|
| `PublicationNotFoundException` | 404 | `ErrorResponse { status, message, timestamp }` |
| `NoSuchElementException` (imagen no encontrada) | 404 | idem |
| `UnauthorizedException` (faltan `X-User-Id`/`X-User-Role`) | 401 | idem |
| `ForbiddenException` (ownership/rol) | 403 | idem |
| `MethodArgumentNotValidException` (falla de `@Valid`) | 400 | mapa `{ campo: mensaje }` |
| `IllegalArgumentException` | 400 | `ErrorResponse` |

## Flujo típico de uso

1. El vendedor crea la publicación con su `X-User-Id` → `POST /api/publications` (queda `ACTIVE`; `sellerId` sale del header).
2. Se le agregan una o más imágenes → `POST /api/publications/{id}/images` (la primera puede marcarse `isPrimary=true`).
3. Búsquedas del catálogo → `GET /api/publications?status=ACTIVE` o `?sellerId=...` (público y paginado).
4. Cuando alguien reserva/compra → `PATCH /api/publications/{id}/status` (solo `WORKSHOP_ADMIN`) para mover el estado (`RESERVED` → `SOLD`), o `IN_INSPECTION` si aplica revisión.
5. Edición de datos comerciales o publicar/retirar (`ACTIVE`↔`WITHDRAWN`) → `PUT /api/publications/{id}` (solo dueño).
6. Baja definitiva → `DELETE /api/publications/{id}`.


