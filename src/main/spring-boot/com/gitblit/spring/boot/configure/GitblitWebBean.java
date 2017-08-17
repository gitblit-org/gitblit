package com.gitblit.spring.boot.configure;

import java.util.Arrays;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequestListener;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.servlet.GuiceFilter;

@Component
public class GitblitWebBean {
    @Bean(name = "Http 请求预处理器")
    @Order(value = Ordered.HIGHEST_PRECEDENCE)
    public ServletRequestListener registrationRequestContextListener(Environment environment, ApplicationContext ctx) {
        // TODO 目前一些类并没有使用注入的方式进行工作，这导致了一些配置信息与Spring中得到的配置信息不一致，暂先保留这个代码。

           return new org.springframework.web.context.request.RequestContextListener();
    }
    @Bean
    public FilterRegistrationBean getGuiceFilter(ServletContext instance) {
        Module injectors = new Module() {

            @Override
            public void configure(Binder binder) {
                // binder.bind(ServletContext.class).toInstance(instance);
            }
        };
        Injector injector = Guice.createInjector(injectors);
        GuiceFilter f = injector.getInstance(com.google.inject.servlet.GuiceFilter.class);
        //
        f = new com.google.inject.servlet.GuiceFilter();
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setFilter(f);
        // registration.addUrlPatterns("/*");
        registration.setUrlPatterns(Arrays.asList(new String[] {"/*"}));
        registration.setName("guiceFilter");
        return registration;
    }
}
