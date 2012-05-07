package com.gitblit.tests;

import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Constants;
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
		
		oldSection = Constants.CUSTOM_FIELDS_PROP_SECTION;
		oldSubSection = Constants.CUSTOM_FIELDS_PROP_SUBSECTION;
		
		Constants.CUSTOM_FIELDS_PROP_SECTION = "RepositoryModelTest";
		Constants.CUSTOM_FIELDS_PROP_SUBSECTION = "RepositoryModelTestSubSection";
	}
	
	@AfterClass
	public static void stopGitBlit() throws Exception {
		if (wasStarted == false)
			GitBlitSuite.stopGitblit();
		
		Constants.CUSTOM_FIELDS_PROP_SECTION = oldSection;
		Constants.CUSTOM_FIELDS_PROP_SUBSECTION = oldSubSection;
	}
	
	@Before
	public void initializeConfiguration() throws Exception{
		Repository r = GitBlitSuite.getHelloworldRepository();
		StoredConfig config = JGitUtils.readConfig(r);
		
		config.unsetSection(Constants.CUSTOM_FIELDS_PROP_SECTION, Constants.CUSTOM_FIELDS_PROP_SUBSECTION);
		config.setString(Constants.CUSTOM_FIELDS_PROP_SECTION, Constants.CUSTOM_FIELDS_PROP_SUBSECTION, "commitMessageRegEx", "\\d");
		config.setString(Constants.CUSTOM_FIELDS_PROP_SECTION, Constants.CUSTOM_FIELDS_PROP_SUBSECTION, "anotherProperty", "Hello");
		
		config.save();
	}
	
	@After
	public void teardownConfiguration() throws Exception {
		Repository r = GitBlitSuite.getHelloworldRepository();
		StoredConfig config = JGitUtils.readConfig(r);
		
		config.unsetSection(Constants.CUSTOM_FIELDS_PROP_SECTION, Constants.CUSTOM_FIELDS_PROP_SUBSECTION);
		config.save();
	}

	@Test
	public void testGetCustomProperty() throws Exception {
		RepositoryModel model = GitBlit.self().getRepositoryModel(
				GitBlitSuite.getHelloworldRepository().getDirectory().getName());
		
		assertEquals("\\d", model.customFields.get("commitMessageRegEx"));
		assertEquals("Hello", model.customFields.get("anotherProperty"));
	}
	
	@Test
	public void testSetCustomProperty() throws Exception {
		RepositoryModel model = GitBlit.self().getRepositoryModel(
				GitBlitSuite.getHelloworldRepository().getDirectory().getName());
		
		assertEquals("\\d", model.customFields.get("commitMessageRegEx"));
		assertEquals("Hello", model.customFields.get("anotherProperty"));
		
		assertEquals("Hello", model.customFields.put("anotherProperty", "GoodBye"));
		GitBlit.self().updateRepositoryModel(model.name, model, false);
		
		model = GitBlit.self().getRepositoryModel(
				GitBlitSuite.getHelloworldRepository().getDirectory().getName());
		
		assertEquals("\\d", model.customFields.get("commitMessageRegEx"));
		assertEquals("GoodBye", model.customFields.get("anotherProperty"));
	}

}
