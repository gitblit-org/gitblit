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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	public static enum BuildType {
		RUNTIME, COMPILETIME;
	}

	public static void main(String... args) {
		runtime();
		compiletime();
		buildSettingKeys();
	}

	public static void runtime() {
		downloadFromApache(MavenObject.JCOMMANDER, BuildType.RUNTIME);
		downloadFromApache(MavenObject.JETTY, BuildType.RUNTIME);
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

		downloadFromEclipse(MavenObject.JGIT, BuildType.RUNTIME);
		downloadFromEclipse(MavenObject.JGIT_HTTP, BuildType.RUNTIME);
	}

	public static void compiletime() {
		downloadFromApache(MavenObject.JUNIT, BuildType.RUNTIME);
		downloadFromApache(MavenObject.JCOMMANDER, BuildType.COMPILETIME);
		downloadFromApache(MavenObject.JETTY, BuildType.COMPILETIME);
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

		downloadFromEclipse(MavenObject.JGIT, BuildType.COMPILETIME);
		downloadFromEclipse(MavenObject.JGIT_HTTP, BuildType.COMPILETIME);

		// needed for site publishing
		downloadFromApache(MavenObject.COMMONSNET, BuildType.RUNTIME);
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

		// Determine static key group classes
		Map<String, List<String>> staticClasses = new HashMap<String, List<String>>();
		staticClasses.put("", new ArrayList<String>());
		for (String key : keys) {
			String clazz = "";
			String field = key;
			if (key.indexOf('.') > -1) {
				clazz = key.substring(0, key.indexOf('.'));
				field = key.substring(key.indexOf('.') + 1);
			}
			if (!staticClasses.containsKey(clazz)) {
				staticClasses.put(clazz, new ArrayList<String>());
			}
			staticClasses.get(clazz).add(field);
		}

		// Assemble Keys source file
		StringBuilder sb = new StringBuilder();
		sb.append("package com.gitblit;\n");
		sb.append('\n');
		sb.append("/*\n");
		sb.append(" * This class is auto-generated from the properties file.\n");
		sb.append(" * Do not version control!\n");
		sb.append(" */\n");
		sb.append("public final class Keys {\n");
		sb.append('\n');
		List<String> classSet = new ArrayList<String>(staticClasses.keySet());
		Collections.sort(classSet);
		for (String clazz : classSet) {
			List<String> keySet = staticClasses.get(clazz);
			if (clazz.equals("")) {
				// root keys
				for (String key : keySet) {
					sb.append(MessageFormat.format(
							"\tpublic static final String {0} = \"{1}\";\n\n",
							key.replace('.', '_'), key));
				}
			} else {
				// class keys
				sb.append(MessageFormat.format("\tpublic static final class {0} '{'\n\n", clazz));
				sb.append(MessageFormat.format(
						"\t\tpublic static final String _ROOT = \"{0}\";\n\n", clazz));
				for (String key : keySet) {
					sb.append(MessageFormat.format(
							"\t\tpublic static final String {0} = \"{1}\";\n\n",
							key.replace('.', '_'), clazz + "." + key));
				}
				sb.append("\t}\n\n");
			}
		}
		sb.append('}');

		// Save Keys class definition
		try {
			File file = new File("src/com/gitblit/Keys.java");
			FileWriter fw = new FileWriter(file, false);
			fw.write(sb.toString());
			fw.close();
		} catch (Throwable t) {
			t.printStackTrace();
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
			jars = new String[] { "-sources", "-javadoc" };
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
					}
				}
				in.close();
				updateDownload(1f, targetFile);

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

	private static class MavenObject {

		public static final MavenObject JCOMMANDER = new MavenObject("jCommander", "com/beust",
				"jcommander", "1.17", 34000, 32000, 141000,
				"219a3540f3b27d7cc3b1d91d6ea046cd8723290e",
				"0bb50eec177acf0e94d58e0cf07262fe5164331d",
				"c7adc475ca40c288c93054e0f4fe58f3a98c0cb5");

		public static final MavenObject JETTY = new MavenObject("Jetty",
				"org/eclipse/jetty/aggregate", "jetty-webapp", "7.4.2.v20110526", 1000000, 680000,
				2720000, "56331143afa22d24d9faba96e86e6371b0686c7c",
				"9f38230fd589e29c8be0fc3c80fb51c5093c2e1e",
				"0d48212889c25252c5c14bef62703e28215d80cc");

		public static final MavenObject SERVLET = new MavenObject("Servlet 2.5", "javax/servlet",
				"servlet-api", "2.5", 105000, 158000, 0,
				"5959582d97d8b61f4d154ca9e495aafd16726e34",
				"021599814ad9a605b86f3e6381571beccd861a32", null);

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
				"org/apache/wicket", "wicket", "1.4.17", 1960000, 1906000, 6818000,
				"39815e37a6f56465b2d2c3d3017c4f3bf17db50a",
				"a792ebae4123253ffd039c3be49e773f8622f94e",
				"f2f244ca72d10081529b017e89d6276eab62c621");

		public static final MavenObject WICKET_EXT = new MavenObject("Apache Wicket Extensions",
				"org/apache/wicket", "wicket-extensions", "1.4.17", 1180000, 1118000, 1458000,
				"01111d0dbffdc425581b006a43864c22797ce72a",
				"f194f40ea6e361bb745dfa22e2f9171eb63a9355",
				"bd42e5ba9444a426bb2d7cacce91c6033b663b57");

		public static final MavenObject WICKET_AUTH_ROLES = new MavenObject(
				"Apache Wicket Auth Roles", "org/apache/wicket", "wicket-auth-roles", "1.4.17",
				44000, 45000, 166000, "86d20ff32f62d3026213ff11a78555da643bc676",
				"37e815350a2d6b97734b250a8a03d8bf3712bba7",
				"ac3896368bfb372d178041a4ac3ee2c44f62e21c");

		public static final MavenObject WICKET_GOOGLE_CHARTS = new MavenObject(
				"Apache Wicket Google Charts Add-On", "org/wicketstuff", "googlecharts", "1.4.17",
				34000, 18750, 161000, "c567b98b0c5efe4147e77ef2d0d3c2d45c49dea5",
				"3d32d958b2f7aa58388af5701ea3aafc433e573f",
				"c37518b67ea85af485dd61fe854137eeacc50318");

		public static final MavenObject JUNIT = new MavenObject("JUnit", "junit", "junit", "4.8.2",
				237000, 0, 0, "c94f54227b08100974c36170dcb53329435fe5ad", "", "");

		public static final MavenObject MARKDOWNPAPERS = new MavenObject("MarkdownPapers",
				"org/tautua/markdownpapers", "markdownpapers-core", "1.1.0", 87000, 58000, 278000,
				"b879b4720fa642d3c490ab559af132daaa16dbb4",
				"d98c53939815be2777d5a56dcdc3bbc9ddb468fa",
				"4c09d2d3073e85b973572292af00bd69681df76b");

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
				"org.eclipse.jgit", "1.0.0.201106090707-r", 1318000, 1354000, 2993000,
				"34e70691382d67ee5c84ef207fb8d3784594ba2c",
				"78dbd385cf40cb266f4fb2de8651b288a72f4e2d",
				"dab55685bb6eee8d07cc87faf0cedaa3f9d04a0d");

		public static final MavenObject JGIT_HTTP = new MavenObject("JGit", "org/eclipse/jgit",
				"org.eclipse.jgit.http.server", "1.0.0.201106090707-r", 68000, 62000, 99000,
				"35e22f7000af95d0c90caaf2012071ef3734ff59",
				"4a2368beb1e9db4a6a0d609b7b869f218bf8e7a9",
				"3100ce7c40d6968481a12377c59c708cda2d17b5");

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

		public static final MavenObject JDOM = new MavenObject("jdom", "org/jdom", "jdom", "1.1",
				153000, 235000, 445000, "1d04c0f321ea337f3661cf7ede8f4c6f653a8fdd",
				"a7ed425c4c46605b8f2bf2ee118c1609682f4f2c",
				"f3df91edccba2f07a0fced70887c2f7b7836cb75");

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
