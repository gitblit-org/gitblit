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
import java.io.FilenameFilter;
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

    private static final String osName = System.getProperty("os.name");

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
		downloadFromApache(MavenObject.LUCENE_QUERIES, BuildType.RUNTIME);
		downloadFromApache(MavenObject.JAKARTA_REGEXP, BuildType.RUNTIME);
		downloadFromApache(MavenObject.UNBOUND_ID, BuildType.RUNTIME);
		downloadFromApache(MavenObject.IVY, BuildType.RUNTIME);

		downloadFromEclipse(MavenObject.JGIT, BuildType.RUNTIME);
		downloadFromEclipse(MavenObject.JGIT_HTTP, BuildType.RUNTIME);
	}

	public static void compiletime() {
		downloadFromApache(MavenObject.JUNIT, BuildType.RUNTIME);
		downloadFromApache(MavenObject.HAMCREST, BuildType.RUNTIME);
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
		downloadFromApache(MavenObject.LUCENE_QUERIES, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.JAKARTA_REGEXP, BuildType.COMPILETIME);
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
		downloadFromApache(MavenObject.LUCENE, BuildType.RUNTIME);
		downloadFromApache(MavenObject.LUCENE_HIGHLIGHTER, BuildType.RUNTIME);
		downloadFromApache(MavenObject.LUCENE_MEMORY, BuildType.RUNTIME);
		downloadFromApache(MavenObject.LUCENE_QUERIES, BuildType.RUNTIME);
		downloadFromApache(MavenObject.JAKARTA_REGEXP, BuildType.RUNTIME);

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
			if ("-sources".equals(jar)) {
				File relocated = new File("ext/src", targetFile.getName());
				if (targetFile.exists()) {
					// move -sources jar to ext/src folder
					targetFile.renameTo(relocated);
				}
				// -sources jars are located in ext/src
				targetFile = relocated;
			}
			
			if (targetFile.exists()) {
				downloads.add(targetFile);
				removeObsoleteArtifacts(mo, type, targetFile.getParentFile());
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
			
			removeObsoleteArtifacts(mo, type, targetFile.getParentFile());
		}
		return downloads;
	}
	
	private static void removeObsoleteArtifacts(final MavenObject mo, final BuildType type, File folder) {
		File [] removals = folder.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				String n = name.toLowerCase();
				String dep = mo.artifact.toLowerCase();
				if (n.startsWith(dep)) {
					String suffix = "-" + mo.version;
					if (type.equals(BuildType.COMPILETIME)) {
						suffix += "-sources.jar";
					} else {
						suffix += ".jar";
					}
					if (!n.endsWith(suffix)) {
						return true;
					}
				}
				return false;
			}
		});
		
		// delete any matches
		if (removals != null) {
			for (File file : removals) {
				System.out.println("deleting " + file);
				file.delete();
			}
		}
	}

	private static void updateDownload(float progress, File file) {
		updateProgress(progress, "d/l: " + file.getName());
	}

	private static void updateProgress(float progress, String url) {
        boolean isWindows = osName.contains("Windows");
        String anim = "==========";
		int width = Math.round(anim.length() * progress);
        if (isWindows) System.out.print("\r");
		System.out.print("[");
		System.out.print(anim.substring(0, Math.min(width, anim.length())));
		for (int i = 0; i < anim.length() - width; i++) {
			System.out.print(' ');
		}
		System.out.print("] " + url);
        if (!isWindows) System.out.println();
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
				"org/eclipse/jetty/aggregate", "jetty-webapp", "7.6.7.v20120910", 1000000, 680000,
				2720000, "d621fa6419aaa37edbcab8e16a5e6b05c9527e62",
				"b505f7b493c5aa262d371d90754bded8b392ffb0",
				"");

		public static final MavenObject JETTY_AJP = new MavenObject("Jetty-AJP",
				"org/eclipse/jetty", "jetty-ajp", "7.6.7.v20120910", 32000, 22000,
				97000, "578d502bc78ed7aa1c0b6afef4cd59477041ec37", "6cfed9a1354f720fcde12ec15d5e1ae9cf97000c", "");
		
		public static final MavenObject SERVLET = new MavenObject("Servlet 3.0", "javax/servlet",
				"javax.servlet-api", "3.0.1", 84000, 211000, 0,
				"6bf0ebb7efd993e222fc1112377b5e92a13b38dd",
				"01952f91d84016a39e31346c9d18bd8c9c4a128a", null);

		public static final MavenObject SLF4JAPI = new MavenObject("SLF4J API", "org/slf4j",
				"slf4j-api", "1.6.6", 25500, 45000, 182000,
				"ce53b0a0e2cfbb27e8a59d38f79a18a5c6a8d2b0",
				"bcd0e21b1572960cefd449f8a16efab5b6b8e644",
				"4253b52aabf1c5a5f20c191a261e6ada0fcf621d");

		public static final MavenObject SLF4LOG4J = new MavenObject("SLF4J LOG4J", "org/slf4j",
				"slf4j-log4j12", "1.6.6", 9800, 9500, 52400,
				"5cd9b4fbc3ff6a97beaade3206137d76f65df805",
				"497bfac9a678118e7ff75d1f3b8c3bcc06dc9c8c",
				"69855e2a85d9249bb577df3c5076bc2cb34975d7");

		public static final MavenObject LOG4J = new MavenObject("Apache LOG4J", "log4j", "log4j",
				"1.2.17", 481000, 471000, 1455000, "5af35056b4d257e4b64b9e8069c0746e8b08629f",
				"677abe279b68c5e7490d6d50c6951376238d7d3e",
				"c10c20168206896442f3192d5417815df7fcbf9a");

		public static final MavenObject WICKET = new MavenObject("Apache Wicket",
				"org/apache/wicket", "wicket", "1.4.21", 1960000, 1906000, 6818000,
				"cce9dfd3088e18a3cdcf9be33b5b76caa48dc4c6",
				"e8c2bfe2c96a2da7a0eca947a2f60dc3242e7229",
				"");

		public static final MavenObject WICKET_EXT = new MavenObject("Apache Wicket Extensions",
				"org/apache/wicket", "wicket-extensions", "1.4.21", 1180000, 1118000, 1458000,
				"fac510c7ee4399a29b927405ec3de40b67d105d8",
				"ee3409ce9ed64ad8cc8d69abbd7d63f07e10851a",
				"");

		public static final MavenObject WICKET_AUTH_ROLES = new MavenObject(
				"Apache Wicket Auth Roles", "org/apache/wicket", "wicket-auth-roles", "1.4.21",
				44000, 45000, 166000, "e78df70ca942e2e15287c393f236b32fbe6f9a30",
				"47c301212cce43a70caa72f41a9a1aefcf26a533",
				"");

		public static final MavenObject WICKET_GOOGLE_CHARTS = new MavenObject(
				"Apache Wicket Google Charts Add-On", "org/wicketstuff", "googlecharts", "1.4.21",
				34000, 18750, 161000, "73d7540267afc3a0e91ca6148d3073e050dba180",
				"627b125cc6029d4d5c59c3a910c1bef347384d97",
				"");

		public static final MavenObject JUNIT = new MavenObject("JUnit", "junit", "junit", "4.10",
				253000, 141000, 0, "e4f1766ce7404a08f45d859fb9c226fc9e41a861", "6c98d6766e72d5575f96c9479d1c1d3b865c6e25", "");

		public static final MavenObject HAMCREST = new MavenObject("Hamcrest Core", "org/hamcrest", "hamcrest-core", "1.1",
				77000, 0, 0, "860340562250678d1a344907ac75754e259cdb14", null, "");

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
				"org.eclipse.jgit", "2.1.0.201209190230-r", 1600000, 1565000, 3460000,
				"5e7296d21645a479a1054fc96f3ec8469cede137",
				"5f492aaeae1beda2a31d1efa182f5d34e76d7b77",
				"");

		public static final MavenObject JGIT_HTTP = new MavenObject("JGit", "org/eclipse/jgit",
				"org.eclipse.jgit.http.server", "2.1.0.201209190230-r", 68000, 62000, 110000,
				"0bd9e5801c246d6f8ad9268d18c45ca9915f9a50",
				"210c434c38ddcf2126af250018d5845ea41ff502",
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
				"gson", "1.7.2", 174000, 142000, 247000,
				"112366d8158749e25532ebce162232c6e0fb20a5",
				"a6fe3006df46174a9c1c56b3c51357b9bfde5874",
				"537f729ac63b6132a795a3c1f2e13b327e872333");

		public static final MavenObject MAIL = new MavenObject("javax.mail", "javax/mail", "mail",
				"1.4.3", 462000, 642000, 0, "8154bf8d666e6db154c548dc31a8d512c273f5ee",
				"5875e2729de83a4e46391f8f979ec8bd03810c10", null);

		public static final MavenObject GROOVY = new MavenObject("groovy", "org/codehaus/groovy", "groovy-all",
				"1.8.8", 6143000, 2290000, 4608000, "98a489343d3c30da817d36cbea5de11ed07bef31",
				"5f847ed18009f8a034bad3906e39f771c01728c1", "");

		public static final MavenObject LUCENE = new MavenObject("lucene", "org/apache/lucene", "lucene-core",
				"3.6.1", 1540000, 1431000, 3608000, "6ae2c83c77a1ffa5840b9151a271ab3f451f6e0c",
				"6925deb6b78e63bbcac382004f00b98133327057", "");

		public static final MavenObject LUCENE_HIGHLIGHTER = new MavenObject("lucene highlighter", "org/apache/lucene", "lucene-highlighter",
				"3.6.1", 89200, 85000, 0, "2bd49695e9891697c5f290aa94c3412dfb95b096",
				"20ae81816ce9c27186ef0f2e92a57812c9ee3b6c", "");

		public static final MavenObject LUCENE_MEMORY = new MavenObject("lucene memory", "org/apache/lucene", "lucene-memory",
				"3.6.1", 30000, 23000, 0, "8c7ca5572edea50973dc0d26cf75c27047eebe7e",
				"2e291e96d25132e002b1c8240e361d1272d113e1", "");

		public static final MavenObject LUCENE_QUERIES = new MavenObject("lucene queries", "org/apache/lucene", "lucene-queries",
				"3.6.1", 47400, 48600, 0, "4ed6022dd4aa80b932a1546e7e39e3b8bbe7acb7",
				"dc425c75d988e4975d314772035a46b6a17dcc8d", "");

		public static final MavenObject JAKARTA_REGEXP = new MavenObject("jakarta regexp", "jakarta-regexp", "jakarta-regexp",
				"1.4", 28500, 0, 0, "0ea514a179ac1dd7e81c7e6594468b9b9910d298",
				null, "");
		
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
