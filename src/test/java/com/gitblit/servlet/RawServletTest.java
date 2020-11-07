package com.gitblit.servlet;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.tests.mock.MockGitblitContext;
import com.gitblit.tests.mock.MockRuntimeManager;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;


import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RawServletTest
{
    private final static String FSC = "!";

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
        assertEquals(baseUrl + Constants.RAW_PATH + repository + "/" + branch.replaceAll("/", FSC) + "/", link);
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
                + branch.replaceAll("/", FSC) + "/", link);
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
                + branch.replaceAll("/", FSC) + "/", link);
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
                + branch.replaceAll("/", FSC) + "/", link);
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
                + branch + "/" + path.replaceAll("/", FSC), link);
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
                + branch + "/" + path.replaceAll("/", FSC), link);
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
                + branch.replaceAll("/", FSC) + "/" + path, link);
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
                + branch.replaceAll("/", FSC) + "/" + path.replaceAll("/", FSC), link);
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
                + branch.replaceAll("/", FSC) + "/" + path, link);
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
                + branch.replaceAll("/", FSC) + "/" + path.replaceAll("/", FSC), link);
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
                 + branch.replaceAll("/", FSC) + "/", link);
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
                + branch.replaceAll("/", FSC) + "/" + path.replaceAll("/", FSC), link);
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
                + branch + "/" , link);
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
                + branch + "/" + path.replaceAll("/", FSC), link);
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
    public void getBranch()
    {
    }

    @Test
    public void getPath()
    {
    }

    @Test
    public void getBranch_Repo()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getPathInfo()).thenReturn("/test.git/");

        String branch = rawServlet.getBranch("test.git", request);

        assertEquals("Branch was supposed to be empty as no branch was given.", "", branch);
    }

    @Test
    public void getBranch_RepoNoTrailingSlash()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getPathInfo()).thenReturn("/test.git");

        String branch = rawServlet.getBranch("test.git", request);

        assertEquals("Branch was supposed to be empty as no branch was given.", "", branch);
    }

    @Test
    public void getBranch_PiNull()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getPathInfo()).thenReturn(null);

        String branch = rawServlet.getBranch("test.git", request);

        assertEquals("Branch was supposed to be empty as path info is null.", "", branch);
    }


    @Test
    public void getBranch_PiEmpty()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getPathInfo()).thenReturn("");

        String branch = rawServlet.getBranch("test.git", request);

        assertEquals("Branch was supposed to be empty as no path info exists.", "", branch);
    }






    
    
    @Test
    public void getPath_Repo()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getPathInfo()).thenReturn("/test.git/");

        String path = rawServlet.getPath("test.git", "", request);

        assertEquals("Path was supposed to be empty as no path was given.", "", path);
    }

    @Test
    public void getPath_RepoNoTrailingSlash()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getPathInfo()).thenReturn("/test.git");

        String path = rawServlet.getPath("test.git", "", request);

        assertEquals("Path was supposed to be empty as no path was given.", "", path);
    }

    @Test
    public void getPath_PiNull()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getPathInfo()).thenReturn(null);

        String path = rawServlet.getPath("test.git", "", request);

        assertEquals("Path was supposed to be empty as path info is null.", "", path);
    }


    @Test
    public void getPath_PiEmpty()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getPathInfo()).thenReturn("");

        String path = rawServlet.getPath("test.git", "", request);

        assertEquals("Path was supposed to be empty as no path info exists.", "", path);
    }

}
