package com.kapil.cognos.kafka;

import java.util.Set;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapil.cognos.dto.TenantActionEvent;
import com.kapil.cognos.dto.TenantRequest;
import com.kapil.cognos.service.TenantService;
import com.kapil.cognos.utility.TenantUtils;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;


@Component
public class CognosTenantKafkaListener {
	
	@Autowired
	public TenantService tenantService;
	
	@Autowired
	private ObjectMapper mapper;
	
    @KafkaListener(topics = "Tenant_Action_Response_Topic", groupId = "utp_group")
    public void listen(@Payload String message, @Header(KafkaHeaders.RECEIVED_KEY) String key, 
    		@Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
    	
        System.out.println("Received message: " + message + " with key: " + key);
        try {
        	
        	TenantActionEvent tenantActionEvent = deserializeAndValidate(message);
            System.out.println("Received event from topic "+topic+", "+ tenantActionEvent);

            JSONObject json = new JSONObject(message);
            String event = json.getString("event");
            String authHeader = json.getString("authHeader");
            String tenantName = json.has("tenantName")? json.getString("tenantName"):"";
            String tenantId = json.has("tenantId")? json.getString("tenantId"):"";
            String productName = json.has("productName")? json.getString("productName"):"";
            
            switch (event) {
                case "createTenant":
                	TenantRequest tenantRequest = mapper.readValue(json.getString("tenantRequest"), TenantRequest.class);
                	tenantService.provisionTenant(tenantRequest, authHeader);
                    break;

                case "assignProduct":
                    String product = json.getString("product");
                    tenantService.assignProductToTenant(tenantName, product, authHeader);
                    break;
                
                case "deactivateProduct":
                    tenantService.deactiveProductTenant(tenantId, productName, authHeader);
                    break;

                case "reactivateProduct":
                    tenantService.deactiveProductTenant(tenantId, productName, authHeader);
                    break;
                
                case "clone":
                    String sourceTenantName = json.has("sourceTenantName")? json.getString("sourceTenantName"):"";
                    String targetTenantName = json.has("targetTenantName")? json.getString("targetTenantName"):"";
                    if(TenantUtils.checkNullOrEmptyStr(sourceTenantName) || 
                    		TenantUtils.checkNullOrEmptyStr(targetTenantName))
                    tenantService.cloneTenant(sourceTenantName, targetTenantName, authHeader);
                    break;

                default:
                    System.out.println("Unhandled event type: " + event +". Please ddclare valid event");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private TenantActionEvent deserializeAndValidate(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        TenantActionEvent event = mapper.readValue(json, TenantActionEvent.class);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<TenantActionEvent>> violations = validator.validate(event);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        return event;
    }
}