package com.gitblit.servertests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Keys;
import com.gitblit.manager.FilestoreManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.models.FilestoreModel.Status;
import com.gitblit.servlet.FilestoreServlet;
import com.gitblit.utils.FileUtils;

public class FilestoreServletTest extends GitblitUnitTest {
	
	private static final AtomicBoolean started = new AtomicBoolean(false);
	
	private static final String SHA256_EG = "9a712c5d4037503a2d5ee1d07ad191eb99d051e84cbb020c171a5ae19bbe3cbd";
	
	private static final String repoName = "helloworld.git";
	
    private static final String repoLfs = "/r/" + repoName + "/info/lfs/objects/";
	
	@BeforeClass
	public static void startGitblit() throws Exception {
		started.set(GitBlitSuite.startGitblit());
	}

	@AfterClass
	public static void stopGitblit() throws Exception {
		if (started.get()) {
			GitBlitSuite.stopGitblit();
		}
	}
	
	
	@Test
	public void testRegexGroups() throws Exception {
		
		Pattern p = Pattern.compile(FilestoreServlet.REGEX_PATH);
		
		String basicUrl = "https://localhost:8080/r/test.git/info/lfs/objects/";
		String batchUrl = basicUrl + "batch";
		String oidUrl = basicUrl + SHA256_EG; 
		
        Matcher m = p.matcher(batchUrl);
        assertTrue(m.find());
        assertEquals("https://localhost:8080", m.group(FilestoreServlet.REGEX_GROUP_BASE_URI));
        assertEquals("r", m.group(FilestoreServlet.REGEX_GROUP_PREFIX));
        assertEquals("test.git", m.group(FilestoreServlet.REGEX_GROUP_REPOSITORY));
        assertEquals("batch", m.group(FilestoreServlet.REGEX_GROUP_ENDPOINT));
        
        m = p.matcher(oidUrl);
        assertTrue(m.find());
        assertEquals("https://localhost:8080", m.group(FilestoreServlet.REGEX_GROUP_BASE_URI));
        assertEquals("r", m.group(FilestoreServlet.REGEX_GROUP_PREFIX));
        assertEquals("test.git", m.group(FilestoreServlet.REGEX_GROUP_REPOSITORY));
        assertEquals(SHA256_EG, m.group(FilestoreServlet.REGEX_GROUP_ENDPOINT));
	}
	
	@Test
	public void testRegexGroupsNestedRepo() throws Exception {
		
		Pattern p = Pattern.compile(FilestoreServlet.REGEX_PATH);
		
		String basicUrl = "https://localhost:8080/r/nested/test.git/info/lfs/objects/";
		String batchUrl = basicUrl + "batch";
		String oidUrl = basicUrl + SHA256_EG; 
		
        Matcher m = p.matcher(batchUrl);
        assertTrue(m.find());
        assertEquals("https://localhost:8080", m.group(FilestoreServlet.REGEX_GROUP_BASE_URI));
        assertEquals("r", m.group(FilestoreServlet.REGEX_GROUP_PREFIX));
        assertEquals("nested/test.git", m.group(FilestoreServlet.REGEX_GROUP_REPOSITORY));
        assertEquals("batch", m.group(FilestoreServlet.REGEX_GROUP_ENDPOINT));
        
        m = p.matcher(oidUrl);
        assertTrue(m.find());
        assertEquals("https://localhost:8080", m.group(FilestoreServlet.REGEX_GROUP_BASE_URI));
        assertEquals("r", m.group(FilestoreServlet.REGEX_GROUP_PREFIX));
        assertEquals("nested/test.git", m.group(FilestoreServlet.REGEX_GROUP_REPOSITORY));
        assertEquals(SHA256_EG, m.group(FilestoreServlet.REGEX_GROUP_ENDPOINT));
	}
	
	@Test
	public void testDownload() throws Exception {
		
		FileUtils.delete(filestore().getStorageFolder());
		filestore().clearFilestoreCache();
		
		RepositoryModel r =  gitblit().getRepositoryModel(repoName);
		
		UserModel u = new UserModel("admin");
		u.canAdmin = true;

		//No upload limit
		settings().overrideSetting(Keys.filestore.maxUploadSize, FilestoreManager.UNDEFINED_SIZE);

		final BlobInfo blob = new BlobInfo(512*FileUtils.KB);
		
		//Emulate a pre-existing Git-LFS repository by using using internal pre-tested methods
		assertEquals(Status.Available, filestore().uploadBlob(blob.hash, blob.length, u, r, new ByteArrayInputStream(blob.blob)));
		
        final String downloadURL = GitBlitSuite.url + repoLfs + blob.hash;
        
        HttpClient client = HttpClientBuilder.create().build();
    	HttpGet request = new HttpGet(downloadURL);

    	// add request header
    	request.addHeader(HttpHeaders.ACCEPT, FilestoreServlet.GIT_LFS_META_MIME);
    	HttpResponse response = client.execute(request);
    	
		assertEquals(200, response.getStatusLine().getStatusCode());

		String content = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
		
		String expectedContent = String.format("{%s:%s,%s:%d,%s:{%s:{%s:%s}}}",
				"\"oid\"", "\"" + blob.hash + "\"",
				"\"size\"", blob.length,
				"\"actions\"",
				"\"download\"",
				"\"href\"", "\"" + downloadURL + "\"");
		
		assertEquals(expectedContent, content);
		
		
		//Now try the binary download
		request.removeHeaders(HttpHeaders.ACCEPT);
		response = client.execute(request);
		
		assertEquals(200, response.getStatusLine().getStatusCode());
		
		byte[] dlData = IOUtils.toByteArray(response.getEntity().getContent());
				
		assertArrayEquals(blob.blob,  dlData);
		
	}
	
	@Test
	public void testDownloadMultiple() throws Exception {
		
		FileUtils.delete(filestore().getStorageFolder());
		filestore().clearFilestoreCache();
		
		RepositoryModel r =  gitblit().getRepositoryModel(repoName);
		
		UserModel u = new UserModel("admin");
		u.canAdmin = true;

		//No upload limit
		settings().overrideSetting(Keys.filestore.maxUploadSize, FilestoreManager.UNDEFINED_SIZE);

		final BlobInfo blob = new BlobInfo(512*FileUtils.KB);
		
		//Emulate a pre-existing Git-LFS repository by using using internal pre-tested methods
		assertEquals(Status.Available, filestore().uploadBlob(blob.hash, blob.length, u, r, new ByteArrayInputStream(blob.blob)));
		
        final String batchURL = GitBlitSuite.url + repoLfs + "batch";
		final String downloadURL = GitBlitSuite.url + repoLfs + blob.hash;
        
        HttpClient client = HttpClientBuilder.create().build();
    	HttpPost request = new HttpPost(batchURL);

    	// add request header
    	request.addHeader(HttpHeaders.ACCEPT, FilestoreServlet.GIT_LFS_META_MIME);
    	request.addHeader(HttpHeaders.CONTENT_ENCODING, FilestoreServlet.GIT_LFS_META_MIME);
    	
    	String content = String.format("{%s:%s,%s:[{%s:%s,%s:%d},{%s:%s,%s:%d}]}",
    			"\"operation\"", "\"download\"",
    			"\"objects\"",
    			"\"oid\"", "\"" + blob.hash + "\"",
    			"\"size\"", blob.length,
    			"\"oid\"", "\"" + SHA256_EG + "\"",
    			"\"size\"", 0);
    	
    	HttpEntity entity = new ByteArrayEntity(content.getBytes("UTF-8"));
    	request.setEntity(entity);

    	HttpResponse response = client.execute(request);
    	
    	String responseMessage = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
		assertEquals(200, response.getStatusLine().getStatusCode());

		String expectedContent = String.format("{%s:[{%s:%s,%s:%d,%s:{%s:{%s:%s}}},{%s:%s,%s:%d,%s:{%s:%s,%s:%d}}]}",
				"\"objects\"",
				"\"oid\"", "\"" + blob.hash + "\"",
				"\"size\"", blob.length,
				"\"actions\"",
				"\"download\"",
				"\"href\"", "\"" + downloadURL + "\"",
				"\"oid\"", "\"" + SHA256_EG + "\"",
				"\"size\"", 0,
				"\"error\"",
				"\"message\"", "\"Object not available\"",
				"\"code\"", 404
				);
		
		assertEquals(expectedContent, responseMessage);
	}
	
	@Test
	public void testDownloadUnavailable() throws Exception {
		
		FileUtils.delete(filestore().getStorageFolder());
		filestore().clearFilestoreCache();
		
		//No upload limit
		settings().overrideSetting(Keys.filestore.maxUploadSize, FilestoreManager.UNDEFINED_SIZE);

		final BlobInfo blob = new BlobInfo(512*FileUtils.KB);
		
        final String downloadURL = GitBlitSuite.url + repoLfs + blob.hash;
        
        HttpClient client = HttpClientBuilder.create().build();
    	HttpGet request = new HttpGet(downloadURL);

    	// add request header
    	request.addHeader(HttpHeaders.ACCEPT, FilestoreServlet.GIT_LFS_META_MIME);
    	HttpResponse response = client.execute(request);
    	
		assertEquals(404, response.getStatusLine().getStatusCode());

		String content = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
		
		String expectedError = String.format("{%s:%s,%s:%d}",
				"\"message\"", "\"Object not available\"",
				"\"code\"", 404);
		
		assertEquals(expectedError, content);
	}
	
	@Test
	public void testUpload() throws Exception {
		
		FileUtils.delete(filestore().getStorageFolder());
		filestore().clearFilestoreCache();
		
		RepositoryModel r =  gitblit().getRepositoryModel(repoName);
		
		UserModel u = new UserModel("admin");
		u.canAdmin = true;

		//No upload limit
		settings().overrideSetting(Keys.filestore.maxUploadSize, FilestoreManager.UNDEFINED_SIZE);

		final BlobInfo blob = new BlobInfo(512*FileUtils.KB);
        
        final String expectedUploadURL = GitBlitSuite.url + repoLfs + blob.hash;
        final String initialUploadURL = GitBlitSuite.url + repoLfs + "batch";
        
        HttpClient client = HttpClientBuilder.create().build();
    	HttpPost request = new HttpPost(initialUploadURL);

    	// add request header
    	request.addHeader(HttpHeaders.ACCEPT, FilestoreServlet.GIT_LFS_META_MIME);
    	request.addHeader(HttpHeaders.CONTENT_ENCODING, FilestoreServlet.GIT_LFS_META_MIME);
    	
    	String content = String.format("{%s:%s,%s:[{%s:%s,%s:%d}]}",
    			"\"operation\"", "\"upload\"",
    			"\"objects\"",
    			"\"oid\"", "\"" + blob.hash + "\"",
    			"\"size\"", blob.length);
    	
    	HttpEntity entity = new ByteArrayEntity(content.getBytes("UTF-8"));
    	request.setEntity(entity);
    	
    	HttpResponse response = client.execute(request);
    	String responseMessage = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
		assertEquals(200, response.getStatusLine().getStatusCode());

		String expectedContent = String.format("{%s:[{%s:%s,%s:%d,%s:{%s:{%s:%s}}}]}",
				"\"objects\"",
				"\"oid\"", "\"" + blob.hash + "\"",
				"\"size\"", blob.length,
				"\"actions\"",
				"\"upload\"",
				"\"href\"", "\"" + expectedUploadURL + "\"");
		
		assertEquals(expectedContent, responseMessage);
		
		
		//Now try to upload the binary download
		HttpPut putRequest = new HttpPut(expectedUploadURL);
		putRequest.setEntity(new ByteArrayEntity(blob.blob));
		response = client.execute(putRequest);
		
		responseMessage = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
		
		assertEquals(200, response.getStatusLine().getStatusCode());
		
		//Confirm behind the scenes that it is available
		ByteArrayOutputStream savedBlob = new ByteArrayOutputStream();
		assertEquals(Status.Available, filestore().downloadBlob(blob.hash, u, r, savedBlob));
		assertArrayEquals(blob.blob,  savedBlob.toByteArray());
	}

	@Test
	public void testMalformedUpload() throws Exception {
		
		FileUtils.delete(filestore().getStorageFolder());
		filestore().clearFilestoreCache();
		
		//No upload limit
		settings().overrideSetting(Keys.filestore.maxUploadSize, FilestoreManager.UNDEFINED_SIZE);

		final BlobInfo blob = new BlobInfo(512*FileUtils.KB);
        
        final String initialUploadURL = GitBlitSuite.url + repoLfs + "batch";
        
        HttpClient client = HttpClientBuilder.create().build();
    	HttpPost request = new HttpPost(initialUploadURL);

    	// add request header
    	request.addHeader(HttpHeaders.ACCEPT, FilestoreServlet.GIT_LFS_META_MIME);
    	request.addHeader(HttpHeaders.CONTENT_ENCODING, FilestoreServlet.GIT_LFS_META_MIME);
    	
    	//Malformed JSON, comma instead of colon and unquoted strings
    	String content = String.format("{%s:%s,%s:[{%s:%s,%s,%d}]}",
    			"operation", "upload",
    			"objects",
    			"oid", blob.hash,
    			"size", blob.length);
    	
    	HttpEntity entity = new ByteArrayEntity(content.getBytes("UTF-8"));
    	request.setEntity(entity);
    	
    	HttpResponse response = client.execute(request);
    	String responseMessage = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
		assertEquals(400, response.getStatusLine().getStatusCode());
		
		String expectedError = String.format("{%s:%s,%s:%d}",
				"\"message\"", "\"Malformed Git-LFS request\"",
				"\"code\"", 400);
				
		assertEquals(expectedError, responseMessage);
	}
	
}
