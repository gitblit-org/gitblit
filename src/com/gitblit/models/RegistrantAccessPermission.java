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
	public RegistrantType type;

	public RegistrantAccessPermission() {
	}
	
	public RegistrantAccessPermission(String registrant, AccessPermission permission, RegistrantType type) {
		this.registrant = registrant;
		this.permission = permission;
		this.type = type;
	}
	
	@Override
	public int compareTo(RegistrantAccessPermission p) {
		switch (type) {
		case REPOSITORY:
			return StringUtils.compareRepositoryNames(registrant, p.registrant);
		default:
			return registrant.toLowerCase().compareTo(p.registrant.toLowerCase());		
		}
	}
	
	@Override
	public String toString() {
		return permission.asRole(registrant);
	}
}