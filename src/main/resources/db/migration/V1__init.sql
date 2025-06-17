-- Table to track products assigned to tenants
CREATE TABLE tenant_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    product_status VARCHAR(50)NOT NULL,   -- ACTIVE, INACTIVE, etc.
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_product_status ON tenant_product(product_status);
CREATE INDEX idx_tenant_product_name ON tenant_product(tenant_id, product_name);

-- Table to track tenant metadata and lifecycle
CREATE TABLE tenant_metadata (
    tenant_id VARCHAR(100) PRIMARY KEY,               -- Unique tenant ID
    tenant_name VARCHAR(255) NOT NULL,                -- Human-readable name
    tenant_status VARCHAR(50) NOT NULL,               -- ACTIVE, INACTIVE, etc.
    tenant_namespace VARCHAR(255) DEFAULT 'Cognos',   -- DEFAULT must be quoted
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    metadata JSON                                     -- Flexible tenant info
);

CREATE INDEX idx_tenant_status ON tenant_metadata(tenant_status);
CREATE INDEX idx_tenant_name ON tenant_metadata(tenant_name);
