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

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class OAuthProtocol {
  private static final String ME_SEPARATOR = ",";
  private static final Logger log = LoggerFactory
      .getLogger(OAuthProtocol.class);

  private final GitHubOAuthConfig config;
  private final HttpClient http;
  private final Gson gson;
  private final String state;

  @Inject
  OAuthProtocol(GitHubOAuthConfig config, HttpClient http,
      Gson gson) {
    this.config = config;
    this.http = http;
    this.gson = gson;
    this.state = generateRandomState();
  }

  void loginPhase1(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    log.debug("Initiating GitHub Login for ClientId=" + config.gitHubClientId);
    response.sendRedirect(String.format(
        "%s?client_id=%s&redirect_uri=%s&state=%s%s", config.gitHubOAuthUrl,
        config.gitHubClientId, getURLEncoded(config.oAuthFinalRedirectUrl),
        me(), getURLEncoded(request.getRequestURI().toString())));
  }

  String loginPhase2(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    HttpPost post = new HttpPost(config.gitHubOAuthAccessTokenUrl);
    post.setHeader("Accept", "application/json");
    List<NameValuePair> nvps = new ArrayList<>(3);
    nvps.add(new BasicNameValuePair("client_id", config.gitHubClientId));
    nvps.add(new BasicNameValuePair("client_secret",
        config.gitHubClientSecret));
    nvps.add(new BasicNameValuePair("code", request.getParameter("code")));
    post.setEntity(new UrlEncodedFormEntity(nvps));

    try {
      HttpResponse postResponse = http.execute(post);
      if (postResponse.getStatusLine().getStatusCode() !=
          HttpURLConnection.HTTP_OK) {
        log.error("POST " + config.gitHubOAuthAccessTokenUrl
            + " request for access token failed with status "
            + postResponse.getStatusLine());
        response.sendError(HttpURLConnection.HTTP_UNAUTHORIZED,
            "Request for access token not authorised");
        EntityUtils.consume(postResponse.getEntity());
        return null;
      }

      return getAccessToken(getAccessTokenJson(postResponse));
    } catch (IOException e) {
      log.error("POST " + config.gitHubOAuthAccessTokenUrl
          + " request for access token failed", e);
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
          "Request for access token not authorised");
      return null;
    }
  }

  String retrieveUser(String authToken) throws IOException {
    HttpGet get = new HttpGet(config.gitHubUserUrl);
    get.setHeader("Authorization", String.format("token %s", authToken));
    try {
      return getLogin(getUserJson(httpGetGitHubUserInfo(get)));
    } catch (IOException e) {
      log.error("GET {} with authToken {} request failed",
          config.gitHubUserUrl, config.gitHubOAuthAccessTokenUrl, e);
      return null;
    }
  }

  private InputStream httpGetGitHubUserInfo(HttpGet get) throws IOException,
      ClientProtocolException {
    HttpResponse resp = http.execute(get);
    int statusCode = resp.getStatusLine().getStatusCode();
    if (statusCode == HttpServletResponse.SC_OK) {
      return resp.getEntity().getContent();
    } else {
      throw new IOException(String.format(
          "Invalid HTTP status code %s returned from %s", statusCode,
          get.getURI()));
    }
  }

  private String getAccessToken(JsonElement accessTokenJson)
      throws IOException {
    JsonElement accessTokenString =
        accessTokenJson.getAsJsonObject().get("access_token");
    if (accessTokenString != null) {
      return accessTokenString.getAsString();
    } else {
      throw new IOException(String.format(
          "Invalid JSON '%s': cannot find access_token field",
          accessTokenJson));
    }
  }

  private JsonObject getAccessTokenJson(HttpResponse postResponse)
      throws UnsupportedEncodingException, IOException {
    JsonElement accessTokenJson =
        gson.fromJson(new InputStreamReader(postResponse.getEntity()
            .getContent(), Charsets.UTF_8), JsonElement.class);
    if (accessTokenJson.isJsonObject()) {
      return accessTokenJson.getAsJsonObject();
    } else {
      throw new IOException(String.format(
          "Invalid JSON '%s': not a JSON Object"));
    }
  }

  boolean isOAuthRequest(HttpServletRequest httpRequest) {
    return OAuthProtocol.isGerritLogin(httpRequest)
        || OAuthProtocol.isOAuthFinal(httpRequest);
  }

  String getTargetUrl(ServletRequest request) {
    String requestState = state(request);
    int meEnd = requestState.indexOf(ME_SEPARATOR);
    if (meEnd >= 0 && requestState.subSequence(0, meEnd).equals(state)) {
      return requestState.substring(meEnd + 1);
    } else {
      log.warn("Illegal request state '" + requestState + "' on OAuthProtocol "
          + this);
      return null;
    }
  }

  private String me() {
    return state + ME_SEPARATOR;
  }

  private JsonObject getUserJson(InputStream userContentStream)
      throws IOException {
    JsonElement userJson =
        gson.fromJson(new InputStreamReader(userContentStream,
            Charsets.UTF_8), JsonElement.class);
    if (userJson.isJsonObject()) {
      return userJson.getAsJsonObject();
    } else {
      throw new IOException(String.format(
          "Invalid JSON '%s': not a JSON Object", userJson));
    }
  }

  static boolean isOAuthFinal(HttpServletRequest request) {
    return Strings.emptyToNull(request.getParameter("code")) != null;
  }

  static boolean isGerritLogin(HttpServletRequest request) {
    return request.getRequestURI().indexOf(
        GitHubOAuthConfig.GITHUB_LOGIN) >= 0;
  }

  private static String getLogin(JsonElement userJson) throws IOException {
    JsonElement userString = userJson.getAsJsonObject().get("login");
    if (userString != null) {
      return userString.getAsString();
    } else {
      throw new IOException(String.format(
          "Invalid JSON '%s': cannot find login field", userJson));
    }
  }

  private static String generateRandomState() {
    byte[] randomState = new byte[32];
    new SecureRandom().nextBytes(randomState);
    return Base64.encodeBase64URLSafeString(randomState);
  }

  private static String getURLEncoded(String url) {
    try {
      return URLEncoder.encode(url, Charsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      // UTF-8 is hardcoded, cannot fail
      return null;
    }
  }

  private static String state(ServletRequest request) {
    return Strings.nullToEmpty(request.getParameter("state"));
  }

  @Override
  public String toString() {
    return "OAuthProtocol/" + state;
  }
}
