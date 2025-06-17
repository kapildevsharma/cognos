package com.kapil.cognos.customFilter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.kapil.cognos.service.CognosAuthService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class UserValidationFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(UserValidationFilter.class);

	@Value("${cognos.api.url}") // The base URL for the Cognos API
	private String cognosApiUrl;
	
	@Autowired
    CognosAuthService cognosAuthService;
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		
		logger.info("In Filter, Request URI : " + request.getRequestURI());

		String username = request.getHeader("X-Cognos-User");
        String password = request.getHeader("X-Cognos-Password");
        String namespace = "LDAP";
        logger.info("CognosAuthenticationFilter : username, " + username+ " namespace: "+ namespace);
        
        try {
            BiBusHeader session = cognosAuthService.authenticate(username, password, namespace);

            boolean isAuthorized = cognosAuthService.isUserInGroup(session,
                    "CAMID(\"/namespace::LDAP/group::Administrators\")",
                    "CAMID(\"/namespace::LDAP/user::" + username + "\")");

            isAuthorized = cognosAuthService.isVerfiyUserAndGroup(username,"Administrators");
            
            if (!isAuthorized) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("User '" + username + "' not authorized");
                return;
            }
            
            var header = cognosAuthService.authenticate(username, password, namespace);
            var camId = "CAMID(\\\"/namespace::" + namespace + "/user::" + username + "\\\")";
            var groups = cognosAuthService.getUserGroups(camId, header);
            
            var roles = CognosRoleMapper.mapGroupsToRoles(groups);
            var authorities = roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());

            var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
            
            // Proceed if authenticated and authorized
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
        	response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        	response.getWriter().write("Authentication failed: " + ex.getMessage());
        }
	}

	
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
	    String path = request.getRequestURI();
	    return path.startsWith("/swagger-ui")
	        || path.startsWith("/v3/api-docs")
	        || path.startsWith("/swagger-resources")
	        || path.startsWith("/webjars")
	        || path.startsWith("/actuator");
	}
	
}