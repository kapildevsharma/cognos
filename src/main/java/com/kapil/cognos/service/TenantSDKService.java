package com.kapil.cognos.service;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;

import com.cognos.developer.schemas.bibus._3.*;
import com.kapil.cognos.config.CognosSDKConnector;
import com.kapil.cognos.config.ContentManagerService_PortType;
import com.kapil.cognos.dto.TenantRequest;
import com.kapil.cognos.exception.CustomException;
import com.kapil.cognos.model.Status;
import com.kapil.cognos.model.Tenant;
import com.kapil.cognos.utility.CognosRoleMapper;
import com.kapil.cognos.utility.TenantUtils;

public class TenantSDKService {

	@Autowired
	private CognosSDKConnector cognosSDKConnector;

	public Map<String, Object> provisionTenant(TenantRequest tenantRequest) throws Exception {
		String tenantName = tenantRequest.getTenantName();
		String namespace = tenantRequest.getNameSpace();
		
		ContentManagerService_PortType cmService = cognosSDKConnector.getCmService();

		// 1. Define the tenant
		Tenant tenant = new Tenant();
		tenant.setDefaultName(new Nmtoken(tenantName));
		tenant.setDisplayName(new MultilingualString(new LocalizedString[] { new LocalizedString("en", tenantName) }));
		tenant.setNamespace(new Nmtoken(namespace));
		tenant.setTenantId(new Nmtoken(tenantRequest.getTenantId()));

		// 2. Add tenant
		BaseClass[] created = cmService.add(new SearchPathSingleObject("/tenants"), new BaseClass[] { targetCreation });

		Tenant createdTenant = (Tenant) created[0];
		// 3. Create Tenant Components
		createTenantComponents(cmService , createdTenant, tenantRequest.getUserId(), namespace);

		// 4. Get SearchPath of the created tenant
		String tenantPath = created[0].getSearchPath().getValue();

		// 5. Enable tenant after creation
        tenant.setStatus(new Nmtoken("active"));
        cmService.update(new BaseClass[]{tenant}, new UpdateOptions());
        System.out.println("Tenant '" + tenantName + "' has been enabled.");
        
		// 6. Fetch full tenant details
		Map<String, Object> response = new HashMap<>();
		response = getTenantDetails(created[0].getDefaultName().getValue());
		response.put("result", true);
		String msg = "Tenant'" + tenantName + "' is created successfully";
		response.put("causeMesage", msg);
		System.out.println(msg);

	//	logoff();
		return response;
	}
	

	public void createTenantComponents(ContentManagerService_PortType cmService, Tenant tenant, String userId, String nameSpace) throws Exception {
		String tenantName = tenant.created[0].getDefaultName().getValue();
		// 1. Create Tenant Content Folder
		Folder folder = new Folder();
		folder.setDefaultName(new BaseClass().new DefaultName(tenantName));
		folder.setParent(new SearchPathSingleObject("/content"));
		folder.setSearchPath(new SearchPathSingleObject("/content/folder[@name='" + tenantName + "']"));
		cmService.add(new BaseClass[] { folder }, new AddOptions());

		// 2. Create group
		Group groupAdmin = TenantUtils.findOrCreateGroup(cmService, tenantName, "admins", nameSpace);
		Group groupUsers = TenantUtils.findOrCreateGroup(cmService, tenantName, "users", nameSpace);

		// 3: Create roles
		Role roleAuthor = TenantUtils.createRole(cmService, tenantName, "author", nameSpace);
		Role roleConsumer = TenantUtils.createRole(cmService, tenantName, "consumer", nameSpace);

		// 4: Assign groups to roles
		TenantUtils.assignGroupToRole(cmService, groupAdmin, roleAuthor);
		TenantUtils.assignGroupToRole(cmService, groupUsers, roleConsumer);

		// 5. Check User and if user not exists then create user "role_" + tenantId + "_" + type;
		Account user = TenantUtils.findOrCreateUserByUserId(cmService, nameSpace, userId, "tenant_admin" + userId);

		// 6. Add user to group
		groupAdmin.setMembers(new SearchPathSingleObject[] { new SearchPathSingleObject("/account[@defaultName='" + userId + "']") });
		groupUsers.setMembers(new SearchPathSingleObject[] { new SearchPathSingleObject("/account[@defaultName='" + userId + "']") });

		cmService.update(new BaseClass[] { groupAdmin, groupUsers }, new UpdateOptions());

		// 7. Create Permision
		Permission adminPermission = new Permission();
		adminPermission.setAccess(new AccessRight[] { AccessRight.fullControl });
		adminPermission.setPrincipal(groupAdmin);
		adminPermission.setName(new Nmtoken(tenantName + "_Admin"));

		Permission userPermission = new Permission();
		userPermission.setAccess(new AccessEnum[] { AccessEnum.read, AccessEnum.traverse });
		userPermission.setPrincipal(groupUsers);
		userPermission.setName(new Nmtoken(tenantName + "_User"));

		// 8. Set Policy
		Policy adminPolicy = new Policy();
		adminPolicy.setName(tenantName + "_Admins");
		adminPolicy.setPrincipal(groupAdmin);
		adminPolicy.setAccess(new AccessRight[] { AccessRight.read, AccessRight.write, AccessRight.traverse,
				AccessRight.execute, AccessRight.fullControl });
		adminPolicy.setPermissions(new PermissionEnum[] { adminPermission });
		adminPolicy.setMember(new BaseClass[] { user });
		adminPolicy.setAppliesTo(new BaseClass[] { tenant });

		Policy userPolicy = new Policy();
		userPolicy.setName(tenantName + "_Users");
		userPolicy.setPrincipal(groupUsers);
		userPolicy.setAccess(new AccessRightEnum[] { AccessRightEnum.read, AccessRightEnum.traverse });
		userPolicy.setPermissions(new PermissionEnum[] { userPermission });
		userPolicy.setMember(new BaseClass[] { user });
		userPolicy.setAppliesTo(new BaseClass[] { tenant });

		// 9. Apply ACL (Access Control List)
		SecurityPolicy security = new SecurityPolicy();
		security.setPolicies(new Policy[] { adminPolicy, userPolicy });
		folder.setSecurityPolicy(security);
		// folder.setPermissions(new Permission[]{adminPermission, userPermission});
		cmService.update(new BaseClass[] { folder }, new UpdateOptions());
		System.out.println("Security policy applied to folder: " + folder);
		System.out.println("All component of Tenant '" + tenantName + "'is created successfully here");
	}

	/**
	 * Assign product to newly created Tenant
	 * 
	 * @param tenantId
	 * @param productName
	 * @return
	 */
	public Map<String, Object> assignProductToTenant(String tenantId, String productName) {
		Map<String, Object> response = new HashMap<>();
		try {
		    // Step 1: Get tenant details
		    String tenantName = getTenantNameByTenantId(tenantId);
		    String namespace = getNamespaceForTenant(tenantName);

		    // Step 2: Validate product exists in the available applications
		    List<Map<String, String>> availableApplications = listAvailableApplications();
		    Optional<Map<String, String>> matchedProduct = availableApplications.stream()
		        .filter(map -> productName.equals(map.get("name"))).findFirst();

		    if (matchedProduct.isEmpty()) {
		        String message = "Product " + productName + " doesn't exist. Please provide valid product name.";
		        System.out.println(message);
		        response.put("result", false);
		        response.put("causeMesage", message);
		        return response;
		    }

		    // Step 3: Initialize Cognos SDK Services
		    ContentManagerService_PortType cmService = cognosSDKConnector.getCmService();
		    ReportNetServiceSoapStub reportNetService = cognosSDKConnector.getReportNetService();

		    // Step 4: Get the product folder path
		    SearchPathMultipleObject productPath = new SearchPathMultipleObject("/content/folder[@name='" + productName + "']");
		    BaseClass[] products = cmService.query(productPath, new PropEnum[]{PropEnum.searchPath}, new Sort[]{}, new QueryOptions());

		    if (products == null || products.length == 0) {
		        throw new CustomException("Product folder not found in Cognos Content Store.");
		    }

		    String productFolderPath = products[0].getSearchPath().getValue();

		    // Step 5: Get the tenant folder path
		    String tenantFolderPath = "/content/folder[@name='" + tenantName + "']";
		    BaseClass[] tenantContent = cmService.query(
		        new SearchPathMultipleObject(tenantFolderPath),
		        new PropEnum[]{PropEnum.searchPath, PropEnum.defaultName},
		        new Sort[]{}, new QueryOptions());

		    if (tenantContent.length == 0 || !(tenantContent[0] instanceof Folder)) {
		        throw new CustomException("Tenant folder not found for tenant: " + tenantName);
		    }
		    Folder tenantFolder = (Folder) tenantContent[0];

		    // Step 6: Copy the product folder under the tenant folder
		    CopyOptions copyOptions = new CopyOptions();
		    copyOptions.setIncludePermissions(true);

		    reportNetService.copy(
		        new SearchPathMultipleObject[]{new SearchPathMultipleObject(productFolderPath)},
		        new SearchPathSingleObject(tenantFolder.getSearchPath().getValue()),
		        copyOptions
		    );

		    // Step 7: Create and apply permission policy
		    Permission permission = new Permission();
		    permission.setName(new Nmtoken(tenantName + "_Admins"));
		    permission.setAccess(new AccessEnum[]{AccessEnum.read, AccessEnum.traverse, AccessEnum.execute});

		    Policy policy = new Policy();
		    policy.setName(productName + "_Admins");
		    policy.setPrincipal(new Principal(tenantName + "_Admins", namespace));
		    policy.setSecuredObject(new SearchPathSingleObject(productFolderPath));
		    policy.setPermissions(new Permission[]{permission});

		    reportNetService.update(new BaseClass[]{policy}, new UpdateOptions());

		    // Step 8: Add metadata annotation to the product folder
		    Annotation annotation = new Annotation();
		    annotation.setName(new Nmtoken(productName));
		    annotation.setValue(new MultilingualString(new LocalizedString[]{
		        new LocalizedString("en", productName + " Dashboard v2.0")
		    }));

		    BaseClass[] productFolderArr = cmService.query(
		        new SearchPathMultipleObject(productFolderPath),
		        new PropEnum[]{PropEnum.annotations},
		        new Sort[]{}, new QueryOptions());

		    if (productFolderArr.length > 0) {
		        BaseClass folder = productFolderArr[0];
		        folder.setAnnotations(new Annotation[]{annotation});
		        reportNetService.update(new BaseClass[]{folder});
		    }

		    // Step 9: Create a subfolder under tenant folder for the product
		    Folder productFolder = new Folder();
		    productFolder.setDefaultName(productName);
		    productFolder.setParent(new SearchPathSingleObject(tenantFolder.getSearchPath().getValue()));
		    cmService.add(new BaseClass[]{productFolder}, new AddOptions());

		    // Step 10: Create group for product-specific users
		    Group productUsersGroup = new Group();
		    productUsersGroup.setDefaultName(productName + "_Users");
		    productUsersGroup.setNamespace(namespace);
		    cmService.add(new BaseClass[]{productUsersGroup}, new AddOptions());

		    // Step 11: Apply policy to the newly created folder
		    productFolder.setPolicies(new Policy[]{policy});
		    cmService.update(new BaseClass[]{productFolder}, new UpdateOptions());

		    // Step 12: Final response
		    response = getTenantDetails(tenantName);
		    String message = "Product '" + productName + "' is assigned successfully to tenant '" + tenantName + "'";
		    System.out.println(message);
		    response.put("result", true);
		    response.put("causeMesage", message);
		    return response;
		} catch (Exception e) {
			response.put("result", false);
			String errorMessage = "Error assigning product to tenant: " + e.getMessage();
			System.out.println(errorMessage);
			throw new CustomException(errorMessage);
		}
	}

	/**
	 * Deactivate tenant product
	 * 
	 * @param tenantId
	 * @param productName
	 * @return
	 */
	public Map<String, Object> deactiveProductTenant(String tenantId, String productName) {
		Map<String, Object> response = new HashMap<>();
		try {

			String tenantName = getTenantNameByTenantId(tenantId);
			String namespace = getNamespaceForTenant(tenantName);

			String productFlag = getTenantProductStatus(productName, tenantId);
			if (Status.PRODUCT_DEACTIVATE.name().equals(productFlag)) {
				String msg = "Product '" + productName + "' is already deactivated for tenant '" + tenantName + "'";
				System.out.println(msg);
				response.put("result", false);
				response.put("causeMesage", msg);
				return response;
			} else if ("NotFound".equals(productFlag)) {
				String msg = "Product '" + productName + "' doesn't exist. Please provide valid product name.";
				System.out.println(msg);
				response.put("result", false);
				response.put("causeMesage", msg);
				return response;
			}

			ContentManagerService_PortType cmService = cognosSDKConnector.getCmService();

			// Query the product folder under tenant
			Folder productFolder = TenantUtils.getProductFolderByName(cmService, tenantName, productName, true);

			// Backup old policies and remove all non-admin policies
			Policy[] existingPolicies = productFolder.getPolicies();
			List<Policy> retainedPolicies = new ArrayList<>();
			for (Policy policy : existingPolicies) {
				if (policy.getPrincipal() != null
						&& (tenantName + "_admins").equalsIgnoreCase(policy.getPrincipal().getName())) {
					retainedPolicies.add(policy);
				}
			}
			productFolder.setPolicies(retainedPolicies.toArray(new Policy[0]));

			// 3. Mark folder as deactivated
			String newName = productFolder.getDefaultName();
			if (!newName.contains("[DEACTIVATED]")) {
				newName += " [DEACTIVATED]";
			}
			productFolder.setDefaultName(newName);
			productFolder.setDescription("Deactivated on " + new Date());

			// 4. Update the folder to move as Archive Folder
			cmService.update(new BaseClass[] { productFolder }, new UpdateOptions());

			// 5. Save changes
			response = getTenantDetails(tenantName);
			response.put("result", true);
			String msg = "Product'" + productName + "' is deactivated successfully from tenant'" + tenantName + "'";
			response.put("causeMesage", msg);
			System.out.println(msg);

		} catch (Exception e) {
			String msg = "Error deactivating product for tenant: " + e.getMessage();
			System.out.println(msg);
			throw new CustomException(msg);
		}
		return response;
	}

	/**
	 * Reactivate tenant product
	 * 
	 * @param tenantId
	 * @param productName
	 * @return
	 */
	public Map<String, Object> reactiveProductTenant(String tenantId, String productName) {
		Map<String, Object> response = new HashMap<>();
		try {

			String tenantName = getTenantNameByTenantId(tenantId);
			String namespace = getNamespaceForTenant(tenantName);

			String productFlag = getTenantProductStatus(productName, tenantId);
			if (Status.PRODUCT_ACTIVATE.name().equals(productFlag)) {
				String msg = "Product '" + productName + "' is already reactivated for tenant '" + tenantName + "'";
				System.out.println(msg);
				response.put("result", false);
				response.put("causeMesage", msg);
				return response;
			} else if ("NotFound".equals(productFlag)) {
				String msg = "Product '" + productName + "' doesn't exist. Please provide valid productName";
				System.out.println(msg);
				response.put("result", false);
				response.put("causeMesage", msg);
				return response;
			}

			ContentManagerService_PortType cmService = cognosSDKConnector.getCmService();

			// Query the product folder
			Folder productFolder = TenantUtils.getProductFolderByName(cmService, tenantName, productName, true);

			// Rename folder to remove "[DEACTIVATED]" suffix
			String currentName = productFolder.getDefaultName();
			String updatedName = currentName.replace(" [DEACTIVATED]", "").trim();
			productFolder.setDefaultName(updatedName);
			productFolder.setDescription("Reactivated on " + new Date());

			// Move folder back to active path
			productFolder.setParent(new SearchPathSingleObject("/content/" + tenantId));

			// Backup old policies and remove non-admin access and Revoke Permissions
			Policy[] existingPolicies = productFolder.getPolicies();
			List<Policy> retainedPolicies = new ArrayList<>();
			for (Policy policy : existingPolicies) {
				if (policy.getPrincipal() != null
						&& (tenantName + "_admins").equalsIgnoreCase(policy.getPrincipal().getName())) {
					retainedPolicies.add(policy);
				}
			}

			// Apply permission for tenant admin group
			Permission permission = new Permission();
			permission.setName(new Nmtoken(tenantName + "_Admins"));
			permission.setAccess(new AccessEnum[] { AccessEnum.read, AccessEnum.traverse, AccessEnum.execute });

			Policy userPolicy = new Policy();
			userPolicy.setName(productName + "__Admins");
			userPolicy.setPrincipal(new Principal(tenantName + "_admins", namespace));
			userPolicy.setSecuredObject(new SearchPathSingleObject(productFolderPath));
			userPolicy.setPermissions(new Permission[] { permission });

			retainedPolicies.add(userPolicy);
			productFolder.setPolicies(new Policy[] { retainedPolicies.toArray(new Policy[0]) });

			// Update folder
			cmService.update(new BaseClass[] { productFolder }, new UpdateOptions());

			response = getTenantDetails(tenantName);
			response.put("result", true);
			String msg = "Product '" + productName + "' is reactivated successfully for tenant '" + tenantName + "'";
			response.put("causeMesage", msg);
			System.out.println(mesg);

		} catch (Exception e) {
			System.out.println("Error during reactivation: " + e.getMessage());
			throw new CustomException("Error reactivating product for tenant: " + e.getMessage());
		}

		return response;
	}

	/**
	 * LogOff logged user session.
	 * 
	 * @throws Exception
	 */
	public void logoff() throws Exception {
		cognos.getSessionService().sessionService.logoff();
		System.out.println("Logged off from Cognos.");
	}

	public void getUserInfoWithGroupMemberships() {
		SecurityService_PortType secService = new SecurityServiceLocator().getsecurityService(new URL(dispatcherUrl));
		UserInfo userInfo = secService.getUserInfo();
		System.out.println("User: " + userInfo.getUserID());
		System.out.println("CAMID: " + userInfo.getUserIdentity().getObjectValue());
		BaseClass[] userGroups = secService.getGroupsForUser(userInfo.getUserIdentity(),
				new SearchPathMultipleObject("/group"), new QueryOptions());

		Arrays.stream(userGroups).map(obj -> ((Base) Obj).getDefaultName().getValue())
				.forEach(group -> System.out.println("Member of Group: " + group));

	}

	/**
	 * Get all available applications
	 * 
	 * @return
	 */
	public List<Map<String, String>> listAvailableApplications() {
		List<Map<String, String>> apps = new ArrayList();
		try {
			ReportNetServiceSoapStub service = cognosSDKConnector.getReportNetService();

			SearchPathMultipleObject searchPath = new SearchPathMultipleObject("/content/Applications//*");
			PropEnum[] props = new PropEnum[] { PropEnum.searchPath, PropEnum.defaultName, PropEnum.objectClass };
			Sort[] sort = new Sort[] {};
			QueryOptions options = new QueryOptions();

			BaseClass[] results = service.query(searchPath, props, sort, options);

			for (BaseClass item : results) {
				// if (item instanceof Folder || item instanceof Report || item instanceof
				// Dashboard) {
				if (item instanceof Folder) {
					Map<String, String> entry = new HashMap<>();
					entry.put("name", item.getDefaultName().getValue());
					entry.put("path", item.getSearchPath().getValue());
					entry.put("type", item.getClass().getSimpleName());
					apps.add(entry);
				}
			}

		} catch (Exception e) {
			throw new CustomException(TenantService.class + " error in getting application " + e.getMessage());
		}
		return apps;
	}

	public Map<String, Object> cloneTenant(Tenant sourceTenant, Tenant targetTenant, String targetTenantName, List<String> productList) {
		ContentManagerService_PortType cmService = cognosSDKConnector.getCmService();
		
		// Retrieve the Source Tenant
		String sourceTenantName = sourceTenant.getDefaultName().getValue();
		BaseClass[] sourceTenant = cmService.query(
			    new SearchPathMultipleObject("/tenants/" + sourceTenantName),
			    new PropEnum[] { PropEnum.defaultName, PropEnum.searchPath, PropEnum.policies },
			    new Sort[] {},
			    new QueryOptions()
			);
		Tenant sourceCreated = (Tenant) sourceTenant[0];
		
		String displayName = targetTenantName;
		
		// Define the tenant for targetCreation
		Tenant targetCreation = new Tenant();
		targetCreation.setName(displayName);
		targetCreation.setDefaultName(new Nmtoken(targetTenant.getDefaultName().getValue()));
		targetCreation.setDisplayName(new MultilingualString(new LocalizedString[] { new LocalizedString("en", displayName) }));
		targetCreation.setNamespace(new Nmtoken(targetTenant.getNamespace()));
		
		// Add target tenant in Cognos 
		BaseClass[] results = cmService.add(new SearchPathSingleObject("/tenants"), new BaseClass[] { targetCreation });
		Tenant targetCreated = (Tenant) results[0];
		
		// Clone groups and roles from source to target tenant
		SearchPathMultipleObject sourceGroupsPath = new SearchPathMultipleObject(
				"/content/tenants/" + sourceTenantName + "/directory/*");
		BaseClass[] groupsAndRoles = cmService.query(sourceGroupsPath, null, null, new Sort[] {});

		List<BaseClass> newGroups = new ArrayList<>();
		for (BaseClass base : groupsAndRoles) {
			if (base instanceof Group || base instanceof Role) {
				Group group = (Group) base;
				Group newGroup = new Group();
				newGroup.setName(group.getName().replace(sourceTenantName, targetTenantName));
				newGroup.setDefaultName(group.getDefaultName());
				newGroup.setDisplayName(group.getDisplayName());
				newGroup.setParent(new SearchPathSingleObject("/content/tenants/" + targetTenantName + "/directory"));

				newGroups.add(newGroup);
			}
		}

		if (!newGroups.isEmpty()) {
			cmService.create(newGroups.toArray(new BaseClass[0]));
		}
		
		// Create Target Folder
		Folder folder = new Folder();
		folder.setDefaultName(new BaseClass().new DefaultName(targetTenant.getTenantId()));
		folder.setParent(new SearchPathSingleObject("/content"));
		folder.setSearchPath(new SearchPathSingleObject("/content/folder[@name='" + tenantName + "']"));
		BaseClass[] addedFolders = cmService.add(new BaseClass[] { folder }, new AddOptions());
		Folder newFolder = (Folder) addedFolders[0];
		
		// Clone folder structure & Content
		BaseClass[] sourceObjects  = cmService.query(
				new SearchPathMultipleObject("/content/folder[@name='" + sourceTenantName + "']"),
				new PropEnum[] { PropEnum.defaultName, PropEnum.searchPath }, new Sort[] {}, new QueryOptions());

		for (BaseClass obj : sourceObjects) {
		    BaseClass clonedObject = cloneContent(obj);
		    clonedObject.setSearchPath(new SearchPathSingleObject("/content/folder[@name='"+targetTenant.getTenantId()+"']"));
		    cmService.add(new BaseClass[]{clonedObject}, options);
		}
		
		BaseClass[] copiedObjects = cmService.copy(
				new SearchPathMultipleObject[] {
						new SearchPathMultipleObject("/content/folder[@name='" + sourceTenantName + "']") },
				new SearchPathSingleObject("/content/folder"), // destination
				new String[] { targetTenant.getDefaultName().getValue() }, // new names
				true, // recursive
				false // don't modify search paths
		);
		 applyAdminPolicy(targetTenantName, "/content/folder[@name='" + targetTenantName + "']");
		
		// Clone product-specific folders & Content
	    for (String folder : productList) {
	        String sourcePath = "/content/tenants/" + sourceTenantName + "/" + folder;
	        String targetPath = "/content/tenants/" + targetTenantName + "/" + folder;

	        cmService.copy(
	            new SearchPathSingleObject[]{new SearchPathSingleObject(sourcePath)},
	            new SearchPathSingleObject("/content/tenants/" + targetTenantName),
	            false
	        );

	        // Optionally apply updated permissions to cloned folder
	        applyAdminPolicy(targetTenantName, targetPath);
	    }
		
		response = getTenantDetails(sourceTenantName);
		System.out.println("Cloning complete.");
		return response;
	}
	
	private void applyAdminPolicy(String tenantName, String folderPath) throws Exception {
	    // Fetch the folder
	    SearchPathSingleObject path = new SearchPathSingleObject(folderPath);
	    BaseClass[] result = cmService.query(path, null, null, new Sort[] {});
	    if (result.length == 0 || !(result[0] instanceof Folder)) return;

	    Folder folder = (Folder) result[0];
	    // Create policy
	    Permission permission = new Permission();
	    permission.setName(new Nmtoken(tenantName + "_Admin"));
	    permission.setAccess(new AccessEnum[]{AccessEnum.read, AccessEnum.traverse, AccessEnum.execute});

	    Policy policy = new Policy();
	    policy.setName(tenantName + "_Admins");
	    policy.setPrincipal(new Principal(tenantName + "_admins", namespace));
	    policy.setPermissions(new Permission[]{permission});
	    policy.setSecuredObject(new SearchPathSingleObject(folderPath));

	    folder.setPolicies(new Policy[]{policy});
	    cmService.update(new BaseClass[]{folder});
	
	}
	
	public void moveTenant(String tenantId, String newPath) {
		Folder tenantFolder = cognosSdkService.findFolder("/content/ukg/" + tenantId);
		Folder newParent = cognosSdkService.findOrCreateFolder(newPath);
		tenantFolder.setParent(new SearchPathSingleObject(newParent.getSearchPath().getValue()));
		cmService.update(new BaseClass[] { tenantFolder }, new UpdateOptions());
		auditService.log("MOVE", tenantId, "Moved to " + newPath);
	}

	/**
	 * Retrieve all tenant list
	 * 
	 * @throws Exception
	 */
	public void listTenants() throws Exception {
		ContentManagerService_PortType cmService = cognosSDKConnector.getCmService();
		SearchPathMultipleObject searchPath = new SearchPathMultipleObject();
		searchPath.setValue("CAMID(*)");
		BaseClass[] results = cmService.query(searchPath, null, new Sort[] {}, new QueryOptions());

		Arrays.stream(results).filter(obj -> obj instanceof Account)
				.map(obj -> ((Account) obj).getDefaultName().getValue())
				.forEach(name -> System.out.println("Tenant Name : " + name));

	}

	/**
	 * Retrieve tenant details
	 * 
	 * @throws Exception
	 */
	public void getTenantDetails(String tenantName) throws Exception {
		String tenantId = getTenantIdFromFolder(tenantName);
		String tenantNamespace = getNamespaceForTenant(tenantId);

		PropEnum[] props = { PropEnum.searchPath, PropEnum.defaultName, PropEnum.owner, PropEnum.description,
				PropEnum.creationTime, PropEnum.modifiedTime };

		BaseClass[] tenantObjects = reportNetService.query(
				new SearchPathMultipleObject("/content/folder[@name='" + tenantName + "']"), props, new Sort[] {},
				new QueryOptions());

		Map<String, Object> response = new HashMap<>();
		if (tenantObjects.length > 0 && tenantObjects[0] instanceof Folder) {
			Folder tenantFolder = (Folder) tenantObjects[0];
			response.put("name", tenantFolder.getDefaultName().getValue());
			response.put("displayName", tenantFolder.getDisplayName().getLocalizedString()[0].getValue());
			response.put("namespace", tenantNamespace);
			response.put("searchPath", tenantFolder.getSearchPath().getValue()); // Full search path to the object
			response.put("created", tenantFolder.getCreationTime().toString());
			response.put("modified", tenantFolder.getModifiedTime().getValue());
			response.put("owner", tenantFolder.getOwner().getValue());

			String groupName = tenantId + "_Users";
			response.put("users", getUsersFromGroup(groupName));
			response.put("productWithStatus",getProductsForTenant(tenantId));
		}

		return response;
	}

	public String getNamespaceForTenant(String tenantId) throws Exception {
		ReportNetServiceSoapStub reportNetService = cognosSDKConnector.getReportNetService();
		// Step 1: Try to find nameSpace via group
		String groupName = tenantId + "_Users";
		String groupPath = "/namespace//group[defaultName='" + groupName + "']";

		BaseClass[] groupResult = reportNetService.query(new SearchPathMultipleObject(groupPath),
				new PropEnum[] { PropEnum.defaultName, PropEnum.namespace }, new Sort[] {}, new QueryOptions());

		if (groupResult.length > 0 && groupResult[0] instanceof Group) {
			Group group = (Group) groupResult[0];
			String namespace = group.getNamespace().getValue();
			System.out.println("Found namespace via group: " + namespace);
			return namespace;
		}

		// Step 2: Try folder description or screenTip
		String folderPath = "/content/folder[@name='" + tenantId + "']";

		BaseClass[] folderResult = reportNetService.query(new SearchPathMultipleObject(folderPath),
				new PropEnum[] { PropEnum.description, PropEnum.screenTip }, new Sort[] {}, new QueryOptions());

		if (folderResult.length > 0 && folderResult[0] instanceof Folder) {
			Folder folder = (Folder) folderResult[0];

			// Try screenTip first
			String screenTip = folder.getScreenTip() != null ? folder.getScreenTip().getValue() : null;
			if (screenTip != null && screenTip.toLowerCase().contains("namespace")) {
				System.out.println("Found namespace in screenTip: " + screenTip);
				return TenantUtils.extractNamespace(screenTip);
			}

			// Try description
			String description = folder.getDescription() != null ? folder.getDescription().getValue() : null;
			if (description != null && description.toLowerCase().contains("namespace")) {
				System.out.println("Found namespace in description: " + description);
				return TenantUtils.extractNamespace(description);
			}
		}

		// Step 3: (Optional) fallback to default or throw
		throw new Exception("Namespace not found for tenant: " + tenantId);
	}

	public String getTenantIdFromFolder(String folderName) throws Exception {
		String searchPath = "/content/folder[@name='" + folderName + "']";
		ContentManagerService_PortType contentManagerService = cognosSDKConnector.getCmService();

		BaseClass[] result = contentManagerService.query(new SearchPathMultipleObject(searchPath), new PropEnum[] {
				PropEnum.description, PropEnum.screenTip, PropEnum.customAttributes, PropEnum.defaultName },
				new Sort[] {}, new QueryOptions());

		if (result.length == 0 || !(result[0] instanceof Folder)) {
			throw new Exception("Folder not found: " + folderName);
		}

		Folder folder = (Folder) result[0];

		// Check description
		if (folder.getDescription() != null && folder.getDescription().getValue() != null) {
			String desc = folder.getDescription().getValue();
			if (desc.toLowerCase().contains("tenantid=")) {
				return TenantUtils.extractValue(desc, "tenantid");
			}
		}

		// Check screenTip
		if (folder.getScreenTip() != null && folder.getScreenTip().getValue() != null) {
			String tip = folder.getScreenTip().getValue();
			if (tip.toLowerCase().contains("tenantid=")) {
				return TenantUtils.extractValue(tip, "tenantid");
			}
		}

		// Check custom attributes
		CustomProp[] props = folder.getCustomAttributes();
		for (CustomProp prop : props) {
			if (prop.getName().equalsIgnoreCase("TenantID")) {
				return TenantUtils.prop.getValue();
			}
		}

		throw new Exception("Tenant ID not found in metadata for folder: " + folderName);
	}

	public List<String> getUsersFromGroup(String groupName) throws Exception {
		String searchGroupPath = "/namespace//group[defaultName='" + groupName + "']";
		ContentManagerService_PortType contentManagerService = cognosSDKConnector.getCmService();

		BaseClass[] groupResult = contentManagerService.query(new SearchPathMultipleObject(searchGroupPath),
				new PropEnum[] { PropEnum.member }, new Sort[] {}, new QueryOptions());

		List<String> userList = new ArrayList<>();

		if (groupResult.length > 0 && groupResult[0] instanceof Group) {
			Group group = (Group) groupResult[0];

			BaseClass[] members = group.getMember();
			for (BaseClass member : members) {
				if (member instanceof Account) {
					Account user = (Account) member;
					userList.add(user.getDefaultName().getValue());
				}
			}
		} else {
			System.out.println("Group not found: " + groupName);
		}

		return userList;
	}

	public String getTenantNameByTenantId(String tenantId) throws Exception {
		// Search all folders under /content
		String searchPath = "/content/folder//*";
		ContentManagerService_PortType contentManagerService = cognosSDKConnector.getCmService();

		// Query Cognos
		BaseClass[] results = contentManagerService.query(new SearchPathMultipleObject(searchPath), new PropEnum[] {
				PropEnum.defaultName, PropEnum.description, PropEnum.screenTip, PropEnum.customAttributes },
				new Sort[] {}, new QueryOptions());

		// Loop through folders to find matching TenantID
		for (BaseClass base : results) {
			if (!(base instanceof Folder))
				continue;

			Folder folder = (Folder) base;

			// Check custom attributes first
			for (CustomProp prop : folder.getCustomAttributes()) {
				if ("TenantID".equalsIgnoreCase(prop.getName()) && tenantId.equals(prop.getValue())) {
					return folder.getDefaultName().getValue();
				}
			}

			// Fallback: check description or screenTip
			String desc = folder.getDescription() != null ? folder.getDescription().getValue() : "";
			String tip = folder.getScreenTip() != null ? folder.getScreenTip().getValue() : "";

			if (TenantUtils.containsTenantId(desc, tenantId) || TenantUtils.containsTenantId(tip, tenantId)) {
				return folder.getDefaultName().getValue();
			}
		}

		throw new Exception("Tenant name not found for Tenant ID: " + tenantId);
	}

	public String getTenantProductStatus(String productName, String tenantId) {
		List<String> productList = getProductsForTenant(tenantId);
		Optional<String> matchingProduct = productList.stream().filter(product -> product.contains(productName))
				.findFirst();
		String productFlag = "";
		if (matchingProduct.isPresent()) {
			String status = matchingProduct.get().split("::")[1];
			if (!Status.PRODUCT_DEACTIVATE.name().equals(status)) {
				productFlag = Status.PRODUCT_DEACTIVATE.name();
			} else {
				productFlag = Status.PRODUCT_ACTIVATE.name();
			}
		} else {
			productFlag = "NotFound";
		}
		return productFlag;
	}

	public List<String> getProductsForTenant(String tenantKey) {
		List<String> products = new ArrayList<>();
		try {
			ContentManagerService_PortType cmService = cognosSDKConnector.getCmService();

			String folderPath = "/content/" + tenantKey;
			SearchPathMultipleObject path = new SearchPathMultipleObject(folderPath + "/*");

			PropEnum[] props = new PropEnum[] { PropEnum.searchPath, PropEnum.defaultName, PropEnum.objectClass,
					PropEnum.custom };

			BaseClass[] results = cmService.query(path, props, new Sort[] {}, new QueryOptions());

			for (BaseClass obj : results) {
				if (obj instanceof Folder) {
					products.add(obj.getDefaultName().getValue() + "::" + obj.getStatus().getValue());
				}
			}
		} catch (Exception e) {
			throw new CustomException("Failed to fetch product list", e.getMessage());
		}

		return products;
	}
	
	public void updateTenantStatus(String tenantName, String status) throws Exception {
        // Step 1: Build search path
        SearchPathMultipleObject searchPath = new SearchPathMultipleObject("CAMID(\"" + tenantCAMID + "\")");
		ContentManagerService_PortType cmService = cognosSDKConnector.getCmService();

        // Step 2: Query the tenant
        BaseClass[] results = cmService.query(
                searchPath,
                new PropEnum[]{PropEnum.defaultName, PropEnum.searchPath, PropEnum.status},
                new Sort[]{},
                new QueryOptions()
        );

        if (results == null || results.length == 0 || !(results[0] instanceof Tenant)) {
            throw new RuntimeException("Tenant not found: " + tenantName);
        }

        // Step 3: Set tenant status to inactive
        Tenant tenant = (Tenant) results[0];
        System.out.println("Before updating, status for Tenant:" + tenant.getstatus());

        tenant.setStatus(new Nmtoken(status));

        // Step 4: Update tenant in Cognos
        cmService.update(new BaseClass[]{tenant}, new UpdateOptions());

        System.out.println("Status for tenant updation: " + tenant.getDefaultName().getValue());
    }

	public void assignTenantAdminRole(
	        String tenantName,
	        String adminName,         // e.g. "kapil.sharma"
	        String namespace,         // e.g. "UKG_AUTH"
	        String tenantFolderPath   // e.g. "/content/folder[@name='TenantX']"
	) throws Exception {

		ReportNetService_PortType reportNetService = cognosSDKConnector.getReportNetService();

	    // Build the security principal
	    Principal principal = new Principal();
	    principal.setName(adminName);
	    principal.setNamespace(namespace);

	    // Set admin-level permissions
	    Permission adminPermissions = new Permission();
	    adminPermissions.setName(new Nmtoken(adminName + "_admin_perm"));
	    adminPermissions.setAccess(new AccessEnum[] {
	        AccessEnum.read,
	        AccessEnum.traverse,
	        AccessEnum.execute,
	        AccessEnum.setPolicy,
	        AccessEnum.write
	    });

	    // Create a policy to apply to the tenant folder
	    Policy policy = new Policy();
	    policy.setName("Policy_" + tenantName + "_Admins");
	    policy.setPrincipal(principal);
	    policy.setPermissions(new Permission[] { adminPermissions });
	    policy.setSecuredObject(new SearchPathSingleObject(tenantFolderPath));

	    // Apply the policy using the SDK
	    reportNetService.update(new BaseClass[] { policy }, new UpdateOptions());

	    System.out.println("Tenant Admin '" + adminName + "' assigned to '" + tenantName + "' successfully.");
	}

}
