package com.gitblit.tests;

import static org.junit.Assert.*;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.GitBlit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.JGitUtils;

public class RepositoryModelTest {
	
	private static String oldSection;
	private static String oldSubSection;
	private static boolean wasStarted = false;
	
	@BeforeClass
	public static void startGitBlit() throws Exception {
		wasStarted = GitBlitSuite.startGitblit() == false;
		
		oldSection = RepositoryModel.CUSTOM_DEFINED_PROP_SECTION;
		oldSubSection = RepositoryModel.CUSTOM_DEFINED_PROP_SUBSECTION;
		
		RepositoryModel.CUSTOM_DEFINED_PROP_SECTION = "RepositoryModelTest";
		RepositoryModel.CUSTOM_DEFINED_PROP_SUBSECTION = "RepositoryModelTestSubSection";
	}
	
	@AfterClass
	public static void stopGitBlit() throws Exception {
		if (wasStarted == false)
			GitBlitSuite.stopGitblit();
		
		RepositoryModel.CUSTOM_DEFINED_PROP_SECTION = oldSection;
		RepositoryModel.CUSTOM_DEFINED_PROP_SUBSECTION = oldSubSection;
	}
	
	@Before
	public void initializeConfiguration() throws Exception{
		Repository r = GitBlitSuite.getHelloworldRepository();
		StoredConfig config = JGitUtils.readConfig(r);
		
		config.unsetSection(RepositoryModel.CUSTOM_DEFINED_PROP_SECTION, RepositoryModel.CUSTOM_DEFINED_PROP_SUBSECTION);
		config.setString(RepositoryModel.CUSTOM_DEFINED_PROP_SECTION, RepositoryModel.CUSTOM_DEFINED_PROP_SUBSECTION, "commitMessageRegEx", "\\d");
		config.setString(RepositoryModel.CUSTOM_DEFINED_PROP_SECTION, RepositoryModel.CUSTOM_DEFINED_PROP_SUBSECTION, "anotherProperty", "Hello");
		
		config.save();
	}
	
	@After
	public void teardownConfiguration() throws Exception {
		Repository r = GitBlitSuite.getHelloworldRepository();
		StoredConfig config = JGitUtils.readConfig(r);
		
		config.unsetSection(RepositoryModel.CUSTOM_DEFINED_PROP_SECTION, RepositoryModel.CUSTOM_DEFINED_PROP_SUBSECTION);
		config.save();
	}

	@Test
	public void testGetCustomProperty() throws Exception {
		RepositoryModel model = GitBlit.self().getRepositoryModel(
				GitBlitSuite.getHelloworldRepository().getDirectory().getName());
		
		assertEquals("\\d", model.getCustomProperty("commitMessageRegEx"));
		assertEquals("Hello", model.getCustomProperty("anotherProperty"));
	}
	
	@Test
	public void testSetCustomProperty() throws Exception {
		RepositoryModel model = GitBlit.self().getRepositoryModel(
				GitBlitSuite.getHelloworldRepository().getDirectory().getName());
		
		assertEquals("\\d", model.getCustomProperty("commitMessageRegEx"));
		assertEquals("Hello", model.getCustomProperty("anotherProperty"));
		
		assertEquals("Hello", model.setCustomProperty("anotherProperty", "GoodBye"));
		
		assertEquals("\\d", model.getCustomProperty("commitMessageRegEx"));
		assertEquals("GoodBye", model.getCustomProperty("anotherProperty"));
	}

}
