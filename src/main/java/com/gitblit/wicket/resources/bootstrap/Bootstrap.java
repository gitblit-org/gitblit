package com.gitblit.wicket.resources.bootstrap;

import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.resource.PackageResourceReference;


public class Bootstrap {

	public static void install(WebApplication app) {
		app.mountResource("/bootstrap/css/bootstrap.css", new PackageResourceReference(Bootstrap.class, "css/bootstrap.css"));
		app.mountResource("/bootstrap/css/bootstrap-responsive.css", new PackageResourceReference(Bootstrap.class, "css/bootstrap-responsive.css"));
		app.mountResource("/bootstrap/css/iconic.css", new PackageResourceReference(Bootstrap.class, "css/iconic.css"));

		app.mountResource("/bootstrap/font/iconic_fill.afm", new PackageResourceReference(Bootstrap.class, "font/iconic_fill.afm"));
		app.mountResource("/bootstrap/font/iconic_fill.css", new PackageResourceReference(Bootstrap.class, "font/iconic_fill.css"));
		app.mountResource("/bootstrap/font/iconic_fill.eot", new PackageResourceReference(Bootstrap.class, "font/iconic_fill.eot"));
		app.mountResource("/bootstrap/font/iconic_fill.otf", new PackageResourceReference(Bootstrap.class, "font/iconic_fill.otf"));
		app.mountResource("/bootstrap/font/iconic_fill.svg", new PackageResourceReference(Bootstrap.class, "font/iconic_fill.svg"));
		app.mountResource("/bootstrap/font/iconic_fill.ttf", new PackageResourceReference(Bootstrap.class, "font/iconic_fill.ttf"));
		app.mountResource("/bootstrap/font/iconic_fill.woff", new PackageResourceReference(Bootstrap.class, "font/iconic_fill.woff"));
		
		app.mountResource("/bootstrap/font/iconic_stroke.afm", new PackageResourceReference(Bootstrap.class, "font/iconic_stroke.afm"));
		app.mountResource("/bootstrap/font/iconic_stroke.css", new PackageResourceReference(Bootstrap.class, "font/iconic_stroke.css"));
		app.mountResource("/bootstrap/font/iconic_stroke.eot", new PackageResourceReference(Bootstrap.class, "font/iconic_stroke.eot"));
		app.mountResource("/bootstrap/font/iconic_stroke.otf", new PackageResourceReference(Bootstrap.class, "font/iconic_stroke.otf"));
		app.mountResource("/bootstrap/font/iconic_stroke.svg", new PackageResourceReference(Bootstrap.class, "font/iconic_stroke.svg"));
		app.mountResource("/bootstrap/font/iconic_stroke.ttf", new PackageResourceReference(Bootstrap.class, "font/iconic_stroke.ttf"));
		app.mountResource("/bootstrap/font/iconic_stroke.woff", new PackageResourceReference(Bootstrap.class, "font/iconic_stroke.woff"));

		app.mountResource("/bootstrap/img/glyphicons-halflings-white.png", new PackageResourceReference(Bootstrap.class, "img/glyphicons-halflings-white.png"));
		app.mountResource("/bootstrap/img/glyphicons-halflings.png", new PackageResourceReference(Bootstrap.class, "img/glyphicons-halflings.png"));

		app.mountResource("/bootstrap/js/bootstrap.js", new PackageResourceReference(Bootstrap.class, "js/bootstrap.js"));
	}

}
