package com.gitblit.utils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.wicket.request.cycle.RequestCycle;

public class GitBlitRequestUtils {
	public static HttpServletRequest getServletRequest(){
		return (HttpServletRequest)RequestCycle.get().getRequest().getContainerRequest();
	}
	
	public static HttpServletResponse getServletResponse(){
		return (HttpServletResponse)RequestCycle.get().getResponse().getContainerResponse();
	}
	
}
