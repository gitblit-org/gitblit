package com.gitblit;

import com.gitblit.wicket.User;

public interface ILoginService {

	User authenticate(String username, char[] password);

	User authenticate(char[] cookie);
}
