package com.kapil.cognos.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;

@Controller
public class CustomErrorController implements ErrorController {

    private final DefaultErrorAttributes errorAttributes = new DefaultErrorAttributes();

    @RequestMapping("/error")
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
        ServletWebRequest webRequest = new ServletWebRequest(request);
        Map<String, Object> errorAttributesMap =
                errorAttributes.getErrorAttributes(webRequest, ErrorAttributeOptions.defaults());

        // Customize message if 404
        if ((int) errorAttributesMap.get("status") == 404) {
            errorAttributesMap.put("message", "Custom 404 - This URL is not valid.");
        }
        return new ResponseEntity<>(errorAttributesMap, HttpStatus.valueOf((int) errorAttributesMap.get("status")));
    }
}
