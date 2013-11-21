/*
 * Copyright 2013 gitblit.com.
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
package com.gitblit.servlet;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injection context listener instantiates and injects servlets, filters, and
 * anything else you might want into a servlet context.  This class provides
 * convenience methods for servlet & filter registration and also tracks
 * registered paths.
 *
 * @author James Moger
 *
 */
public abstract class InjectionContextListener implements ServletContextListener {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private final List<String> registeredPaths = new ArrayList<String>();

	protected final List<String> getRegisteredPaths() {
		return registeredPaths;
	}

	/**
	 * Hook for subclasses to manipulate context initialization before
	 * standard initialization procedure.
	 *
	 * @param context
	 */
	protected void beforeServletInjection(ServletContext context) {
		// NOOP
	}

	/**
	 * Hook for subclasses to instantiate and inject servlets and filters
	 * into the servlet context.
	 *
	 * @param context
	 */
	protected abstract void injectServlets(ServletContext context);

	/**
	 * Hook for subclasses to manipulate context initialization after
	 * servlet registration.
	 *
	 * @param context
	 */
	protected void afterServletInjection(ServletContext context) {
		// NOOP
	}

	/**
	 * Configure Gitblit from the web.xml, if no configuration has already been
	 * specified.
	 *
	 * @see ServletContextListener.contextInitialize(ServletContextEvent)
	 */
	@Override
	public final void contextInitialized(ServletContextEvent contextEvent) {
		ServletContext context = contextEvent.getServletContext();
		beforeServletInjection(context);
		injectServlets(context);
		afterServletInjection(context);
	}


	/**
	 * Registers a file path.
	 *
	 * @param context
	 * @param file
	 * @param servletClass
	 */
	protected void file(ServletContext context, String file, Class<? extends Servlet> servletClass) {
		file(context, file, servletClass, null);
	}

	/**
	 * Registers a file path with init parameters.
	 *
	 * @param context
	 * @param file
	 * @param servletClass
	 * @param initParams
	 */
	protected void file(ServletContext context, String file, Class<? extends Servlet> servletClass, Map<String, String> initParams) {
		Servlet servlet = instantiate(context, servletClass);
		ServletRegistration.Dynamic d = context.addServlet(sanitize(servletClass.getSimpleName() + file), servlet);
		d.addMapping(file);
		if (initParams != null) {
			d.setInitParameters(initParams);
		}
		registeredPaths.add(file);
	}

	/**
	 * Serves a path (trailing wildcard will be appended).
	 *
	 * @param context
	 * @param route
	 * @param servletClass
	 */
	protected void serve(ServletContext context, String route, Class<? extends Servlet> servletClass) {
		serve(context, route, servletClass, (Class<Filter>) null);
	}

	/**
	 * Serves a path (trailing wildcard will be appended) with init parameters.
	 *
	 * @param context
	 * @param route
	 * @param servletClass
	 * @param initParams
	 */
	protected void serve(ServletContext context, String route, Class<? extends Servlet> servletClass, Map<String, String> initParams) {
		Servlet servlet = instantiate(context, servletClass);
		ServletRegistration.Dynamic d = context.addServlet(sanitize(servletClass.getSimpleName() + route), servlet);
		d.addMapping(route + "*");
		if (initParams != null) {
			d.setInitParameters(initParams);
		}
		registeredPaths.add(route);
	}

	/**
	 * Serves a path (trailing wildcard will be appended) and also maps a filter
	 * to that path.
	 *
	 * @param context
	 * @param route
	 * @param servletClass
	 * @param filterClass
	 */
	protected void serve(ServletContext context, String route, Class<? extends Servlet> servletClass, Class<? extends Filter> filterClass) {
		Servlet servlet = instantiate(context, servletClass);
		ServletRegistration.Dynamic d = context.addServlet(sanitize(servletClass.getSimpleName() + route), servlet);
		d.addMapping(route + "*");
		if (filterClass != null) {
			filter(context, route + "*", filterClass);
		}
		registeredPaths.add(route);
	}

	/**
	 * Registers a path filter.
	 *
	 * @param context
	 * @param route
	 * @param filterClass
	 */
	protected void filter(ServletContext context, String route, Class<? extends Filter> filterClass) {
		filter(context, route, filterClass, null);
	}

	/**
	 * Registers a path filter with init parameters.
	 *
	 * @param context
	 * @param route
	 * @param filterClass
	 * @param initParams
	 */
	protected void filter(ServletContext context, String route, Class<? extends Filter> filterClass, Map<String, String> initParams) {
		Filter filter = instantiate(context, filterClass);
		FilterRegistration.Dynamic d = context.addFilter(sanitize(filterClass.getSimpleName() + route), filter);
		d.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, route);
		if (initParams != null) {
			d.setInitParameters(initParams);
		}
	}

	/**
	 * Limit the generated servlet/filter names to alpha-numeric values with a
	 * handful of acceptable other characters.
	 *
	 * @param name
	 * @return a sanitized name
	 */
	protected String sanitize(String name) {
		StringBuilder sb = new StringBuilder();
		for (char c : name.toCharArray()) {
			if (Character.isLetterOrDigit(c)) {
				sb.append(c);
			} else if ('-' == c) {
				sb.append(c);
			} else if ('*' == c) {
				sb.append("all");
			} else if ('.' == c) {
				sb.append('.');
			} else {
				sb.append('_');
			}
		}
		return sb.toString();
	}

	/**
	 * Instantiates an object.
	 *
	 * @param clazz
	 * @return the object
	 */
	protected <X> X instantiate(ServletContext context, Class<X> clazz) {
		try {
			return clazz.newInstance();
		} catch (Throwable t) {
			logger.error(null, t);
		}
		return null;
	}
}
