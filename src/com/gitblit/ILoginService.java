package com.gitblit;

import com.gitblit.wicket.models.UserModel;

public interface ILoginService {

	UserModel authenticate(String username, char[] password);

	UserModel authenticate(char[] cookie);
	
	UserModel getUserModel(String username);
	
	boolean updateUserModel(UserModel model);
	
	boolean deleteUserModel(UserModel model);
	
}
