package com.kapil.cognos.service;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapil.cognos.exception.CustomException;
import com.kapil.cognos.model.Tenant;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
public class CognosApiOkHttpClientService {

    @Value("${cognos.api.url}")
    private String baseAPIUrl;

    @Value("${cognos.api.cam-token}")
    private String authToken;

    @Value("${cognos.api.xsrf-token}")
    private String xsrfToken;
    
    private final OkHttpClient okHttpClient;
   	private final ObjectMapper objectMapper;
   	
    
    public CognosApiOkHttpClientService(OkHttpClient okHttpClient, ObjectMapper objectMapper) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
    }

    public Request getRequestWithHeader(String url, String authHeader, RequestBody requestBody,  String method) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .addHeader("IBM-BA-Authorization", "CAM " + authToken)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-XSRF-Token", xsrfToken) // modify every session
                .addHeader("Cache-Control", "no-cache");

      
        // Set the HTTP method
        switch (method.toUpperCase()) {
            case "GET" -> builder.get();
            case "POST" -> builder.post(requestBody);
            case "PUT" -> builder.put(requestBody);
            case "DELETE" -> builder.delete(null);
            case "PATCH" -> builder.patch(requestBody);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        Request request = builder.build();

    	return request;
    }
    
    public String provisionTenant(String requestBodyJson, boolean createTeamFolder,  String authHeader) {
        MediaType mediaType = MediaType.parse("text/plain");
        RequestBody body = RequestBody.create(requestBodyJson, mediaType);

        String url = baseAPIUrl + "/tenants?create_team_folder=" + createTeamFolder;
        Request request = getRequestWithHeader(url, authHeader, body, "POST");
        System.out.println("Cognos API Servcie to create the tenant: " + request.url().toString());
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response: " + response);
            }
            return response.body().string();
        }catch(IOException e ) {
        	return e.getMessage();
        }
    }
    
    public void getTenant(String tenantId, String authHeader) {
    	String url = baseAPIUrl + "/tenants?tenant_idr=" + tenantId;
    	Request request = getRequestWithHeader(url, authHeader, null, "GET");
         
        String tenantDetails = "";
        System.out.println("Cognos API Servcie to create the tenant: " + request.url().toString());
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response: " + response);
            }
            tenantDetails = response.body().string();
        }catch(IOException e ) { 
        	tenantDetails = e.getMessage();
        }
        
        System.out.println("Cognos API Servcie to get the tenant detail: " + tenantDetails);
        
    }
    
 	public String getAPIKeys(String authHeader) {
 		String url = baseAPIUrl + "/security/login_api_keys";
    	Request request = getRequestWithHeader(url, authHeader, null, "GET");

    	String responseBody = "";
    	
    	 try (Response response = okHttpClient.newCall(request).execute()) {
             if (!response.isSuccessful()) {
                 throw new IOException("Unexpected response: " + response);
             }
             responseBody = response.body().string();
             JsonNode root = objectMapper.readTree(responseBody);
             JsonNode keysArray = root.get("keys");
      		// Get the first JSON object in the array
      		JsonNode firstObject = keysArray.get(0);
      		// Print the full object
      		System.out.println(firstObject.toPrettyString());
         }catch(IOException e ) { 
        	 responseBody = e.getMessage();
         }
 		return responseBody;
 	}
 	
 	public String updateTenant(String tenantId, Tenant reqeustTenantBody, String authHeader) throws JsonProcessingException {
      
		String requestBodyJson = objectMapper.writeValueAsString(reqeustTenantBody);
		MediaType mediaType = MediaType.parse("text/plain");
        RequestBody body = RequestBody.create(requestBodyJson, mediaType);

        String url = baseAPIUrl + "/tenants?tenant_id" + tenantId;
        Request request = getRequestWithHeader(url, authHeader, body, "PUT");
        System.out.println("Cognos API Servcie to udpate the tenant: " + request.url().toString());
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new CustomException("Unexpected response: " + response);
            }
            return response.body().string();
        }catch(IOException e ) {
        	throw new CustomException(CognosApiOkHttpClientService.class+ " update Tenant" + e.getMessage());

        }
    }
 	
 	public String deleteTenant(String tenantId, String authHeader) {
        String url = baseAPIUrl + "/tenants?tenant_id" + tenantId;
        Request request = getRequestWithHeader(url, authHeader, null, "DELETE");
        System.out.println("Cognos API Servcie to delete the tenant: " + request.url().toString());
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new CustomException("Unexpected response: " + response);
            }
            return response.body().string();
        }catch(IOException e ) {
        	throw new CustomException(CognosApiOkHttpClientService.class+ " Delete Tenant" + e.getMessage());
        }
    }
 	
}
