#!/usr/bin/env bash
#
# Crea una publicación de prueba en el publication-service, usando un
# productId real del product-service (localhost:8080).
# Uso:
#   ./crear-publicacion.sh
#   BASE_URL=... ./crear-publicacion.sh        # por defecto http://localhost:8083
#   PRODUCTS_URL=... ./crear-publicacion.sh    # por defecto http://localhost:8080
#   PRODUCT_ID=<uuid> ./crear-publicacion.sh   # fija un producto específico
#
# La creación exige los headers X-User-Id / X-User-Role (auth simulada).
set -e

BASE_URL="${BASE_URL:-http://localhost:8083}"
PRODUCTS_URL="${PRODUCTS_URL:-http://localhost:8080}"
SELLER_ID="${SELLER_ID:-11111111-1111-1111-1111-111111111111}"
ROLE="${ROLE:-BUYER_SELLER}"

# Si no se fija un PRODUCT_ID, lo tomamos del primer producto del catálogo.
if [ -z "${PRODUCT_ID:-}" ]; then
  echo "Consultando product-service ($PRODUCTS_URL/api/v1/products)..."
  PRODUCT_ID=$(curl -s "${PRODUCTS_URL}/api/v1/products" | jq -r '.content[0].productId // .[0].productId' 2>/dev/null)
  if [ -z "$PRODUCT_ID" ] || [ "$PRODUCT_ID" = "null" ]; then
    echo "No se pudo obtener un productId del product-service." >&2
    exit 1
  fi
  echo "Usando producto: $PRODUCT_ID"
fi

BODY=$(jq -n \
  --arg pid "$PRODUCT_ID" \
  '{productId:$pid, title:"RTX 3090 usada - prueba", description:"GPU en buen estado, se vende porque upgrade.", price:450000, grade:"GRADE_B", usageTimeMonths:12}')

resp=$(curl -s -w '\n%{http_code}' \
  -X POST "${BASE_URL}/api/publications" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: ${SELLER_ID}" \
  -H "X-User-Role: ${ROLE}" \
  -d "$BODY")

code=$(printf '%s' "$resp" | tail -1)
json=$(printf '%s' "$resp" | head -1)

echo "HTTP $code"
printf '%s\n' "$json" | jq . 2>/dev/null || printf '%s\n' "$json"

if [ "$code" = "201" ]; then
  pid=$(printf '%s' "$json" | jq -r '.publicationId')
  echo
  echo "Publicación creada: $pid"
  echo "Verla:  curl -s ${BASE_URL}/api/publications/${pid} | jq"
fi
