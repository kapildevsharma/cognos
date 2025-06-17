package com.kapil.cognos.service;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Comment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.cognos.developer.schemas.bibus._3.*;
import com.kapil.cognos.config.CognosSDKConnector;

@Component
public class CognosAuthService {

	@Value("${cognos.dispatcher.url}") // The dispatcher URL for the Cognos API
	private String dispatcherURL;

	@Value("${cognos.developer.url}") // The developer URL for the Cognos API
	private String developerURL;

	@Autowired
	private CognosSDKConnector cognosSDKConnector;

	public BiBusHeader authenticate(String username, String password, String namespace) throws Exception {
		ReportNetServiceSoapStub service = cognosSDKConnector.getReportNetService();

		// Create credential XML
		String credentialXML = "<credential>" + "<namespace>" + namespace + "</namespace>" + "<username>" + username
				+ "</username>" + "<password>" + password + "</password>" + "</credential>";
		// Login
		service.logon(credentialXML, null);

		// Extract BiBusHeader (session token)
		BiBusHeader header = (BiBusHeader) service._getCall().getResponseHeader(developerURL, "biBusHeader");
		return header;
	}

	public List<String> getUserGroups(String userCAMID, BiBusHeader header) throws RemoteException {
		ReportNetServiceSoapStub service = cognosSDKConnector.getReportNetService();
		service.setHeader(developerURL, "biBusHeader", header);

		BaseClass[] userInfo = service.query(new SearchPathMultipleObject(userCAMID),
				new PropEnum[] { PropEnum.memberOf }, new Sort[] {}, new QueryOptions());

		List<String> groupNames = new ArrayList<>();
		if (userInfo != null && userInfo.length > 0) {
			for (BaseClass group : ((User) userInfo[0]).getMemberOf()) {
				if (group instanceof Group) {
					groupNames.add(group.getDefaultName().getValue());
				}
			}
		}
		return groupNames;
	}

	public boolean isUserInGroup(BiBusHeader header, String groupSearchPath, String userSearchPath) {
		try {
			ReportNetServiceSoapStub service = cognosSDKConnector.getReportNetService();
			service.setHeader(developerURL, "biBusHeader", header);

			// Check group membership using user and group search paths
			Association association = service.getMembership(userSearchPath, groupSearchPath);
			return association != null && association.getValue() != null;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * In this method, verify authorization for provided user and group
	 * 
	 * @param userName  "tenant1user"
	 * @param groupName "Tenant1Users"
	 * @return boolean value : authorize user in group or not
	 * @throws Exception
	 */
	public boolean isVerfiyUserAndGroup(String userName, String groupName) throws Exception {
		
		ContentManagerService_PortType cmService = cognosSDKConnector.getCmService();
		
		String userPath = "/account[@defaultName='" + userName + "']";
		String groupPath = "/group[@defaultName='" + groupName + "']";

		BaseClass[] groupResult = cmService.query(new SearchPathMultipleObject(groupPath),
				new PropEnum[] { PropEnum.members }, null, null, new QueryOptions());

		if (groupResult.length == 0)
			return false;

		Group group = (Group) groupResult[0];
		SearchPathSingleObject[] members = group.getMembers();

		if (members == null)
			return false;

		for (SearchPathSingleObject member : members) {
			if (member.getValue().contains(userPath)) {
				System.out.println("User '" + userName + "' in group '" + groupName + " has authorized successfully");
				return true;
			}
		}
		System.out.println("User '" + userName + "' in group '" + groupName + " has not authorized");
		return false;
	}

	 public void getUserGroups(String namespace, String username) throws Exception {
	        // Build search path for the user
	        String userPath = "CAMID(\"" + namespace + ":" + username + "\")";
			ReportNetServiceSoapStub service = cognosSDKConnector.getReportNetService();

			
	        // Fetch memberships
	        BaseClass[] memberships = service.getMembership(
	            new SearchPathSingleObject(userPath),
	            new PropEnum[]{PropEnum.defaultName, PropEnum.searchPath},
	            new Sort[]{},
	            new QueryOptions()
	        );

	        // Print group/role memberships
	        System.out.println("Groups for user: " + username);
	        for (BaseClass obj : memberships) {
	            if (obj instanceof Group || obj instanceof Role) {
	                System.out.println("- " + obj.getDefaultName().getValue());
	            }
	        }
	    }
}
