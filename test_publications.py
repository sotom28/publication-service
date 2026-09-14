"""
Script de prueba para publication-service (puerto 8083) y su vinculo con
product-service (puerto 8082).

Requisitos:
    - product-service corriendo en :8082 con product-data.sql ya cargado
    - publication-service corriendo en :8083 (con la tabla publications
      creada, ver database.sql)
    - Python 3 + libreria "requests"

Instalar dependencia (Windows, cmd o PowerShell):
    pip install requests

Ejecutar:
    python test_publications.py

El script:
  1. Verifica que product-service responda y que exista el producto de
     prueba (product_id fijo de product-data.sql).
  2. Crea una publicacion en publication-service usando ese product_id.
  3. Prueba el CRUD completo de publicaciones (get, listar, update, status).
  4. Prueba el manejo de imagenes (agregar, marcar primaria, eliminar).
  5. Vuelve a resolver el product_id contra product-service para confirmar
     que la referencia remota efectivamente vincula ambos microservicios.
  6. Limpia lo que creo (borra la publicacion de prueba).

No usa autenticacion: ninguno de los dos servicios expone seguridad por
ahora, solo REST plano.
"""

import sys
import requests

PRODUCT_SERVICE_URL = "http://localhost:8082/api/v1/products"
PUBLICATION_SERVICE_URL = "http://localhost:8083/api/v1/publications"

# UUID fijo insertado por product-data.sql (AMD Ryzen 7 7800X3D)
TEST_PRODUCT_ID = "10000000-0000-0000-0000-000000000001"
# UUID de prueba, no necesita existir en db_users (no hay FK real)
TEST_SELLER_ID = "20000000-0000-0000-0000-000000000099"

TIMEOUT = 5

passed = 0
failed = 0


def check(label, condition, detail=""):
    global passed, failed
    if condition:
        passed += 1
        print(f"[OK]   {label}")
    else:
        failed += 1
        print(f"[FAIL] {label} {('- ' + detail) if detail else ''}")


def step(title):
    print(f"\n--- {title} ---")


def main():
    publication_id = None
    image_id = None

    # 1. product-service arriba y con el producto de prueba
    step("1. Verificar product-service")
    try:
        resp = requests.get(f"{PRODUCT_SERVICE_URL}/{TEST_PRODUCT_ID}", timeout=TIMEOUT)
        check("product-service responde", resp.status_code == 200, f"status={resp.status_code}")
        if resp.status_code == 200:
            product = resp.json()
            print(f"       producto: {product.get('brand')} {product.get('model')}")
    except requests.exceptions.ConnectionError:
        check("product-service responde", False, "no se pudo conectar a :8082 (¿esta corriendo?)")
        product = None

    # 2. Crear publicacion vinculada a ese product_id
    step("2. Crear publicacion (POST /crear)")
    body = {
        "sellerId": TEST_SELLER_ID,
        "productId": TEST_PRODUCT_ID,
        "title": "Publicacion de prueba - AMD Ryzen 7 7800X3D",
        "description": "Creada por test_publications.py",
        "price": 400000,
        "grade": "GRADE_A",
        "usageTimeMonths": 3,
    }
    resp = requests.post(f"{PUBLICATION_SERVICE_URL}/crear", json=body, timeout=TIMEOUT)
    check("crea publicacion (201)", resp.status_code == 201, f"status={resp.status_code} body={resp.text}")
    if resp.status_code == 201:
        publication = resp.json()
        publication_id = publication.get("publicationId")
        check("productId devuelto == enviado", publication.get("productId") == TEST_PRODUCT_ID)
        check("status inicial ACTIVE", publication.get("status") == "ACTIVE")
        print(f"       publicationId: {publication_id}")
    else:
        print("No se pudo crear la publicacion, se aborta el resto de las pruebas.")
        print_summary()
        sys.exit(1)

    # 3. Obtener por id
    step("3. Obtener publicacion (GET /{id})")
    resp = requests.get(f"{PUBLICATION_SERVICE_URL}/{publication_id}", timeout=TIMEOUT)
    check("obtiene publicacion (200)", resp.status_code == 200, f"status={resp.status_code}")

    # 4. Listar todas / por sellerId / por status
    step("4. Listar publicaciones (GET /listartodos)")
    resp = requests.get(f"{PUBLICATION_SERVICE_URL}/listartodos", timeout=TIMEOUT)
    check("lista todas (200)", resp.status_code == 200)

    resp = requests.get(f"{PUBLICATION_SERVICE_URL}/listartodos", params={"sellerId": TEST_SELLER_ID}, timeout=TIMEOUT)
    ids = [p.get("publicationId") for p in resp.json()] if resp.status_code == 200 else []
    check("filtra por sellerId e incluye la creada", resp.status_code == 200 and publication_id in ids)

    resp = requests.get(f"{PUBLICATION_SERVICE_URL}/listartodos", params={"status": "ACTIVE"}, timeout=TIMEOUT)
    check("filtra por status=ACTIVE (200)", resp.status_code == 200)

    # 5. Actualizar datos comerciales
    step("5. Actualizar publicacion (PUT /{id})")
    update_body = dict(body, price=380000, description="Precio actualizado por el test")
    resp = requests.put(f"{PUBLICATION_SERVICE_URL}/{publication_id}", json=update_body, timeout=TIMEOUT)
    check("actualiza publicacion (200)", resp.status_code == 200)
    check("precio actualizado", resp.status_code == 200 and resp.json().get("price") == 380000)

    # 6. Agregar imagen
    step("6. Agregar imagen (POST /{id}/images)")
    image_body = {"imageUrl": "https://example-bucket.s3.amazonaws.com/test.jpg", "isPrimary": True}
    resp = requests.post(f"{PUBLICATION_SERVICE_URL}/{publication_id}/images", json=image_body, timeout=TIMEOUT)
    check("agrega imagen (201)", resp.status_code == 201, f"status={resp.status_code} body={resp.text}")
    if resp.status_code == 201:
        images = resp.json().get("images", [])
        check("la publicacion tiene 1 imagen", len(images) == 1)
        if images:
            image_id = images[0].get("imageId")
            check("imagen queda marcada primaria", images[0].get("isPrimary") is True)

    # 7. Marcar imagen como primaria (idempotente aqui, pero prueba el endpoint)
    if image_id:
        step("7. Marcar imagen primaria (PATCH /{id}/images/{imageId}/primary)")
        resp = requests.patch(f"{PUBLICATION_SERVICE_URL}/{publication_id}/images/{image_id}/primary", timeout=TIMEOUT)
        check("marca imagen primaria (200)", resp.status_code == 200)

    # 8. Cambiar estado del ciclo de vida
    step("8. Cambiar estado (PATCH /{id}/status)")
    resp = requests.patch(f"{PUBLICATION_SERVICE_URL}/{publication_id}/status", json={"status": "RESERVED"}, timeout=TIMEOUT)
    check("cambia status a RESERVED (200)", resp.status_code == 200)
    check("status refleja RESERVED", resp.status_code == 200 and resp.json().get("status") == "RESERVED")

    # 9. Confirmar el vinculo cruzado con product-service
    step("9. Confirmar vinculo con product-service")
    resp = requests.get(f"{PUBLICATION_SERVICE_URL}/{publication_id}", timeout=TIMEOUT)
    linked_product_id = resp.json().get("productId") if resp.status_code == 200 else None
    if linked_product_id:
        resp_product = requests.get(f"{PRODUCT_SERVICE_URL}/{linked_product_id}", timeout=TIMEOUT)
        check("productId de la publicacion resuelve en product-service", resp_product.status_code == 200)
    else:
        check("productId de la publicacion resuelve en product-service", False, "no se obtuvo productId")

    # 10. Eliminar imagen
    if image_id:
        step("10. Eliminar imagen (DELETE /{id}/images/{imageId})")
        resp = requests.delete(f"{PUBLICATION_SERVICE_URL}/{publication_id}/images/{image_id}", timeout=TIMEOUT)
        check("elimina imagen (200)", resp.status_code == 200)
        check("la publicacion queda sin imagenes", resp.status_code == 200 and len(resp.json().get("images", [])) == 0)

    # 11. Limpieza: eliminar la publicacion de prueba
    step("11. Eliminar publicacion (DELETE /{id})")
    resp = requests.delete(f"{PUBLICATION_SERVICE_URL}/{publication_id}", timeout=TIMEOUT)
    check("elimina publicacion (204)", resp.status_code == 204)

    resp = requests.get(f"{PUBLICATION_SERVICE_URL}/{publication_id}", timeout=TIMEOUT)
    check("publicacion ya no existe (404)", resp.status_code == 404)

    print_summary()
    sys.exit(0 if failed == 0 else 1)


def print_summary():
    total = passed + failed
    print(f"\n=== Resumen: {passed}/{total} pruebas OK ===")


if __name__ == "__main__":
    try:
        main()
    except requests.exceptions.ConnectionError as e:
        print(f"\n[ERROR] No se pudo conectar a alguno de los servicios: {e}")
        print("Verifica que publication-service (:8083) y product-service (:8082) esten corriendo.")
        sys.exit(2)
