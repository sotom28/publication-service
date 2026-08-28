# Referencia de la API — publication-service

> Microservicio Spring Boot (Java 21) que administra las **publicaciones** (avisos de venta) del marketplace de PC parts.
>
> - **Puerto:** `8083`
> - **Base URL:** `/api/publications`
> - **Formato:** JSON (`Content-Type: application/json`)

---

## Autenticación (simulada por headers)

Aún no existe un Gateway/Authorizer real (Azure Entra ID / AWS Cognito). Mientras tanto, la autenticación/autorización se simula con dos headers que **el Gateway inyectará** cuando exista. En pruebas se mandan a mano:

| Header | Descripción | Ejemplo |
|---|---|---|
| `X-User-Id` | Identidad del usuario autenticado (UUID). El `sellerId` de la publicación **siempre** sale de aquí. | `11111111-1111-1111-1111-111111111111` |
| `X-User-Role` | Rol del usuario autenticado. | `BUYER_SELLER` o `WORKSHOP_ADMIN` |

**Reglas:**
- Los endpoints de **escritura** (`POST`, `PUT`, `PATCH` y `DELETE` de imágenes) exigen ambos headers → **`401 Unauthorized`** si faltan.
- Los endpoints de **lectura** (`GET`) son públicos (catálogo), no requieren headers.
- `create()` toma el `sellerId` exclusivamente del header `X-User-Id`. Cualquier `sellerId` que llegue en el body se **ignora**.
- Operaciones sobre una publicación ajena (no eres el `sellerId`) → **`403 Forbidden`**.
- `PATCH /{id}/status` exige rol **`WORKSHOP_ADMIN`** → si no, **`403`**.

---

## Modelo de datos de respuesta (`PublicationResponse`)

| Campo | Tipo | Descripción |
|---|---|---|
| `publicationId` | string (UUID) | ID de la publicación (PK) |
| `sellerId` | string (UUID) | Referencia remota al vendedor (inmutable, sale del `X-User-Id`) |
| `productId` | string (UUID) | Referencia remota al producto (inmutable) |
| `title` | string | Título (máx. 150) |
| `description` | string \| null | Descripción opcional |
| `price` | integer | Precio en CLP |
| `grade` | enum | `GRADE_A` \| `GRADE_B` \| `GRADE_C` |
| `usageTimeMonths` | integer \| null | Tiempo de uso en meses |
| `status` | enum | `ACTIVE` \| `RESERVED` \| `SOLD` \| `IN_INSPECTION` \| `WITHDRAWN` |
| `createdAt` | string (ISO datetime) | Fecha de creación |
| `images` | array | Lista de imágenes |

Cada elemento de `images`:

| Campo | Tipo | Descripción |
|---|---|---|
| `imageId` | string (UUID) | ID de la imagen |
| `imageUrl` | string | URL del objeto en S3 |
| `isPrimary` | boolean | `true` si es la imagen principal (solo una por publicación) |

### Ejemplo

```json
{
  "publicationId": "fecd2917-c8be-4994-81fe-bf959f66155d",
  "sellerId": "11111111-1111-1111-1111-111111111111",
  "productId": "22222222-2222-2222-2222-222222222222",
  "title": "RTX 3090 usada",
  "description": "GPU en buen estado",
  "price": 450000,
  "grade": "GRADE_B",
  "usageTimeMonths": 12,
  "status": "ACTIVE",
  "createdAt": "2026-08-27T19:20:03.02231",
  "images": [
    { "imageId": "63028d8e-506d-4293-98a3-8c4bdfb03213", "imageUrl": "https://s3.example/foto-a.jpg", "isPrimary": true },
    { "imageId": "4aca165c-24c2-4dea-890f-197a31543d59", "imageUrl": "https://s3.example/foto-b.jpg", "isPrimary": false }
  ]
}
```

---

## Endpoints

### `POST /api/publications` — Crear publicación

**Headers requeridos:** `X-User-Id`, `X-User-Role`. El `sellerId` sale de `X-User-Id` (no se recibe en el body).

**Body (`PublicationRequest`):**

```json
{
  "productId": "22222222-2222-2222-2222-222222222222",
  "title": "RTX 3090 usada",
  "description": "GPU en buen estado",
  "price": 450000,
  "grade": "GRADE_B",
  "usageTimeMonths": 12
}
```

**Respuesta `201 Created`:** objeto `PublicationResponse`. La publicación se crea con `status = "ACTIVE"` y `sellerId = X-User-Id`.

**Validaciones (400):**
- `productId`, `title`: obligatorios
- `title`: máx. 150 caracteres
- `price`: obligatorio, `>= 0`
- `grade`: obligatorio
- `usageTimeMonths`: `>= 0` si viene
- `description`: opcional

**Errores:** `401` si faltan headers; `400` si falla validación.

---

### `GET /api/publications` — Listar / buscar publicaciones (público, paginado)

> Nota: la ruta es **sin barra final**. `GET /api/publications/` (con barra) responde **`404`**.

**Query params (todos opcionales y **combinables** entre sí):**
- `sellerId=<uuid>` → publicaciones de ese vendedor
- `status=<ACTIVE|RESERVED|SOLD|IN_INSPECTION|WITHDRAWN>` → filtra por estado
- `productId=<uuid>` → publicaciones de ese producto
- `maxPrice=<int>` → publicaciones con `price <= maxPrice`
- `grade=<GRADE_A|GRADE_B|GRADE_C>` → filtra por estado de conservación
- `page=<int>` (default `1`, 1-based) → número de página
- `limit=<int>` (default `20`) → tamaño de página

**Respuesta `200 OK`:** un **`Page`** de Spring Data (no es un arreglo plano):

```json
{
  "content": [ { ...PublicationResponse... } ],
  "pageable": { "pageNumber": 0, "pageSize": 20, ... },
  "totalElements": 2,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "numberOfElements": 2,
  "first": true,
  "last": true,
  "empty": false,
  "sort": { "sorted": false, "unsorted": true, "empty": true }
}
```

---

### `GET /api/publications/{id}` — Obtener por ID (público)

**Respuesta `200 OK`:** objeto `PublicationResponse`.

**Respuesta `404 Not Found`:** si el id no existe.

---

### `PUT /api/publications/{id}` — Actualizar publicación (solo dueño)

**Headers requeridos:** `X-User-Id`, `X-User-Role`. Debes ser el `sellerId` de la publicación → si no, **`403`**.

**Body (`PublicationRequest`):** igual que en creación, más `status` **opcional**.

```json
{
  "productId": "22222222-2222-2222-2222-222222222222",
  "title": "RTX 3090 - actualizada",
  "price": 400000,
  "grade": "GRADE_A",
  "status": "ACTIVE"
}
```

**Respuesta `200 OK`:** objeto `PublicationResponse` actualizado.

> **Reglas importantes:**
> - `sellerId` y `productId` **no** se modifican (inmutables), aunque vengan distintos en el body.
> - El `status` del body está **restringido** a `ACTIVE` o `WITHDRAWN` (el dueño puede publicar/retirar su aviso). Cualquier otro valor (`RESERVED`, `SOLD`, `IN_INSPECTION`) → **`403`**; esas transiciones solo van por `PATCH /status`.

**Errores:** `401` sin headers; `403` si no eres dueño o status no permitido; `404` si no existe.

---

### `PATCH /api/publications/{id}/status` — Cambiar estado del ciclo de vida (solo WORKSHOP_ADMIN)

**Headers requeridos:** `X-User-Id`, `X-User-Role` con rol **`WORKSHOP_ADMIN`** → si no, **`403`**.

**Body (`UpdateStatusRequest`):**

```json
{ "status": "SOLD" }
```

**Valores:** `ACTIVE`, `RESERVED`, `SOLD`, `IN_INSPECTION`, `WITHDRAWN`.

**Respuesta `200 OK`:** objeto `PublicationResponse` con el nuevo `status`.

**Errores:** `401` sin headers; `403` si el rol no es `WORKSHOP_ADMIN`; `404` si no existe.

---

### `DELETE /api/publications/{id}` — Eliminar publicación

**Respuesta `204 No Content`:** sin cuerpo. Borra la publicación y sus imágenes (cascada).

**Respuesta `404 Not Found`:** si el id no existe.

> ⚠️ Borrado físico actual. Si ya existe un `order_item` en `db_payments` referenciando la publicación, queda una referencia rota (a resolver en fase posterior).

---

### `POST /api/publications/{id}/images` — Agregar imagen (solo dueño)

**Headers requeridos:** `X-User-Id`, `X-User-Role`. Debes ser el `sellerId` → **`403`** si no.

**Body (`PublicationImageRequest`):**

```json
{
  "imageUrl": "https://s3.example/foto-a.jpg",
  "isPrimary": true
}
```

**Respuesta `201 Created`:** objeto `PublicationResponse` completo.

> Si `isPrimary: true`, cualquier otra imagen primaria se desmarca.

**Validaciones (400):** `imageUrl` obligatorio. **Errores:** `401`/`403`/`404`.

---

### `PATCH /api/publications/{id}/images/{imageId}/primary` — Marcar imagen primaria (solo dueño)

**Headers requeridos.** Debes ser el `sellerId` → **`403`** si no.

**Respuesta `200 OK`:** la imagen `{imageId}` queda `isPrimary: true` y las demás `false`.

**Respuesta `404`:** si la publicación o la imagen no existe.

---

### `DELETE /api/publications/{id}/images/{imageId}` — Eliminar una imagen (solo dueño)

**Headers requeridos.** Debes ser el `sellerId` → **`403`** si no.

**Respuesta `200 OK`:** objeto `PublicationResponse` sin esa imagen.

**Respuesta `404`:** si la publicación o la imagen no existe.

---

## Manejo de errores

| Caso | Código | Respuesta |
|---|---|---|
| Faltan headers de auth (`X-User-Id` / `X-User-Role`) | `401` | `{ "status", "message", "timestamp" }` |
| No eres dueño de la publicación | `403` | `{ "status", "message", "timestamp" }` |
| Rol insuficiente (PATCH /status) | `403` | `{ "status", "message", "timestamp" }` |
| PUT con status no permitido | `403` | `{ "status", "message", "timestamp" }` |
| Publicación o imagen no encontrada | `404` | `{ "status", "message", "timestamp" }` |
| Fallo de validación `@Valid` | `400` | `{ "campo": "mensaje", ... }` |
| `GET /api/publications/` (con barra) | `404` | error estándar de Spring |

### Ejemplo de error de validación (400)

```json
{
  "productId": "must not be blank",
  "grade": "must not be null",
  "title": "must not be blank",
  "price": "must be greater than or equal to 0"
}
```

### Ejemplo de error de autorización (403)

```json
{
  "status": 403,
  "message": "No tienes permiso para operar sobre esta publicación (owner: 11111111-1111-1111-1111-111111111111)",
  "timestamp": "2026-08-27T19:55:20.257271"
}
```

---

## Flujo típico

1. `POST /api/publications` (con `X-User-Id`) → crea la publicación con `status: ACTIVE` y `sellerId` del header.
2. `POST /api/publications/{id}/images` → agrega imágenes (primera con `isPrimary: true`).
3. `GET /api/publications?sellerId=...&status=ACTIVE&page=1&limit=20` → búsquedas del catálogo.
4. `PATCH /api/publications/{id}/status` (con `X-User-Role: WORKSHOP_ADMIN`) → mover el ciclo de vida.
5. `PUT /api/publications/{id}` → editar datos comerciales; el dueño también puede `ACTIVE`↔`WITHDRAWN`.
6. `DELETE /api/publications/{id}` → baja definitiva.
