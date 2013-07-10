package com.gitblit.utils;

import com.gitblit.IStoredSettings;

public class ModelUtils
{
    private static final String DEFAULT_USER_REPO_PREFIX = "~";

    private static String userRepoPrefix = DEFAULT_USER_REPO_PREFIX;



    public static void setUserRepoPrefix(IStoredSettings settings)
    {
        userRepoPrefix = settings.getString("repo.userPrefix", DEFAULT_USER_REPO_PREFIX);
    }


    public static String getUserRepoPrefix()
    {
        return userRepoPrefix;
    }


    public static String getPersonalPath(String username)
    {
        return userRepoPrefix + username.toLowerCase();
    }


    public static boolean isPersonalRepository(String name)
    {
        if ( name.startsWith(getUserRepoPrefix()) ) return true;
        return false;
    }


    public static boolean isUsersPersonalRepository(String username, String name)
    {
        if ( name.equalsIgnoreCase(getPersonalPath(username)) ) return true;
        return false;
    }


    public static String getUserNameFromRepoPath(String path)
    {
        if ( !isPersonalRepository(path) ) return "";
        
        return path.substring(getUserRepoPrefix().length());
    }

}
