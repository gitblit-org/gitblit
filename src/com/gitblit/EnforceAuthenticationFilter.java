/**
 * 
 */
package com.gitblit;

import java.io.IOException;
import java.text.MessageFormat;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.UserModel;

/**
 * This filter enforces authentication via HTTP Basic Authentication, if the settings indicate so.
 * It looks at the settings "web.authenticateViewPages" and "web.enforceHttpBasicAuthentication"; if
 * both are true, any unauthorized access will be met with a HTTP Basic Authentication header.
 *
 * @author Laurens Vrijnsen
 *
 */
public class EnforceAuthenticationFilter implements Filter {
	
	protected transient Logger logger = LoggerFactory.getLogger(getClass());

	/* 
	 * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
	 */
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// nothing to be done

	} //init
	

	/* 
	 * This does the actual filtering: is the user authenticated? If not, enforce HTTP authentication (401)
	 * 
	 * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		
		/*
		 * Determine whether to enforce the BASIC authentication:
		 */
		@SuppressWarnings("static-access")
		Boolean mustForceAuth = GitBlit.self().getBoolean("web.authenticateViewPages", false)
								&& GitBlit.self().getBoolean("web.enforceHttpBasicAuthentication", false);
		
		HttpServletRequest  HttpRequest  = (HttpServletRequest)request;
		HttpServletResponse HttpResponse = (HttpServletResponse)response; 
		UserModel user = GitBlit.self().authenticate(HttpRequest);
		
		if (mustForceAuth && (user == null)) {
			// not authenticated, enforce now:
			logger.info(MessageFormat.format("EnforceAuthFilter: user not authenticated for URL {0}!", request.toString()));
			@SuppressWarnings("static-access")
			String CHALLENGE = MessageFormat.format("Basic realm=\"{0}\"", GitBlit.self().getString("web.siteName",""));
			HttpResponse.setHeader("WWW-Authenticate", CHALLENGE);
			HttpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;

		} else {
			// user is authenticated, or don't care, continue handling
			chain.doFilter( request, response );
			
		} // authenticated
	} // doFilter

	
	/* 
	 * @see javax.servlet.Filter#destroy()
	 */
	@Override
	public void destroy() {
		// Nothing to be done

	} // destroy

}
