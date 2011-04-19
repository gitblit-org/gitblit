package com.gitblit.tests;

import java.io.File;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.FileRepository;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.models.PathModel.PathChangeModel;
import com.gitblit.wicket.models.RefModel;
import com.gitblit.wicket.models.TicketModel;

public class JGitUtilsTest extends TestCase {

	private File repositoriesFolder = new File("c:/projects/git");
	private boolean exportAll = true;
	private boolean readNested = true;

	private List<String> getRepositories() {
		return JGitUtils.getRepositoryList(repositoriesFolder, exportAll, readNested);
	}

	private Repository getRepository() throws Exception {
		return new FileRepository(new File(repositoriesFolder, getRepositories().get(0)) + "/" + Constants.DOT_GIT);
	}

	public void testFindRepositories() {
		List<String> list = getRepositories();
		assertTrue("No repositories found in " + repositoriesFolder, list.size() > 0);
	}

	public void testOpenRepository() throws Exception {
		Repository r = getRepository();
		r.close();
		assertTrue("Could not find repository!", r != null);
	}

	public void testLastChangeRepository() throws Exception {
		Repository r = getRepository();
		Date date = JGitUtils.getLastChange(r);
		r.close();
		assertTrue("Could not get last repository change date!", date != null);
	}

	public void testRetrieveRevObject() throws Exception {
		Repository r = getRepository();
		RevCommit commit = JGitUtils.getCommit(r, Constants.HEAD);
		RevTree tree = commit.getTree();
		RevObject object = JGitUtils.getRevObject(r, tree, "AUTHORS");
		r.close();
		assertTrue("Object is null!", object != null);
	}

	public void testRetrieveStringContent() throws Exception {
		Repository r = getRepository();
		RevCommit commit = JGitUtils.getCommit(r, Constants.HEAD);
		RevTree tree = commit.getTree();
		RevBlob blob = (RevBlob) JGitUtils.getRevObject(r, tree, "AUTHORS");
		String content = JGitUtils.getRawContentAsString(r, blob);
		r.close();
		assertTrue("Content is null!", content != null);
	}

	public void testTicGit() throws Exception {
		Repository r = new FileRepository(new File(repositoriesFolder, "ticgit") + "/" + Constants.DOT_GIT);
		RefModel ticgit = JGitUtils.getTicketsBranch(r);
		assertTrue("Ticgit branch does not exist!", ticgit != null);
		List<TicketModel> tickets = JGitUtils.getTickets(r);
		assertTrue("No tickets found!", tickets.size() > 0);
		r.close();
	}

	public void testFilesInCommit() throws Exception {
		Repository r = getRepository();
		RevCommit commit = JGitUtils.getCommit(r, Constants.HEAD);
		List<PathChangeModel> paths = JGitUtils.getFilesInCommit(r, commit);
		r.close();
		assertTrue("No changed paths found!", paths.size() > 0);
	}

	public void testCommitDiff() throws Exception {
		Repository r = getRepository();
		RevCommit commit = JGitUtils.getCommit(r, Constants.HEAD);
		String diff = JGitUtils.getCommitDiff(r, commit, false);
		r.close();
		System.out.println(diff);
	}

}
