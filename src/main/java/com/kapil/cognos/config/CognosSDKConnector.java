package com.kapil.cognos.config;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.cognos.developer.schemas.bibus._3.*;

import jakarta.annotation.PostConstruct;

@Configuration
public class CognosSDKConnector {

	@Value("${cognos.username}")
    private String namespace; // authentication provider or identity source registered with Cognos. Exp : Cognos

    @Value("${cognos.username}")
    private String username;

    @Value("${cognos.password}")
    private String password;

    @Value("${cognos.dispatcher.url}")
    private String dispatcherUrl;
    
	private final ContentManagerService_PortType cmService;
	private final CmsessionService_PortType sessionService;
	private final ReportNetService_PortType reportNetService;
	
	@PostConstruct
    public void init() throws Exception {
		URL dispatcher = new URL(dispatcherUrl);
		sessionService = new CmsessionServiceLocator().getcmsession(dispatcher); 
        cmService = new ContentManagerServiceLocator().getcontentManagerService(dispatcher);
        reportNetService =  new ReportNetServiceLocator().getreportNetService(dispatcher);

        Credential credential = new Credential();
		credential.setNamespaceID(namespace);
        credential.setUserID(username);
	    credential.setPassword(password);
		sessionService.logon(credential, null);

		CognosConnectionHelper.logon(cmService, username, password, namespace);
	}

	public CmsessionService_PortType getSessionService() {
        return sessionService;
    }

	public ContentManagerService_PortType getCmService() {
		return cmService;
	}

	public ReportNetService_PortType getReportNetService() {
		return reportNetService;
	}
	   
}
