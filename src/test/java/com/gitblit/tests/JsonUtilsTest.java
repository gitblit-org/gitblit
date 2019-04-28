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
package com.gitblit.tests;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.gitblit.utils.JsonUtils;
import com.google.gson.reflect.TypeToken;

public class JsonUtilsTest extends GitblitUnitTest {

	@Test
	public void testSerialization() {
		Map<String, String> map = new LinkedHashMap<String, String>();
		//LinkedHashMap preserves the order of insertion
		map.put("a", "alligator");
		map.put("b", "bear");
		map.put("c", "caterpillar");
		map.put("d", "dingo");
		map.put("e", "eagle");
		String json = JsonUtils.toJsonString(map);
		assertEquals(
				"{\"a\":\"alligator\",\"b\":\"bear\",\"c\":\"caterpillar\",\"d\":\"dingo\",\"e\":\"eagle\"}",
				json);
		Map<String, String> map2 = JsonUtils.fromJsonString(json,
				new TypeToken<Map<String, String>>() {
				}.getType());
		assertEquals(map, map2);

		SomeJsonObject someJson = new SomeJsonObject();
		json = JsonUtils.toJsonString(someJson);
		SomeJsonObject someJson2 = JsonUtils.fromJsonString(json, SomeJsonObject.class);
		assertEquals(someJson.name, someJson2.name);
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd HHmmss");
		assertEquals(df.format(someJson.date), df.format(someJson2.date));
	}

	private class SomeJsonObject {
		Date date = new Date();
		String name = "myJson";
	}
}
