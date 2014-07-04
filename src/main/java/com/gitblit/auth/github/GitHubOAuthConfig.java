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

import java.net.MalformedURLException;
import java.net.URL;

import com.gitblit.IStoredSettings;
import com.google.common.base.CharMatcher;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
class GitHubOAuthConfig {
  private static final String GITHUB_URL = "https://github.com";
  private static final String GITHUB_API_URL = "https://api.github.com";
  protected static final String CONF_SECTION = "github";
  private static final String GITHUB_OAUTH_AUTHORIZE = "/login/oauth/authorize";
  public static final String GITHUB_OAUTH_ACCESS_TOKEN =
      "/login/oauth/access_token";
  private static final String GITHUB_GET_USER = "/user";
  //private static final String GITHUB_OAUTH_FINAL = "/oauth";
  static final String GITHUB_LOGIN = "/login";

  private final String gitHubUrl;
  private final String gitHubApiUrl;
  final String gitHubUserUrl;
  final String gitHubClientId;
  final String gitHubClientSecret;
  final String httpHeader;
  final String gitHubOAuthUrl;
  final String oAuthFinalRedirectUrl;
  final String gitHubOAuthAccessTokenUrl;
  final boolean autoLogin;

  @Inject
  GitHubOAuthConfig(IStoredSettings settings)
      throws MalformedURLException {
    httpHeader = settings.getString("httpHeader", "GITHUB_USER");
    gitHubUrl = GITHUB_URL;
    gitHubApiUrl = GITHUB_API_URL;
    gitHubClientId = settings.getString("clientId", "4711");
    gitHubClientSecret = settings.getString("clientSecret", "4712");

    gitHubOAuthUrl = getUrl(gitHubUrl, GITHUB_OAUTH_AUTHORIZE);
    gitHubOAuthAccessTokenUrl = getUrl(gitHubUrl, GITHUB_OAUTH_ACCESS_TOKEN);
    gitHubUserUrl = getUrl(gitHubApiUrl, GITHUB_GET_USER);
    oAuthFinalRedirectUrl = settings.getString("canonicalWebUrl",
        "http://locahost:8080");
    autoLogin = false;
  }

  private static String trimTrailingSlash(String url) {
    return CharMatcher.is('/').trimTrailingFrom(url);
  }

  private String getUrl(String baseUrl, String path)
      throws MalformedURLException {
    if (baseUrl.indexOf("://") > 0) {
      return new URL(new URL(baseUrl), path).toExternalForm();
    } else {
      return baseUrl + trimTrailingSlash(baseUrl) + "/"
          + CharMatcher.is('/').trimLeadingFrom(path);
    }
  }
}
