package com.gitblit.wicket.panels;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class RoleMapping implements Serializable{
	
	public static final String GLOBAL_SYSTEM_ADMIN = "system.admin";	
	public static final String GLOBAL_REPO_ADMIN = "global.repo.admin";
	public static final String GLOBAL_REPO_CREATOR = "global.repo.creator";
	public static final String SYSTEM_USER = "system.user";
	private static Set<String> systemRoles = new HashSet<String>(); 
	
	private static final long serialVersionUID = -2291201377239222256L;
	private String remoteRole;
	private String systemRoleSelection;
	public void setRemoteRole(String remoteRole) {
		this.remoteRole = remoteRole;
	}
	public String getRemoteRole() {
		return remoteRole;
	}
	public void setSystemRoleSelection(String systemRole) {
		if (!systemRoles.contains(systemRole)) {
			try {
				throw new Throwable("invalid system role in RoleMapping");
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
		this.systemRoleSelection = systemRole;
	}
	public String getSystemRoleSelection() {
		return systemRoleSelection;
	}
	
	static {
		fillOptionSet();
	}
	
	private static void fillOptionSet() {
		systemRoles.add(GLOBAL_SYSTEM_ADMIN);
		systemRoles.add(GLOBAL_REPO_ADMIN);
		systemRoles.add(GLOBAL_REPO_CREATOR);
		systemRoles.add(SYSTEM_USER);
	}
	
	public static Set<String> getSystemRoles() {
		return new HashSet<String>(systemRoles);
	}
	
	public RoleMapping(String remoteRole,String systemRole) {
		super();		
		this.setRemoteRole(remoteRole);
		this.setSystemRoleSelection(systemRole);
	}
}
