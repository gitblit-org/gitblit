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

/**
 * SubmoduleModel is a serializable model class that represents a git submodule
 * definition.
 *
 * @author James Moger
 *
 */
public class SubmoduleModel implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String name;
	public final String path;
	public final String url;

	public boolean hasSubmodule;
	public String gitblitPath;

	public SubmoduleModel(String name, String path, String url) {
		this.name = name;
		this.path = path;
		this.url = url;
	}

	@Override
	public String toString() {
		return path + "=" + url;
	}
}