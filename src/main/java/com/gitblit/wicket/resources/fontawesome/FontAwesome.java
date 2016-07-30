package com.gitblit.wicket.resources.fontawesome;

import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.resource.PackageResourceReference;

public class FontAwesome {
	public static void install(WebApplication app) {
		app.mountResource("/fontawesome/css/font-awesome.min.css", new PackageResourceReference(FontAwesome.class, "css/font-awesome.min.css"));

		app.mountResource("/fontawesome/fonts/fontawesome-webfont.eot", new PackageResourceReference(FontAwesome.class, "fonts/fontawesome-webfont.eot"));
		app.mountResource("/fontawesome/fonts/fontawesome-webfont.svg", new PackageResourceReference(FontAwesome.class, "fonts/fontawesome-webfont.svg"));
		app.mountResource("/fontawesome/fonts/fontawesome-webfont.ttf", new PackageResourceReference(FontAwesome.class, "fonts/fontawesome-webfont.ttf"));
		app.mountResource("/fontawesome/fonts/fontawesome-webfont.woff", new PackageResourceReference(FontAwesome.class, "fonts/fontawesome-webfont.woff"));
		app.mountResource("/fontawesome/fonts/fontawesome-webfont.woff2", new PackageResourceReference(FontAwesome.class, "fonts/fontawesome-webfont.woff2"));
		app.mountResource("/fontawesome/fonts/FontAwesome.otf", new PackageResourceReference(FontAwesome.class, "fonts/FontAwesome.otf"));
		
	}
}
