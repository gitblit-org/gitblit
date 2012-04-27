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
package com.gitblit.build;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import com.gitblit.Constants;
import com.gitblit.utils.StringUtils;

/**
 * The Build class downloads runtime and compile-time jar files from the Apache
 * or Eclipse Maven repositories.
 * 
 * It also generates the Keys class from the gitblit.properties file.
 * 
 * Its important that this class have minimal compile dependencies since its
 * called very early in the build script.
 * 
 * @author James Moger
 * 
 */
public class Build {

	public interface DownloadListener {
		public void downloading(String name);
	}

	/**
	 * BuildType enumeration representing compile-time or runtime. This is used
	 * to download dependencies either for Gitblit GO runtime or for setting up
	 * a development environment.
	 */
	public static enum BuildType {
		RUNTIME, COMPILETIME;
	}

	private static DownloadListener downloadListener;

	public static void main(String... args) {
		runtime();
		compiletime();
		buildSettingKeys();
	}

	public static void runtime() {
		downloadFromApache(MavenObject.JCOMMANDER, BuildType.RUNTIME);
		downloadFromApache(MavenObject.JETTY, BuildType.RUNTIME);
		downloadFromApache(MavenObject.JETTY_AJP, BuildType.RUNTIME);
		downloadFromApache(MavenObject.SERVLET, BuildType.RUNTIME);
		downloadFromApache(MavenObject.SLF4JAPI, BuildType.RUNTIME);
		downloadFromApache(MavenObject.SLF4LOG4J, BuildType.RUNTIME);
		downloadFromApache(MavenObject.LOG4J, BuildType.RUNTIME);
		downloadFromApache(MavenObject.WICKET, BuildType.RUNTIME);
		downloadFromApache(MavenObject.WICKET_EXT, BuildType.RUNTIME);
		downloadFromApache(MavenObject.WICKET_AUTH_ROLES, BuildType.RUNTIME);
		downloadFromApache(MavenObject.WICKET_GOOGLE_CHARTS, BuildType.RUNTIME);
		downloadFromApache(MavenObject.MARKDOWNPAPERS, BuildType.RUNTIME);
		downloadFromApache(MavenObject.BOUNCYCASTLE, BuildType.RUNTIME);
		downloadFromApache(MavenObject.BOUNCYCASTLE_MAIL, BuildType.RUNTIME);
		downloadFromApache(MavenObject.JSCH, BuildType.RUNTIME);
		downloadFromApache(MavenObject.ROME, BuildType.RUNTIME);
		downloadFromApache(MavenObject.JDOM, BuildType.RUNTIME);
		downloadFromApache(MavenObject.GSON, BuildType.RUNTIME);
		downloadFromApache(MavenObject.MAIL, BuildType.RUNTIME);
		downloadFromApache(MavenObject.GROOVY, BuildType.RUNTIME);
		downloadFromApache(MavenObject.LUCENE, BuildType.RUNTIME);
		downloadFromApache(MavenObject.LUCENE_HIGHLIGHTER, BuildType.RUNTIME);
		downloadFromApache(MavenObject.LUCENE_MEMORY, BuildType.RUNTIME);
		downloadFromApache(MavenObject.UNBOUND_ID, BuildType.RUNTIME);
		downloadFromApache(MavenObject.IVY, BuildType.RUNTIME);

		downloadFromEclipse(MavenObject.JGIT, BuildType.RUNTIME);
		downloadFromEclipse(MavenObject.JGIT_HTTP, BuildType.RUNTIME);
	}

	public static void compiletime() {
		downloadFromApache(MavenObject.JUNIT, BuildType.RUNTIME);
		downloadFromApache(MavenObject.JCOMMANDER, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.JETTY, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.JETTY_AJP, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.SERVLET, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.SLF4JAPI, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.SLF4LOG4J, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.LOG4J, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.WICKET, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.WICKET_EXT, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.WICKET_AUTH_ROLES, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.WICKET_GOOGLE_CHARTS, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.MARKDOWNPAPERS, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.BOUNCYCASTLE, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.BOUNCYCASTLE_MAIL, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.JSCH, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.ROME, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.JDOM, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.GSON, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.MAIL, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.GROOVY, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.LUCENE, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.LUCENE_HIGHLIGHTER, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.LUCENE_MEMORY, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.UNBOUND_ID, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.IVY, BuildType.COMPILETIME);
		
		downloadFromEclipse(MavenObject.JGIT, BuildType.COMPILETIME);
		downloadFromEclipse(MavenObject.JGIT_HTTP, BuildType.COMPILETIME);

		// needed for site publishing
		downloadFromApache(MavenObject.COMMONSNET, BuildType.RUNTIME);
	}

	public static void federationClient() {
		downloadFromApache(MavenObject.JCOMMANDER, BuildType.RUNTIME);
		downloadFromApache(MavenObject.SERVLET, BuildType.RUNTIME);
		downloadFromApache(MavenObject.MAIL, BuildType.RUNTIME);
		downloadFromApache(MavenObject.SLF4JAPI, BuildType.RUNTIME);
		downloadFromApache(MavenObject.SLF4LOG4J, BuildType.RUNTIME);
		downloadFromApache(MavenObject.LOG4J, BuildType.RUNTIME);
		downloadFromApache(MavenObject.GSON, BuildType.RUNTIME);
		downloadFromApache(MavenObject.JSCH, BuildType.RUNTIME);

		downloadFromEclipse(MavenObject.JGIT, BuildType.RUNTIME);
	}

	public static void manager(DownloadListener listener) {
		downloadListener = listener;
		downloadFromApache(MavenObject.GSON, BuildType.RUNTIME);
		downloadFromApache(MavenObject.ROME, BuildType.RUNTIME);
		downloadFromApache(MavenObject.JDOM, BuildType.RUNTIME);
		downloadFromApache(MavenObject.JSCH, BuildType.RUNTIME);

		downloadFromEclipse(MavenObject.JGIT, BuildType.RUNTIME);
	}

	/**
	 * Builds the Keys class based on the gitblit.properties file and inserts
	 * the class source into the project source folder.
	 */
	public static void buildSettingKeys() {
		// Load all keys
		Properties properties = new Properties();
		FileInputStream is = null;
		try {
			is = new FileInputStream(Constants.PROPERTIES_FILE);
			properties.load(is);
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (Throwable t) {
					// IGNORE
				}
			}
		}
		List<String> keys = new ArrayList<String>(properties.stringPropertyNames());
		Collections.sort(keys);

		KeyGroup root = new KeyGroup();
		for (String key : keys) {
			root.addKey(key);
		}

		// Save Keys class definition
		try {
			File file = new File("src/com/gitblit/Keys.java");
			FileWriter fw = new FileWriter(file, false);
			fw.write(root.generateClass("com.gitblit", "Keys"));
			fw.close();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
	
	private static class KeyGroup {
		final KeyGroup parent;
		final String namespace;
		
		String name;
		List<KeyGroup> children;		
		List<String> fields;		
		
		KeyGroup() {
			this.parent = null;
			this.namespace = "";
			this.name = "";	
		}
		
		KeyGroup(String namespace, KeyGroup parent) {
			this.parent = parent;
			this.namespace = namespace;
			if (parent.children == null) {
				parent.children = new ArrayList<KeyGroup>();
			}
			parent.children.add(this);
		}
		
		void addKey(String key) {
			String keyspace = "";
			String field = key;
			if (key.indexOf('.') > -1) {
				keyspace = key.substring(0, key.lastIndexOf('.'));
				field = key.substring(key.lastIndexOf('.') + 1);
				KeyGroup group = addKeyGroup(keyspace);
				group.addKey(field);
			} else {
				if (fields == null) {
					fields = new ArrayList<String>();
				}
				fields.add(key);
			}
		}
				
		KeyGroup addKeyGroup(String keyspace) {
			KeyGroup parent = this;
			KeyGroup node = null;			
			String [] space = keyspace.split("\\.");
			for (int i = 0; i < space.length; i++) {
				StringBuilder namespace = new StringBuilder();
				for (int j = 0; j <= i; j++) {
					namespace.append(space[j]);
					if (j < i) {
						namespace.append('.');
					}
				}
				if (parent.children != null) {
					for (KeyGroup child : parent.children) {
						if (child.name.equals(space[i])) {
							node = child;					
						}
					}
				}
				if (node == null) {
					node = new KeyGroup(namespace.toString(), parent);
					node.name = space[i];
				}
				parent = node;
				node = null;
			}
			return parent;
		}		
		
		String fullKey(String field) {
			if (namespace.equals("")) {
				return field;
			}
			return namespace + "." + field;
		}
		
		String generateClass(String packageName, String className) {
			StringBuilder sb = new StringBuilder();
			sb.append("package ").append(packageName).append(";\n");
			sb.append('\n');
			sb.append("/*\n");
			sb.append(" * This class is auto-generated from the properties file.\n");
			sb.append(" * Do not version control!\n");
			sb.append(" */\n");
			sb.append(MessageFormat.format("public final class {0} '{'\n\n", className));
			sb.append(generateClass(this, 0));
			sb.append("}\n");
			return sb.toString();
		}
		
		String generateClass(KeyGroup group, int level) {
			String classIndent = StringUtils.leftPad("", level, '\t');
			String fieldIndent = StringUtils.leftPad("", level + 1, '\t');
			
			// begin class
			StringBuilder sb = new StringBuilder();
			if (!group.namespace.equals("")) {
				sb.append(classIndent).append(MessageFormat.format("public static final class {0} '{'\n\n", group.name));
				sb.append(fieldIndent).append(MessageFormat.format("public static final String _ROOT = \"{0}\";\n\n", group.namespace));
			}
			
			if (group.fields != null) {
				// fields
				for (String field : group.fields) {					
					sb.append(fieldIndent).append(MessageFormat.format("public static final String {0} = \"{1}\";\n\n", field, group.fullKey(field)));
				}
			}
			if (group.children != null) {
				// inner classes
				for (KeyGroup child : group.children) {
					sb.append(generateClass(child, level + 1));
				}
			}
			// end class
			if (!group.namespace.equals("")) {
				sb.append(classIndent).append("}\n\n");
			}
			return sb.toString();			
		}
	}

	/**
	 * Download a file from the official Apache Maven repository.
	 * 
	 * @param mo
	 *            the maven object to download.
	 * @return
	 */
	private static List<File> downloadFromApache(MavenObject mo, BuildType type) {
		return downloadFromMaven("http://repo1.maven.org/maven2/", mo, type);
	}

	/**
	 * Download a file from the official Eclipse Maven repository.
	 * 
	 * @param mo
	 *            the maven object to download.
	 * @return
	 */
	private static List<File> downloadFromEclipse(MavenObject mo, BuildType type) {
		return downloadFromMaven("http://download.eclipse.org/jgit/maven/", mo, type);
	}

	/**
	 * Download a file from a Maven repository.
	 * 
	 * @param mo
	 *            the maven object to download.
	 * @return
	 */
	private static List<File> downloadFromMaven(String mavenRoot, MavenObject mo, BuildType type) {
		List<File> downloads = new ArrayList<File>();
		String[] jars = { "" };
		if (BuildType.RUNTIME.equals(type)) {
			jars = new String[] { "" };
		} else if (BuildType.COMPILETIME.equals(type)) {
			jars = new String[] { "-sources" };
		}
		for (String jar : jars) {
			File targetFile = mo.getLocalFile("ext", jar);
			if (targetFile.exists()) {
				downloads.add(targetFile);
				continue;
			}
			String expectedSHA1 = mo.getSHA1(jar);
			if (expectedSHA1 == null) {
				// skip this jar
				continue;
			}
			float approximateLength = mo.getApproximateLength(jar);
			String mavenURL = mavenRoot + mo.getRepositoryPath(jar);
			if (!targetFile.getAbsoluteFile().getParentFile().exists()) {
				boolean success = targetFile.getAbsoluteFile().getParentFile().mkdirs();
				if (!success) {
					throw new RuntimeException("Failed to create destination folder structure!");
				}
			}
			if (downloadListener != null) {
				downloadListener.downloading(mo.name + "...");
			}
			ByteArrayOutputStream buff = new ByteArrayOutputStream();
			try {
				URL url = new URL(mavenURL);
				InputStream in = new BufferedInputStream(url.openStream());
				byte[] buffer = new byte[4096];
				int downloadedLen = 0;
				float lastProgress = 0f;

				updateDownload(0, targetFile);
				while (true) {
					int len = in.read(buffer);
					if (len < 0) {
						break;
					}
					downloadedLen += len;
					buff.write(buffer, 0, len);
					float progress = downloadedLen / approximateLength;
					if (progress - lastProgress >= 0.1f) {
						lastProgress = progress;
						updateDownload(progress, targetFile);
						if (downloadListener != null) {
							int percent = Math.min(100, Math.round(100 * progress));
							downloadListener.downloading(mo.name + " (" + percent + "%)");
						}
					}
				}
				in.close();
				updateDownload(1f, targetFile);
				if (downloadListener != null) {
					downloadListener.downloading(mo.name + " (100%)");
				}

			} catch (IOException e) {
				throw new RuntimeException("Error downloading " + mavenURL + " to " + targetFile, e);
			}
			byte[] data = buff.toByteArray();
			String calculatedSHA1 = StringUtils.getSHA1(data);

			System.out.println();

			if (expectedSHA1.length() == 0) {
				updateProgress(0, "sha: " + calculatedSHA1);
				System.out.println();
			} else {
				if (!calculatedSHA1.equals(expectedSHA1)) {
					throw new RuntimeException("SHA1 checksum mismatch; got: " + calculatedSHA1);
				}
			}
			try {
				RandomAccessFile ra = new RandomAccessFile(targetFile, "rw");
				ra.write(data);
				ra.setLength(data.length);
				ra.close();
			} catch (IOException e) {
				throw new RuntimeException("Error writing to file " + targetFile, e);
			}
			downloads.add(targetFile);
		}
		return downloads;
	}

	private static void updateDownload(float progress, File file) {
		updateProgress(progress, "d/l: " + file.getName());
	}

	private static void updateProgress(float progress, String url) {
		String anim = "==========";
		int width = Math.round(anim.length() * progress);
		System.out.print("\r[");
		System.out.print(anim.substring(0, Math.min(width, anim.length())));
		for (int i = 0; i < anim.length() - width; i++) {
			System.out.print(' ');
		}
		System.out.print("] " + url);
	}

	/**
	 * MavenObject represents a complete maven artifact (binary, sources, and
	 * javadoc). MavenObjects can be downloaded and checksummed to confirm
	 * authenticity.
	 */
	private static class MavenObject {

		public static final MavenObject JCOMMANDER = new MavenObject("jCommander", "com/beust",
				"jcommander", "1.17", 34000, 32000, 141000,
				"219a3540f3b27d7cc3b1d91d6ea046cd8723290e",
				"0bb50eec177acf0e94d58e0cf07262fe5164331d",
				"c7adc475ca40c288c93054e0f4fe58f3a98c0cb5");

		public static final MavenObject JETTY = new MavenObject("Jetty",
				"org/eclipse/jetty/aggregate", "jetty-webapp", "7.4.3.v20110701", 1000000, 680000,
				2720000, "bde072b178f9650e2308f0babe58a4baaa469e3c",
				"bc75f05dd4f7fa848720ac669b8b438ee4a6b146",
				"dcd42f672e734521d1a6ccc0c2f9ecded1a1a281");

		public static final MavenObject JETTY_AJP = new MavenObject("Jetty-AJP",
				"org/eclipse/jetty", "jetty-ajp", "7.4.3.v20110701", 32000, 22000,
				97000, "ddeb533bcf29e9b95555a9c0f34c1de3ab14c430", "bc4798286d705ea972643b3a0b31f46a0c53f605", "");
		
		public static final MavenObject SERVLET = new MavenObject("Servlet 3.0", "javax/servlet",
				"javax.servlet-api", "3.0.1", 84000, 211000, 0,
				"6bf0ebb7efd993e222fc1112377b5e92a13b38dd",
				"01952f91d84016a39e31346c9d18bd8c9c4a128a", null);

		public static final MavenObject SLF4JAPI = new MavenObject("SLF4J API", "org/slf4j",
				"slf4j-api", "1.6.1", 25500, 45000, 182000,
				"6f3b8a24bf970f17289b234284c94f43eb42f0e4",
				"46a386136c901748e6a3af67ebde6c22bc6b4524",
				"e223571d77769cdafde59040da235842f3326453");

		public static final MavenObject SLF4LOG4J = new MavenObject("SLF4J LOG4J", "org/slf4j",
				"slf4j-log4j12", "1.6.1", 9800, 9500, 52400,
				"bd245d6746cdd4e6203e976e21d597a46f115802",
				"7a26b08b265f55622fa1fed3bda68bbd37a465ba",
				"6e4b16bce7994e3692e82002f322a0dd2f32741e");

		public static final MavenObject LOG4J = new MavenObject("Apache LOG4J", "log4j", "log4j",
				"1.2.16", 481000, 471000, 1455000, "7999a63bfccbc7c247a9aea10d83d4272bd492c6",
				"bf945d1dc995be7fe64923625f842fbb6bf443be",
				"78aa1cbf0fa3b259abdc7d87f9f6788d785aac2a");

		public static final MavenObject WICKET = new MavenObject("Apache Wicket",
				"org/apache/wicket", "wicket", "1.4.20", 1960000, 1906000, 6818000,
				"bafe47d8ce8647cabeff691b5fc1ffd241ffee00",
				"7a6570df4ee7fbad71a38042c53780d46b5856db",
				"");

		public static final MavenObject WICKET_EXT = new MavenObject("Apache Wicket Extensions",
				"org/apache/wicket", "wicket-extensions", "1.4.20", 1180000, 1118000, 1458000,
				"5dc6353c3c69e39e6d5a0aaeedbbaf7a53e539c4",
				"c30112665f4c4874489d7df9fc8f866c57f93cc8",
				"");

		public static final MavenObject WICKET_AUTH_ROLES = new MavenObject(
				"Apache Wicket Auth Roles", "org/apache/wicket", "wicket-auth-roles", "1.4.20",
				44000, 45000, 166000, "7e8f99b96bce03cc0a115e6a70d9eed7fbcf6a4b",
				"d7d0479ecca239bd020b247e82562fe047f53620",
				"");

		public static final MavenObject WICKET_GOOGLE_CHARTS = new MavenObject(
				"Apache Wicket Google Charts Add-On", "org/wicketstuff", "googlecharts", "1.4.20",
				34000, 18750, 161000, "a4bed7d4a3632f95f3e204017ee60332a95da7c6",
				"16bda0794345b113c8dd5c8775e1ce493541dc67",
				"");

		public static final MavenObject JUNIT = new MavenObject("JUnit", "junit", "junit", "4.8.2",
				237000, 0, 0, "c94f54227b08100974c36170dcb53329435fe5ad", "", "");

		public static final MavenObject MARKDOWNPAPERS = new MavenObject("MarkdownPapers",
				"org/tautua/markdownpapers", "markdownpapers-core", "1.2.7", 87000, 58000, 268000,
				"84ac5636ac7ddfad9d2ee8456a0f4f69709b6ee0",
				"453cf00a289c46a0e4f6f019a28d2a2605f652c8",
				"");

		public static final MavenObject BOUNCYCASTLE = new MavenObject("BouncyCastle",
				"org/bouncycastle", "bcprov-jdk16", "1.46", 1900000, 1400000, 4670000,
				"ce091790943599535cbb4de8ede84535b0c1260c",
				"d2b70567594225923450d7e3f80cd022c852725e",
				"873a6fe765f33fc27df498a5d1f5bf077e503b2f");

		public static final MavenObject BOUNCYCASTLE_MAIL = new MavenObject("BouncyCastle Mail",
				"org/bouncycastle", "bcmail-jdk16", "1.46", 502000, 420000, 482000,
				"08a9233bfd6ad38ea32df5e6ff91035b650584b9",
				"3ebd62bc56854767512dc5deec0a17795f2e671d",
				"3b7c5f3938f202311bdca0bf7ed46bc0118af081");

		public static final MavenObject JGIT = new MavenObject("JGit", "org/eclipse/jgit",
				"org.eclipse.jgit", "1.3.0.201202151440-r", 1532000, 1565000, 3460000,
				"a00dc524b1d1db1abbf95407aa3e6009c3d2c157",
				"68864beaa0856f539deafecf9e1fe105a7e996c3",
				"");

		public static final MavenObject JGIT_HTTP = new MavenObject("JGit", "org/eclipse/jgit",
				"org.eclipse.jgit.http.server", "1.3.0.201202151440-r", 68000, 62000, 110000,
				"099468bdd59d6f4919d54d5b66022d3ec8077b29",
				"721ef2b857a7e92989a8f1ee688e361510303bb1",
				"");

		public static final MavenObject JSCH = new MavenObject("JSch", "com/jcraft", "jsch",
				"0.1.44-1", 214000, 211000, 413000, "2e9ae08de5a71bd0e0d3ba2558598181bfa71d4e",
				"e528f593b19b04d500992606f58b87fcfded8883",
				"d0ffadd0a4ab909d94a577b5aad43c13b617ddcb");

		public static final MavenObject COMMONSNET = new MavenObject("commons-net", "commons-net",
				"commons-net", "1.4.0", 181000, 0, 0, "eb47e8cad2dd7f92fd7e77df1d1529cae87361f7",
				"", "");

		public static final MavenObject ROME = new MavenObject("rome", "rome", "rome", "0.9",
				208000, 196000, 407000, "dee2705dd01e79a5a96a17225f5a1ae30470bb18",
				"226f851dc44fd94fe70b9c471881b71f88949cbf",
				"8d7d867b97eeb3a9196c3926da550ad042941c1b");

		public static final MavenObject JDOM = new MavenObject("jdom", "jdom", "jdom", "1.0",
				153000, 235000, 445000, "a2ac1cd690ab4c80defe7f9bce14d35934c35cec",
				"662abe0196cf554d4d7374f5d6382034171b652c",
				"");

		public static final MavenObject GSON = new MavenObject("gson", "com/google/code/gson",
				"gson", "1.7.1", 174000, 142000, 247000,
				"0697e3a1fa094a983cd12f7f6f61abf9c6ea52e2",
				"51f6f78aec2d30d0c2bfb4a5f00d456a6f7a5e7e",
				"f0872fe17d484815328538b89909d5e46d85db74");

		public static final MavenObject MAIL = new MavenObject("javax.mail", "javax/mail", "mail",
				"1.4.3", 462000, 642000, 0, "8154bf8d666e6db154c548dc31a8d512c273f5ee",
				"5875e2729de83a4e46391f8f979ec8bd03810c10", null);

		public static final MavenObject GROOVY = new MavenObject("groovy", "org/codehaus/groovy", "groovy-all",
				"1.8.5", 6143000, 2290000, 4608000, "3be3914c49ca7d8e8afb29a7772a74c30a1f1b28",
				"1435cc8c90e3a91e5fee7bb53e83aad96e93aeb7", "5a214b52286523f9e2a4b5fed526506c763fa6f1");

		public static final MavenObject LUCENE = new MavenObject("lucene", "org/apache/lucene", "lucene-core",
				"3.5.0", 1470000, 1347000, 3608000, "90ff0731fafb05c01fee4f2247140d56e9c30a3b",
				"0757113199f9c8c18c678c96d61c2c4160b9baa6", "19f8e80e5e7f6ec88a41d4f63495994692e31bf1");

		public static final MavenObject LUCENE_HIGHLIGHTER = new MavenObject("lucene highlighter", "org/apache/lucene", "lucene-highlighter",
				"3.5.0", 88000, 82334, 0, "9b38acfa185337dac65e350073a26fe2416f2b0e",
				"200a9b9857e589b9f5bc9f65ecf5daa37e19527d", "");

		public static final MavenObject LUCENE_MEMORY = new MavenObject("lucene memory", "org/apache/lucene", "lucene-memory",
				"3.5.0", 30000, 23000, 0, "7908e954e8c1b4b2463aa712b34fa4a5612e241d",
				"69b19b38d78cc3b27ea5542a14f0ebbb1625ffdd", "");
		
		public static final MavenObject UNBOUND_ID = new MavenObject("unbound id", "com/unboundid", "unboundid-ldapsdk",
				"2.3.0", 1383417, 1439721, 0, "6fde8d9fb4ee3e7e3d7e764e3ea57195971e2eb2",
				"5276d3d29630693dba99ab9f7ea54f4c471d3af1", "");
		
		public static final MavenObject IVY = new MavenObject("ivy", "org/apache/ivy", "ivy",
				"2.2.0", 948000, 744000, 0, "f9d1e83e82fc085093510f7d2e77d81d52bc2081",
				"0312527950ad0e8fbab37228fbed3bf41a6fe0a1", "");

		
		public final String name;
		public final String group;
		public final String artifact;
		public final String version;
		public final int approxLibraryLen;
		public final int approxSourcesLen;
		public final int approxJavadocLen;
		public final String librarySHA1;
		public final String sourcesSHA1;
		public final String javadocSHA1;

		private MavenObject(String name, String group, String artifact, String version,
				int approxLibraryLen, int approxSourcesLen, int approxJavadocLen,
				String librarySHA1, String sourcesSHA1, String javadocSHA1) {
			this.name = name;
			this.group = group;
			this.artifact = artifact;
			this.version = version;
			this.approxLibraryLen = approxLibraryLen;
			this.approxSourcesLen = approxSourcesLen;
			this.approxJavadocLen = approxJavadocLen;
			this.librarySHA1 = librarySHA1;
			this.sourcesSHA1 = sourcesSHA1;
			this.javadocSHA1 = javadocSHA1;
		}

		private String getRepositoryPath(String jar) {
			return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + jar
					+ ".jar";
		}

		private File getLocalFile(String basePath, String jar) {
			return new File(basePath, artifact + "-" + version + jar + ".jar");
		}

		private String getSHA1(String jar) {
			if (jar.equals("")) {
				return librarySHA1;
			} else if (jar.equals("-sources")) {
				return sourcesSHA1;
			} else if (jar.equals("-javadoc")) {
				return javadocSHA1;
			}
			return librarySHA1;
		}

		private int getApproximateLength(String jar) {
			if (jar.equals("")) {
				return approxLibraryLen;
			} else if (jar.equals("-sources")) {
				return approxSourcesLen;
			} else if (jar.equals("-javadoc")) {
				return approxJavadocLen;
			}
			return approxLibraryLen;
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
