
package com.kapil.cognos.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kapil.cognos.kafka.KafkaActionService;
import com.kapil.cognos.service.TenantService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Tag(name = "Cognos Security Management", description = "Operations on cognos security.")
@RestController
@RequestMapping("/security")
public class CognosSecurityController {

    private static final Logger logger = LoggerFactory.getLogger(CognosSecurityController.class);

    @Autowired
    private TenantService tenantService;
    
    @Autowired
    private KafkaActionService kafkaActionService;


    @Operation(summary = "Get all api keys details")
    @GetMapping("/login_api_keys")
    public ResponseEntity<String> getAPIKeys(@RequestHeader("IBM-BA-Authorization") String authHeader) {
    	// trigger to Kafka
    //    kafkaActionService.triggerTenantAction("get All API Keys", "API");
        String responseBody = tenantService.getAPIKeys(authHeader);
        logger.info("getAPIKeys : "+ responseBody);
        return ResponseEntity.ok("Get API Keys details"+ responseBody);
    }
}
