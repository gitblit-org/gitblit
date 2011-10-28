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

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.jgit.util.Base64;

/**
 * Utility class for establishing HTTP/HTTPS connections.
 * 
 * @author James Moger
 * 
 */
public class ConnectionUtils {

	static final String CHARSET;

	private static final SSLContext SSL_CONTEXT;

	private static final DummyHostnameVerifier HOSTNAME_VERIFIER;

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

	public static void setAuthorization(URLConnection conn, String username, char[] password) {
		if (!StringUtils.isEmpty(username) && (password != null && password.length > 0)) {
			conn.setRequestProperty(
					"Authorization",
					"Basic "
							+ Base64.encodeBytes((username + ":" + new String(password)).getBytes()));
		}
	}

	public static URLConnection openReadConnection(String url, String username, char[] password)
			throws IOException {
		URLConnection conn = openConnection(url, username, password);
		conn.setRequestProperty("Accept-Charset", ConnectionUtils.CHARSET);
		return conn;
	}

	public static URLConnection openConnection(String url, String username, char[] password)
			throws IOException {
		URL urlObject = new URL(url);
		URLConnection conn = urlObject.openConnection();
		setAuthorization(conn, username, password);
		conn.setUseCaches(false);
		conn.setDoOutput(true);
		if (conn instanceof HttpsURLConnection) {
			HttpsURLConnection secureConn = (HttpsURLConnection) conn;
			secureConn.setSSLSocketFactory(SSL_CONTEXT.getSocketFactory());
			secureConn.setHostnameVerifier(HOSTNAME_VERIFIER);
		}
		return conn;
	}

	/**
	 * DummyTrustManager trusts all certificates.
	 * 
	 * @author James Moger
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
	 * 
	 * @author James Moger
	 */
	private static class DummyHostnameVerifier implements HostnameVerifier {
		@Override
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	}
}
