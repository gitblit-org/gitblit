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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.GitBlitException.ForbiddenException;
import com.gitblit.GitBlitException.NotAllowedException;
import com.gitblit.GitBlitException.UnauthorizedException;
import com.gitblit.GitBlitException.UnknownRequestException;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

/**
 * Utility methods for json calls to a Gitblit server.
 *
 * @author James Moger
 *
 */
public class JsonUtils {

	public static final Type REPOSITORIES_TYPE = new TypeToken<Map<String, RepositoryModel>>() {
	}.getType();

	public static final Type USERS_TYPE = new TypeToken<Collection<UserModel>>() {
	}.getType();

	/**
	 * Creates JSON from the specified object.
	 *
	 * @param o
	 * @return json
	 */
	public static String toJsonString(Object o) {
		String json = gson().toJson(o);
		return json;
	}

	/**
	 * Convert a json string to an object of the specified type.
	 * 
	 * @param json
	 * @param clazz
	 * @return the deserialized object
	 * @throws JsonParseException
	 * @throws JsonSyntaxException
	 */
	public static <X> X fromJsonString(String json, Class<X> clazz) throws JsonParseException,
			JsonSyntaxException {
		return gson().fromJson(json, clazz);
	}

	/**
	 * Convert a json string to an object of the specified type.
	 * 
	 * @param json
	 * @param type
	 * @return the deserialized object
	 * @throws JsonParseException
	 * @throws JsonSyntaxException
	 */
	public static <X> X fromJsonString(String json, Type type) throws JsonParseException,
			JsonSyntaxException {
		return gson().fromJson(json, type);
	}

	/**
	 * Reads a gson object from the specified url.
	 *
	 * @param url
	 * @param type
	 * @return the deserialized object
	 * @throws {@link IOException}
	 */
	public static <X> X retrieveJson(String url, Type type) throws IOException,
			UnauthorizedException {
		return retrieveJson(url, type, null, null);
	}

	/**
	 * Reads a gson object from the specified url.
	 *
	 * @param url
	 * @param type
	 * @return the deserialized object
	 * @throws {@link IOException}
	 */
	public static <X> X retrieveJson(String url, Class<? extends X> clazz) throws IOException,
			UnauthorizedException {
		return retrieveJson(url, clazz, null, null);
	}

	/**
	 * Reads a gson object from the specified url.
	 *
	 * @param url
	 * @param type
	 * @param username
	 * @param password
	 * @return the deserialized object
	 * @throws {@link IOException}
	 */
	public static <X> X retrieveJson(String url, Type type, String username, char[] password)
			throws IOException {
		String json = retrieveJsonString(url, username, password);
		if (StringUtils.isEmpty(json)) {
			return null;
		}
		return gson().fromJson(json, type);
	}

	/**
	 * Reads a gson object from the specified url.
	 *
	 * @param url
	 * @param clazz
	 * @param username
	 * @param password
	 * @return the deserialized object
	 * @throws {@link IOException}
	 */
	public static <X> X retrieveJson(String url, Class<X> clazz, String username, char[] password)
			throws IOException {
		String json = retrieveJsonString(url, username, password);
		if (StringUtils.isEmpty(json)) {
			return null;
		}
		return gson().fromJson(json, clazz);
	}

	/**
	 * Retrieves a JSON message.
	 *
	 * @param url
	 * @return the JSON message as a string
	 * @throws {@link IOException}
	 */
	public static String retrieveJsonString(String url, String username, char[] password)
			throws IOException {
		try {
			URLConnection conn = ConnectionUtils.openReadConnection(url, username, password);
			InputStream is = conn.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(is,
					ConnectionUtils.CHARSET));
			StringBuilder json = new StringBuilder();
			char[] buffer = new char[4096];
			int len = 0;
			while ((len = reader.read(buffer)) > -1) {
				json.append(buffer, 0, len);
			}
			is.close();
			return json.toString();
		} catch (IOException e) {
			if (e.getMessage().indexOf("401") > -1) {
				// unauthorized
				throw new UnauthorizedException(url);
			} else if (e.getMessage().indexOf("403") > -1) {
				// requested url is forbidden by the requesting user
				throw new ForbiddenException(url);
			} else if (e.getMessage().indexOf("405") > -1) {
				// requested url is not allowed by the server
				throw new NotAllowedException(url);
			} else if (e.getMessage().indexOf("501") > -1) {
				// requested url is not recognized by the server
				throw new UnknownRequestException(url);
			}
			throw e;
		}
	}

	/**
	 * Sends a JSON message.
	 *
	 * @param url
	 *            the url to write to
	 * @param json
	 *            the json message to send
	 * @return the http request result code
	 * @throws {@link IOException}
	 */
	public static int sendJsonString(String url, String json) throws IOException {
		return sendJsonString(url, json, null, null);
	}

	/**
	 * Sends a JSON message.
	 *
	 * @param url
	 *            the url to write to
	 * @param json
	 *            the json message to send
	 * @param username
	 * @param password
	 * @return the http request result code
	 * @throws {@link IOException}
	 */
	public static int sendJsonString(String url, String json, String username, char[] password)
			throws IOException {
		try {
			byte[] jsonBytes = json.getBytes(ConnectionUtils.CHARSET);
			URLConnection conn = ConnectionUtils.openConnection(url, username, password);
			conn.setRequestProperty("Content-Type", "text/plain;charset=" + ConnectionUtils.CHARSET);
			conn.setRequestProperty("Content-Length", "" + jsonBytes.length);

			// write json body
			OutputStream os = conn.getOutputStream();
			os.write(jsonBytes);
			os.close();

			int status = ((HttpURLConnection) conn).getResponseCode();
			return status;
		} catch (IOException e) {
			if (e.getMessage().indexOf("401") > -1) {
				// unauthorized
				throw new UnauthorizedException(url);
			} else if (e.getMessage().indexOf("403") > -1) {
				// requested url is forbidden by the requesting user
				throw new ForbiddenException(url);
			} else if (e.getMessage().indexOf("405") > -1) {
				// requested url is not allowed by the server
				throw new NotAllowedException(url);
			} else if (e.getMessage().indexOf("501") > -1) {
				// requested url is not recognized by the server
				throw new UnknownRequestException(url);
			}
			throw e;
		}
	}

	// build custom gson instance with GMT date serializer/deserializer
	// http://code.google.com/p/google-gson/issues/detail?id=281
	public static Gson gson(ExclusionStrategy... strategies) {
		GsonBuilder builder = new GsonBuilder();
		builder.registerTypeAdapter(Date.class, new GmtDateTypeAdapter());
		builder.registerTypeAdapter(AccessPermission.class, new AccessPermissionTypeAdapter());
		if (!ArrayUtils.isEmpty(strategies)) {
			builder.setExclusionStrategies(strategies);
		}
		return builder.create();
	}

	public static class GmtDateTypeAdapter implements JsonSerializer<Date>, JsonDeserializer<Date> {
		private final DateFormat dateFormat;

		public GmtDateTypeAdapter() {
			dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
			dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		}

		@Override
		public synchronized JsonElement serialize(Date date, Type type,
				JsonSerializationContext jsonSerializationContext) {
			synchronized (dateFormat) {
				String dateFormatAsString = dateFormat.format(date);
				return new JsonPrimitive(dateFormatAsString);
			}
		}

		@Override
		public synchronized Date deserialize(JsonElement jsonElement, Type type,
				JsonDeserializationContext jsonDeserializationContext) {
			try {
				synchronized (dateFormat) {
					Date date = dateFormat.parse(jsonElement.getAsString());
					return new Date((date.getTime() / 1000) * 1000);
				}
			} catch (ParseException e) {
				throw new JsonSyntaxException(jsonElement.getAsString(), e);
			}
		}
	}

	private static class AccessPermissionTypeAdapter implements JsonSerializer<AccessPermission>, JsonDeserializer<AccessPermission> {

		private AccessPermissionTypeAdapter() {
		}

		@Override
		public synchronized JsonElement serialize(AccessPermission permission, Type type,
				JsonSerializationContext jsonSerializationContext) {
			return new JsonPrimitive(permission.code);
		}

		@Override
		public synchronized AccessPermission deserialize(JsonElement jsonElement, Type type,
				JsonDeserializationContext jsonDeserializationContext) {
			return AccessPermission.fromCode(jsonElement.getAsString());
		}
	}

	public static class ExcludeField implements ExclusionStrategy {

		private Class<?> c;
		private String fieldName;

		public ExcludeField(String fqfn) throws SecurityException, NoSuchFieldException,
				ClassNotFoundException {
			this.c = Class.forName(fqfn.substring(0, fqfn.lastIndexOf(".")));
			this.fieldName = fqfn.substring(fqfn.lastIndexOf(".") + 1);
		}

		@Override
		public boolean shouldSkipClass(Class<?> arg0) {
			return false;
		}

		@Override
		public boolean shouldSkipField(FieldAttributes f) {
			return (f.getDeclaringClass() == c && f.getName().equals(fieldName));
		}
	}
}
