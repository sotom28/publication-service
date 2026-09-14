-- ============================================================================
-- PUBLICATION-SERVICE - Esquema + datos iniciales (MariaDB)
-- ============================================================================
-- Microservicio: publication-service   | puerto 8083 | BD propia: db_listings
-- Se referencia (sin FK real) al microservicio product-service:
--   product-service | puerto 8082 | BD: db_product | tabla master_products
-- Arquitectura de microservicios: cada servicio tiene su propia base de
-- datos, por lo que no se puede hacer JOIN ni FK cross-database. El
-- product_id (y el seller_id, que apunta a db_users) se guardan tal cual
-- llegan y se validan a nivel de aplicación, no de esquema.
--
-- Este script combina el esquema (db_listings) y los datos de ejemplo.
-- Cada fila usa como product_id uno de los 10 UUID fijos que inserta
-- product-service en product-data.sql ('10000000-...-0001' a '...-0010'),
-- para poder probar el flujo vinculado con product-service (puerto 8082).
--
-- Corresponde a Publication.java y PublicationImage.java (Model/).
-- ============================================================================

CREATE DATABASE IF NOT EXISTS db_listings
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE db_listings;

-- ----------------------------------------------------------------------------
-- ESQUEMA
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS publications (
    publication_id     CHAR(36) PRIMARY KEY DEFAULT (UUID()),

    -- Referencia remota a db_users.users.user_id (microservicio de usuarios).
    -- No es FK real: se valida contra el claim del JWT.
    seller_id           CHAR(36) NOT NULL,

    -- Referencia remota a db_product.master_products.product_id
    -- (product-service, puerto 8082). No es FK real.
    product_id          CHAR(36) NOT NULL,

    title               VARCHAR(150) NOT NULL,
    description         TEXT,
    price               INT NOT NULL,                          -- Precio en CLP
    grade               ENUM('GRADE_A', 'GRADE_B', 'GRADE_C') NOT NULL,
    usage_time_months   INT,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
        -- valores app: ACTIVE, RESERVED, SOLD, IN_INSPECTION
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_publications_seller_id (seller_id),
    INDEX idx_publications_product_id (product_id),
    INDEX idx_publications_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS publication_images (
    image_id        CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    publication_id  CHAR(36) NOT NULL,
    image_url       TEXT NOT NULL,              -- URL del objeto en Amazon S3
    is_primary      BOOLEAN DEFAULT FALSE,

    CONSTRAINT fk_img_pub FOREIGN KEY (publication_id)
        REFERENCES publications(publication_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- DATOS INICIALES
-- ----------------------------------------------------------------------------
-- seller_id es un UUID fijo de prueba (referencia remota a db_users, no FK real).

START TRANSACTION;

INSERT INTO publications
    (publication_id, seller_id, product_id, title, description, price,
     grade, usage_time_months, status)
SELECT '30000000-0000-0000-0000-000000000001',
       '20000000-0000-0000-0000-000000000001',
       '10000000-0000-0000-0000-000000000001',
       'AMD Ryzen 7 7800X3D', 'Procesador usado, funcionando perfecto, incluye caja original.',
       420000, 'GRADE_A', 6, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM publications WHERE publication_id = '30000000-0000-0000-0000-000000000001');

INSERT INTO publications
    (publication_id, seller_id, product_id, title, description, price,
     grade, usage_time_months, status)
SELECT '30000000-0000-0000-0000-000000000002',
       '20000000-0000-0000-0000-000000000001',
       '10000000-0000-0000-0000-000000000002',
       'Intel Core i5-14600K', 'Poco uso, se vende por cambio de plataforma.',
       280000, 'GRADE_A', 3, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM publications WHERE publication_id = '30000000-0000-0000-0000-000000000002');

INSERT INTO publications
    (publication_id, seller_id, product_id, title, description, price,
     grade, usage_time_months, status)
SELECT '30000000-0000-0000-0000-000000000003',
       '20000000-0000-0000-0000-000000000002',
       '10000000-0000-0000-0000-000000000003',
       'ASUS TUF Gaming B650-PLUS', 'Placa madre en buen estado, con caja y accesorios.',
       150000, 'GRADE_B', 10, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM publications WHERE publication_id = '30000000-0000-0000-0000-000000000003');

INSERT INTO publications
    (publication_id, seller_id, product_id, title, description, price,
     grade, usage_time_months, status)
SELECT '30000000-0000-0000-0000-000000000004',
       '20000000-0000-0000-0000-000000000002',
       '10000000-0000-0000-0000-000000000004',
       'Corsair Vengeance 32GB', 'Kit de RAM DDR5, sin problemas de estabilidad.',
       90000, 'GRADE_A', 4, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM publications WHERE publication_id = '30000000-0000-0000-0000-000000000004');

INSERT INTO publications
    (publication_id, seller_id, product_id, title, description, price,
     grade, usage_time_months, status)
SELECT '30000000-0000-0000-0000-000000000005',
       '20000000-0000-0000-0000-000000000003',
       '10000000-0000-0000-0000-000000000005',
       'NVIDIA GeForce RTX 4070 SUPER', 'Tarjeta de video, temperaturas normales, sin mineria.',
       550000, 'GRADE_B', 8, 'RESERVED'
WHERE NOT EXISTS (SELECT 1 FROM publications WHERE publication_id = '30000000-0000-0000-0000-000000000005');

INSERT INTO publications
    (publication_id, seller_id, product_id, title, description, price,
     grade, usage_time_months, status)
SELECT '30000000-0000-0000-0000-000000000006',
       '20000000-0000-0000-0000-000000000003',
       '10000000-0000-0000-0000-000000000006',
       'Samsung 990 PRO 1TB', 'SSD NVMe, 5% de desgaste segun SMART.',
       70000, 'GRADE_A', 2, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM publications WHERE publication_id = '30000000-0000-0000-0000-000000000006');

INSERT INTO publications
    (publication_id, seller_id, product_id, title, description, price,
     grade, usage_time_months, status)
SELECT '30000000-0000-0000-0000-000000000007',
       '20000000-0000-0000-0000-000000000004',
       '10000000-0000-0000-0000-000000000007',
       'Corsair RM750e', 'Fuente de poder 80+ Gold, funcionando sin ruido.',
       65000, 'GRADE_B', 12, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM publications WHERE publication_id = '30000000-0000-0000-0000-000000000007');

INSERT INTO publications
    (publication_id, seller_id, product_id, title, description, price,
     grade, usage_time_months, status)
SELECT '30000000-0000-0000-0000-000000000008',
       '20000000-0000-0000-0000-000000000004',
       '10000000-0000-0000-0000-000000000008',
       'NZXT H5 Flow', 'Gabinete mid tower, algunas marcas de uso normal.',
       45000, 'GRADE_C', 18, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM publications WHERE publication_id = '30000000-0000-0000-0000-000000000008');

INSERT INTO publications
    (publication_id, seller_id, product_id, title, description, price,
     grade, usage_time_months, status)
SELECT '30000000-0000-0000-0000-000000000009',
       '20000000-0000-0000-0000-000000000005',
       '10000000-0000-0000-0000-000000000009',
       'Noctua NH-D15', 'Disipador de aire, incluye pasta termica nueva.',
       55000, 'GRADE_A', 5, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM publications WHERE publication_id = '30000000-0000-0000-0000-000000000009');

INSERT INTO publications
    (publication_id, seller_id, product_id, title, description, price,
     grade, usage_time_months, status)
SELECT '30000000-0000-0000-0000-000000000010',
       '20000000-0000-0000-0000-000000000005',
       '10000000-0000-0000-0000-000000000010',
       'AMD Radeon RX 7800 XT', 'Tarjeta de video, se vende con caja y factura.',
       480000, 'GRADE_B', 7, 'IN_INSPECTION'
WHERE NOT EXISTS (SELECT 1 FROM publications WHERE publication_id = '30000000-0000-0000-0000-000000000010');

COMMIT;

-- Verificacion: debe devolver 10 filas, cada una con su product_id
-- listo para consultar en product-service (GET localhost:8082/api/v1/products/{product_id}).
SELECT publication_id, product_id, title, price, status
FROM publications
WHERE publication_id BETWEEN '30000000-0000-0000-0000-000000000001'
                          AND '30000000-0000-0000-0000-000000000010'
ORDER BY publication_id;
