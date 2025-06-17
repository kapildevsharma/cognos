package com.kapil.cognos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String tenantId;

    private String tenantName;
    
    private String schemaName;
    
    private String namespace;
    
    private String userId;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String product_assignments;
    
    // Default constructor
    public Tenant() {}

    // Constructor with all parameters
    public Tenant(String tenantId, String tenantName, String schemaName, Status status,  String namespace, String userId) {
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.schemaName = schemaName;
        this.status = status;
        this.namespace = namespace;
        this.userId = userId;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    
    public String getTenantName() {
		return tenantName;
	}

	public void setTenantName(String tenantName) {
		this.tenantName = tenantName;
	}

	public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}
	
	public String getProduct_assignments() {
		return product_assignments;
	}

	public void setProduct_assignments(String product_assignments) {
		this.product_assignments = product_assignments;
	}
	
}

