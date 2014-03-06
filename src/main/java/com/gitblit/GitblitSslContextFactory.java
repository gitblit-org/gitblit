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
package com.gitblit;

import java.io.File;
import java.security.KeyStore;
import java.security.cert.CRL;
import java.util.Collection;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.utils.StringUtils;

/**
 * Special SSL context factory that configures Gitblit GO and replaces the
 * primary trustmanager with a GitblitTrustManager.
 *
 * @author James Moger
 */
public class GitblitSslContextFactory extends SslContextFactory {

	private static final Logger logger = LoggerFactory.getLogger(GitblitSslContextFactory.class);

	private final File caRevocationList;

	public GitblitSslContextFactory(String certAlias, File keyStore, File clientTrustStore,
			String storePassword, File caRevocationList) {
		super(keyStore.getAbsolutePath());

		this.caRevocationList = caRevocationList;

		// disable renegotiation unless this is a patched JVM
		boolean allowRenegotiation = false;
		String v = System.getProperty("java.version");
		if (v.startsWith("1.7")) {
			allowRenegotiation = true;
		} else if (v.startsWith("1.6")) {
			// 1.6.0_22 was first release with RFC-5746 implemented fix.
			if (v.indexOf('_') > -1) {
				String b = v.substring(v.indexOf('_') + 1);
				if (Integer.parseInt(b) >= 22) {
					allowRenegotiation = true;
				}
			}
		}
		if (allowRenegotiation) {
			logger.info("   allowing SSL renegotiation on Java " + v);
			setAllowRenegotiate(allowRenegotiation);
		}


		if (!StringUtils.isEmpty(certAlias)) {
			logger.info("   certificate alias = " + certAlias);
			setCertAlias(certAlias);
		}
		setKeyStorePassword(storePassword);
		setTrustStore(clientTrustStore.getAbsolutePath());
		setTrustStorePassword(storePassword);

		logger.info("   keyStorePath   = " + keyStore.getAbsolutePath());
		logger.info("   trustStorePath = " + clientTrustStore.getAbsolutePath());
		logger.info("   crlPath        = " + caRevocationList.getAbsolutePath());
	}

	@Override
	protected TrustManager[] getTrustManagers(KeyStore trustStore, Collection<? extends CRL> crls)
			throws Exception {
		TrustManager[] managers = super.getTrustManagers(trustStore, crls);
		X509TrustManager delegate = (X509TrustManager) managers[0];
		GitblitTrustManager root = new GitblitTrustManager(delegate, caRevocationList);

		// replace first manager with the GitblitTrustManager
		managers[0] = root;
		return managers;
	}
}
