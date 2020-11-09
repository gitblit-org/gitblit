package com.gitblit.servlet;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.tests.mock.MockGitblitContext;
import com.gitblit.tests.mock.MockRuntimeManager;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;


import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class RawServletTest
{
    private static final char FSC = RawServlet.FSC;

    private static MockRuntimeManager mockRuntimeManager = new MockRuntimeManager();
    private static IStoredSettings settings;

    private IRepositoryManager repositoryMngr;

    private RawServlet rawServlet;


    @BeforeClass
    public static void init()
    {
        MockGitblitContext gitblitContext = new MockGitblitContext();
        gitblitContext.addManager(mockRuntimeManager);
        settings = mockRuntimeManager.getSettings();
    }

    @Before
    public void setUp()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "/");

        repositoryMngr = mock(IRepositoryManager.class);
        rawServlet = new RawServlet(mockRuntimeManager, repositoryMngr);
    }



    @Test
    public void asLink_HttpUrlRepo()
    {
        String baseUrl = "http://localhost";
        String repository = "test.git";
        String branch = null;
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/", link);
    }

    @Test
    public void asLink_HttpUrlTrailingSlashRepo()
    {
        String baseUrl = "http://localhost/";
        String repository = "test.git";
        String branch = null;
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl.substring(0, baseUrl.length()-1) + Constants.RAW_PATH + repository + "/", link);
    }

    @Test
    public void asLink_HttpUrlRepoLeadingSlash()
    {
        String baseUrl = "http://localhost";
        String repository = "/test.git";
        String branch = null;
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository.substring(1) + "/", link);
    }

    @Test
    public void asLink_HttpUrlTrailingSlashRepoLeadingSlash()
    {
        String baseUrl = "http://localhost/";
        String repository = "/test.git";
        String branch = null;
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl.substring(0, baseUrl.length()-1) + Constants.RAW_PATH + repository.substring(1) + "/", link);
    }

    @Test
    public void asLink_HttpUrlRepoBranch()
    {
        String baseUrl = "http://localhost";
        String repository = "test.git";
        String branch = "b52";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/" + branch + "/", link);
    }

    @Test
    public void asLink_HttpUrlTrailingSlashRepoBranch()
    {
        String baseUrl = "http://localhost/";
        String repository = "test.git";
        String branch = "branch";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl.substring(0, baseUrl.length()-1) + Constants.RAW_PATH + repository + "/"  + branch + "/", link);
    }

    @Test
    public void asLink_HttpUrlRepoLeadingSlashBranch()
    {
        String baseUrl = "http://localhost";
        String repository = "/test.git";
        String branch = "featureOne";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository.substring(1) + "/"  + branch + "/", link);
    }

    @Test
    public void asLink_HttpUrlTrailingSlashRepoLeadingSlashBranch()
    {
        String baseUrl = "http://localhost/";
        String repository = "/test.git";
        String branch = "b";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl.substring(0, baseUrl.length()-1) + Constants.RAW_PATH + repository.substring(1) + "/"  + branch + "/", link);
    }


    @Test
    public void asLink_HttpUrlRepoBranchWithSlash()
    {
        String baseUrl = "http://localhost";
        String repository = "test.git";
        String branch = "feature/whatever";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/" + branch.replace('/', FSC) + "/", link);
    }

    @Test
    public void asLink_HttpUrlTrailingSlashRepoBranchWithSlash()
    {
        String baseUrl = "http://localhost/";
        String repository = "test.git";
        String branch = "branch/for/issue/16";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl.substring(0, baseUrl.length()-1) + Constants.RAW_PATH + repository + "/"
                + branch.replace('/', FSC) + "/", link);
    }

    @Test
    public void asLink_HttpUrlRepoLeadingSlashBranchWithSlash()
    {
        String baseUrl = "http://localhost";
        String repository = "/test.git";
        String branch = "releases/1.2.3";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository.substring(1) + "/"
                + branch.replace('/', FSC) + "/", link);
    }

    @Test
    public void asLink_HttpUrlTrailingSlashRepoLeadingSlashBranchWithSlash()
    {
        String baseUrl = "http://localhost/";
        String repository = "/test.git";
        String branch = "b/52";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl.substring(0, baseUrl.length()-1) + Constants.RAW_PATH + repository.substring(1) + "/"
                + branch.replace('/', FSC) + "/", link);
    }


    @Test
    public void asLink_HttpUrlRepoBranchPathFile()
    {
        String baseUrl = "http://localhost";
        String repository = "test.git";
        String branch = "b52";
        String path = "file.txt";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/" + branch + "/" + path, link);
    }

    @Test
    public void asLink_HttpUrlTrailingSlashRepoBranchPathFolderFile()
    {
        String baseUrl = "http://localhost/";
        String repository = "test.git";
        String branch = "branch";
        String path = "path/to/file.png";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl.substring(0, baseUrl.length()-1) + Constants.RAW_PATH + repository + "/"
                + branch + "/" + path.replace('/', FSC), link);
    }

    @Test
    public void asLink_HttpUrlRepoLeadingSlashBranchPathFolderLeadingSlash()
    {
        String baseUrl = "http://localhost";
        String repository = "/test.git";
        String branch = "featureOne";
        String path = "/folder";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository.substring(1) + "/"  + branch + path, link);
    }

    @Test
    public void asLink_HttpUrlTrailingSlashRepoLeadingSlashBranchSubFolder()
    {
        String baseUrl = "http://localhost/";
        String repository = "/test.git";
        String branch = "b";
        String path = "sub/folder";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl.substring(0, baseUrl.length()-1) + Constants.RAW_PATH + repository.substring(1) + "/"
                + branch + "/" + path.replace('/', FSC), link);
    }


    @Test
    public void asLink_HttpUrlRepoBranchWithSlashPathFolder()
    {
        String baseUrl = "http://localhost";
        String repository = "test.git";
        String branch = "feature/whatever";
        String path = "folder";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch.replace('/', FSC) + "/" + path, link);
    }

    @Test
    public void asLink_HttpUrlTrailingSlashRepoBranchWithSlashPathFolderFile()
    {
        String baseUrl = "http://localhost/";
        String repository = "test.git";
        String branch = "branch/for/issue/16";
        String path = "a/file.gif";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl.substring(0, baseUrl.length()-1) + Constants.RAW_PATH + repository + "/"
                + branch.replace('/', FSC) + "/" + path.replace('/', FSC), link);
    }

    @Test
    public void asLink_HttpUrlRepoLeadingSlashBranchWithSlashPathFile()
    {
        String baseUrl = "http://localhost";
        String repository = "/test.git";
        String branch = "releases/1.2.3";
        String path = "hurray.png";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository.substring(1) + "/"
                + branch.replace('/', FSC) + "/" + path, link);
    }

    @Test
    public void asLink_HttpUrlTrailingSlashRepoLeadingSlashBranchWithSlashPathFolderFile()
    {
        String baseUrl = "http://localhost/";
        String repository = "/test.git";
        String branch = "b/52";
        String path = "go/to/f.k";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl.substring(0, baseUrl.length()-1) + Constants.RAW_PATH + repository.substring(1) + "/"
                + branch.replace('/', FSC) + "/" + path.replace('/', FSC), link);
    }

    @Test
    public void asLink_HttpUrlRepoInFolder()
    {
        String baseUrl = "http://localhost";
        String repository = "project/repo.git";
        String branch = null;
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/", link);
    }

    @Test
    public void asLink_HttpUrlRepoInSubFolder()
    {
        String baseUrl = "http://localhost";
        String repository = "some/project/repo.git";
        String branch = null;
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/", link);
    }

    @Test
    public void asLink_HttpUrlRepoInSubFolderBranch()
    {
        String baseUrl = "http://localhost";
        String repository = "some/project/repo.git";
        String branch = "laluna";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch + "/", link);
    }

    @Test
    public void asLink_HttpUrlRepoInSubFolderBranchWithSlash()
    {
        String baseUrl = "http://localhost";
        String repository = "some/project/repo.git";
        String branch = "la/le/lu";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                 + branch.replace('/', FSC) + "/", link);
    }

    @Test
    public void asLink_HttpUrlRepoInSubFolderBranchPathFile()
    {
        String baseUrl = "http://localhost";
        String repository = "some/project/repo.git";
        String branch = "laluna";
        String path = "elrtkx.fg";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch + "/" + path, link);
    }

    @Test
    public void asLink_HttpUrlRepoInSubFolderLeadingSlashBranchWithSlashPathFolderFile()
    {
        String baseUrl = "http://localhost";
        String repository = "/some/project/repo.git";
        String branch = "la/le/lu";
        String path = "doremi/fa/SOLA/di.mp3";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository.substring(1) + "/"
                + branch.replace('/', FSC) + "/" + path.replace('/', FSC), link);
    }

    @Test
    public void asLink_HttpUrlRepoPathFolderFile()
    {
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = null;
        String path = "doko/di.mp3";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/", link);
    }

    @Test
    public void asLink_HttpUrlRepoTrailingSlashPathFileLeadingSlash()
    {
        String baseUrl = "http://localhost";
        String repository = "repo.git/";
        String branch = null;
        String path = "/di.mp3";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository, link);
    }

    @Test
    public void asLink_HttpUrlRepoBranchPathFileLeadingSlash()
    {
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = "bee";
        String path = "/bop.mp3";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch + path, link);
    }

    @Test
    public void asLink_HttpUrlRepoBranchPathFolderLeadingSlashTrailingSlash()
    {
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = "bee";
        String path = "/bam/";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch + "/bam" + FSC, link);
    }

    @Test
    public void asLink_HttpUrlRepoBranchPathSubFolderLeadingSlashTrailingSlash()
    {
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = "bee";
        String path = "/bapedi/boo/";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch + "/" + "bapedi" + FSC + "boo" + FSC, link);
    }

    @Test
    public void asLink_HttpUrlRepoCommitId()
    {
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = "c7eef37bfe5ae246cdf5ca5c502e4b5471290cb1";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch + "/", link);
    }

    @Test
    public void asLink_HttpUrlRepoCommitIdPathFile()
    {
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = "c7eef37bfe5ae246cdf5ca5c502e4b5471290cb1";
        String path = "file";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch + "/" + path, link);
    }

    @Test
    public void asLink_HttpUrlRepoCommitIdPathFolderFileFile()
    {
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = "c7eef37bfe5ae246cdf5ca5c502e4b5471290cb1";
        String path = "file/in/folder";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch + "/" + path.replace('/', FSC), link);
    }

    @Test
    public void asLink_HttpUrlRepoBranchWithSlash_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "|");
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = "some/feature";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch.replaceAll("/", "|") + "/", link);
    }

    @Test
    public void asLink_HttpUrlRepoBranchWithSlashPathFolderFile_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, ";");
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = "some/feature";
        String path = "file/in/folder";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch.replaceAll("/", ";") + "/" + path.replaceAll("/", ";"), link);
    }

    @Test
    public void asLink_HttpUrlRepoBranchWithSlash_explicitFscSameAsDefault()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "!");
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = "some/feature";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch.replaceAll("/", "!") + "/", link);
    }

    @Test
    public void asLink_HttpUrlRepoBranchWithSlashPathFolderFile_explicitFscSameAsDefault()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "!");
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = "some/feature";
        String path = "file/in/folder";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch.replaceAll("/", "!") + "/" + path.replaceAll("/", "!"), link);
    }

    @Test
    public void asLink_HttpUrlRepoBranchWithDefaultFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, ":");
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = "important" + FSC + "feature";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch + "/", link);
    }

    @Test
    public void asLink_HttpUrlRepoBranchWithSlashPathFileWithDefaultFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "|");
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = "some/feature";
        String path = "large" + FSC + "file";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch.replaceAll("/", "|") + "/" + path, link);
    }

    @Test
    public void asLink_HttpUrlRepoBranchWithDefaultFscAndSlash_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, ":");
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = "hotf" + FSC + "x/issue/1234";
        String path = null;

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch.replaceAll("/", ":") + "/", link);
    }

    @Test
    public void asLink_HttpUrlRepoBranchWithDefaultFscAndSlashPathFolderFileWithDefaultFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "|");
        String baseUrl = "http://localhost";
        String repository = "repo.git";
        String branch = "some/feature" + FSC + "in/here";
        String path = "large" + FSC + "stuff/folder/file" + FSC + "16";

        String link = RawServlet.asLink(baseUrl, repository, branch, path);

        assertNotNull(link);
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/"
                + branch.replaceAll("/", "|") + "/" + path.replaceAll("/", "|"), link);
    }


    @Test
    public void getBranch_Repo()
    {
        String branch = rawServlet.getBranch("test.git", "test.git/");

        assertEquals("Branch was supposed to be empty as no branch was given.", "", branch);
    }

    @Test
    public void getBranch_PiNull()
    {
        String branch = rawServlet.getBranch("test.git", null);

        assertEquals("Branch was supposed to be empty as path info is null.", "", branch);
    }


    @Test
    public void getBranch_PiEmpty()
    {
        String branch = rawServlet.getBranch("test.git", "");

        assertEquals("Branch was supposed to be empty as no path info exists.", "", branch);
    }

    @Test
    public void getBranch_LeadinRepo()
    {
        String branch = rawServlet.getBranch("test.git", "some/test.git/");

        assertEquals("Branch was supposed to be empty as no branch was given.", "", branch);
    }

    @Test
    public void getBranch_ProjectRepo()
    {
        String branch = rawServlet.getBranch("smack/dab.git", "smack/dab.git/");

        assertEquals("Branch was supposed to be empty as no branch was given.", "", branch);
    }

    @Test
    public void getBranch_RepoBranch()
    {
        String branch = rawServlet.getBranch("test.git", "test.git/bee");

        assertEquals("bee", branch);
    }

    @Test
    public void getBranch_LeadinRepoBranch()
    {
        String branch = rawServlet.getBranch("repo.git", "project/repo.git/bae");

        assertEquals("bae", branch);
    }

    @Test
    public void getBranch_ProjectRepoBranch()
    {
        String branch = rawServlet.getBranch("test/r.git", "test/r.git/b");

        assertEquals("b", branch);
    }

    @Test
    public void getBranch_LeadinProjectRepoBranch()
    {
        String branch = rawServlet.getBranch("test/r.git", "a/b/test/r.git/b");

        assertEquals("b", branch);
    }

    @Test
    public void getBranch_RepoBranchTrailingSlash()
    {
        String branch = rawServlet.getBranch("/test.git", "test.git/fixthis/");

        assertEquals("fixthis", branch);
    }

    @Test
    public void getBranch_RepoBranchFile()
    {
        String branch = rawServlet.getBranch("/bob.git", "bob.git/branch/file.txt");

        assertEquals("branch", branch);
    }

    @Test
    public void getBranch_RepoBranchFolderFile()
    {
        String branch = rawServlet.getBranch("/bill.git", "bill.git/flex/fold!laundr.y");

        assertEquals("flex", branch);
    }

    @Test
    public void getBranch_RepoBranchFoldersTrailingSlash()
    {
        String branch = rawServlet.getBranch("bill.git", "bill.git/flex/fold!gold/");

        assertEquals("flex", branch);
    }

    @Test
    public void getBranch_RepoBranchFoldersTrailingFsc()
    {
        String branch = rawServlet.getBranch("bill.git", "bill.git/flex/fold"+ FSC + "gold" + FSC);

        assertEquals("flex", branch);
    }

    @Test
    public void getBranch_LeadinProjectRepoBranchFoldersTrailingSlash()
    {
        String branch = rawServlet.getBranch("bam/bum.git", "bim/bam/bum.git/klingeling/dumm" + FSC + "di" + FSC + "dumm/");

        assertEquals("klingeling", branch);
    }

    @Test
    public void getBranch_LeadinProjectRepoBranchFoldersTrailingFsc()
    {
        String branch = rawServlet.getBranch("bam/bum.git", "bim/bam/bum.git/klingeling/dumm" + FSC + "di" + FSC + "dumm" + FSC);

        assertEquals("klingeling", branch);
    }

    @Test
    public void getBranch_RepoCommitid()
    {
        String branch = rawServlet.getBranch("git.git", "git.git/a02159e6378d63d0e1ad3c04a05462d9fc62fe89");

        assertEquals("a02159e6378d63d0e1ad3c04a05462d9fc62fe89", branch);
    }

    @Test
    public void getBranch_ProjectRepoCommitidFolderTrailingSlash()
    {
        String branch = rawServlet.getBranch("git/git.git", "git/git.git/a02159e6378d63d0e1ad3c04a05462d9fc62fe89/src/");

        assertEquals("a02159e6378d63d0e1ad3c04a05462d9fc62fe89", branch);
    }

    @Test
    public void getBranch_ProjectRepoCommitidFolderTrailingFsc()
    {
        String branch = rawServlet.getBranch("git/git.git", "git/git.git/a02159e6378d63d0e1ad3c04a05462d9fc62fe89/src" + FSC);

        assertEquals("a02159e6378d63d0e1ad3c04a05462d9fc62fe89", branch);
    }

    @Test
    public void getBranch_ProjectSubRepoCommitidFolderFile()
    {
        String branch = rawServlet.getBranch("git/git.git", "Git implementations/git/git.git/a02159e6378d63d0e1ad3c04a05462d9fc62fe89/src" + FSC + "README.md");

        assertEquals("a02159e6378d63d0e1ad3c04a05462d9fc62fe89", branch);
    }

    @Test
    public void getBranch_RepoBranchWithFsc()
    {
        String branch = rawServlet.getBranch("git.git", "git.git/feature" + FSC + "rebase");

        assertEquals("feature/rebase", branch);
    }

    @Test
    public void getBranch_RepoBranchWithTwoFsc()
    {
        String branch = rawServlet.getBranch("git.git", "git.git/feature" + FSC + "rebase" + FSC + "onto");

        assertEquals("feature/rebase/onto", branch);
    }

    @Test
    public void getBranch_ProjectRepoBranchWithTwoFsc()
    {
        String branch = rawServlet.getBranch("Java/git/git.git", "Java/git/git.git/feature" + FSC + "rebase" + FSC + "onto");

        assertEquals("feature/rebase/onto", branch);
    }

    @Test
    public void getBranch_RepoBranchWithTwoFscTrailingSlash()
    {
        String branch = rawServlet.getBranch("git.git", "git.git/feature" + FSC + "rebase" + FSC + "onto/");

        assertEquals("feature/rebase/onto", branch);
    }

    @Test
    public void getBranch_ProjectRepoBranchWithTwoFscTrailingSlash()
    {
        String branch = rawServlet.getBranch("in Go/git.git", "in Go/git.git/feature" + FSC + "rebase" + FSC + "onto/");

        assertEquals("feature/rebase/onto", branch);
    }

    @Test
    public void getBranch_LeadinProjectRepoBranchWithTwoFscTrailingSlash()
    {
        String branch = rawServlet.getBranch("Go/git.git", "all the gits/Go/git.git/feature" + FSC + "rebase" + FSC + "onto/");

        assertEquals("feature/rebase/onto", branch);
    }

    @Test
    public void getBranch_RepoBranchWithFscFolder()
    {
        String branch = rawServlet.getBranch("git.git", "git.git/feature" + FSC + "rebase/onto");

        assertEquals("feature/rebase", branch);
    }

    @Test
    public void getBranch_RepoBranchWithFscFolderFile()
    {
        String branch = rawServlet.getBranch("git.git", "git.git/feature" + FSC + "rebase/onto" + FSC + "head");

        assertEquals("feature/rebase", branch);
    }

    @Test
    public void getBranch_RepoBranchWithFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "|");

        String branch = rawServlet.getBranch("git.git", "git.git/some|feature");

        assertEquals("some/feature", branch);
    }

    @Test
    public void getBranch_RepoBranchWithFsc_explicitFscSameAsDefault()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, FSC);

        String branch = rawServlet.getBranch("git.git", "git.git/some" + FSC + "feature");

        assertEquals("some/feature", branch);
    }

    @Test
    public void getBranch_RepoBranchWithFscFolders_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, ":");

        String branch = rawServlet.getBranch("git.git", "git.git/hotfix:1.2.3/src:main:java/");

        assertEquals("hotfix/1.2.3", branch);
    }

    @Test
    public void getBranch_LeadindRepoBranchWithFscFolderFile_explicitFscSameAsDefault()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, FSC);

        String branch = rawServlet.getBranch("git.git", "IBM/git.git/some" + FSC + "feature/some" + FSC + "folder" + FSC + "file.dot");

        assertEquals("some/feature", branch);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getBranch_RepoBranchWithDefaultFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, ";");

        String branch = rawServlet.getBranch("git.git", "git.git/some" + FSC + "feature");

        assertEquals("some" + FSC + "feature", branch);
    }

    @Test
    public void getBranch_RepoBranchWithDifferentFscFolderFileWithDefaultFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, ";");

        String branch = rawServlet.getBranch("git.git", "git.git/some;feature/path" + FSC + "to" + FSC + "file.txt");

        assertEquals("some/feature", branch);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getBranch_RepoBranchWithDefaultFscAndDifferentFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, ":");

        String branch = rawServlet.getBranch("git.git", "git.git/go" + FSC + "to:start");

        assertEquals("go" + FSC + "to/start", branch);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getBranch_RepoBranchWithDefaultFscAndDifferentFscFolderWithDefaultFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "+");

        String branch = rawServlet.getBranch("git.git", "git.git/go" + FSC + "to+prison/dont" + FSC + "collect");

        assertEquals("go" + FSC + "to/prison", branch);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getBranch_RepoBranchWithDefaultFscAndDifferentFscFolderWithDefaultFscTrailingSlash_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "+");

        String branch = rawServlet.getBranch("git.git", "git.git/go" + FSC + "to+   prison/dont" + FSC + "collect/");

        assertEquals("go" + FSC + "to/prison", branch);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getBranch_RepoBranchWithDefaultFscAndDifferentFscFolderWithDefaultFscTrailingFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "+");

        String branch = rawServlet.getBranch("git.git", "git.git/go" + FSC + "to+prison/dont" + FSC + "collect+");

        assertEquals("go" + FSC + "to/prison", branch);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getBranch_RepoBranchWithDefaultFscAndDifferentFscFolderWithDefaultFscTrailingDefaultFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "+");

        String branch = rawServlet.getBranch("git.git", "git.git/go" + FSC + "to+prison/dont" + FSC + "collect" + FSC);

        assertEquals("go" + FSC + "to/prison", branch);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getBranch_RepoBranchWithDefaultFscAndDifferentFscFolderFileWithDefaultFscAndDifferentFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "+");

        String branch = rawServlet.getBranch("git.git", "git.git/go" + FSC + "to+prison/dont" + FSC + "collect+money.eur");

        assertEquals("go" + FSC + "to/prison", branch);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getBranch_LeadinProjectRepoBranchWithDefaultFscAndDifferentFscFolderFileWithDefaultFscAndDifferentFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "+");

        String branch = rawServlet.getBranch("games/Monopoly/git.git", "blah/games/Monopoly/git.git/go" + FSC + "to+prison/dont" + FSC + "collect+money.eur");

        assertEquals("go" + FSC + "to/prison", branch);
    }






    
    
    @Test
    public void getPath_Repo()
    {
        String path = rawServlet.getPath("test.git", "", "test.git/");

        assertEquals("Path was supposed to be empty as no path was given.", "", path);
    }

    @Test
    public void getPath_PiNull()
    {
        String path = rawServlet.getPath("test.git", "", null);

        assertEquals("Path was supposed to be empty as path info is null.", "", path);
    }


    @Test
    public void getPath_PiEmpty()
    {
        String path = rawServlet.getPath("test.git", "", "");

        assertEquals("Path was supposed to be empty as no path info exists.", "", path);
    }

    @Test
    public void getPath_LeadinRepo()
    {
        String path = rawServlet.getPath("test.git", "", "some/test.git/");

        assertEquals("Path was supposed to be empty as no path was given.", "", path);
    }

    @Test
    public void getPath_ProjectRepo()
    {
        String path = rawServlet.getPath("smack/dab.git", "", "smack/dab.git/");

        assertEquals("Path was supposed to be empty as no path was given.", "", path);
    }

    @Test
    public void getPath_RepoBranch()
    {
        String path = rawServlet.getPath("test.git", "bee", "test.git/bee");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Test
    public void getPath_LeadinRepoBranch()
    {
        String path = rawServlet.getPath("repo.git", "bae", "project/repo.git/bae");

        assertEquals("Expected path to be empty since no path was present","" , path);
    }

    @Test
    public void getPath_ProjectRepoBranch()
    {
        String path = rawServlet.getPath("test/r.git", "b", "test/r.git/b");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Test
    public void getPath_LeadinProjectRepoBranch()
    {
        String path = rawServlet.getPath("test/r.git", "b", "a/b/test/r.git/b");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Test
    public void getPath_RepoBranchTrailingSlash()
    {
        String path = rawServlet.getPath("test.git", "fixthis", "test.git/fixthis/");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Test
    public void getPath_RepoBranchFile()
    {
        String path = rawServlet.getPath("/bob.git", "branch", "bob.git/branch/file.txt");

        assertEquals("file.txt", path);
    }

    @Test
    public void getPath_RepoBranchFolderFile()
    {
        String path = rawServlet.getPath("/bill.git", "flex", "bill.git/flex/fold" + FSC + "laundr.y");

        assertEquals("fold/laundr.y", path);
    }

    @Test
    public void getPath_RepoBranchFoldersTrailingSlash()
    {
        String path = rawServlet.getPath("bill.git", "flex", "bill.git/flex/fold"+ FSC + "gold/");

        assertEquals("fold/gold", path);
    }

    @Test
    public void getPath_RepoBranchFoldersTrailingFsc()
    {
        String path = rawServlet.getPath("bill.git", "flex", "bill.git/flex/fold"+ FSC + "gold" + FSC);

        assertEquals("fold/gold", path);
    }

    @Test
    public void getPath_LeadinProjectRepoBranchFoldersTrailingSlash()
    {
        String path = rawServlet.getPath("bam/bum.git", "klingeling", "bim/bam/bum.git/klingeling/dumm" + FSC + "di" + FSC + "dumm/");

        assertEquals("dumm/di/dumm", path);
    }

    @Test
    public void getPath_LeadinProjectRepoBranchFoldersTrailingFsc()
    {
        String path = rawServlet.getPath("bam/bum.git", "klingeling", "bim/bam/bum.git/klingeling/dumm" + FSC + "di" + FSC + "dumm" + FSC);

        assertEquals("dumm/di/dumm", path);
    }

    @Test
    public void getPath_RepoCommitid()
    {
        String path = rawServlet.getPath("/git.git", "a02159e6378d63d0e1ad3c04a05462d9fc62fe89", "git.git/a02159e6378d63d0e1ad3c04a05462d9fc62fe89/");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Test
    public void getPath_RepoCommitidNoTrailingSlash()
    {
        String path = rawServlet.getPath("/git.git", "a02159e6378d63d0e1ad3c04a05462d9fc62fe89", "git.git/a02159e6378d63d0e1ad3c04a05462d9fc62fe89");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Test
    public void getPath_ProjectRepoCommitidFolderTrailingSlash()
    {
        String path = rawServlet.getPath("git/git.git", "a02159e6378d63d0e1ad3c04a05462d9fc62fe89", "git/git.git/a02159e6378d63d0e1ad3c04a05462d9fc62fe89/src/");

        assertEquals("src", path);
    }

    @Test
    public void getPath_ProjectRepoCommitidFolderTrailingFsc()
    {
        String path = rawServlet.getPath("git/git.git", "a02159e6378d63d0e1ad3c04a05462d9fc62fe89", "git/git.git/a02159e6378d63d0e1ad3c04a05462d9fc62fe89/src" + FSC);

        assertEquals("src", path);
    }

    @Test
    public void getPath_ProjectSubRepoCommitidFolderFile()
    {
        String path = rawServlet.getPath("git/git.git", "a02159e6378d63d0e1ad3c04a05462d9fc62fe89", "Git implementations/git/git.git/a02159e6378d63d0e1ad3c04a05462d9fc62fe89/src" + FSC + "README.md");

        assertEquals("src/README.md", path);
    }

    @Test
    public void getPath_RepoBranchWithFsc()
    {
        String path = rawServlet.getPath("git.git", "feature/rebase", "git.git/feature" + FSC + "rebase");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Test
    public void getPath_RepoBranchWithTwoFsc()
    {
        String path = rawServlet.getPath("git.git", "feature/rebase/onto", "git.git/feature" + FSC + "rebase" + FSC + "onto");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Test
    public void getPath_ProjectRepoBranchWithTwoFsc()
    {
        String path = rawServlet.getPath("Java/git/git.git", "feature/rebase/onto", "Java/git/git.git/feature" + FSC + "rebase" + FSC + "onto");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Test
    public void getPath_RepoBranchWithTwoFscTrailingSlash()
    {
        String path = rawServlet.getPath("git.git", "feature/rebase/onto", "git.git/feature" + FSC + "rebase" + FSC + "onto/");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Test
    public void getPath_ProjectRepoBranchWithTwoFscTrailingSlash()
    {
        String path = rawServlet.getPath("in Go/git.git", "feature/rebase/onto", "in Go/git.git/feature" + FSC + "rebase" + FSC + "onto/");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Test
    public void getPath_LeadinProjectRepoBranchWithTwoFscTrailingSlash()
    {
        String path = rawServlet.getPath("Go/git.git", "feature/rebase/onto", "all the gits/Go/git.git/feature" + FSC + "rebase" + FSC + "onto/");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Test
    public void getPath_RepoBranchWithFscFolder()
    {
        String path = rawServlet.getPath("git.git", "feature/rebase", "git.git/feature" + FSC + "rebase/onto");

        assertEquals("onto", path);
    }

    @Test
    public void getPath_RepoBranchWithFscFolderFile()
    {
        String path = rawServlet.getPath("git.git", "feature/rebase", "git.git/feature" + FSC + "rebase/onto" + FSC + "head");

        assertEquals("onto/head", path);
    }

    @Test
    public void getPath_RepoBranchWithFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "|");

        String path = rawServlet.getPath("git.git", "some/feature", "git.git/some|feature");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Test
    public void getPath_RepoBranchWithFsc_explicitFscSameAsDefault()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, String.valueOf(FSC));

        String path = rawServlet.getPath("git.git", "some/feature", "git.git/some" + FSC + "feature");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Test
    public void getPath_RepoBranchWithFscFoldersTrailingSlash_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, ":");

        String path = rawServlet.getPath("git.git", "hotfix/1.2.3", "git.git/hotfix:1.2.3/src:main:java/");

        assertEquals("src/main/java", path);
    }

    @Test
    public void getPath_LeadindRepoBranchWithFscFolderFile_explicitFscSameAsDefault()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, String.valueOf(FSC));

        String path = rawServlet.getPath("git.git", "some/feature", "IBM/git.git/some" + FSC + "feature/some" + FSC + "folder" + FSC + "file.dot");

        assertEquals("some/folder/file.dot", path);
    }

    @Test
    public void getPath_RepoBranchWithDefaultFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, ";");

        String path = rawServlet.getPath("git.git", "some" + FSC + "feature", "git.git/some" + FSC + "feature");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }


    @Test
    public void getPath_RepoBranchWithDefaultFscTrailingSlash_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, ";");

        String path = rawServlet.getPath("git.git", "some" + FSC + "feature", "git.git/some" + FSC + "feature/");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getPath_RepoBranchWithDifferentFscFolderFileWithDefaultFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, ";");

        String path = rawServlet.getPath("git.git", "some/feature", "git.git/some;feature/path" + FSC + "to" + FSC + "file.txt");

        assertEquals("path" + FSC + "to" + FSC + "file.txt", path);
    }

    @Test
    public void getPath_RepoBranchWithDefaultFscAndDifferentFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, ":");

        String path = rawServlet.getPath("git.git", "go" + FSC + "to/start", "git.git/go" + FSC + "to:start");

        assertEquals("Expected returned path to be empty since no path was present", "", path);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getPath_RepoBranchWithDefaultFscAndDifferentFscFolderWithDefaultFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "+");

        String path = rawServlet.getPath("git.git", "go" + FSC + "to/prison", "git.git/go" + FSC + "to+prison/dont" + FSC + "collect");

        assertEquals("dont" + FSC + "collect", path);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getPath_RepoBranchWithDefaultFscAndDifferentFscFolderWithDefaultFscTrailingSlash_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "+");

        String path = rawServlet.getPath("git.git", "go" + FSC + "to/prison", "git.git/go" + FSC + "to+prison/dont" + FSC + "collect/");

        assertEquals("dont" + FSC + "collect", path);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getPath_RepoBranchWithDefaultFscAndDifferentFscFolderWithDefaultFscTrailingFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "+");

        String path = rawServlet.getPath("git.git", "go" + FSC + "to/prison", "git.git/go" + FSC + "to+prison/dont" + FSC + "collect+");

        assertEquals("dont" + FSC + "collect", path);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getPath_RepoBranchWithDefaultFscAndDifferentFscFolderWithDefaultFscTrailingDefaultFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "+");

        String path = rawServlet.getPath("git.git", "go" + FSC + "to/prison", "git.git/go" + FSC + "to+prison/dont" + FSC + "collect" + FSC);

        assertEquals("dont" + FSC + "collect" + FSC, path);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getPath_RepoBranchWithDefaultFscAndDifferentFscFolderFileWithDefaultFscAndDifferentFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "+");

        String path = rawServlet.getPath("git.git", "go" + FSC + "to/prison", "git.git/go" + FSC + "to+prison/" + FSC + "dont" + FSC + "collect+money.eur");

        assertEquals(FSC + "dont" + FSC + "collect/money.eur", path);
    }

    @Ignore  // TODO: Why was it implemented this way?
    @Test
    public void getPath_LeadinProjectRepoBranchWithDefaultFscAndDifferentFscFolderFileWithDefaultFscAndDifferentFsc_differentFsc()
    {
        settings.overrideSetting(Keys.web.forwardSlashCharacter, "+");

        String path = rawServlet.getPath("games/Monopoly/git.git", "go" + FSC + "to/prison", "blah/games/Monopoly/git.git/go" + FSC + "to+prison/dont" + FSC + "collect+money.eur");

        assertEquals("dont" + FSC + "collect/money.eur", path);
    }
}
