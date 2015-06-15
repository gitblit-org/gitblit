/*
 * Copyright 2015 gitblit.com.
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
package com.gitblit;

import com.gitblit.utils.ActivityUtils;
import com.google.inject.Singleton;

@Singleton
public class GravatarGenerator implements AvatarGenerator {

	@Override
	public String getURL(String username, String emailaddress, boolean identicon, int width) {
		String email = emailaddress == null ? username : emailaddress;
		if (identicon) {
			return ActivityUtils.getGravatarIdenticonUrl(email, width);
		} else {
			return ActivityUtils.getGravatarThumbnailUrl(email, width);
		}
	}

}
