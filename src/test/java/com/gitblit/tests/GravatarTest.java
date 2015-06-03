package com.gitblit.tests;

import org.junit.Assert;
import org.junit.Test;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.AuthenticationManager;
import com.gitblit.manager.FederationManager;
import com.gitblit.manager.GitblitManager;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IGitblit;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IServicesManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.manager.NotificationManager;
import com.gitblit.manager.PluginManager;
import com.gitblit.manager.ProjectManager;
import com.gitblit.manager.RepositoryManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.ServicesManager;
import com.gitblit.manager.UserManager;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.tickets.FileTicketService;
import com.gitblit.tickets.ITicketService;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.transport.ssh.MemoryKeyManager;
import com.gitblit.utils.IdGenerator;
import com.gitblit.utils.WorkQueue;
import com.gitblit.utils.XssFilter;
import com.gitblit.utils.XssFilter.AllowXssFilter;
import com.gitblit.wicket.GitBlitWebApp;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class GravatarTest extends GitblitUnitTest {

	public static  class SomeModule extends AbstractModule {
		private final IStoredSettings settings = new MemorySettings();
		SomeModule() {
			settings.overrideSetting(Keys.web.avatarClass, "com.gitblit.GravatarGenerator");
		}
		@Override 
		protected void configure() {
			bind(IGitblit.class).to(GitblitManager.class);
			bind(IAuthenticationManager.class).to(AuthenticationManager.class);
			bind(IFederationManager.class).to(FederationManager.class);
			bind(INotificationManager.class).to(NotificationManager.class);
			bind(IPluginManager.class).to(PluginManager.class);
			bind(IRepositoryManager.class).to(RepositoryManager.class);
			bind(IProjectManager.class).to(ProjectManager.class);
			bind(IRuntimeManager.class).to(RuntimeManager.class);
			bind(IUserManager.class).to(UserManager.class);
			bind(ITicketService.class).to(FileTicketService.class);
			bind(XssFilter.class).to(AllowXssFilter.class);
			bind(IStoredSettings.class).toInstance(settings);
			bind(IPublicKeyManager.class).to(MemoryKeyManager.class);
			bind(IServicesManager.class).to(ServicesManager.class);
			bind(WorkQueue.class).toInstance(new WorkQueue(new IdGenerator(), 0));
		}
	}

	@Test
	public void oneTest() {
		Injector injector = Guice.createInjector(new SomeModule());
		GitBlitWebApp webapp = injector.getInstance(GitBlitWebApp.class);
		webapp.init();
		Assert.assertNotNull(webapp.buildAvatarUrl("username", "emailaddress", "cssClass", 10, true));
	}
	
}
