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

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.LoggerFactory;

import com.gitblit.models.UserModel;
import com.gitblit.utils.X509Utils.X509Metadata;

/**
 * Collection of utility methods for http requests.
 *
 * @author James Moger
 *
 */
public class HttpUtils {

	/**
	 * Returns the Gitblit URL based on the request.
	 *
	 * @param request
	 * @return the host url
	 */
	public static String getGitblitURL(HttpServletRequest request) {
		// default to the request scheme and port
		String scheme = request.getScheme();
		int port = request.getServerPort();

		// try to use reverse-proxy server's port
        String forwardedPort = request.getHeader("X-Forwarded-Port");
        if (StringUtils.isEmpty(forwardedPort)) {
        	forwardedPort = request.getHeader("X_Forwarded_Port");
        }
        if (!StringUtils.isEmpty(forwardedPort)) {
        	// reverse-proxy server has supplied the original port
        	try {
        		port = Integer.parseInt(forwardedPort);
        	} catch (Throwable t) {
        	}
        }

		// try to use reverse-proxy server's scheme
        String forwardedScheme = request.getHeader("X-Forwarded-Proto");
        if (StringUtils.isEmpty(forwardedScheme)) {
        	forwardedScheme = request.getHeader("X_Forwarded_Proto");
        }
        if (!StringUtils.isEmpty(forwardedScheme)) {
        	// reverse-proxy server has supplied the original scheme
        	scheme = forwardedScheme;

        	if ("https".equals(scheme) && port == 80) {
        		// proxy server is https, inside server is 80
        		// this is likely because the proxy server has not supplied
        		// x-forwarded-port. since 80 is almost definitely wrong,
        		// make an educated guess that 443 is correct.
        		port = 443;
        	}
        }

		// try to use reverse-proxy's context
        String context = request.getContextPath();
        String forwardedContext = request.getHeader("X-Forwarded-Context");
        if (StringUtils.isEmpty(forwardedContext)) {
        	forwardedContext = request.getHeader("X_Forwarded_Context");
        }
        if (!StringUtils.isEmpty(forwardedContext)) {
        	context = forwardedContext;
        }

        // trim any trailing slash
        if (context.length() > 0 && context.charAt(context.length() - 1) == '/') {
        	context = context.substring(1);
        }

		// try to use reverse-proxy's hostname
		String host = request.getServerName();
		String forwardedHost = request.getHeader("X-Forwarded-Host");
		if (StringUtils.isEmpty(forwardedHost)) {
			forwardedHost = request.getHeader("X_Forwarded_Host");
		}
		if (!StringUtils.isEmpty(forwardedHost)) {
			host = forwardedHost;
		}

		// build result
		StringBuilder sb = new StringBuilder();
		sb.append(scheme);
		sb.append("://");
		sb.append(host);
		if (("http".equals(scheme) && port != 80)
				|| ("https".equals(scheme) && port != 443)) {
			sb.append(":").append(port);
		}
		sb.append(context);
		return sb.toString();
	}

	/**
	 * Returns a user model object built from attributes in the SSL certificate.
	 * This model is not retrieved from the user service.
	 *
	 * @param httpRequest
	 * @param checkValidity ensure certificate can be used now
	 * @param usernameOIDs if unspecified, CN is used as the username
	 * @return a UserModel, if a valid certificate is in the request, null otherwise
	 */
	public static UserModel getUserModelFromCertificate(HttpServletRequest httpRequest, boolean checkValidity, String... usernameOIDs) {
		if (httpRequest.getAttribute("javax.servlet.request.X509Certificate") != null) {
			X509Certificate[] certChain = (X509Certificate[]) httpRequest
					.getAttribute("javax.servlet.request.X509Certificate");
			if (certChain != null) {
				X509Certificate cert = certChain[0];
				// ensure certificate is valid
				if (checkValidity) {
					try {
						cert.checkValidity(new Date());
					} catch (CertificateNotYetValidException e) {
						LoggerFactory.getLogger(HttpUtils.class).info(MessageFormat.format("X509 certificate {0} is not yet valid", cert.getSubjectDN().getName()));
						return null;
					} catch (CertificateExpiredException e) {
						LoggerFactory.getLogger(HttpUtils.class).info(MessageFormat.format("X509 certificate {0} has expired", cert.getSubjectDN().getName()));
						return null;
					}
				}
				return getUserModelFromCertificate(cert, usernameOIDs);
			}
		}
		return null;
	}

	/**
	 * Creates a UserModel from a certificate
	 * @param cert
	 * @param usernameOids if unspecified CN is used as the username
	 * @return
	 */
	public static UserModel getUserModelFromCertificate(X509Certificate cert, String... usernameOIDs) {
		X509Metadata metadata = X509Utils.getMetadata(cert);

		UserModel user = new UserModel(metadata.commonName);
		user.emailAddress = metadata.emailAddress;
		user.isAuthenticated = false;

		if (usernameOIDs == null || usernameOIDs.length == 0) {
			// use default usename<->CN mapping
			usernameOIDs = new String [] { "CN" };
		}

		// determine username from OID fingerprint
		StringBuilder an = new StringBuilder();
		for (String oid : usernameOIDs) {
			String val = metadata.getOID(oid.toUpperCase(), null);
			if (val != null) {
				an.append(val).append(' ');
			}
		}
		user.username = an.toString().trim();
		return user;
	}

	public static X509Metadata getCertificateMetadata(HttpServletRequest httpRequest) {
		if (httpRequest.getAttribute("javax.servlet.request.X509Certificate") != null) {
			X509Certificate[] certChain = (X509Certificate[]) httpRequest
					.getAttribute("javax.servlet.request.X509Certificate");
			if (certChain != null) {
				X509Certificate cert = certChain[0];
				return X509Utils.getMetadata(cert);
			}
		}
		return null;
	}

	public static boolean isIpAddress(String address) {
		if (StringUtils.isEmpty(address)) {
			return false;
		}
		String [] fields = address.split("\\.");
		if (fields.length == 4) {
			// IPV4
			for (String field : fields) {
				try {
					int value = Integer.parseInt(field);
					if (value < 0 || value > 255) {
						return false;
					}
				} catch (Exception e) {
					return false;
				}
			}
			return true;
		}
		// TODO IPV6?
		return false;
	}
}
