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
package com.gitblit.wicket;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Page attribute to control what date as last-modified for the browser cache.
 *
 * http://betterexplained.com/articles/how-to-optimize-your-site-with-http-caching
 * https://developers.google.com/speed/docs/best-practices/caching
 *
 * @author James Moger
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheControl {

	public static enum LastModified {
		BOOT, ACTIVITY, PROJECT, REPOSITORY, COMMIT, NONE
	}

	LastModified value() default LastModified.NONE;
}