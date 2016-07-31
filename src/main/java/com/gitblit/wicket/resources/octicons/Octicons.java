package com.gitblit.wicket.resources.octicons;

import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.resource.PackageResourceReference;


public class Octicons {
	public static void install(WebApplication app) {
		app.mountResource("/octicons/octicons.css", new PackageResourceReference(Octicons.class, "octicons.css"));
		app.mountResource("/octicons/octicons-local.ttf", new PackageResourceReference(Octicons.class, "octicons-local.ttf"));
		app.mountResource("/octicons/octicons.eot", new PackageResourceReference(Octicons.class, "octicons.eot"));
		app.mountResource("/octicons/octicons.less", new PackageResourceReference(Octicons.class, "octicons.less"));
		app.mountResource("/octicons/octicons.svg", new PackageResourceReference(Octicons.class, "octicons.svg"));
		app.mountResource("/octicons/octicons.ttf", new PackageResourceReference(Octicons.class, "octicons.ttf"));
		app.mountResource("/octicons/octicons.woff", new PackageResourceReference(Octicons.class, "octicons.woff"));
		app.mountResource("/octicons/sprockets-octicons.scss", new PackageResourceReference(Octicons.class, "sprockets-octicons.scss"));

	}
}
