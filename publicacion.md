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
| `status` | enum `ACTIVE / RESERVED / SOLD / IN_INSPECTION` | por defecto `ACTIVE` al crear |
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

| Método | Path | Body / Params | Qué hace |
|---|---|---|---|
| `POST` | `/` | `PublicationRequest` | Crea una publicación nueva con `status = ACTIVE`. Devuelve `201`. |
| `GET` | `/{id}` | — | Busca por `publicationId`. `404` si no existe. |
| `GET` | `/` | `?sellerId=` o `?status=` (opcionales, excluyentes) | Sin params → todas. Con `sellerId` → las de ese vendedor. Con `status` → filtra por estado. |
| `PUT` | `/{id}` | `PublicationRequest` | Actualiza `title`, `description`, `price`, `grade`, `usageTimeMonths`. **No** toca `sellerId` ni `productId` (son inmutables una vez creada). |
| `PATCH` | `/{id}/status` | `UpdateStatusRequest { status }` | Cambia solo el estado del ciclo de vida (ej. pasar a `RESERVED`, `SOLD`, etc.). |
| `DELETE` | `/{id}` | — | Borra la publicación (y sus imágenes, por cascada). `204`. |
| `POST` | `/{id}/images` | `PublicationImageRequest { imageUrl, isPrimary }` | Agrega una imagen a la publicación. Si `isPrimary=true`, desmarca cualquier otra imagen primaria existente. `201`. |
| `DELETE` | `/{id}/images/{imageId}` | — | Quita una imagen puntual de la publicación. |
| `PATCH` | `/{id}/images/{imageId}/primary` | — | Marca esa imagen como primaria y desmarca todas las demás de la misma publicación. |

### Validaciones (Bean Validation, `PublicationRequest`)
- `sellerId`, `productId`, `title`: `@NotBlank`
- `title`: máx. 150 caracteres
- `price`: `@NotNull`, `@Min(0)`
- `grade`: `@NotNull`
- `usageTimeMonths`: `@Min(0)` si viene
- `PublicationImageRequest.imageUrl`: `@NotBlank`
- `UpdateStatusRequest.status`: `@NotNull`

### Manejo de errores (`GlobalExceptionHandler`)
| Excepción | HTTP | Respuesta |
|---|---|---|
| `PublicationNotFoundException` | 404 | `ErrorResponse { status, message, timestamp }` |
| `NoSuchElementException` (imagen no encontrada) | 404 | idem |
| `MethodArgumentNotValidException` (falla de `@Valid`) | 400 | mapa `{ campo: mensaje }` |
| `IllegalArgumentException` | 400 | `ErrorResponse` |

## Flujo típico de uso

1. El vendedor crea la publicación → `POST /api/publications` (queda `ACTIVE`).
2. Se le agregan una o más imágenes → `POST /api/publications/{id}/images` (la primera puede marcarse `isPrimary=true`).
3. Búsquedas del catálogo → `GET /api/publications?status=ACTIVE` o `?sellerId=...`.
4. Cuando alguien reserva/compra → `PATCH /api/publications/{id}/status` para mover el estado (`RESERVED` → `SOLD`), o `IN_INSPECTION` si aplica revisión.
5. Edición de datos comerciales (precio, título, descripción, grade) → `PUT /api/publications/{id}`.
6. Baja definitiva → `DELETE /api/publications/{id}`.

## Correr localmente

```bash
./mvnw spring-boot:run          # perfil dev por defecto, conecta a MariaDB local (localhost:3306/db_listings)
# o con Docker:
docker compose up               # levanta MariaDB + el servicio, healthcheck antes de arrancar la app
```

En prod, todo se resuelve por variables de entorno (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT`, más las de AWS) — ver `application-prod.properties` y `.env.example`.
