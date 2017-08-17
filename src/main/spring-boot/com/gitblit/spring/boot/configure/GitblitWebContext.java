package com.gitblit.spring.boot.configure;

import java.io.File;

import javax.servlet.annotation.WebListener;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.gitblit.FileSettings;
import com.gitblit.IStoredSettings;
import com.gitblit.servlet.GitblitContext;

@WebListener
@Component
public class GitblitWebContext extends GitblitContext {
    
    public GitblitWebContext(ApplicationContext env) {
        
        super((IStoredSettings)new FileSettings(env.getEnvironment().getProperty("baseFolder")+"/gitblit.properties"),new File(env.getEnvironment().getProperty("baseFolder")));
         
    }
}
