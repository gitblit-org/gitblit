package com.gitblit;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Build {

	public static void main(String... args) {
		runtime();
		compiletime();
	}

	public static void runtime() {
		downloadFromMaven(MavenObject.JCOMMANDER);
		downloadFromMaven(MavenObject.JETTY);
		downloadFromMaven(MavenObject.SERVLET);
		downloadFromMaven(MavenObject.SLF4JAPI);
		downloadFromMaven(MavenObject.SLF4LOG4J);
		downloadFromMaven(MavenObject.LOG4J);
		downloadFromMaven(MavenObject.WICKET);
		downloadFromMaven(MavenObject.WICKET_EXT);
		downloadFromMaven(MavenObject.WICKET_AUTH_ROLES);
	}

	public static void compiletime() {
		downloadFromMaven(MavenObject.JUNIT);
	}

	/**
	 * Download a file from a Maven repository.
	 * 
	 * @param mo
	 *            the maven object to download.
	 * @return
	 */
	private static File downloadFromMaven(MavenObject mo) {
		File targetFile = mo.getLocalFile("ext");
		if (targetFile.exists()) {
			return targetFile;
		}

		String mavenURL = "http://repo1.maven.org/maven2/" + mo.getRepositoryPath();
		if (!targetFile.getAbsoluteFile().getParentFile().exists()) {
			boolean success = targetFile.getAbsoluteFile().getParentFile().mkdirs();
			if (!success) {
				throw new RuntimeException("Failed to create destination folder structure!");
			}
		}
		ByteArrayOutputStream buff = new ByteArrayOutputStream();
		try {
			System.out.println("Downloading " + mavenURL);
			URL url = new URL(mavenURL);
			InputStream in = new BufferedInputStream(url.openStream());
			long last = System.currentTimeMillis();
			int len = 0;
			while (true) {
				long now = System.currentTimeMillis();
				if (now > last + 200) {
					System.out.println("  downloaded " + len + " bytes");
					last = now;
				}
				int x = in.read();
				len++;
				if (x < 0) {
					break;
				}
				buff.write(x);
			}
			in.close();
		} catch (IOException e) {
			throw new RuntimeException("Error downloading " + mavenURL + " to " + targetFile, e);
		}
		byte[] data = buff.toByteArray();
		String got = getSHA1(data);
		if (mo.sha1 != null && !got.equals(mo.sha1)) {
			throw new RuntimeException("SHA1 checksum mismatch; got: " + got);
		}
		try {
			RandomAccessFile ra = new RandomAccessFile(targetFile, "rw");
			ra.write(data);
			ra.setLength(data.length);
			ra.close();
		} catch (IOException e) {
			throw new RuntimeException("Error writing to file " + targetFile, e);
		}
		return targetFile;
	}

	/**
	 * Generate the SHA1 checksum of a byte array.
	 * 
	 * @param data
	 *            the byte array
	 * @return the SHA1 checksum
	 */
	public static String getSHA1(byte[] data) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-1");
			byte[] value = md.digest(data);
			StringBuilder buff = new StringBuilder(value.length * 2);
			for (byte c : value) {
				int x = c & 0xff;
				buff.append(Integer.toString(x >> 4, 16)).append(Integer.toString(x & 0xf, 16));
			}
			return buff.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	private static class MavenObject {

		public static final MavenObject JCOMMANDER = new MavenObject("jCommander", "com/beust", "jcommander", "1.17", "219a3540f3b27d7cc3b1d91d6ea046cd8723290e");

		public static final MavenObject JETTY = new MavenObject("Jetty", "org/eclipse/jetty/aggregate", "jetty-all", "7.2.2.v20101205", "b9b7c812a732721c427e208c54fbb71ca17a2ee1");

		public static final MavenObject SERVLET = new MavenObject("Servlet 2.5", "javax/servlet", "servlet-api", "2.5", "5959582d97d8b61f4d154ca9e495aafd16726e34");

		public static final MavenObject SLF4JAPI = new MavenObject("SLF4J API", "org/slf4j", "slf4j-api", "1.6.1", "6f3b8a24bf970f17289b234284c94f43eb42f0e4");

		public static final MavenObject SLF4LOG4J = new MavenObject("SLF4J LOG4J", "org/slf4j", "slf4j-log4j12", "1.6.1", "bd245d6746cdd4e6203e976e21d597a46f115802");

		public static final MavenObject LOG4J = new MavenObject("Apache LOG4J", "log4j", "log4j", "1.2.16", "7999a63bfccbc7c247a9aea10d83d4272bd492c6");

		public static final MavenObject WICKET = new MavenObject("Apache Wicket", "org/apache/wicket", "wicket", "1.4.17", "39815e37a6f56465b2d2c3d3017c4f3bf17db50a");

		public static final MavenObject WICKET_EXT = new MavenObject("Apache Wicket Extensions", "org/apache/wicket", "wicket-extensions", "1.4.17", "01111d0dbffdc425581b006a43864c22797ce72a");

		public static final MavenObject WICKET_AUTH_ROLES = new MavenObject("Apache Wicket Auth Roles", "org/apache/wicket", "wicket-auth-roles", "1.4.17", "86d20ff32f62d3026213ff11a78555da643bc676");
		
		public static final MavenObject JUNIT = new MavenObject("JUnit", "junit", "junit", "3.8.2", "07e4cde26b53a9a0e3fe5b00d1dbbc7cc1d46060");

		public final String name;
		public final String group;
		public final String artifact;
		public final String version;
		public final String sha1;

		private MavenObject(String name, String group, String artifact, String version, String sha1) {
			this.name = name;
			this.group = group;
			this.artifact = artifact;
			this.version = version;
			this.sha1 = sha1;
		}

		private String getRepositoryPath() {
			return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
		}

		private File getLocalFile(String basePath) {
			return new File(basePath, artifact + "-" + version + ".jar");
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
