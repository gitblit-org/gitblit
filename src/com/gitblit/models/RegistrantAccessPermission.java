/*
 * Copyright 2012 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.models;

import java.io.Serializable;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.PermissionType;
import com.gitblit.Constants.RegistrantType;
import com.gitblit.utils.StringUtils;

/**
 * Represents a Registrant-AccessPermission tuple.
 * 
 * @author James Moger
 */
public class RegistrantAccessPermission implements Serializable, Comparable<RegistrantAccessPermission> {

	private static final long serialVersionUID = 1L;

	public String registrant;
	public AccessPermission permission;
	public RegistrantType registrantType;
	public PermissionType permissionType;
	public boolean mutable;
	public String source;
	
	public RegistrantAccessPermission() {
	}

	public RegistrantAccessPermission(RegistrantType registrantType) {
		this.registrantType = registrantType;
		this.permissionType = PermissionType.EXPLICIT;
		this.mutable = true;
	}
	
	public RegistrantAccessPermission(String registrant, AccessPermission permission, PermissionType permissionType, RegistrantType registrantType, String source, boolean mutable) {
		this.registrant = registrant;
		this.permission = permission;
		this.permissionType = permissionType;
		this.registrantType = registrantType;
		this.source = source;
		this.mutable = mutable;
	}
	
	public boolean isAdmin() {
		return PermissionType.ADMINISTRATOR.equals(permissionType);
	}

	public boolean isOwner() {
		return PermissionType.OWNER.equals(permissionType);
	}

	@Override
	public int compareTo(RegistrantAccessPermission p) {
		switch (registrantType) {
		case REPOSITORY:
			return StringUtils.compareRepositoryNames(registrant, p.registrant);
		default:
			return registrant.toLowerCase().compareTo(p.registrant.toLowerCase());		
		}
	}
	
	@Override
	public int hashCode() {
		return registrant.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof RegistrantAccessPermission) {
			RegistrantAccessPermission p = (RegistrantAccessPermission) o;
			return registrant.equals(p.registrant);
		}
		
		return false;
	}
	
	@Override
	public String toString() {
		return permission.asRole(registrant);
	}
}