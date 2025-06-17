
package com.kapil.cognos.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kapil.cognos.dto.TenantRequest;
import com.kapil.cognos.dto.TenantResponse;
import com.kapil.cognos.kafka.KafkaActionService;
import com.kapil.cognos.model.Status;
import com.kapil.cognos.model.Tenant;
import com.kapil.cognos.service.TenantService;
import com.kapil.cognos.utility.TenantUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Tag(name = "Tenant Management", description = "Operations related to tenant management")
@RestController
@RequestMapping("/tenants")
public class TenantController {

	@Autowired
	private TenantService tenantService;

	@Autowired
	private KafkaActionService kafkaActionService;

    @CircuitBreaker(name = "tenantServiceCB", fallbackMethod = "fallbackCreateTenant")
	@Retry(name = "tenantServiceRetry", fallbackMethod = "fallbackCreateTenant")
	@Operation(summary = "Create a new tenant")
	@PostMapping
	public ResponseEntity<TenantResponse> createTenant(@RequestBody TenantRequest tenantRequest,
			@RequestHeader("IBM-BA-Authorization") String authHeader) {
		Tenant createdTenant = tenantService.provisionTenant(tenantRequest, authHeader);
		TenantResponse response = new TenantResponse(
				"Tenant " + tenantRequest.getTenantName() + " is created successfully", createdTenant.getTenantId(),
				createdTenant.getStatus().name());
		kafkaActionService.sendNotificationTenant("tenant-create", TenantUtils.convertWithJackson(response));
	// 	kafkaActionService.triggerTenantAction("create", tenantRequest.getTenantName());
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Assign product to an tenant")
	@PostMapping("/{tenantId}/add-product")
	public ResponseEntity<String> assignProductToTenant(@RequestParam String productName, @PathVariable String tenantId,
			@RequestHeader("IBM-BA-Authorization") String authHeader) throws JsonProcessingException {
		String response = "";
		Map<String, Object> payload = new HashMap<String, Object>();
		payload.put("tenantId", tenantId);
		payload.put("productName", productName);
		boolean isProductAssign = tenantService.assignProductToTenant(tenantId, productName, authHeader);
		if (isProductAssign) {
			response = "Product " + productName + " assigned successfully to tenant(" + tenantId + ")";
			payload.put("action", "PRODUCT_ADDED");
		} else {
			response = "Product " + productName + " does not assign to tenant(" + tenantId + ")";
			payload.put("action", "PRODUCT_NOT_ADDED");
		}
		payload.put("timestamp", System.currentTimeMillis());

		kafkaActionService.sendNotificationTenant("tenant-product-addition", payload);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Deactivate product for the tenant")
	@PostMapping("/{tenantId}/deactive-product")
	public ResponseEntity<String> deactiveProductTenant(@RequestParam String productName, @PathVariable String tenantId,
			@RequestHeader("IBM-BA-Authorization") String authHeader) throws JsonProcessingException {
		String response = "";
		Map<String, Object> payload = new HashMap<String, Object>();
		payload.put("tenantId", tenantId);
		payload.put("productName", productName);
		boolean isProductDeactivate = tenantService.deactiveProductTenant(tenantId, productName, authHeader);
		if (isProductDeactivate) {
			response = "Product " + productName + " is deactivated successfully for tenant(" + tenantId + ")";
			payload.put("product_status", "PRODUCT_DEACTIVATE");
		} else {
			response = "Product " + productName + " does not deactivate for tenant(" + tenantId + ")";
			payload.put("product_status", "PRODUCT_NOT_DEACTIVATE");
		}
		payload.put("timestamp", System.currentTimeMillis());

		kafkaActionService.sendNotificationTenant("tenant-product-deactivate", payload);
		return ResponseEntity.ok(response);
	}
	
	@Operation(summary = "Reactivate product for the tenant")
	@PostMapping("/{tenantId}/reactive-product")
	public ResponseEntity<String> reActiveProductTenant(@RequestParam String productName, @PathVariable String tenantId,
			@RequestHeader("IBM-BA-Authorization") String authHeader) throws JsonProcessingException {
		String response = "";
		Map<String, Object> payload = new HashMap<String, Object>();
		payload.put("tenantId", tenantId);
		payload.put("productName", productName);
		boolean isProductDeactivate = tenantService.reactiveProductTenant(tenantId, productName, authHeader);
		if (isProductDeactivate) {
			response = "Product " + productName + " is reactivated successfully for tenant(" + tenantId + ")";
			payload.put("product_status", "PRODUCT_REACTIVATE");
		} else {
			response = "Product " + productName + " does not reactivate for tenant(" + tenantId + ")";
			payload.put("product_status", "PRODUCT_NOT_REACTIVATE");
		}
		payload.put("timestamp", System.currentTimeMillis());

		kafkaActionService.sendNotificationTenant("tenant-product-reactivate", payload);
		return ResponseEntity.ok(response);
	}
	
	@Operation(summary = "Clone the tenant")
	@PostMapping("/{tenantId}/clone")
	public ResponseEntity<String> cloneTenant(@RequestParam String targetTenantName, @PathVariable String tenantId,
			@RequestHeader("IBM-BA-Authorization") String authHeader) throws JsonProcessingException {
		String response = "";
		Map<String, Object> payload = new HashMap<String, Object>();
		payload.put("tenantId", tenantId);
		payload.put("targetTenantName", targetTenantName);
		Tenant cloneTenant = tenantService.cloneTenant(targetTenantName, tenantId, authHeader);
		if (cloneTenant!=null) {
			response = "Tenant " + tenantId + " is cloned for tenant(" + targetTenantName + ")";
			payload.put("tenant_status", "TENANT_CLONE");
		} else {
			response = "Tenant " + tenantId + " does not clone for tenant(" + targetTenantName + ")";
			payload.put("tenant_status", "TENANT_NOT_CLONE");
		}
		payload.put("timestamp", System.currentTimeMillis());

		kafkaActionService.sendNotificationTenant("tenant-clone", payload);
		return ResponseEntity.ok(response);
	}
	
	@Operation(summary = "Move the tenant")
	@PostMapping("/{tenantId}/move")
	public ResponseEntity<String> moveTenant(@RequestParam String targetTenantName, @PathVariable String tenantId,
			@RequestHeader("IBM-BA-Authorization") String authHeader) throws JsonProcessingException {
		String response = "";
		Map<String, Object> payload = new HashMap<String, Object>();
		payload.put("tenantId", tenantId);
		payload.put("targetTenantName", targetTenantName);
		Tenant cloneTenant = tenantService.cloneTenant(targetTenantName, tenantId, authHeader);
		if (cloneTenant!=null) {
			response = "Tenant " + tenantId + " is cloned for tenant(" + targetTenantName + ")";
			payload.put("tenant_status", "TENANT_CLONE");
		} else {
			response = "Tenant " + tenantId + " does not clone for tenant(" + targetTenantName + ")";
			payload.put("tenant_status", "TENANT_NOT_CLONE");
		}
		payload.put("timestamp", System.currentTimeMillis());

		kafkaActionService.sendNotificationTenant("tenant-clone", payload);
		return ResponseEntity.ok(response);
	}


	@Operation(summary = "Update an existing tenant by its tenant id")
	@PutMapping("/{tenantId}")
	public ResponseEntity<TenantResponse> resetTenant(@RequestBody Tenant reqeustBody, @PathVariable String tenantId,
			@RequestHeader("IBM-BA-Authorization") String authHeader) throws JsonProcessingException {

		Tenant updatedTenant = tenantService.updateTenant(tenantId, authHeader, reqeustBody);
		TenantResponse response = new TenantResponse("Tenant " + tenantId + " is updated", updatedTenant.getTenantId(),
				updatedTenant.getStatus().name());
		kafkaActionService.sendNotificationTenant("tenant-update", TenantUtils.convertWithJackson(response));
		return ResponseEntity.ok(response);

	}

	@Operation(summary = "Get tenant information")
	@GetMapping("/{tenantId}")
	public ResponseEntity<TenantResponse> getTenant(@PathVariable String tenantId,
			@RequestHeader("IBM-BA-Authorization") String authHeader) {
		Tenant tenant = tenantService.getTenant(tenantId, authHeader);

		// trigger to Kafka
		kafkaActionService.triggerTenantAction("get Tenant details", tenantId);
		TenantResponse response = new TenantResponse("Tenant " + tenantId + " is found successfully",
				tenant.getTenantId(), tenant.getStatus().name());

		return ResponseEntity.ok(response);
	}

	 // Fallback method with matching parameters + Throwable at the end
	public ResponseEntity<TenantResponse> fallbackCreateTenant(TenantRequest tenantRequest,
	        String authHeader, Throwable t) {
		// Log the exception or error
	    System.err.println("Fallback triggered due to: " + t.getMessage());
	    TenantResponse fallbackResponse = new TenantResponse(
	            "Tenant creation failed for " + tenantRequest.getTenantName(),
	            null,
	            Status.FAILED.toString());

	    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(fallbackResponse);
	}
	
}
