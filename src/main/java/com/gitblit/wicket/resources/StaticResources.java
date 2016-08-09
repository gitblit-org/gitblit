package com.gitblit.wicket.resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.resource.PackageResourceReference;

public class StaticResources {

	public static void install(WebApplication app) {
		try {
			List<String> names = getResourceFiles();
			for (String res : names) {
				app.mountResource("/" + res, new PackageResourceReference(StaticResources.class, res));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static List<String> getResourceFiles() throws IOException {
		List<String> filenames = new ArrayList<>();

		try (InputStream in = StaticResources.class.getResourceAsStream("");
				BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
			String resource;

			while ((resource = br.readLine()) != null) {
				if (!resource.equals("StaticResources.class")){
					filenames.add(resource);
				}
			}
		}

		return filenames;
	}
}