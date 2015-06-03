package com.gitblit;

public abstract class AvatarGenerator {
	
	public abstract String getURL(String username, String emailaddress, boolean identicon, int width);

	/**
	 * A method that can extract custom settings for the avatar generator
	 * The default does nothing, it can be overridden
	 * 
	 * @param settings
	 */
	public void configure(IStoredSettings settings) {

	}

}
