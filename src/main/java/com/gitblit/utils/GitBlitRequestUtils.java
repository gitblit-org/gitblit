package com.gitblit.utils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.wicket.Page;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;

public class GitBlitRequestUtils {
	public static HttpServletRequest getServletRequest(){
		return (HttpServletRequest)RequestCycle.get().getRequest().getContainerRequest();
	}
	
	public static HttpServletResponse getServletResponse(){
		return (HttpServletResponse)RequestCycle.get().getResponse().getContainerResponse();
	}

	public static String toAbsoluteUrl(Class<? extends Page> pageClass, PageParameters params){
		String relativeUrl = RequestCycle.get().urlFor(pageClass, params).toString();
		return RequestCycle.get().getUrlRenderer().renderFullUrl(Url.parse(relativeUrl));
	}
}

