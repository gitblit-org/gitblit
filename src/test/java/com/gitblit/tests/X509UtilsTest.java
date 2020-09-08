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
package com.gitblit.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.gitblit.models.UserModel;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.X509Utils;
import com.gitblit.utils.X509Utils.RevocationReason;
import com.gitblit.utils.X509Utils.X509Log;
import com.gitblit.utils.X509Utils.X509Metadata;

/**
 * Unit tests for X509 certificate generation.
 *
 * @author James Moger
 *
 */
public class X509UtilsTest {

	// passwords are case-sensitive and may be length-limited
	// based on the JCE policy files
	String caPassword = "aBcDeFg";
	File folder = new File(System.getProperty("user.dir"), "x509test");

	X509Log log = new X509Log() {
		@Override
		public void log(String message) {
			System.out.println(message);
		}
	};

	@Before
	public void prepare() throws Exception {
		cleanUp();
		X509Metadata goMetadata = new X509Metadata("localhost", caPassword);
		X509Utils.prepareX509Infrastructure(goMetadata, folder, log);
	}

	@After
	public void cleanUp() throws Exception {
		if (folder.exists()) {
			FileUtils.delete(folder, FileUtils.RECURSIVE);
		}
	}

	@Test
	public void testNewCA() throws Exception {
		File storeFile = new File(folder, X509Utils.CA_KEY_STORE);
		X509Utils.getPrivateKey(X509Utils.CA_ALIAS, storeFile, caPassword);
		X509Certificate cert = X509Utils.getCertificate(X509Utils.CA_ALIAS, storeFile, caPassword);
		assertEquals("O=Gitblit,OU=Gitblit,CN=Gitblit Certificate Authority", cert.getIssuerDN().getName());
	}

	@Test
	public void testCertificateUserMapping() throws Exception {
		File storeFile = new File(folder, X509Utils.CA_KEY_STORE);
		PrivateKey caPrivateKey = X509Utils.getPrivateKey(X509Utils.CA_ALIAS, storeFile, caPassword);
		X509Certificate caCert = X509Utils.getCertificate(X509Utils.CA_ALIAS, storeFile, caPassword);

		X509Metadata userMetadata = new X509Metadata("james", "james");
		userMetadata.serverHostname = "www.myserver.com";
		userMetadata.userDisplayname = "James Moger";
		userMetadata.passwordHint = "your name";
		userMetadata.oids.put("C",  "US");

		X509Certificate cert1 = X509Utils.newClientCertificate(userMetadata, caPrivateKey, caCert, storeFile.getParentFile());
		UserModel userModel1 = HttpUtils.getUserModelFromCertificate(cert1);
		assertEquals(userMetadata.commonName, userModel1.username);
		assertEquals(userMetadata.emailAddress, userModel1.emailAddress);
		assertEquals("C=US,O=Gitblit,OU=Gitblit,CN=james", cert1.getSubjectDN().getName());


		X509Certificate cert2 = X509Utils.newClientCertificate(userMetadata, caPrivateKey, caCert, storeFile.getParentFile());
		UserModel userModel2 = HttpUtils.getUserModelFromCertificate(cert2);
		assertEquals(userMetadata.commonName, userModel2.username);
		assertEquals(userMetadata.emailAddress, userModel2.emailAddress);
		assertEquals("C=US,O=Gitblit,OU=Gitblit,CN=james", cert2.getSubjectDN().getName());

		assertNotSame("Serial numbers are the same!", cert1.getSerialNumber().longValue(), cert2.getSerialNumber().longValue());
	}

	@Test
	public void testUserBundle() throws Exception {
		File storeFile = new File(folder, X509Utils.CA_KEY_STORE);

		X509Metadata userMetadata = new X509Metadata("james", "james");
		userMetadata.serverHostname = "www.myserver.com";
		userMetadata.userDisplayname = "James Moger";
		userMetadata.passwordHint = "your name";

		File zip = X509Utils.newClientBundle(userMetadata, storeFile, caPassword, log);
		assertTrue(zip.exists());

		List<String> expected = Arrays.asList(
				userMetadata.commonName + ".pem",
				userMetadata.commonName + ".p12",
				userMetadata.commonName + ".cer",
				"ca.cer",
				"README.TXT");

		ZipInputStream zis = new ZipInputStream(new FileInputStream(zip));
		ZipEntry entry = null;
		while ((entry = zis.getNextEntry()) != null) {
			assertTrue("Unexpected file: " + entry.getName(), expected.contains(entry.getName()));
		}
		zis.close();
	}

	@Test
	public void testCertificateRevocation() throws Exception {
		File storeFile = new File(folder, X509Utils.CA_KEY_STORE);
		PrivateKey caPrivateKey = X509Utils.getPrivateKey(X509Utils.CA_ALIAS, storeFile, caPassword);
		X509Certificate caCert = X509Utils.getCertificate(X509Utils.CA_ALIAS, storeFile, caPassword);

		X509Metadata userMetadata = new X509Metadata("james", "james");
		userMetadata.serverHostname = "www.myserver.com";
		userMetadata.userDisplayname = "James Moger";
		userMetadata.passwordHint = "your name";

		// generate a new client certificate
		X509Certificate cert1 = X509Utils.newClientCertificate(userMetadata, caPrivateKey, caCert, storeFile.getParentFile());

		// confirm this certificate IS NOT revoked
		File caRevocationList = new File(folder, X509Utils.CA_REVOCATION_LIST);
		assertFalse(X509Utils.isRevoked(cert1, caRevocationList));

		// revoke certificate and then confirm it IS revoked
		X509Utils.revoke(cert1, RevocationReason.ACompromise, caRevocationList, storeFile, caPassword, log);
		assertTrue(X509Utils.isRevoked(cert1, caRevocationList));

		// generate a second certificate
		X509Certificate cert2 = X509Utils.newClientCertificate(userMetadata, caPrivateKey, caCert, storeFile.getParentFile());

		// confirm second certificate IS NOT revoked
		assertTrue(X509Utils.isRevoked(cert1, caRevocationList));
		assertFalse(X509Utils.isRevoked(cert2, caRevocationList));

		// revoke second certificate and then confirm it IS revoked
		X509Utils.revoke(cert2, RevocationReason.ACompromise, caRevocationList, caPrivateKey, log);
		assertTrue(X509Utils.isRevoked(cert1, caRevocationList));
		assertTrue(X509Utils.isRevoked(cert2, caRevocationList));

		// generate a third certificate
		X509Certificate cert3 = X509Utils.newClientCertificate(userMetadata, caPrivateKey, caCert, storeFile.getParentFile());

		// confirm third certificate IS NOT revoked
		assertTrue(X509Utils.isRevoked(cert1, caRevocationList));
		assertTrue(X509Utils.isRevoked(cert2, caRevocationList));
		assertFalse(X509Utils.isRevoked(cert3, caRevocationList));

		// revoke third certificate and then confirm it IS revoked
		X509Utils.revoke(cert3, RevocationReason.ACompromise, caRevocationList, caPrivateKey, log);
		assertTrue(X509Utils.isRevoked(cert1, caRevocationList));
		assertTrue(X509Utils.isRevoked(cert2, caRevocationList));
		assertTrue(X509Utils.isRevoked(cert3, caRevocationList));
	}
}
