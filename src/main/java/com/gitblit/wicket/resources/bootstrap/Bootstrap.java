package com.gitblit.wicket.resources.bootstrap;

import java.util.List;

import org.apache.wicket.markup.head.HeaderItem;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.resource.CssPackageResource;
import org.apache.wicket.request.resource.IResource;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.request.resource.SharedResourceReference;
import org.apache.wicket.resource.JQueryResourceReference;


public class Bootstrap {
	
	public static final String BOOTSTRAP_RESPONSIVE_CSS_RESOURCE = "bootstrap:responsiveCss";

	public static void install(WebApplication app) {
		app.mountResource("/bootstrap/css/bootstrap.css", new PackageResourceReference(Bootstrap.class, "css/bootstrap.css"));

		app.getSharedResources().add(BOOTSTRAP_RESPONSIVE_CSS_RESOURCE, new CssPackageResource(Bootstrap.class, "css/bootstrap-responsive.css", null, null, null));
		app.mountResource("/bootstrap/css/bootstrap-responsive.css", app.getSharedResources().get(BOOTSTRAP_RESPONSIVE_CSS_RESOURCE));

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

		app.mountResource("/bootstrap/js/bootstrap.js", new PackageResourceReference(Bootstrap.class, "js/bootstrap.js"){
			private static final long serialVersionUID = 1L;

			@Override
			public List<HeaderItem> getDependencies() {
				List<HeaderItem> deps = super.getDependencies();
				deps.add(JavaScriptHeaderItem.forReference(JQueryResourceReference.get()));
				return deps;
			}
		});
	}

}
