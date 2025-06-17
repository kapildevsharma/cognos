package com.kapil.cognos.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapil.cognos.dto.TenantStatusUpdate;

@Service
public class TenantStatusProducer {
	private static final Logger logger = LoggerFactory.getLogger(KafkaActionService.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic.status-updates}")
    private String statusTopic;

    public TenantStatusProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }
    
    public void sendStatusUpdate(TenantStatusUpdate statusUpdate) {
        try {
            String json = objectMapper.writeValueAsString(statusUpdate);
            kafkaTemplate.send(statusTopic, statusUpdate.getTenantId(), json);
            logger.info("Sent status update for tenant "+statusUpdate.getTenantId()+": "+ json);
        } catch (JsonProcessingException e) {
        	logger.error("Failed to serialize status update"+ e.getMessage());
        }
    }
}
