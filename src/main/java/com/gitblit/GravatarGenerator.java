package com.gitblit;

import com.gitblit.utils.ActivityUtils;

public class GravatarGenerator extends AvatarGenerator {

	public String getURL(String username, String emailaddress,
			boolean identicon, int width) {
		String email = emailaddress == null ? username.toLowerCase() : emailaddress.toLowerCase();
		if (identicon) {
			return ActivityUtils.getGravatarIdenticonUrl(email, width);
		} else {
			return ActivityUtils.getGravatarThumbnailUrl(email, width);
		}			
	}

}
