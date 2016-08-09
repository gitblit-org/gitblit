package com.gitblit.utils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.wicket.Page;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;

public class GitBlitRequestUtils {
	public static HttpServletRequest getServletRequest() {
		return (HttpServletRequest) RequestCycle.get().getRequest().getContainerRequest();
	}

	public static HttpServletResponse getServletResponse() {
		return (HttpServletResponse) RequestCycle.get().getResponse().getContainerResponse();
	}

	public static String toAbsoluteUrl(Class<? extends Page> pageClass, PageParameters params) {
		String relativeUrl = RequestCycle.get().urlFor(pageClass, params).toString();
		return RequestCycle.get().getUrlRenderer().renderFullUrl(Url.parse(relativeUrl));
	}

	/**
	 * COPIED FROM WICKET 1.3 Docs:
	 * 
	 * Gets a prefix to make this relative to the context root.
	 *
	 * For example, if your context root is http://server.com/myApp/ and the
	 * request is for /myApp/mountedPage/, then the prefix returned might be
	 * "../../".
	 *
	 * For a particular technology, this might return either an absolute prefix
	 * or a relative one.
	 */
	public static String getRelativePathPrefixToContextRoot() {
		// String contextUrl =
		// RequestCycle.get().getRequest().getRelativePathPrefixToContextRoot();
		// TODO: test it! i thing deeper mounted pages will not work yet
		Request r = RequestCycle.get().getRequest();
		String p = r.getPrefixToContextPath();
		return p;
	}
}
