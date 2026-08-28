#!/usr/bin/env bash
#
# Pruebas end-to-end del publication-service.
# Uso:
#   ./test-publications.sh              # usa http://localhost:8083
#   BASE_URL=http://localhost:8083 ./test-publications.sh
#
# Dependencias: curl y jq (jq es opcional, cae a raw JSON si no está).
#
# Autenticación: aún no hay Gateway real. Se simula con los headers
# X-User-Id / X-User-Role. Los endpoints de escritura los exigen (401 si
# faltan). Definimos dos actores:
#   - SELLER   : dueño de la publicación (puede crear/editar/borrar imágenes)
#   - ADMIN    : rol WORKSHOP_ADMIN (único que puede hacer PATCH /status)
#   - OTHER    : otro comprador/vendedor (NO es dueño -> 403 en ownership)

set -u
BASE_URL="${BASE_URL:-http://localhost:8083}"

SELLER_ID="${SELLER_ID:-11111111-1111-1111-1111-111111111111}"
SELLER_ROLE="${SELLER_ROLE:-BUYER_SELLER}"
HDR_BASE=(-H "X-User-Id: $SELLER_ID" -H "X-User-Role: $SELLER_ROLE")
OTHER_ID="99999999-9999-9999-9999-999999999999"
ADMIN_ROLE="WORKSHOP_ADMIN"
ADMIN_HDR=(-H "X-User-Id: $SELLER_ID" -H "X-User-Role: $ADMIN_ROLE")
OTHER_HDR=(-H "X-User-Id: $OTHER_ID" -H "X-User-Role: BUYER_SELLER")

HTTP_COLOR='\033[1;36m'; OK_COLOR='\033[1;32m'; FAIL_COLOR='\033[1;31m'; DIM='\033[2m'; RESET='\033[0m'
PASS=0
FAIL=0

# ---------- helpers ----------
# req <headers...> -- <método> <path> [body]
req() {
  local hdrs=()
  while [[ "$1" != "--" ]]; do hdrs+=("$1"); shift; done
  shift # el --
  local method="$1" path="$2" body="${3:-}"
  local args=(curl -s -o /tmp/pub_resp.json -w '%{http_code}' -X "$method" "${hdrs[@]}" "${BASE_URL}${path}")
  if [[ -n "$body" ]]; then
    args+=( -H 'Content-Type: application/json' -d "$body" )
  fi
  _CODE=$("${args[@]}")
  printf "${HTTP_COLOR}%s ${DIM}%s${RESET}\n" "$method" "$path"
  if command -v jq >/dev/null; then
    jq -c . /tmp/pub_resp.json 2>/dev/null || cat /tmp/pub_resp.json
  else
    cat /tmp/pub_resp.json; echo
  fi
}

jqget() { jq -r "$1" /tmp/pub_resp.json 2>/dev/null; }

expect() {
  local expected="$1" label="${2:-}"
  if [[ "$_CODE" == "$expected" ]]; then
    PASS=$((PASS+1))
    printf "  ${OK_COLOR}PASS${RESET} ${label:-expect ${expected}}: got %s\n" "$_CODE"
  else
    FAIL=$((FAIL+1))
    printf "  ${FAIL_COLOR}FAIL${RESET} ${label:-expect ${expected}}: got %s (esperaba %s)\n" "$_CODE" "$expected"
  fi
}
section() { printf "\n${DIM}-- ${1} --${RESET}\n"; }

SELLER_BODY() { printf '{"productId":"22222222-2222-2222-2222-222222222222","title":"%s","description":"%s","price":%s,"grade":"%s","usageTimeMonths":%s}' "$1" "${2:-}" "$3" "$4" "${5:-null}"; }

# ---------- 1. listado sin auth (público) ----------
section "1. GET /api/publications (sin barra) - público, paginado"
req -- GET "/api/publications"
expect 200 "listar (page)"
echo "  -> totalElements=$(jqget '.totalElements') size=$(jqget '.size')"

section "1b. GET /api/publications/ con barra ya NO mapea (fix 2.1)"
req -- GET "/api/publications/"
expect 404 "barra final -> 404"

# ---------- 2. auth obligatoria ----------
section "2. POST sin headers -> 401"
req -- POST "/api/publications" "$(SELLER_BODY 'Sin auth' '' 100 GRADE_A)"
expect 401 "falta X-User-Id -> 401"

# ---------- 3. crear publicación (con headers) ----------
section "3. POST /api/publications (crear, sellerId desde header)"
req "${HDR_BASE[@]}" -- POST "/api/publications" "$(SELLER_BODY 'RTX 3090 usada' 'GPU en buen estado' 450000 GRADE_B 12)"
expect 201 "crear válido"
PID=$(jqget '.publicationId'); STATUS=$(jqget '.status'); SID=$(jqget '.sellerId')
echo "  -> pid=$PID status=$STATUS sellerId=$SID"
if [[ -n "$PID" && "$STATUS" == "ACTIVE" && "$SID" == "$SELLER_ID" ]]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); echo "  ${FAIL_COLOR}FAIL${RESET} id/status/sellerId"; fi

section "3b. POST body con sellerId ajeno se ignora (usa header)"
# el cliente intenta colarse un sellerId ajeno en el body: debe guardarse el del header
req "${HDR_BASE[@]}" -- POST "/api/publications" '{"sellerId":"99999999-9999-9999-9999-999999999999","productId":"22222222-2222-2222-2222-222222222222","title":"Caso body id","price":100,"grade":"GRADE_A"}'
expect 201 "crear con sellerId extra en body"
P_SID=$(jqget '.sellerId')
echo "  -> sellerId guardado=$P_SID (esperado $SELLER_ID)"
if [[ "$P_SID" == "$SELLER_ID" ]]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); echo "  ${FAIL_COLOR}FAIL${RESET} sellerId del body prevaleció"; fi
PID3B=$(jqget '.publicationId')
# limpiamos la publicación de prueba del caso 3b (no forma parte del flujo principal)
req -- DELETE "/api/publications/$PID3B"
expect 204 "limpiar publicación de prueba 3b"

# ---------- 4. validación 400 ----------
section "4. POST (validación 400)"
req "${HDR_BASE[@]}" -- POST "/api/publications" '{"productId":"","title":"","price":-1,"grade":null}'
expect 400 "campos inválidos"

# ---------- 5. GET por id ----------
section "5. GET /{id}"
req -- GET "/api/publications/$PID"
expect 200 "obtener por id"

# ---------- 6. imagenes ----------
image_body() { printf '{"imageUrl":"https://s3.example/%s.jpg","isPrimary":%s}' "$1" "$2"; }

section "6. POST /{id}/images (primaria + secundaria)"
req "${HDR_BASE[@]}" -- POST "/api/publications/$PID/images" "$(image_body 'foto-a' true)"
expect 201 "primera imagen primaria"
IMG1=$(jqget '.images[] | select(.isPrimary == true) | .imageId')

req "${HDR_BASE[@]}" -- POST "/api/publications/$PID/images" "$(image_body 'foto-b' false)"
expect 201 "segunda imagen NO primaria"
IMG2=$(jqget '.images[] | select(.isPrimary == false) | .imageId')
echo "  -> img1=$IMG1 img2=$IMG2"

section "6b. POST image sin headers -> 401"
req -- POST "/api/publications/$PID/images" "$(image_body 'foto-nope' true)"
expect 401 "imagen sin auth -> 401"

section "6c. POST image de OTRO usuario (no dueño) -> 403"
req "${OTHER_HDR[@]}" -- POST "/api/publications/$PID/images" "$(image_body 'foto-ajena' false)"
expect 403 "imagen de otro -> 403"

# ---------- 7. PATCH primary ----------
section "7. PATCH /{id}/images/{img2}/primary"
req "${HDR_BASE[@]}" -- PATCH "/api/publications/$PID/images/$IMG2/primary"
expect 200 "cambiar imagen primaria"
PRIM=$(jqget '.images[] | select(.isPrimary == true) | .imageId')
if [[ "$PRIM" == "$IMG2" ]]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); echo "  ${FAIL_COLOR}FAIL${RESET} img2 no quedó primaria"; fi

# ---------- 8. PUT update ----------
section "8. PUT /{id} (update, ownership + status restringido)"
# put de OTRO usuario -> 403
req "${OTHER_HDR[@]}" -- PUT "/api/publications/$PID" "$(SELLER_BODY 'X' '' 1 GRADE_A)"
expect 403 "update de otro -> 403"

# put del dueño -> 200 (sin status)
req "${HDR_BASE[@]}" -- PUT "/api/publications/$PID" "$(SELLER_BODY 'RTX 3090 - actualizada' 'Actualizado' 400000 GRADE_A 6)"
expect 200 "update dueño"
PRICE=$(jqget '.price')
echo "  -> price=$PRICE sellerId=$(jqget '.sellerId')"

# put con status RESERVED -> 403 (solo ACTIVE/WITHDRAWN por PUT)
req "${HDR_BASE[@]}" -- PUT "/api/publications/$PID" '{"productId":"22222222-2222-2222-2222-222222222222","title":"RTX 3090","price":400000,"grade":"GRADE_A","status":"RESERVED"}'
expect 403 "PUT con status RESERVED -> 403"

# put con status WITHDRAWN -> 200 (dueño puede retirar)
req "${HDR_BASE[@]}" -- PUT "/api/publications/$PID" '{"productId":"22222222-2222-2222-2222-222222222222","title":"RTX 3090","price":400000,"grade":"GRADE_A","status":"WITHDRAWN"}'
expect 200 "PUT con status WITHDRAWN -> 200"

# volver a ACTIVE vía PUT (dueño)
req "${HDR_BASE[@]}" -- PUT "/api/publications/$PID" '{"productId":"22222222-2222-2222-2222-222222222222","title":"RTX 3090","price":400000,"grade":"GRADE_A","status":"ACTIVE"}'
expect 200 "PUT volver a ACTIVE"

# ---------- 9. PATCH status (rol) ----------
section "9. PATCH /{id}/status (solo WORKSHOP_ADMIN)"
# comprador normal -> 403
req "${HDR_BASE[@]}" -- PATCH "/api/publications/$PID/status" '{"status":"RESERVED"}'
expect 403 "PATCH status comprador -> 403"
# admin -> 200
req "${ADMIN_HDR[@]}" -- PATCH "/api/publications/$PID/status" '{"status":"RESERVED"}'
expect 200 "PATCH status admin RESERVED"
req "${ADMIN_HDR[@]}" -- PATCH "/api/publications/$PID/status" '{"status":"SOLD"}'
expect 200 "PATCH status admin SOLD"
# volver a ACTIVE para filtros
req "${ADMIN_HDR[@]}" -- PATCH "/api/publications/$PID/status" '{"status":"ACTIVE"}'
expect 200 "PATCH status admin ACTIVE"

# ---------- 10. filtros combinables + paginación ----------
section "10. GET /api/publications?sellerId=...&status=... (combinables)"
req -- GET "/api/publications?sellerId=$SELLER_ID&status=ACTIVE"
expect 200 "filtros combinados sellerId+status"
HAS=$(jq --arg id "$PID" '[.content[] | select(.publicationId == $id)] | length' /tmp/pub_resp.json)
if [[ "$HAS" -ge 1 ]]; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); echo "  ${FAIL_COLOR}FAIL${RESET} publicación no devuelta con filtros combinados"; fi

req -- GET "/api/publications?maxPrice=500000&grade=GRADE_A"
expect 200 "filtros maxPrice+grade"
req -- GET "/api/publications?status=ACTIVE&page=1&limit=5"
expect 200 "paginación page/limit"
echo "  -> content.length=$(jqget '[.content[]] | length') pageable=$(jqget '.pageable.pageNumber + 1')"

# ---------- 11. DELETE imagen ----------
section "11. DELETE /{id}/images/{imageId}"
req "${HDR_BASE[@]}" -- DELETE "/api/publications/$PID/images/$IMG2"
expect 200 "borrar imagen 2 (dueño)"
NIMG=$(jqget '[.images[]] | length')
echo "  -> imágenes restantes=$NIMG"

# ---------- 12. DELETE publicación + 404 ----------
section "12. DELETE /{id} y GET -> 404"
req -- DELETE "/api/publications/$PID"
expect 204 "borrar publicación"
req -- GET "/api/publications/$PID"
expect 404 "obtener borrada -> 404"

# ---------- 13. 404s ----------
section "13. 404s sobre id inexistente"
req -- GET "/api/publications/00000000-0000-0000-0000-000000000000"
expect 404 "GET inexistente"
req "${HDR_BASE[@]}" -- PUT "/api/publications/00000000-0000-0000-0000-000000000000" "$(SELLER_BODY 'x' '' 1 GRADE_A)"
expect 404 "PUT inexistente"
req "${ADMIN_HDR[@]}" -- PATCH "/api/publications/00000000-0000-0000-0000-000000000000/status" '{"status":"ACTIVE"}'
expect 404 "PATCH status inexistente"
req -- DELETE "/api/publications/00000000-0000-0000-0000-000000000000"
expect 404 "DELETE inexistente"

# ---------- resumen ----------
printf "\n${DIM}========================================${RESET}\n"
printf "Resultado: ${OK_COLOR}%d PASS${RESET}  ${FAIL_COLOR}%d FAIL${RESET}\n" "$PASS" "$FAIL"
if [[ "$FAIL" -gt 0 ]]; then exit 1; else exit 0; fi
