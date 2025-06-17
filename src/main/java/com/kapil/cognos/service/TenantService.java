package com.kapil.cognos.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.kapil.cognos.dto.TenantRequest;
import com.kapil.cognos.exception.CustomException;
import com.kapil.cognos.model.Status;
import com.kapil.cognos.model.Tenant;
import com.kapil.cognos.repository.TenantRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Service
public class TenantService {

	@Autowired
	private TenantDatabaseService tenantDatabaseService;

	@Autowired
	private TenantRepository tenantRepository;

	@Autowired
	private CognosApiOkHttpClientService apiOkHttpClientService;

	@Autowired
	private TenantSDKService tenantSDKService;

	@CircuitBreaker(name = "tenantServiceCB", fallbackMethod = "fallbackProvisionTenant")
    @Retry(name = "tenantServiceRetry", fallbackMethod = "fallbackProvisionTenant")
	public Tenant provisionTenant(TenantRequest tenantRequest, String authHeader) {
		String tenantName = tenantRequest.getTenantName();
		Tenant tenant = null;
		try {
			String tenantId = tenantName.toLowerCase().replace("", "_"); // Or use tenantName if needed
			tenantRequest.setTenantId(tenantId);
			tenant = tenantDatabaseService.provisionTenant(tenantRequest);
			tenantRequest.setUserId(tenant.getUserId());
			tenantRequest.setNameSpace(tenant.getNamespace());

			Map<String, Object> createdTenantRespopnse = tenantSDKService.provisionTenant(tenantRequest);
			System.out.println("Tenant '" + tenantName + "is created successfully for user '" + tenantRequest.getUserId() + "'.");

			boolean isTenantCreated = (boolean) createdTenantRespopnse.get("result");
			if (isTenantCreated) {
				try {
					tenantDatabaseService.saveTenantMetaData(tenant, createdTenantRespopnse);
				} catch (SQLException | JsonProcessingException e) {
					throw new CustomException(TenantService.class
							+ " During saving Tenant Metadata into Database, Exception " + e.getMessage());
				}
			}

		} catch (Exception e) {
			throw new CustomException(TenantService.class + " Exception " + e.getMessage());
		}
		return tenant;
	}

	public boolean assignProductToTenant(String tenantId, String productName, String authHeader) {
		Map<String, Object> response = tenantSDKService.assignProductToTenant(tenantId, productName);
		boolean isProductAdded = (boolean) response.get("result");

		if (isProductAdded) {
			try {
				tenantDatabaseService.assignProductToTenantDB(tenantId, productName, response);
			} catch (SQLException | JsonProcessingException e) {
				throw new CustomException(TenantService.class
						+ " During assigning Product To Tenant in Database,  Exception " + e.getMessage());
			}
		}
		return isProductAdded;
	}

	public boolean deactiveProductTenant(String tenantId, String productName, String authHeader)
			throws JsonMappingException, JsonProcessingException {
		Map<String, Object> response = tenantSDKService.deactiveProductTenant(tenantId, productName);
		boolean isProductDeactivate = (boolean) response.get("result");

		if (isProductDeactivate) {
			try {
				tenantDatabaseService.deactivateProductTenantDB(tenantId, productName, response);
			} catch (SQLException | JsonProcessingException e) {
				throw new CustomException(TenantService.class
						+ " During deactivating Tenant Product in Database,  Exception " + e.getMessage());
			}
		}
		return isProductDeactivate;
	}

	public boolean reactiveProductTenant(String tenantId, String productName, String authHeader)
			throws JsonMappingException, JsonProcessingException {
		Map<String, Object> response = tenantSDKService.reactiveProductTenant(tenantId, productName);
		boolean isProductDeactivate = (boolean) response.get("result");

		if (isProductDeactivate) {
			try {
				tenantDatabaseService.reactivateProductTenantDB(tenantId, productName, response);
			} catch (SQLException | JsonProcessingException e) {
				throw new CustomException(TenantService.class
						+ " During reactivating Tenant Product in Database,  Exception " + e.getMessage());
			}
		}
		return isProductDeactivate;
	}

	public Tenant cloneTenant(String sourceTenantName, String targetTenantName, String authHeader) {

		Tenant sourceTenant = tenantDatabaseService.getTenant(sourceTenantName,authHeader);
		Tenant targetTenant = null;
		try {
			// create target tenant for cloning
			String targetTenantId =  targetTenantName.toLowerCase().replace("", "_");
			TenantRequest targetTenantRequest = new TenantRequest();
			targetTenantRequest.setNameSpace(sourceTenant.getNamespace());
			targetTenantRequest.setTenantId(targetTenantId);
			targetTenantRequest.setUserId(sourceTenant.getUserId());
			targetTenant = tenantDatabaseService.provisionTenant(targetTenantRequest);
			
			// clone all product
			tenantDatabaseService.cloneTenantProducts(sourceTenant, targetTenant);
			List<String> productList = tenantDatabaseService.getTenantProducts(targetTenant);
			Map<String, Object> cloneTenantMetaData = tenantSDKService.cloneTenant(sourceTenant, targetTenant, targetTenantName, productList);
			tenantDatabaseService.saveTenantMetaData(sourceTenant,  cloneTenantMetaData);
			
			System.out.println("Tenant '" + sourceTenantName + "is cloned successfully into "+targetTenantName+" for user '"
					+ targetTenant.getUserId() + "'.");
		} catch (Exception e) {
			throw new CustomException(TenantService.class + " Exception in cloneTenant for " + targetTenantName);
		}
		return targetTenant;
	}

	public Tenant getTenant(String tenantId, String authHeader) {
		apiOkHttpClientService.getTenant(tenantId, authHeader);

		return tenantRepository.findByTenantId(tenantId)
				.orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));
	}

	// LIST ALL API Keys
	public String getAPIKeys(String authHeader) {
		return apiOkHttpClientService.getAPIKeys(authHeader);
	}

	public Tenant updateTenant(String tenantId, String authHeader, Tenant reqeustBody) throws JsonProcessingException {

		String result = apiOkHttpClientService.updateTenant(tenantId, reqeustBody, authHeader);
		System.out.println("cognosApiService Tenant Updated : " + result);
		Tenant udpateTenant = tenantDatabaseService.updateTenant(tenantId, reqeustBody);

		return udpateTenant;
	}

	public String deleteTenant(String tenantId, String authHeader) throws JsonProcessingException {
		tenantDatabaseService.deleteTenant(tenantId);
		String result = apiOkHttpClientService.deleteTenant(tenantId, authHeader);
		System.out.println("cognosApiService Tenant deleted : " + result);

		return result;
	}

	public Tenant fallbackCreateTenant(TenantRequest tenantRequest,
	        String authHeader, Throwable t) {
		// Log the exception or error
	    System.err.println(HttpStatus.SERVICE_UNAVAILABLE + "Fallback triggered due to: " + t.getMessage()); 
	    String fallbackResponse =  "Tenant creation failed for " + tenantRequest.getTenantName() + "Status : FAILED";
		Tenant tenant = new Tenant(null, tenantRequest.getTenantName(), fallbackResponse, Status.FAILED, tenantRequest.getNameSpace(),
				tenantRequest.getUserId());
	    
	    return tenant;
	}
	
}
