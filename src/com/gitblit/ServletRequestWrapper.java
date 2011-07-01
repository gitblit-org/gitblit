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
package com.gitblit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * ServletRequestWrapper is a pass-through/delegate wrapper class for a servlet
 * request. This class is used in conjunction with ServletFilters, such as the
 * AccessRestrictionFilter.
 * 
 * The original request is wrapped by instances of this class and this class is
 * set as the servlet request in the filter. This allows for specialized
 * implementations of request methods, like getUserPrincipal() with delegation
 * to the original request for any method not overridden.
 * 
 * This class, by itself, is not altogether interesting. Subclasses of this
 * class, however, are of interest.
 * 
 * @author James Moger
 * 
 */
public abstract class ServletRequestWrapper implements HttpServletRequest {

	protected final HttpServletRequest req;

	public ServletRequestWrapper(HttpServletRequest req) {
		this.req = req;
	}

	@Override
	public Object getAttribute(String name) {
		return req.getAttribute(name);
	}

	@Override
	public Enumeration getAttributeNames() {
		return req.getAttributeNames();
	}

	@Override
	public String getCharacterEncoding() {
		return req.getCharacterEncoding();
	}

	@Override
	public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
		req.setCharacterEncoding(env);
	}

	@Override
	public int getContentLength() {
		return req.getContentLength();
	}

	@Override
	public String getContentType() {
		return req.getContentType();
	}

	@Override
	public ServletInputStream getInputStream() throws IOException {
		return req.getInputStream();
	}

	@Override
	public String getParameter(String name) {
		return req.getParameter(name);
	}

	@Override
	public Enumeration getParameterNames() {
		return req.getParameterNames();
	}

	@Override
	public String[] getParameterValues(String name) {
		return req.getParameterValues(name);
	}

	@Override
	public Map getParameterMap() {
		return req.getParameterMap();
	}

	@Override
	public String getProtocol() {
		return req.getProtocol();
	}

	@Override
	public String getScheme() {
		return req.getScheme();
	}

	@Override
	public String getServerName() {
		return req.getServerName();
	}

	@Override
	public int getServerPort() {
		return req.getServerPort();
	}

	@Override
	public BufferedReader getReader() throws IOException {
		return req.getReader();
	}

	@Override
	public String getRemoteAddr() {
		return req.getRemoteAddr();
	}

	@Override
	public String getRemoteHost() {
		return req.getRemoteHost();
	}

	@Override
	public void setAttribute(String name, Object o) {
		req.setAttribute(name, o);
	}

	@Override
	public void removeAttribute(String name) {
		req.removeAttribute(name);
	}

	@Override
	public Locale getLocale() {
		return req.getLocale();
	}

	@Override
	public Enumeration getLocales() {
		return req.getLocales();
	}

	@Override
	public boolean isSecure() {
		return req.isSecure();
	}

	@Override
	public RequestDispatcher getRequestDispatcher(String path) {
		return req.getRequestDispatcher(path);
	}

	@Override
	@Deprecated
	public String getRealPath(String path) {
		return req.getRealPath(path);
	}

	@Override
	public int getRemotePort() {
		return req.getRemotePort();
	}

	@Override
	public String getLocalName() {
		return req.getLocalName();
	}

	@Override
	public String getLocalAddr() {
		return req.getLocalAddr();
	}

	@Override
	public int getLocalPort() {
		return req.getLocalPort();
	}

	@Override
	public String getAuthType() {
		return req.getAuthType();
	}

	@Override
	public Cookie[] getCookies() {
		return req.getCookies();
	}

	@Override
	public long getDateHeader(String name) {
		return req.getDateHeader(name);
	}

	@Override
	public String getHeader(String name) {
		return req.getHeader(name);
	}

	@Override
	public Enumeration getHeaders(String name) {
		return req.getHeaders(name);
	}

	@Override
	public Enumeration getHeaderNames() {
		return req.getHeaderNames();
	}

	@Override
	public int getIntHeader(String name) {
		return req.getIntHeader(name);
	}

	@Override
	public String getMethod() {
		return req.getMethod();
	}

	@Override
	public String getPathInfo() {
		return req.getPathInfo();
	}

	@Override
	public String getPathTranslated() {
		return req.getPathTranslated();
	}

	@Override
	public String getContextPath() {
		return req.getContextPath();
	}

	@Override
	public String getQueryString() {
		return req.getQueryString();
	}

	@Override
	public String getRemoteUser() {
		return req.getRemoteUser();
	}

	@Override
	public boolean isUserInRole(String role) {
		return req.isUserInRole(role);
	}

	@Override
	public Principal getUserPrincipal() {
		return req.getUserPrincipal();
	}

	@Override
	public String getRequestedSessionId() {
		return req.getRequestedSessionId();
	}

	@Override
	public String getRequestURI() {
		return req.getRequestURI();
	}

	@Override
	public StringBuffer getRequestURL() {
		return req.getRequestURL();
	}

	@Override
	public String getServletPath() {
		return req.getServletPath();
	}

	@Override
	public HttpSession getSession(boolean create) {
		return req.getSession(create);
	}

	@Override
	public HttpSession getSession() {
		return req.getSession();
	}

	@Override
	public boolean isRequestedSessionIdValid() {
		return req.isRequestedSessionIdValid();
	}

	@Override
	public boolean isRequestedSessionIdFromCookie() {
		return req.isRequestedSessionIdFromCookie();
	}

	@Override
	public boolean isRequestedSessionIdFromURL() {
		return req.isRequestedSessionIdFromURL();
	}

	@Override
	@Deprecated
	public boolean isRequestedSessionIdFromUrl() {
		return req.isRequestedSessionIdFromUrl();
	}
}