
package com.kapil.cognos.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.kapil.cognos.model.Tenant;

public interface TenantRepository extends CrudRepository<Tenant, Long> {
    Optional<Tenant> findByTenantId(String tenantId);
}
