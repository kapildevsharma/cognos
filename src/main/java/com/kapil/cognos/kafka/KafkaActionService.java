package com.kapil.cognos.kafka;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class KafkaActionService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaActionService.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper;

    public KafkaActionService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper mapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = mapper;
    }

	public void triggerTenantAction(String action, String tenantId) {
        Map<String, String> event = Map.of("action", action, "tenantId", tenantId);
        try {
            String payload = mapper.writeValueAsString(event);
            if(action.contains("get")) {
            	 kafkaTemplate.send("Tenant_Action_Response_Topic", payload);
            }else {
            	kafkaTemplate.send("Tenant_Action_Request_Topic", payload);
            }
            
        } catch (JsonProcessingException e) {
        	logger.error("Error serializing event", e);
            e.printStackTrace();
        }
    }
	
	public void sendNotificationTenant(String action, Map<String, Object>  message) {
        try {
        	String payload = mapper.writeValueAsString(message);
        	kafkaTemplate.send("Tenant_Action_Response_Topic","notification", payload);
        } catch (JsonProcessingException e) {
        	logger.error("Error serializing event", e);
            e.printStackTrace();
        }
    }
}