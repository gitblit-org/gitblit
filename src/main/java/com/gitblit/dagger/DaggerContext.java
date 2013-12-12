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
package com.gitblit.dagger;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dagger.ObjectGraph;

/**
 * Dagger servlet context listener is a context listener that uses Dagger to
 * instantiate and inject servlets, filters, and anything else you might want.
 *
 * @author James Moger
 *
 */
public abstract class DaggerContext implements ServletContextListener {

	public static final String INJECTOR_NAME = ObjectGraph.class.getName();

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected abstract Object [] getModules();

	protected abstract void destroyContext(ServletContext context);

	protected ObjectGraph getInjector(ServletContext context) {
		Object o = context.getAttribute(INJECTOR_NAME);
		if (o == null) {
			logger.debug("instantiating Dagger modules");
			Object [] modules = getModules();
			logger.debug("getting Dagger injector");
			try {
				o = ObjectGraph.create(modules);
				logger.debug("setting Dagger injector into {} attribute", INJECTOR_NAME);
				context.setAttribute(INJECTOR_NAME, o);
			} catch (Throwable t) {
				logger.error("an error occurred creating the Dagger injector", t);
			}
		}
		return (ObjectGraph) o;
	}

	@Override
	public final void contextDestroyed(ServletContextEvent contextEvent) {
		ServletContext context = contextEvent.getServletContext();
		context.setAttribute(INJECTOR_NAME, null);
		destroyContext(context);
	}
}
