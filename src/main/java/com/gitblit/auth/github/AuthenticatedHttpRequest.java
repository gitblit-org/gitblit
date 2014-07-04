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

import com.google.common.collect.Iterators;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

class AuthenticatedHttpRequest extends HttpServletRequestWrapper {
  private Map<String, String> headers = new HashMap<>();

  AuthenticatedHttpRequest(HttpServletRequest request,
      String key, String value) {
    super(request);
    headers.put(key, value);
  }

  @Override
  public Enumeration<String> getHeaderNames() {
    return Iterators.asEnumeration(
        Iterators.concat(Iterators.forEnumeration(super.getHeaderNames()),
            headers.keySet().iterator()));
  }

  @Override
  public String getHeader(String name) {
    String headerValue = headers.get(name);
    if (headerValue != null) {
      return headerValue;
    } else {
      return super.getHeader(name);
    }
  }
}
