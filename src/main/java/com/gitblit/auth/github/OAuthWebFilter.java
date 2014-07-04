// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.gitblit.auth.github;


import com.gitblit.manager.IAuthenticationManager;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Singleton
class OAuthWebFilter implements Filter {
  private static final Logger log = LoggerFactory
      .getLogger(OAuthWebFilter.class);

  private final GitHubOAuthConfig config;
  private final Provider<GitHubLogin> loginProvider;
  private final IAuthenticationManager authenticationManager;

  @Inject
  OAuthWebFilter(GitHubOAuthConfig config,
      Provider<GitHubLogin> loginProvider,
      IAuthenticationManager authenticationManager) {
    this.config = config;
    this.loginProvider = loginProvider;
    this.authenticationManager = authenticationManager;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    HttpSession httpSession = ((HttpServletRequest) request).getSession(false);
    // TODO(davido): FixMe: How to do that on gitblit?
    //if (authenticationManager.isIdentifiedUser()) {
    if (false) {
      if (httpSession != null) {
        httpSession.invalidate();
      }
      chain.doFilter(request, response);
      return;
    }

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    log.debug("OAuthWebFilter({})", httpRequest.getRequestURL());

    GitHubLogin ghLogin = loginProvider.get();

    if (ghLogin.isLoginRequest(httpRequest) && !ghLogin.isLoggedIn()) {
      ghLogin.login(httpRequest, httpResponse);
    } else if (config.autoLogin && !ghLogin.isLoggedIn()) {
      httpResponse.sendRedirect("/login");
    } else {
      if (ghLogin.isLoggedIn()) {
        httpRequest =
            new AuthenticatedHttpRequest(httpRequest, config.httpHeader,
                ghLogin.getUsername());
      }
      chain.doFilter(httpRequest, response);
    }
  }

  @Override
  public void destroy() {
  }
}
