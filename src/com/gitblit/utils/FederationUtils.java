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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.FederationRequest;
import com.gitblit.FederationServlet;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Utility methods for federation functions.
 * 
 * @author James Moger
 * 
 */
public class FederationUtils {

	public static final String CHARSET;

	public static final Type REPOSITORIES_TYPE = new TypeToken<Map<String, RepositoryModel>>() {
	}.getType();

	public static final Type SETTINGS_TYPE = new TypeToken<Map<String, String>>() {
	}.getType();

	public static final Type USERS_TYPE = new TypeToken<Collection<UserModel>>() {
	}.getType();

	public static final Type RESULTS_TYPE = new TypeToken<List<FederationModel>>() {
	}.getType();

	private static final SSLContext SSL_CONTEXT;

	private static final DummyHostnameVerifier HOSTNAME_VERIFIER;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FederationUtils.class);

	static {
		SSLContext context = null;
		try {
			context = SSLContext.getInstance("SSL");
			context.init(null, new TrustManager[] { new DummyTrustManager() }, new SecureRandom());
		} catch (Throwable t) {
			t.printStackTrace();
		}
		SSL_CONTEXT = context;
		HOSTNAME_VERIFIER = new DummyHostnameVerifier();
		CHARSET = "UTF-8";
	}

	/**
	 * Returns the list of federated gitblit instances that this instance will
	 * try to pull.
	 * 
	 * @return list of registered gitblit instances
	 */
	public static List<FederationModel> getFederationRegistrations(IStoredSettings settings) {
		List<FederationModel> federationRegistrations = new ArrayList<FederationModel>();
		List<String> keys = settings.getAllKeys(Keys.federation._ROOT);
		keys.remove(Keys.federation.name);
		keys.remove(Keys.federation.passphrase);
		keys.remove(Keys.federation.allowProposals);
		keys.remove(Keys.federation.proposalsFolder);
		keys.remove(Keys.federation.defaultFrequency);
		keys.remove(Keys.federation.sets);
		Collections.sort(keys);
		Map<String, FederationModel> federatedModels = new HashMap<String, FederationModel>();
		for (String key : keys) {
			String value = key.substring(Keys.federation._ROOT.length() + 1);
			List<String> values = StringUtils.getStringsFromValue(value, "\\.");
			String server = values.get(0);
			if (!federatedModels.containsKey(server)) {
				federatedModels.put(server, new FederationModel(server));
			}
			String setting = values.get(1);
			if (setting.equals("url")) {
				// url of the origin Gitblit instance
				federatedModels.get(server).url = settings.getString(key, "");
			} else if (setting.equals("token")) {
				// token for the origin Gitblit instance
				federatedModels.get(server).token = settings.getString(key, "");
			} else if (setting.equals("frequency")) {
				// frequency of the pull operation
				federatedModels.get(server).frequency = settings.getString(key, "");
			} else if (setting.equals("folder")) {
				// destination folder of the pull operation
				federatedModels.get(server).folder = settings.getString(key, "");
			} else if (setting.equals("bare")) {
				// whether pulled repositories should be bare
				federatedModels.get(server).bare = settings.getBoolean(key, true);
			} else if (setting.equals("mirror")) {
				// are the repositories to be true mirrors of the origin
				federatedModels.get(server).mirror = settings.getBoolean(key, true);
			} else if (setting.equals("mergeAccounts")) {
				// merge remote accounts into local accounts
				federatedModels.get(server).mergeAccounts = settings.getBoolean(key, false);
			} else if (setting.equals("sendStatus")) {
				// send a status acknowledgment to source Gitblit instance
				// at end of git pull
				federatedModels.get(server).sendStatus = settings.getBoolean(key, false);
			} else if (setting.equals("notifyOnError")) {
				// notify administrators on federation pull failures
				federatedModels.get(server).notifyOnError = settings.getBoolean(key, false);
			} else if (setting.equals("exclude")) {
				// excluded repositories
				federatedModels.get(server).exclusions = settings.getStrings(key);
			} else if (setting.equals("include")) {
				// included repositories
				federatedModels.get(server).inclusions = settings.getStrings(key);
			}
		}

		// verify that registrations have a url and a token
		for (FederationModel model : federatedModels.values()) {
			if (StringUtils.isEmpty(model.url)) {
				LOGGER.warn(MessageFormat.format(
						"Dropping federation registration {0}. Missing url.", model.name));
				continue;
			}
			if (StringUtils.isEmpty(model.token)) {
				LOGGER.warn(MessageFormat.format(
						"Dropping federation registration {0}. Missing token.", model.name));
				continue;
			}
			// set default frequency if unspecified
			if (StringUtils.isEmpty(model.frequency)) {
				model.frequency = settings.getString(Keys.federation.defaultFrequency, "60 mins");
			}
			federationRegistrations.add(model);
		}
		return federationRegistrations;
	}

	/**
	 * Sends a federation proposal to the Gitblit instance at remoteUrl
	 * 
	 * @param remoteUrl
	 *            the remote Gitblit instance to send a federation proposal to
	 * @param proposal
	 *            a complete federation proposal
	 * @return true if the proposal was received
	 */
	public static boolean propose(String remoteUrl, FederationProposal proposal) throws Exception {
		String url = FederationServlet
				.asFederationLink(remoteUrl, null, FederationRequest.PROPOSAL);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(proposal);
		int status = writeJson(url, json);
		return status == HttpServletResponse.SC_OK;
	}

	/**
	 * Retrieves a map of the repositories at the remote gitblit instance keyed
	 * by the repository clone url.
	 * 
	 * @param registration
	 * @param checkExclusions
	 *            should returned repositories remove registration exclusions
	 * @return a map of cloneable repositories
	 * @throws Exception
	 */
	public static Map<String, RepositoryModel> getRepositories(FederationModel registration,
			boolean checkExclusions) throws Exception {
		String url = FederationServlet.asFederationLink(registration.url, registration.token,
				FederationRequest.PULL_REPOSITORIES);
		Map<String, RepositoryModel> models = readGson(url, REPOSITORIES_TYPE);
		if (checkExclusions) {
			Map<String, RepositoryModel> includedModels = new HashMap<String, RepositoryModel>();
			for (Map.Entry<String, RepositoryModel> entry : models.entrySet()) {
				if (registration.isIncluded(entry.getValue())) {
					includedModels.put(entry.getKey(), entry.getValue());
				}
			}
			return includedModels;
		}
		return models;
	}

	/**
	 * Tries to pull the gitblit user accounts from the remote gitblit instance.
	 * 
	 * @param registration
	 * @return a collection of UserModel objects
	 * @throws Exception
	 */
	public static Collection<UserModel> getUsers(FederationModel registration) throws Exception {
		String url = FederationServlet.asFederationLink(registration.url, registration.token,
				FederationRequest.PULL_USERS);
		Collection<UserModel> models = readGson(url, USERS_TYPE);
		return models;
	}

	/**
	 * Tries to pull the gitblit server settings from the remote gitblit
	 * instance.
	 * 
	 * @param registration
	 * @return a map of the remote gitblit settings
	 * @throws Exception
	 */
	public static Map<String, String> getSettings(FederationModel registration) throws Exception {
		String url = FederationServlet.asFederationLink(registration.url, registration.token,
				FederationRequest.PULL_SETTINGS);
		Map<String, String> settings = readGson(url, SETTINGS_TYPE);
		return settings;
	}

	/**
	 * Send an status acknowledgment to the remote Gitblit server.
	 * 
	 * @param identification
	 *            identification of this pulling instance
	 * @param registration
	 *            the source Gitblit instance to receive an acknowledgment
	 * @param results
	 *            the results of your pull operation
	 * @return true, if the remote Gitblit instance acknowledged your results
	 * @throws Exception
	 */
	public static boolean acknowledgeStatus(String identification, FederationModel registration)
			throws Exception {
		String url = FederationServlet.asFederationLink(registration.url, null, registration.token,
				FederationRequest.STATUS, identification);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(registration);
		int status = writeJson(url, json);
		return status == HttpServletResponse.SC_OK;
	}

	/**
	 * Reads a gson object from the specified url.
	 * 
	 * @param url
	 * @param type
	 * @return
	 * @throws Exception
	 */
	public static <X> X readGson(String url, Type type) throws Exception {
		String json = readJson(url);
		if (StringUtils.isEmpty(json)) {
			return null;
		}
		Gson gson = new Gson();
		return gson.fromJson(json, type);
	}

	/**
	 * Reads a JSON response.
	 * 
	 * @param url
	 * @return the JSON response as a string
	 * @throws Exception
	 */
	public static String readJson(String url) throws Exception {
		URL urlObject = new URL(url);
		URLConnection conn = urlObject.openConnection();
		conn.setRequestProperty("Accept-Charset", CHARSET);
		conn.setUseCaches(false);
		conn.setDoInput(true);
		if (conn instanceof HttpsURLConnection) {
			HttpsURLConnection secureConn = (HttpsURLConnection) conn;
			secureConn.setSSLSocketFactory(SSL_CONTEXT.getSocketFactory());
			secureConn.setHostnameVerifier(HOSTNAME_VERIFIER);
		}
		InputStream is = conn.getInputStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(is, CHARSET));
		StringBuilder json = new StringBuilder();
		char[] buffer = new char[4096];
		int len = 0;
		while ((len = reader.read(buffer)) > -1) {
			json.append(buffer, 0, len);
		}
		is.close();
		return json.toString();
	}

	/**
	 * Writes a JSON message to the specified url.
	 * 
	 * @param url
	 *            the url to write to
	 * @param json
	 *            the json message to send
	 * @return the http request result code
	 * @throws Exception
	 */
	public static int writeJson(String url, String json) throws Exception {
		byte[] jsonBytes = json.getBytes(CHARSET);
		URL urlObject = new URL(url);
		URLConnection conn = urlObject.openConnection();
		conn.setRequestProperty("Content-Type", "text/plain;charset=" + CHARSET);
		conn.setRequestProperty("Content-Length", "" + jsonBytes.length);
		conn.setUseCaches(false);
		conn.setDoOutput(true);
		if (conn instanceof HttpsURLConnection) {
			HttpsURLConnection secureConn = (HttpsURLConnection) conn;
			secureConn.setSSLSocketFactory(SSL_CONTEXT.getSocketFactory());
			secureConn.setHostnameVerifier(HOSTNAME_VERIFIER);
		}

		// write json body
		OutputStream os = conn.getOutputStream();
		os.write(jsonBytes);
		os.close();

		int status = ((HttpURLConnection) conn).getResponseCode();
		return status;
	}

	/**
	 * DummyTrustManager trusts all certificates.
	 */
	private static class DummyTrustManager implements X509TrustManager {

		@Override
		public void checkClientTrusted(X509Certificate[] certs, String authType)
				throws CertificateException {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] certs, String authType)
				throws CertificateException {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}
	}

	/**
	 * Trusts all hostnames from a certificate, including self-signed certs.
	 */
	private static class DummyHostnameVerifier implements HostnameVerifier {
		@Override
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	}
}
