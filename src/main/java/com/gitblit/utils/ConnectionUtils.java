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
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


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

		// Disable Java 7 SNI checks
		// http://stackoverflow.com/questions/7615645/ssl-handshake-alert-unrecognized-name-error-since-upgrade-to-java-1-7-0
		System.setProperty("jsse.enableSNIExtension", "false");
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

	// Copyright (C) 2009 The Android Open Source Project
	//
	// Licensed under the Apache License, Version 2.0 (the "License");
	// you may not use this file except in compliance with the License.
	// You may obtain a copy of the License at
	//
	// http://www.apache.org/licenses/LICENSE-2.0
	//
	// Unless required by applicable law or agreed to in writing, software
	// distributed under the License is distributed on an "AS IS" BASIS,
	// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	// See the License for the specific language governing permissions and
	// limitations under the License.
	public static class BlindSSLSocketFactory extends SSLSocketFactory {
		private static final BlindSSLSocketFactory INSTANCE;

		static {
			try {
				final SSLContext context = SSLContext.getInstance("SSL");
				final TrustManager[] trustManagers = { new DummyTrustManager() };
				final SecureRandom rng = new SecureRandom();
				context.init(null, trustManagers, rng);
				INSTANCE = new BlindSSLSocketFactory(context.getSocketFactory());
			} catch (GeneralSecurityException e) {
				throw new RuntimeException("Cannot create BlindSslSocketFactory", e);
			}
		}

		public static SocketFactory getDefault() {
			return INSTANCE;
		}

		private final SSLSocketFactory sslFactory;

		private BlindSSLSocketFactory(final SSLSocketFactory sslFactory) {
			this.sslFactory = sslFactory;
		}

		@Override
		public Socket createSocket(Socket s, String host, int port, boolean autoClose)
				throws IOException {
			return sslFactory.createSocket(s, host, port, autoClose);
		}

		@Override
		public String[] getDefaultCipherSuites() {
			return sslFactory.getDefaultCipherSuites();
		}

		@Override
		public String[] getSupportedCipherSuites() {
			return sslFactory.getSupportedCipherSuites();
		}

		@Override
		public Socket createSocket() throws IOException {
			return sslFactory.createSocket();
		}

		@Override
		public Socket createSocket(String host, int port) throws IOException,
		UnknownHostException {
			return sslFactory.createSocket(host, port);
		}

		@Override
		public Socket createSocket(InetAddress host, int port) throws IOException {
			return sslFactory.createSocket(host, port);
		}

		@Override
		public Socket createSocket(String host, int port, InetAddress localHost,
				int localPort) throws IOException, UnknownHostException {
			return sslFactory.createSocket(host, port, localHost, localPort);
		}

		@Override
		public Socket createSocket(InetAddress address, int port,
				InetAddress localAddress, int localPort) throws IOException {
			return sslFactory.createSocket(address, port, localAddress, localPort);
		}
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
