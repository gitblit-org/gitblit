package com.gitblit.tests.mock;

import com.gitblit.manager.IManager;
import com.gitblit.servlet.GitblitContext;

public class MockGitblitContext extends GitblitContext
{
    public <X extends IManager> void addManager(X x)
    {
        startManager(x);
    }
}
