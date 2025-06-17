package com.kapil.cognos.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapil.cognos.config.FlywayService;
import com.kapil.cognos.dto.TenantRequest;
import com.kapil.cognos.model.Status;
import com.kapil.cognos.model.Tenant;
import com.kapil.cognos.repository.TenantRepository;
import com.kapil.cognos.utility.TenantUtils;

@Component
public class TenantDatabaseService {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private FlywayService flywayService;

	@Autowired
	private TenantRepository tenantRepository;

	@Autowired
	private ObjectMapper mapper;

	public Tenant provisionTenant(TenantRequest tenantRequest) {
		String tenantId = tenantRequest.getTenantId();
		String schema = "tenant_" + tenantId;
		jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
		flywayService.runMigrations(schema);
		Tenant tenant = new Tenant(tenantRequest.getTenantId(), tenantRequest.getTenantName(), schema, Status.CREATE, tenantRequest.getNameSpace(),
				tenantRequest.getUserId());
		tenantRepository.save(tenant);
		System.out.println("New Tenant saved in database");
		return tenant;
	}
		
	public Map<String, Object> getTenantMetaData(String tenantId, String schemaName)
			throws SQLException, JsonProcessingException {
		Connection dbConnection = DriverManager.getConnection(flywayService.getTenantJdbcURL(schemaName),
				flywayService.getDbUsername(), flywayService.getDbPassword());
		String selectSQL = "Select metadata from tenant_metadata where tenant_id =?";
		HashMap<String, Object> tenantMetadata =  new HashMap<>();;
		try (var stmt = dbConnection.prepareStatement(selectSQL)) {
			stmt.setString(1, tenantId);
			var rs = stmt.executeQuery();
			if (rs.next()) {
				tenantMetadata = (HashMap<String, Object>) mapper.readValue(rs.getString("metadata"),
						new TypeReference<Map<String, Object>>() {});  
			}
		}
		return tenantMetadata;
	}
	
	public boolean saveTenantMetaData(Tenant tenant,  Map<String, Object> response)
			throws SQLException, JsonProcessingException {
		Connection dbConnection = DriverManager.getConnection(flywayService.getTenantJdbcURL(tenant.getSchemaName()),
				flywayService.getDbUsername(), flywayService.getDbPassword());
		String insertSQL = "INSERT INTO tenant_metadata (tenant_id, tenant_name, tenant_status, tenant_namespace, metadata) VALUES (?, ?, ?, ?, ?)";
		try (PreparedStatement insertStmt = dbConnection.prepareStatement(insertSQL)) {
			insertStmt.setString(1, tenant.getTenantId());
			insertStmt.setString(2, tenant.getTenantName());
			insertStmt.setString(3, tenant.getStatus().name());
			insertStmt.setString(4, tenant.getNamespace());
			insertStmt.setString(5, mapper.writeValueAsString(response));
			insertStmt.executeUpdate();
		}
		return true;
	}
	
	public Map<String, String> getTenantProductsWithStatus(String tenantId, String schemaName)
			throws SQLException, JsonProcessingException {
		Connection dbConnection = DriverManager.getConnection(flywayService.getTenantJdbcURL(schemaName),
				flywayService.getDbUsername(), flywayService.getDbPassword());
		String selectSQL = "Select * from tenant_product where tenant_id =?";
		Map<String, String> tenantProduct =  new HashMap<>();
		try (var stmt = dbConnection.prepareStatement(selectSQL)) {
			stmt.setString(1, tenantId);
			var rs = stmt.executeQuery();
			while (rs.next()) {
				tenantProduct.put(rs.getString("product_name"), rs.getString("product_status"));
			}
		}
		return tenantProduct;
	}
	
	public void cloneTenantProducts(Tenant sourceTenant, Tenant targetTenant)
			throws SQLException, JsonProcessingException {
		Connection sourceDBConnection = DriverManager.getConnection(flywayService.getTenantJdbcURL(sourceTenant.getSchemaName()),
				flywayService.getDbUsername(), flywayService.getDbPassword());
		
		Connection targetDBConnection = DriverManager.getConnection(flywayService.getTenantJdbcURL(targetTenant.getSchemaName()),
				flywayService.getDbUsername(), flywayService.getDbPassword());
		
		Map<String, String> tenantProduct =  new HashMap<>();
		String selectSQL = "Select * from tenant_product where tenant_id =?";
		// Fetch data from source tenant
		try (var stmt = sourceDBConnection.prepareStatement(selectSQL)) {
			stmt.setString(1, sourceTenant.getTenantId());
			var rs = stmt.executeQuery();
			while (rs.next()) {
	            tenantProduct.put(rs.getString("product_name"), rs.getString("product_status"));
			}
		}
		
		// Insert all fetched products for the target tenant using batch
		String insertSQL = "INSERT INTO tenant_product (tenant_id, product_name, product_status) VALUES (?, ?, ?)";
		try (var stmt = targetDBConnection.prepareStatement(insertSQL)) {
		    for (var entry : tenantProduct.entrySet()) {
		        stmt.setString(1, targetTenant.getTenantId());
		        stmt.setString(2, entry.getKey());
		        stmt.setString(3, entry.getValue()); 
		        stmt.addBatch();
		    }
		    stmt.executeBatch();
		}
	}

	public List<String> getTenantProducts(Tenant tenant) throws SQLException{
		Connection sourceDBConnection = DriverManager.getConnection(flywayService.getTenantJdbcURL(tenant.getSchemaName()),
				flywayService.getDbUsername(), flywayService.getDbPassword());
		
		List<String> products =  new ArrayList<>();
		String selectSQL = "Select * from tenant_product where tenant_id =?";
		try (var stmt = sourceDBConnection.prepareStatement(selectSQL)) {
			stmt.setString(1, tenant.getTenantId());
			var rs = stmt.executeQuery();
			while (rs.next()) {
				products.add(rs.getString("product_name"));
			}
		}
		return products;
		
	}
	public boolean assignProductToTenantDB(String tenantId, String productName, Map<String, Object> response)
			throws SQLException, JsonProcessingException {
		Tenant tenant = tenantRepository.findByTenantId(tenantId)
				.orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

		String productAssignment = Optional.ofNullable(tenant.getProduct_assignments())
			    .filter(s -> !s.isBlank())
			    .map(assignments -> assignments.contains(productName)
			        ? assignments
			        : assignments + ", " + productName)
			    .orElse(productName);
		
		tenant.setProduct_assignments(productAssignment);
		tenantRepository.save(tenant);
		
		String tenantName = tenant.getTenantName();

		Connection dbConnection = DriverManager.getConnection(flywayService.getTenantJdbcURL(tenant.getSchemaName()),
				flywayService.getDbUsername(), flywayService.getDbPassword());

		String selectProdcutSQL = "Select * from tenant_product where tenant_id =? and product_name = ?";
		try (PreparedStatement stmt = dbConnection.prepareStatement(selectProdcutSQL)) {
			stmt.setString(1, tenantId);
			stmt.setString(2, productName);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				String status = rs.getString("product_status");
				System.out.println("tenant_product " + productName + " already exits for tenant " + tenantName
						+ " with status " + status);
				String updateProductSQL = "UPDATE tenant_product SET product_status = ?, modified = ? WHERE tenant_id = ? and product_name = ?";
				try (PreparedStatement updateStmt = dbConnection.prepareStatement(updateProductSQL)) {
					updateStmt.setString(1, Status.ACTIVATE.name());
					updateStmt.setString(2, TenantUtils.getLocalDateTime());
					updateStmt.setString(3, tenantId);
					updateStmt.setString(4, productName);

					updateStmt.executeUpdate();
				}
			} else {
				String insertSQL = "INSERT INTO tenant_product (tenant_id, product_name, product_status) VALUES (?, ?, ?)";
				try (PreparedStatement stmt1 = dbConnection.prepareStatement(insertSQL)) {
					stmt1.setString(1, tenantId);
					stmt1.setString(2, productName);
					stmt1.setString(3, Status.ACTIVATE.name());
					stmt1.executeUpdate();
				}
			}
		}

		String selectSQL = "Select * from tenant_metadata where tenant_id =?";
		try (PreparedStatement stmt = dbConnection.prepareStatement(selectSQL)) {
			stmt.setString(1, tenantId);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				System.out.println("Update tenant_metadata for  " + tenantId);
				String updateSQL = "UPDATE tenant_metadata SET metadata = ?, updated_at = ? WHERE tenant_id = ?";
				try (PreparedStatement updateStmt = dbConnection.prepareStatement(updateSQL)) {
					updateStmt.setString(1, mapper.writeValueAsString(response));
					updateStmt.setString(2, TenantUtils.getLocalDateTime());
					updateStmt.setString(3, tenantId);
					updateStmt.executeUpdate();
				}

			} else {
				String insertSQL = "INSERT INTO tenant_metadata (tenant_id, tenant_name, tenant_status, tenant_namespace, metadata) VALUES (?, ?, ?, ?, ?)";
				try (PreparedStatement insertStmt = dbConnection.prepareStatement(insertSQL)) {
					insertStmt.setString(1, tenantId);
					insertStmt.setString(2, tenantName);
					insertStmt.setString(3, tenant.getStatus().name());
					insertStmt.setString(4, tenant.getNamespace());
					insertStmt.setString(5, mapper.writeValueAsString(response));
					insertStmt.executeUpdate();
				}
			}
		}

		return true;
	}

	public boolean deactivateProductTenantDB(String tenantId, String productName, Map<String, Object> response)
			throws SQLException, JsonProcessingException {
		Tenant tenant = tenantRepository.findByTenantId(tenantId)
				.orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

		String currentAssignments = tenant.getProduct_assignments();

		if (currentAssignments != null && !currentAssignments.isBlank()) {
		    List<String> updatedList = Arrays.stream(currentAssignments.split(","))
		        .map(String::trim)
		        .filter(s -> !s.equalsIgnoreCase(productName))  // Remove target product
		        .toList();

		    String updatedAssignments = String.join(", ", updatedList);
		    tenant.setProduct_assignments(updatedAssignments);
			tenantRepository.save(tenant);
		}
		
		Connection dbConnection = DriverManager.getConnection(flywayService.getTenantJdbcURL(tenant.getSchemaName()),
				flywayService.getDbUsername(), flywayService.getDbPassword());

		String selectProdcutSQL = "Select * from tenant_product where tenant_id =? and product_name = ?";
		try (PreparedStatement stmt = dbConnection.prepareStatement(selectProdcutSQL)) {
			stmt.setString(1, tenantId);
			stmt.setString(2, productName);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				String status = rs.getString("product_status");
				if (status.equals(Status.PRODUCT_DEACTIVATE.name())) {
					System.out.println("Product" + productName + " already deactivated for tenant " + tenantId);
					return false;
				} else {
					System.out.println("Update tenant_product for  " + tenantId);
					String updateProductSQL = "UPDATE tenant_product SET product_status = ?, modified = ? WHERE tenant_id = ? and product_name = ?";
					try (PreparedStatement updateStmt = dbConnection.prepareStatement(updateProductSQL)) {
						updateStmt.setString(1, Status.PRODUCT_DEACTIVATE.name());
						updateStmt.setString(2, TenantUtils.getLocalDateTime());
						updateStmt.setString(3, tenantId);
						updateStmt.setString(4, productName);
						updateStmt.executeUpdate();
					}
				}
			}
		}

		String selectSQL = "Select * from tenant_metadata where tenant_id =?";
		try (PreparedStatement stmt = dbConnection.prepareStatement(selectSQL)) {
			stmt.setString(1, tenantId);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				System.out.println("Update tenant_metadata for  " + tenantId);
				String updateSQL = "UPDATE tenant_metadata SET metadata = ?, updated_at = ? WHERE tenant_id = ?";
				try (PreparedStatement updateStmt = dbConnection.prepareStatement(updateSQL)) {
					updateStmt.setString(1, mapper.writeValueAsString(response));
					updateStmt.setString(2, TenantUtils.getLocalDateTime());
					updateStmt.setString(3, tenantId);
					updateStmt.executeUpdate();
				}

			}
		}

		return true;
	}

	public boolean reactivateProductTenantDB(String tenantId, String productName, Map<String, Object> response)
			throws SQLException, JsonProcessingException {
		Tenant tenant = tenantRepository.findByTenantId(tenantId)
				.orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

		String productAssignment = Optional.ofNullable(tenant.getProduct_assignments())
			    .filter(s -> !s.isBlank())
			    .map(assignments -> assignments.contains(productName)
			        ? assignments
			        : assignments + ", " + productName)
			    .orElse(productName);
		
		tenant.setProduct_assignments(productAssignment);
		tenantRepository.save(tenant);
		
		Connection dbConnection = DriverManager.getConnection(flywayService.getTenantJdbcURL(tenant.getSchemaName()),
				flywayService.getDbUsername(), flywayService.getDbPassword());

		String selectProdcutSQL = "Select * from tenant_product where tenant_id =? and product_name = ?";
		try (PreparedStatement stmt = dbConnection.prepareStatement(selectProdcutSQL)) {
			stmt.setString(1, tenantId);
			stmt.setString(2, productName);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				String status = rs.getString("product_status");
				if (status.equals(Status.PRODUCT_ACTIVATE.name())) {
					System.out.println("Product" + productName + " already reactivated for tenant " + tenantId);
					return false;
				} else {
					System.out.println("reactivateProductTenantDB,  Update tenant_product for  " + tenantId);
					String updateProductSQL = "UPDATE tenant_product SET product_status = ?, modified = ? WHERE tenant_id = ? and product_name = ?";
					try (PreparedStatement updateStmt = dbConnection.prepareStatement(updateProductSQL)) {
						updateStmt.setString(1, Status.PRODUCT_ACTIVATE.name());
						updateStmt.setString(2, TenantUtils.getLocalDateTime());
						updateStmt.setString(3, tenantId);
						updateStmt.setString(4, productName);
						updateStmt.executeUpdate();
					}
				}
			}
		}

		String selectSQL = "Select * from tenant_metadata where tenant_id =?";
		try (PreparedStatement stmt = dbConnection.prepareStatement(selectSQL)) {
			stmt.setString(1, tenantId);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				System.out.println("Update tenant_metadata for  " + tenantId);
				String updateSQL = "UPDATE tenant_metadata SET metadata = ?, updated_at = ? WHERE tenant_id = ?";
				try (PreparedStatement updateStmt = dbConnection.prepareStatement(updateSQL)) {
					updateStmt.setString(1, mapper.writeValueAsString(response));
					updateStmt.setString(2, TenantUtils.getLocalDateTime());
					updateStmt.setString(3, tenantId);
					updateStmt.executeUpdate();
				}

			}
		}

		return true;
	}

	public String getTenantMetaData(String tenantId) {
		Tenant tenant = tenantRepository.findByTenantId(tenantId)
				.orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

		String metadata = "";
		try {
			Connection dbConnection = DriverManager.getConnection(flywayService.getTenantJdbcURL(tenant.getSchemaName()),
					flywayService.getDbUsername(), flywayService.getDbPassword());
			String selectSQL = "Select * from tenant_metadata where tenant_id =?";
			try (PreparedStatement stmt = dbConnection.prepareStatement(selectSQL)) {
				stmt.setString(1, tenantId);
				ResultSet rs = stmt.executeQuery();
				if (rs.next()) {
					metadata = rs.getString("metadata");
					System.out.println("Get tenant_metadata for  " + tenantId);
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return metadata;
	}

	public Tenant updateTenant(String tenantId, Tenant reqeustBody) throws JsonProcessingException {
		Tenant tenant = tenantRepository.findByTenantId(tenantId)
				.orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

		jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + tenant.getSchemaName());
		jdbcTemplate.execute("CREATE SCHEMA " + reqeustBody.getSchemaName());
		flywayService.runMigrations(reqeustBody.getSchemaName());
		tenant.setStatus(Status.UPDATE);
		Tenant udpateTenant = tenantRepository.save(tenant);
		return udpateTenant;
	}

	public void deleteTenant(String tenantId) throws JsonProcessingException {
		Tenant tenant = tenantRepository.findByTenantId(tenantId)
				.orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));
		String schema = "tenant_" + tenantId;
		jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema);
		flywayService.runMigrations(schema);
		tenant.setStatus(Status.DELETE);
		tenantRepository.delete(tenant);
	}

	public Tenant getTenant(String tenantId, String authHeader) {
		return tenantRepository.findByTenantId(tenantId)
				.orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));
	}

	public List<Tenant> getAllTenants() {
		return (List<Tenant>) tenantRepository.findAll();
	}

}
