/*
 * Copyright 2015 gitblit.com.
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
package com.gitblit.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.models.FilestoreModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.FilestoreModel.Status;
import com.gitblit.manager.FilestoreManager;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.UserModel;
import com.gitblit.utils.JsonUtils;


/**
 * Handles large file storage as per the Git LFS v1 Batch API
 * 
 * Further details can be found at https://github.com/github/git-lfs
 * 
 * @author Paul Martin
 */
@Singleton
public class FilestoreServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	public static final int PROTOCOL_VERSION = 1;
	
	public static final String GIT_LFS_META_MIME = "application/vnd.git-lfs+json";
	
	public static final String REGEX_PATH = "^(.*?)/(r)/(.*?)/info/lfs/objects/(batch|" + Constants.REGEX_SHA256 + ")";
	public static final int REGEX_GROUP_BASE_URI = 1;
	public static final int REGEX_GROUP_PREFIX = 2;
	public static final int REGEX_GROUP_REPOSITORY = 3;
	public static final int REGEX_GROUP_ENDPOINT = 4;
	
	protected final Logger logger;
	
	private static IGitblit gitblit;

	@Inject
	public FilestoreServlet(IStoredSettings settings, IGitblit gitblit) {
		
		super();
		logger = LoggerFactory.getLogger(getClass());
		
		FilestoreServlet.gitblit = gitblit;
	}

		
	/**
	 * Handles batch upload request (metadata)
	 *
	 * @param request
	 * @param response
	 * @throws javax.servlet.ServletException
	 * @throws java.io.IOException
	 */
	@Override
	protected void doPost(HttpServletRequest request, 
			HttpServletResponse response) throws ServletException ,IOException {
		
		UrlInfo info = getInfoFromRequest(request);
		if (info == null) {
			sendError(response, HttpServletResponse.SC_NOT_FOUND);
        	return;
		}

		//Post is for batch operations so no oid should be defined
		if (info.oid != null) {
			sendError(response, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		
		IGitLFS.Batch batch = deserialize(request, response, IGitLFS.Batch.class);
		
		if (batch == null) { 
			sendError(response, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		UserModel user = getUserOrAnonymous(request);
		
		IGitLFS.BatchResponse batchResponse = new IGitLFS.BatchResponse();
		
		if (batch.operation.equalsIgnoreCase("upload")) {
			for (IGitLFS.Request item : batch.objects) {
				
				Status state = gitblit.addObject(item.oid, item.size, user, info.repository);

				batchResponse.objects.add(getResponseForUpload(info.baseUrl, item.oid, item.size, user.getName(), info.repository.name, state));
			}
		} else if (batch.operation.equalsIgnoreCase("download")) {
			for (IGitLFS.Request item : batch.objects) {
				
				Status state = gitblit.downloadBlob(item.oid, user, info.repository, null);
				batchResponse.objects.add(getResponseForDownload(info.baseUrl, item.oid, item.size, user.getName(), info.repository.name, state));
			}
		} else {
			sendError(response, HttpServletResponse.SC_NOT_IMPLEMENTED);
			return;
		}
		
		response.setStatus(HttpServletResponse.SC_OK);
		serialize(response, batchResponse);
	}
	
	/**
	 * Handles the actual upload (BLOB)
	 * 
	 * @param request
	 * @param response
	 * @throws javax.servlet.ServletException
	 * @throws java.io.IOException
	 */
	@Override
	protected void doPut(HttpServletRequest request, 
			HttpServletResponse response) throws ServletException ,IOException {
		
		UrlInfo info = getInfoFromRequest(request);
		
		if (info == null) {
			sendError(response, HttpServletResponse.SC_NOT_FOUND);
        	return;
		}

		//Put is a singular operation so must have oid
		if (info.oid == null) {
			sendError(response, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		
		UserModel user = getUserOrAnonymous(request);
		long size = FilestoreManager.UNDEFINED_SIZE;
		
		
		
		FilestoreModel.Status status = gitblit.uploadBlob(info.oid, size, user, info.repository, request.getInputStream());
		IGitLFS.Response responseObject = getResponseForUpload(info.baseUrl, info.oid, size, user.getName(), info.repository.name, status);
		
		logger.info(MessageFormat.format("FILESTORE-AUDIT {0}:{4} {1} {2}@{3}", 
				"PUT", info.oid, user.getName(), info.repository.name, status.toString() ));
		
		if (responseObject.error == null) {
			response.setStatus(responseObject.successCode);
		} else {
			serialize(response, responseObject.error);
		}
	};
	
	/**
	 * Handles a download
	 * Treated as hypermedia request if accept header contains Git-LFS MIME
	 * otherwise treated as a download of the blob
	 * @param request
	 * @param response
	 * @throws javax.servlet.ServletException
	 * @throws java.io.IOException
	 */
	@Override
	protected void doGet(HttpServletRequest request, 
			HttpServletResponse response) throws ServletException ,IOException {
		
		UrlInfo info = getInfoFromRequest(request);
		
		if (info == null || info.oid == null) {
			sendError(response, HttpServletResponse.SC_NOT_FOUND);
        	return;
		}
		
		UserModel user = getUserOrAnonymous(request);
		
		FilestoreModel model = gitblit.getObject(info.oid, user, info.repository);
		long size = FilestoreManager.UNDEFINED_SIZE;
		
		boolean isMetaRequest = AccessRestrictionFilter.hasContentInRequestHeader(request, "Accept", GIT_LFS_META_MIME);
		FilestoreModel.Status status = Status.Unavailable;
		
		if (model != null) {
			size = model.getSize();
			status = model.getStatus();
		}
		
		if (!isMetaRequest) {
			status = gitblit.downloadBlob(info.oid, user, info.repository, response.getOutputStream());
			
			logger.info(MessageFormat.format("FILESTORE-AUDIT {0}:{4} {1} {2}@{3}", 
					"GET", info.oid, user.getName(), info.repository.name, status.toString() ));
		}
		
		if (status == Status.Error_Unexpected_Stream_End) {
			return;
		}

		IGitLFS.Response responseObject = getResponseForDownload(info.baseUrl, 
				info.oid, size, user.getName(), info.repository.name, status);
		
		if (responseObject.error == null) {
			response.setStatus(responseObject.successCode);
			
			if (isMetaRequest) {
				serialize(response, responseObject);
			}
		} else {
			response.setStatus(responseObject.error.code);
			serialize(response, responseObject.error);
		}
	};
	
	private void sendError(HttpServletResponse response, int code) throws IOException {
		
		String msg = "";
		
		switch (code)
		{
			case HttpServletResponse.SC_NOT_FOUND: msg = "Not Found"; break;
			case HttpServletResponse.SC_NOT_IMPLEMENTED: msg = "Not Implemented"; break;
			case HttpServletResponse.SC_BAD_REQUEST: msg = "Malformed Git-LFS request"; break;
			
			default: msg = "Unknown Error";
		}
		
		response.setStatus(code);
		serialize(response, new IGitLFS.ObjectError(code, msg));
	}
	
	@SuppressWarnings("incomplete-switch")
	private IGitLFS.Response getResponseForUpload(String baseUrl, String oid, long size, String user, String repo, FilestoreModel.Status state) {

		switch (state) {
			case AuthenticationRequired:
				return new IGitLFS.Response(oid, size, 401, MessageFormat.format("Authentication required to write to repository {0}", repo));
			case Error_Unauthorized: 
				return new IGitLFS.Response(oid, size, 403, MessageFormat.format("User {0}, does not have write permissions to repository {1}", user, repo));
			case Error_Exceeds_Size_Limit: 
				return new IGitLFS.Response(oid, size, 509, MessageFormat.format("Object is larger than allowed limit of {1}",  gitblit.getMaxUploadSize()));
			case Error_Hash_Mismatch: 
				return new IGitLFS.Response(oid, size, 422, "Hash mismatch");
			case Error_Invalid_Oid: 
				return new IGitLFS.Response(oid, size, 422, MessageFormat.format("{0} is not a valid oid", oid));
			case Error_Invalid_Size: 
				return new IGitLFS.Response(oid, size, 422, MessageFormat.format("{0} is not a valid size", size));
			case Error_Size_Mismatch: 
				return new IGitLFS.Response(oid, size, 422, "Object size mismatch");
			case Deleted: 
				return new IGitLFS.Response(oid, size, 410, "Object was deleted : ".concat("TBD Reason") );
			case Upload_In_Progress:
				return new IGitLFS.Response(oid, size, 503, "File currently being uploaded by another user");
			case Unavailable: 
				return new IGitLFS.Response(oid, size, 404, MessageFormat.format("Repository {0}, does not exist for user {1}", repo, user));
			case Upload_Pending: 
				return new IGitLFS.Response(oid, size, 202, "upload", getObjectUri(baseUrl, repo, oid) );
			case Available: 
				return new IGitLFS.Response(oid, size, 200, "upload", getObjectUri(baseUrl, repo, oid) );
		}
		
		return new IGitLFS.Response(oid, size, 500, "Unknown Error");
	}

	@SuppressWarnings("incomplete-switch")
	private IGitLFS.Response getResponseForDownload(String baseUrl, String oid, long size, String user, String repo, FilestoreModel.Status state) {

		switch (state) {
			case Error_Unauthorized: 
				return new IGitLFS.Response(oid, size, 403, MessageFormat.format("User {0}, does not have read permissions to repository {1}", user, repo));
			case Error_Invalid_Oid: 
				return new IGitLFS.Response(oid, size, 422, MessageFormat.format("{0} is not a valid oid", oid));
			case Error_Unknown:
				return new IGitLFS.Response(oid, size, 500, "Unknown Error");
			case Deleted: 
				return new IGitLFS.Response(oid, size, 410, "Object was deleted : ".concat("TBD Reason") );
			case Available: 
				return new IGitLFS.Response(oid, size, 200, "download", getObjectUri(baseUrl, repo, oid) );
		}
		
		return new IGitLFS.Response(oid, size, 404, "Object not available");
	}

	
	private String getObjectUri(String baseUrl, String repo, String oid) {
		return baseUrl + "/" + repo + "/" + Constants.R_LFS + "objects/" + oid;
	}
	
	
	protected void serialize(HttpServletResponse response, Object o) throws IOException {
		if (o != null) {
			// Send JSON response
			String json = JsonUtils.toJsonString(o);
			response.setCharacterEncoding(Constants.ENCODING);
			response.setContentType(GIT_LFS_META_MIME);
			response.getWriter().append(json);
		}
	}
	
	protected <X> X deserialize(HttpServletRequest request, HttpServletResponse response,
			Class<X> clazz) {
		
		String json = "";
		try {
			
			json = readJson(request, response);
			
			return JsonUtils.fromJsonString(json.toString(), clazz);
			
		} catch (Exception e) {
			//Intentional silent fail
		}
		
		return null;
	}
	
	private String readJson(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		BufferedReader reader = request.getReader();
		StringBuilder json = new StringBuilder();
		String line = null;
		while ((line = reader.readLine()) != null) {
			json.append(line);
		}
		reader.close();

		if (json.length() == 0) {
			logger.error(MessageFormat.format("Failed to receive json data from {0}",
					request.getRemoteAddr()));
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return null;
		}
		return json.toString();
	}
	
	private UserModel getUserOrAnonymous(HttpServletRequest r) {
		UserModel user = (UserModel) r.getUserPrincipal();
		if (user != null) { return user; }
		return UserModel.ANONYMOUS;
	}
	
	private static class UrlInfo {
		public RepositoryModel repository;
		public String oid;
		public String baseUrl;
		
		public UrlInfo(RepositoryModel repo, String oid, String baseUrl) {
			this.repository = repo;
			this.oid = oid;
			this.baseUrl = baseUrl;
		}
	}
	
	public static UrlInfo getInfoFromRequest(HttpServletRequest httpRequest) {
		
		String url = httpRequest.getRequestURL().toString();
		Pattern p = Pattern.compile(REGEX_PATH);
        Matcher m = p.matcher(url);
		
        
        if (m.find()) {
        	RepositoryModel repo = gitblit.getRepositoryModel(m.group(REGEX_GROUP_REPOSITORY));
        	String baseUrl = m.group(REGEX_GROUP_BASE_URI) + "/" + m.group(REGEX_GROUP_PREFIX);
        	
        	if (m.group(REGEX_GROUP_ENDPOINT).equals("batch")) {
        		return new UrlInfo(repo, null, baseUrl);
        	} else {
        		return new UrlInfo(repo, m.group(REGEX_GROUP_ENDPOINT), baseUrl);
        	}
        }
		
		return null;
	}
	
	
	public interface IGitLFS {
	
		@SuppressWarnings("serial")
		public class Request implements Serializable
		{
			public String oid;
			public long size;
		}
		
		
		@SuppressWarnings("serial")
		public class Batch implements Serializable
		{
			public String operation;
			public List<Request> objects;
		}
		
		
		@SuppressWarnings("serial")
		public class Response implements Serializable
		{
			public String oid;
			public long size;
			public Map<String, HyperMediaLink> actions;
			public ObjectError error;
			public transient int successCode; 
			
			public Response(String id, long itemSize, int errorCode, String errorText) {
				oid = id;
				size = itemSize;
				actions = null;
				successCode = 0;
				error = new ObjectError(errorCode, errorText);
			}
			
			public Response(String id, long itemSize, int actionCode, String action, String uri) {
				oid = id;
				size = itemSize;
				error = null;
				successCode = actionCode;
				actions = new HashMap<String, HyperMediaLink>();
				actions.put(action, new HyperMediaLink(action, uri));
			}
			
		}
		
		@SuppressWarnings("serial")
		public class BatchResponse implements Serializable {
			public List<Response> objects;
			
			public BatchResponse() {
				objects = new ArrayList<Response>();
			}
		}
		
		
		@SuppressWarnings("serial")
		public class ObjectError implements Serializable
		{
			public String message;
			public int code;
			public String documentation_url;
			public Integer request_id;
			
			public ObjectError(int errorCode, String errorText) {
				code = errorCode;
				message = errorText;
				request_id = null;
			}
		}
		
		@SuppressWarnings("serial")
		public class HyperMediaLink implements Serializable
		{
			public String href;
			public transient String header;
			//public Date expires_at;
			
			public HyperMediaLink(String action, String uri) {
				header = action;
				href = uri;
			}
		}
	}


	
}
