package com.gitblit.wicket;

import java.util.Date;
import java.util.TimeZone;

import org.apache.wicket.markup.html.WebPage;

import com.gitblit.IStoredSettings;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IGitblit;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.tickets.ITicketService;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.utils.XssFilter;

public interface GitblitWicketApp {

	public abstract void mount(String location, Class<? extends WebPage> clazz, String... parameters);

	public abstract Class<? extends WebPage> getHomePage();

	public abstract boolean isCacheablePage(String mountPoint);

	public abstract CacheControl getCacheControl(String mountPoint);

	public abstract IStoredSettings settings();

	public abstract XssFilter xssFilter();

	/**
	 * Is Gitblit running in debug mode?
	 *
	 * @return true if Gitblit is running in debug mode
	 */
	public abstract boolean isDebugMode();

	/*
	 * These methods look strange... and they are... but they are the first
	 * step towards modularization across multiple commits.
	 */
	public abstract Date getBootDate();

	public abstract Date getLastActivityDate();

	public abstract IRuntimeManager runtime();

	public abstract IPluginManager plugins();

	public abstract INotificationManager notifier();

	public abstract IUserManager users();

	public abstract IAuthenticationManager authentication();

	public abstract IPublicKeyManager keys();

	public abstract IRepositoryManager repositories();

	public abstract IProjectManager projects();

	public abstract IFederationManager federation();

	public abstract IGitblit gitblit();

	public abstract ITicketService tickets();

	public abstract TimeZone getTimezone();

}