/*
 * Copyright 2014 gitblit.com.
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
package com.gitblit.guice;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.servlet.InjectionContextListener;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * Guice servlet context listener is a context listener that uses Guice to
 * instantiate and inject servlets, filters, and anything else you might want.
 *
 * @author James Moger
 *
 */
public abstract class GuiceContext extends InjectionContextListener {

	public static final String INJECTOR_NAME = Injector.class.getName();

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected abstract AbstractModule [] getModules();

	protected abstract void destroyContext(ServletContext context);

	protected Injector getInjector(ServletContext context) {
		Object o = context.getAttribute(INJECTOR_NAME);
		if (o == null) {
			logger.debug("instantiating Guice modules");
			AbstractModule [] modules = getModules();
			logger.debug("getting Guice injector");
			try {
				o = Guice.createInjector(modules);
				logger.debug("setting Guice injector into {} attribute", INJECTOR_NAME);
				context.setAttribute(INJECTOR_NAME, o);
			} catch (Throwable t) {
				logger.error("an error occurred creating the Guice injector", t);
			}
		}
		return (Injector) o;
	}

	/**
	 * Instantiates an object.
	 *
	 * @param clazz
	 * @return the object
	 */
	@Override
	protected <X> X instantiate(ServletContext context, Class<X> clazz) {
		try {
			Injector injector = getInjector(context);
			return injector.getInstance(clazz);
		} catch (Throwable t) {
			logger.error(null, t);
		}
		return null;
	}

	@Override
	public final void contextDestroyed(ServletContextEvent contextEvent) {
		ServletContext context = contextEvent.getServletContext();
		context.removeAttribute(INJECTOR_NAME);
		destroyContext(context);
	}
}
