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
package com.gitblit.authority;

import java.io.File;
import java.text.MessageFormat;
import java.util.Date;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.gitblit.ConfigUserService;
import com.gitblit.Constants;
import com.gitblit.FileSettings;
import com.gitblit.IUserService;
import com.gitblit.Keys;
import com.gitblit.MailExecutor;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.utils.X509Utils;
import com.gitblit.utils.X509Utils.X509Metadata;

/**
 * Utility class to generate self-signed certificates.
 * 
 * @author James Moger
 * 
 */
public class MakeClientCertificate {

	public static void main(String... args) throws Exception {
		Params params = new Params();
		JCommander jc = new JCommander(params);
		try {
			jc.parse(args);
		} catch (ParameterException t) {
			System.err.println(t.getMessage());
			jc.usage();
			System.exit(-1);
		}

		// Load the user list
		String us = Params.FILESETTINGS.getString(Keys.realm.userService, "users.conf");
		String ext = us.substring(us.lastIndexOf(".") + 1).toLowerCase();
		IUserService service = null;
		if (!ext.equals("conf") && !ext.equals("properties")) {
			if (us.equals("com.gitblit.LdapUserService")) {
				us = Params.FILESETTINGS.getString(Keys.realm.ldap.backingUserService, "users.conf");		
			} else if (us.equals("com.gitblit.LdapUserService")) {
				us = Params.FILESETTINGS.getString(Keys.realm.redmine.backingUserService, "users.conf");
			}
		}

		if (us.endsWith(".conf")) {
			service = new ConfigUserService(new File(us));
		} else {
			throw new RuntimeException("Unsupported user service: " + us);
		}
		
		// Confirm the user exists
		UserModel user = service.getUserModel(params.username);
		if (user == null) {
			System.out.println(MessageFormat.format("Failed to find user \"{0}\" in {1}", params.username, us));
			System.exit(-1);
		}
				
		File folder = new File(System.getProperty("user.dir"));
		X509Metadata serverMetadata = new X509Metadata("localhost", params.storePassword);		
		X509Utils.prepareX509Infrastructure(serverMetadata, folder);
		
		File caStore = new File(folder, X509Utils.CA_KEY_STORE);
		
		X509Metadata clientMetadata = new X509Metadata(params.username, params.password);
		clientMetadata.userDisplayname = user.getDisplayName();
		clientMetadata.emailAddress = user.emailAddress;
		clientMetadata.serverHostname = params.serverHostname;
		clientMetadata.passwordHint = params.hint;
		
		UserCertificateModel ucm = null;
		
		// set default values from config file
		File certificatesConfigFile = new File(folder, X509Utils.CA_CONFIG);
		FileBasedConfig config = new FileBasedConfig(certificatesConfigFile, FS.detect());
		if (certificatesConfigFile.exists()) {
			config.load();
			NewCertificateConfig certificateConfig = NewCertificateConfig.KEY.parse(config);
			certificateConfig.update(clientMetadata);
			
			ucm = UserCertificateConfig.KEY.parse(config).getUserCertificateModel(params.username);
		}
		
		// set user's specified OID values
		if (!StringUtils.isEmpty(user.organizationalUnit)) {
			clientMetadata.oids.put("OU", user.organizationalUnit);
		}
		if (!StringUtils.isEmpty(user.organization)) {
			clientMetadata.oids.put("O", user.organization);
		}
		if (!StringUtils.isEmpty(user.locality)) {
			clientMetadata.oids.put("L", user.locality);
		}
		if (!StringUtils.isEmpty(user.stateProvince)) {
			clientMetadata.oids.put("ST", user.stateProvince);
		}
		if (!StringUtils.isEmpty(user.countryCode)) {
			clientMetadata.oids.put("C", user.countryCode);
		}

		if (params.duration > 0) {
			// overriding duration from command-line parameter
			clientMetadata.notAfter = new Date(System.currentTimeMillis() + TimeUtils.ONEDAY * params.duration);
		}

		// generate zip bundle
		File zip = X509Utils.newClientBundle(clientMetadata, caStore, params.storePassword);		
		
		String indent = "  ";
		System.out.println(MessageFormat.format("Client certificate bundle generated for {0}", params.username));
		System.out.print(indent);
		System.out.println(zip);
		
		// update certificates.conf
		if (ucm == null) {
			ucm = new UserCertificateModel(new UserModel(params.username));
		}

		// save latest expiration date
		if (ucm.expires == null || clientMetadata.notAfter.after(ucm.expires)) {
			ucm.expires = clientMetadata.notAfter;
		}
		ucm.update(config);
		config.save();
		
		if (params.sendEmail) {
			if (StringUtils.isEmpty(user.emailAddress)) {
				System.out.print(indent);
				System.out.println(MessageFormat.format("User \"{0}\" does not have an email address.", user.username));
			} else {
				// send email
				MailExecutor mail = new MailExecutor(Params.FILESETTINGS);
				if (mail.isReady()) {
					Message message = mail.createMessage(user.emailAddress);
					message.setSubject("Your Gitblit client certificate for " + clientMetadata.serverHostname);

					// body of email
					String body = X509Utils.processTemplate(new File(caStore.getParentFile(), "mail.tmpl"), clientMetadata);
					if (StringUtils.isEmpty(body)) {
						body = MessageFormat.format("Hi {0}\n\nHere is your client certificate bundle.\nInside the zip file are installation instructions.", user.getDisplayName());
					}
					Multipart mp = new MimeMultipart();
					MimeBodyPart messagePart = new MimeBodyPart();
					messagePart.setText(body);
					mp.addBodyPart(messagePart);

					// attach zip
					MimeBodyPart filePart = new MimeBodyPart();
					FileDataSource fds = new FileDataSource(zip);
					filePart.setDataHandler(new DataHandler(fds));
					filePart.setFileName(fds.getName());
					mp.addBodyPart(filePart);

					message.setContent(mp);

					mail.sendNow(message);
					System.out.println();
					System.out.println("Mail sent.");
				} else {
					System.out.print(indent);
					System.out.println("Mail server is not properly configured.  Can not send email.");
				}
			}
		}
	}

	/**
	 * JCommander Parameters class for MakeClientCertificate.
	 */
	@Parameters(separators = " ")
	private static class Params {

		private static final FileSettings FILESETTINGS = new FileSettings(Constants.PROPERTIES_FILE);

		@Parameter(names = { "--username" }, description = "Username for certificate (CN)", required = true)
		public String username;

		@Parameter(names = { "--password" }, description = "Password to secure user's certificate (<=7 chars unless JCE Unlimited Strength installed)", required = true)
		public String password;

		@Parameter(names = { "--hint" }, description = "Hint for password", required = true)
		public String hint;
		
		@Parameter(names = "--duration", description = "Number of days from now until the certificate expires")
		public int duration = 0;

		@Parameter(names = "--storePassword", description = "Password for CA keystore.")
		public String storePassword = FILESETTINGS.getString(Keys.server.storePassword, "");
		
		@Parameter(names = "--server", description = "Hostname or server identity")
		public String serverHostname = Params.FILESETTINGS.getString(Keys.web.siteName, "localhost");

		@Parameter(names = "--sendEmail", description = "Send an email to the user with their bundle")
		public boolean sendEmail;
		
	}
}
