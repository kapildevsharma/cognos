package com.kapil.cognos.utility;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapil.cognos.exception.CustomException;
import com.cognos.developer.schemas.bibus._3.*;

public class TenantUtils {
	
	public static boolean checkNullOrEmptyStr(String str) {
		if(str==null) {
			return true;
		}else if(str.isEmpty()) {
			return true;
		}
		return false;
	}

	// to extract namespace value from a string like: "namespace=LDAP_ABC"
	public static String extractNamespace(String text) {
		String[] parts = text.split("=");
		if (parts.length == 2) {
			return parts[1].trim();
		}
		return text.trim(); // fallback
	}

	// to extract TenantID from "TenantID=T001" style string
	public String extractValue(String input, String key) {
		String[] lines = input.split("[\\r\\n]+"); // handle multiline
		for (String line : lines) {
			if (line.toLowerCase().contains(key.toLowerCase() + "=")) {
				String[] parts = line.split("=");
				if (parts.length == 2) {
					return parts[1].trim();
				}
			}
		}
		return null;
	}

	public static boolean containsTenantId(String text, String tenantId) {
		if (text == null)
			return false;
		return text.toLowerCase().contains("tenantid=" + tenantId.toLowerCase());
	}

	public static Map<String, Object> convertWithJackson(Object obj) {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.convertValue(obj, new TypeReference<Map<String, Object>>() {
		});
	}

	public static String getLocalDateTime() {
		// Get current LocalDateTime
		LocalDateTime currentTime = LocalDateTime.now();

		// Format the current time to a string in the desired format
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
		String timestamp = currentTime.format(formatter);

		// Print the timestamp
		System.out.println("Current Timestamp: " + timestamp);
		return timestamp;
	}

	public static Account findOrCreateUserByUserId(ContentManagerService_PortType cmService, String namespace,
			String userId, String displayName) throws Exception {

		// Construct search path: search all accounts under the namespace
		String searchPath = "CAMID(\"" + namespace + "\")//account";
		// Apply a filter: userID equals the provided userId
		String filter = "[userID]='" + userId + "'";
		PropEnum[] props = new PropEnum[] { PropEnum.defaultName, PropEnum.searchPath, PropEnum.userID,
				PropEnum.identity };
		// Query for matching account(s)
		BaseClass[] found = cmService.query(new SearchPathMultipleObject(searchPath), props, new Sort[] {},
				new QueryOptions(null, null, filter));

		if (found != null && found.length > 0 && found[0] instanceof Account) {
			return (Account) found[0];
		}

		// User not found — create
		Account account = new Account();
		account.setDefaultName(new NmtokenProp(displayName != null ? displayName : userId));
		account.setUserID(new StringProp(userId));
		account.setUserName(userId);
		account.setNamespace(new Nmtoken(namespace));
		account.setParent(new SearchPathSingleObject("/namespace[@name='" + nameSpace + "']"));
		
		 // Set the namespace
		String searchNamespacePath = "CAMID(\"" + namespace + "\")";
		SearchPathSingleObject parentPath = new SearchPathSingleObject(searchNamespacePath);
		 // Create the user in Cognos
		BaseClass[] added = cmService.add(parentPath, new BaseClass[] { account }, new AddOptions());

		if (added.length > 0 && added[0] instanceof Account) {
			return (Account) added[0];
		} else {
			throw new CustomException("Failed to create user: " + userId);
		}
	
	}

	public static Group findOrCreateGroup(ContentManagerService_PortType cm, String tenantId, String role,
			String namespace) throws Exception {
		String groupName = "group_" + tenantId + "_" + role;

		String groupPath = "CAMID(\"" + namespace + "\")//group";
		String filter = "[defaultName]='" + groupName + "'";
		PropEnum[] props = new PropEnum[] { PropEnum.defaultName, PropEnum.searchPath };

		BaseClass[] found = cm.query(new SearchPathMultipleObject(groupPath), props, new Sort[] {},
				new QueryOptions(null, null, filter));

		if (found != null && found.length > 0 && found[0] instanceof Group) {
			return (Group) found[0];
		}

		AddOptions options = new AddOptions();

		Group group = new Group();
		group.setName(groupName);
		group.setDefaultName(new BaseClass().new DefaultName(groupName));
		group.setNamespace(new BaseClass().new Namespace(namespace));
		group.setSearchPath(new SearchPathSingleObject("CAMID('::" + namespace + "')"));
		cm.add(new BaseClass[] { group }, options);
		System.out.println("Created group: " + groupName);
		return group;

	}

	public static Role createRole(ContentManagerService_PortType cm, String tenantId, String type, String namespace)
			throws Exception {
		String roleName = "role_" + tenantId + "_" + type;

		Role role = new Role();
		role.setDefaultName(roleName);
		role.setName(roleName);
		role.setNamespace(namespace);

		AddOptions options = new AddOptions();
		cm.add(new BaseClass[] { role }, options);
		System.out.println("Created role: " + roleName);
		return role;
	}

	public static void assignGroupToRole(ContentManagerService_PortType cm, Group group, Role role) throws Exception {
		role.setMembers(new BaseClass[] { group });
		UpdateOptions updateOptions = new UpdateOptions();
		cm.update(new BaseClass[] { role }, updateOptions);
		System.out.println("Assigned " + group.getName() + " to " + role.getName());
	}
	
	public static Folder getProductFolderByName(ContentManagerService_PortType cmService, String tenantName, String productName, boolean isDeactivated) throws Exception {
	    String folderName = isDeactivated ? productName + " [DEACTIVATED]" : productName;
	    String folderPath = "/content/folder[@name='" + tenantName + "']/folder[@name='" + folderName + "']";

	    BaseClass[] result = cmService.query(
	            new SearchPathMultipleObject(folderPath),
	            new PropEnum[] {
	                    PropEnum.searchPath,
	                    PropEnum.defaultName,
	                    PropEnum.description,
	                    PropEnum.policies
	            },
	            new Sort[] {},
	            new QueryOptions()
	    );

	    if (result == null || result.length == 0) {
	        throw new CustomException("Product folder '" + folderName + "' not found under tenant '" + tenantName + "'");
	    }

	    return (Folder) result[0];
	}
}
