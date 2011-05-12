package com.gitblit;

import com.gitblit.wicket.models.User;

public interface ILoginService {

	User authenticate(String username, char[] password);

	User authenticate(char[] cookie);
	
	User getUserModel(String username);
	
	boolean updateUserModel(User model);
	
	boolean deleteUserModel(User model);
	
}
