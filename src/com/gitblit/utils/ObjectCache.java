/*
 * Copyright 2011 gitblit.com.
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
package com.gitblit.utils;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reusable coarse date-based object cache. The date precision is in
 * milliseconds and in fast, concurrent systems this cache is too simplistic.
 * However, for the cases where its being used in Gitblit this cache technique
 * is just fine.
 * 
 * @author James Moger
 * 
 */
public class ObjectCache<X> implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Map<String, CachedObject<X>> cache = new ConcurrentHashMap<String, CachedObject<X>>();

	private class CachedObject<Y> {

		public final String name;

		private volatile Date date;

		private volatile Y object;

		CachedObject(String name) {
			this.name = name;
			date = new Date(0);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + ": " + name;
		}
	}

	public boolean hasCurrent(String name, Date date) {
		return cache.containsKey(name) && cache.get(name).date.compareTo(date) == 0;
	}

	public Date getDate(String name) {
		return cache.get(name).date;
	}

	public X getObject(String name) {
		if (cache.containsKey(name)) {
			return cache.get(name).object;
		}
		return null;
	}

	public void updateObject(String name, X object) {
		this.updateObject(name, new Date(), object);
	}

	public void updateObject(String name, Date date, X object) {
		CachedObject<X> obj;
		if (cache.containsKey(name)) {
			obj = cache.get(name);
		} else {
			obj = new CachedObject<X>(name);
			cache.put(name, obj);
		}
		obj.date = date;
		obj.object = object;
	}

	public Object remove(String name) {
		if (cache.containsKey(name)) {
			return cache.remove(name).object;
		}
		return null;
	}
	
	public int size() {
		return cache.size();
	}
}
