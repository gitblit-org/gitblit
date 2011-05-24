package com.gitblit;

import java.util.List;

import com.gitblit.wicket.models.UserModel;

public interface ILoginService {

	UserModel authenticate(String username, char[] password);

	UserModel getUserModel(String username);
	
	boolean updateUserModel(UserModel model);
	
	boolean updateUserModel(String username, UserModel model);
	
	boolean deleteUserModel(UserModel model);
	
	boolean deleteUser(String username);
	
	List<String> getAllUsernames();
	
	List<String> getUsernamesForRole(String role);
	
	boolean setUsernamesForRole(String role, List<String> usernames);
	
	boolean renameRole(String oldRole, String newRole);
	
	boolean deleteRole(String role);
}
