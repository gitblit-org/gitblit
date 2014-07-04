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

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
class PooledHttpClientProvider implements Provider<HttpClient> {

  @Override
  public HttpClient get() {
	  // TODO(davido): handle proxy
	  // TODO(davido): externalize MaxConnPerRoute && MaxConnTotal values
      return HttpClientBuilder
         .create()
         .setMaxConnPerRoute(100)
         .setMaxConnTotal(1024)
         .build();
  }
}
