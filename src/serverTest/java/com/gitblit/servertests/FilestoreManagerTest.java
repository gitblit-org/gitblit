package com.gitblit.servertests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Keys;
import com.gitblit.models.FilestoreModel.Status;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.FileUtils;


/**
 * Test of the filestore manager and confirming filesystem updated
 *
 * @author Paul Martin
 *
 */
public class FilestoreManagerTest extends GitblitUnitTest {

	private static final AtomicBoolean started = new AtomicBoolean(false);
	
	private static final BlobInfo blob_zero = new BlobInfo(0);
	private static final BlobInfo blob_512KB = new BlobInfo(512*FileUtils.KB);
	private static final BlobInfo blob_6MB = new BlobInfo(6*FileUtils.MB);
	
	private static int download_limit_default = -1;
	private static int download_limit_test = 5*FileUtils.MB;
	
	private static final String invalid_hash_empty = "";
	private static final String invalid_hash_major = "INVALID_HASH";
	private static final String invalid_hash_regex_attack = blob_512KB.hash.replace('a', '*');
	private static final String invalid_hash_one_long = blob_512KB.hash.concat("a");
	private static final String invalid_hash_one_short = blob_512KB.hash.substring(1);
	

	
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
	public void testAdminAccess() throws Exception {
		
		FileUtils.delete(filestore().getStorageFolder());
		filestore().clearFilestoreCache();
		
		RepositoryModel r = new RepositoryModel("myrepo.git", null, null, new Date());
		ByteArrayOutputStream streamOut = new ByteArrayOutputStream();
		
		UserModel u = new UserModel("admin");
		u.canAdmin = true;

		//No upload limit
		settings().overrideSetting(Keys.filestore.maxUploadSize, download_limit_default);
		
		//Invalid hash tests
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_major, u, r, streamOut));
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_regex_attack, u, r, streamOut));
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_one_long, u, r, streamOut));
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_one_short, u, r, streamOut));
		
		// Download prior to upload
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		
		//Bad input is rejected with no upload taking place
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_empty, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_major, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_regex_attack, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_one_long, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_one_short, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		
		assertEquals(Status.Error_Invalid_Size, filestore().uploadBlob(blob_512KB.hash, -1, u, r, new ByteArrayInputStream(blob_512KB.blob)));

		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_512KB.hash, 0, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length-1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length+1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		
		assertEquals(Status.Error_Hash_Mismatch, filestore().uploadBlob(blob_zero.hash, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));

		//Confirm no upload with bad input
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		
		//Good input will accept the upload
		assertEquals(Status.Available, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		streamOut.reset();
		assertEquals(Status.Available, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		assertArrayEquals(blob_512KB.blob, streamOut.toByteArray());
		
		//Subsequent failed uploads do not affect file
		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length-1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length+1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		
		streamOut.reset();
		assertEquals(Status.Available, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		assertArrayEquals(blob_512KB.blob, streamOut.toByteArray());
		
		//Zero length upload is valid
		assertEquals(Status.Available, filestore().uploadBlob(blob_zero.hash, blob_zero.length, u, r, new ByteArrayInputStream(blob_zero.blob)));
		
		streamOut.reset();
		assertEquals(Status.Available, filestore().downloadBlob(blob_zero.hash, u, r, streamOut));
		assertArrayEquals(blob_zero.blob, streamOut.toByteArray());
		
		
		//Pre-informed upload identifies identical errors as immediate upload
		assertEquals(Status.Upload_Pending, filestore().addObject(blob_6MB.hash, blob_6MB.length, u, r));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_empty, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_major, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_regex_attack, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_one_long, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_one_short, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		
		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_6MB.hash, 0, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length-1, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length+1, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		
		//Good input will accept the upload
		assertEquals(Status.Available, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		streamOut.reset();
		assertEquals(Status.Available, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		assertArrayEquals(blob_6MB.blob, streamOut.toByteArray());
		
		//Confirm the relevant files exist
		assertTrue("Admin did not save zero length file!", filestore().getStoragePath(blob_zero.hash).exists());
		assertTrue("Admin did not save 512KB file!", filestore().getStoragePath(blob_512KB.hash).exists());
		assertTrue("Admin did not save 6MB file!", filestore().getStoragePath(blob_6MB.hash).exists());
		
		//Clear the files and cache to test upload limit property
		FileUtils.delete(filestore().getStorageFolder());
		filestore().clearFilestoreCache();
		
		settings().overrideSetting(Keys.filestore.maxUploadSize, download_limit_test);
		
		assertEquals(Status.Available, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		streamOut.reset();
		assertEquals(Status.Available, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		assertArrayEquals(blob_512KB.blob, streamOut.toByteArray());
		
		assertEquals(Status.Error_Exceeds_Size_Limit, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		
		assertTrue("Admin did not save 512KB file!", filestore().getStoragePath(blob_512KB.hash).exists());
		assertFalse("Admin saved 6MB file despite (over filesize limit)!", filestore().getStoragePath(blob_6MB.hash).exists());
		
	}
	
	@Test
	public void testAuthenticatedAccess() throws Exception {
		
		FileUtils.delete(filestore().getStorageFolder());
		filestore().clearFilestoreCache();
		
		RepositoryModel r = new RepositoryModel("myrepo.git", null, null, new Date());
		r.authorizationControl = AuthorizationControl.AUTHENTICATED;
		r.accessRestriction = AccessRestrictionType.VIEW;
		
		ByteArrayOutputStream streamOut = new ByteArrayOutputStream();
		
		UserModel u = new UserModel("test");

		//No upload limit
		settings().overrideSetting(Keys.filestore.maxUploadSize, download_limit_default);
		
		//Invalid hash tests
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_major, u, r, streamOut));
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_regex_attack, u, r, streamOut));
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_one_long, u, r, streamOut));
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_one_short, u, r, streamOut));
		
		// Download prior to upload
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		
		//Bad input is rejected with no upload taking place
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_empty, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_major, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_regex_attack, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_one_long, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_one_short, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		
		assertEquals(Status.Error_Invalid_Size, filestore().uploadBlob(blob_512KB.hash, -1, u, r, new ByteArrayInputStream(blob_512KB.blob)));

		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_512KB.hash, 0, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length-1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length+1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		
		assertEquals(Status.Error_Hash_Mismatch, filestore().uploadBlob(blob_zero.hash, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));

		//Confirm no upload with bad input
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		
		//Good input will accept the upload
		assertEquals(Status.Available, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		streamOut.reset();
		assertEquals(Status.Available, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		assertArrayEquals(blob_512KB.blob, streamOut.toByteArray());
		
		//Subsequent failed uploads do not affect file
		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length-1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length+1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		
		streamOut.reset();
		assertEquals(Status.Available, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		assertArrayEquals(blob_512KB.blob, streamOut.toByteArray());
		
		//Zero length upload is valid
		assertEquals(Status.Available, filestore().uploadBlob(blob_zero.hash, blob_zero.length, u, r, new ByteArrayInputStream(blob_zero.blob)));
		
		streamOut.reset();
		assertEquals(Status.Available, filestore().downloadBlob(blob_zero.hash, u, r, streamOut));
		assertArrayEquals(blob_zero.blob, streamOut.toByteArray());
		
		
		//Pre-informed upload identifies identical errors as immediate upload
		assertEquals(Status.Upload_Pending, filestore().addObject(blob_6MB.hash, blob_6MB.length, u, r));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_empty, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_major, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_regex_attack, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_one_long, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Invalid_Oid, filestore().uploadBlob(invalid_hash_one_short, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		
		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_6MB.hash, 0, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length-1, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Size_Mismatch, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length+1, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		
		//Good input will accept the upload
		assertEquals(Status.Available, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		streamOut.reset();
		assertEquals(Status.Available, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		assertArrayEquals(blob_6MB.blob, streamOut.toByteArray());
		
		//Confirm the relevant files exist
		assertTrue("Authenticated user did not save zero length file!", filestore().getStoragePath(blob_zero.hash).exists());
		assertTrue("Authenticated user did not save 512KB file!", filestore().getStoragePath(blob_512KB.hash).exists());
		assertTrue("Authenticated user did not save 6MB file!", filestore().getStoragePath(blob_6MB.hash).exists());
		
		//Clear the files and cache to test upload limit property
		FileUtils.delete(filestore().getStorageFolder());
		filestore().clearFilestoreCache();
		
		settings().overrideSetting(Keys.filestore.maxUploadSize, download_limit_test);
		
		assertEquals(Status.Available, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		streamOut.reset();
		assertEquals(Status.Available, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		assertArrayEquals(blob_512KB.blob, streamOut.toByteArray());
		
		assertEquals(Status.Error_Exceeds_Size_Limit, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		
		assertTrue("Authenticated user did not save 512KB file!", filestore().getStoragePath(blob_512KB.hash).exists());
		assertFalse("Authenticated user saved 6MB file (over filesize limit)!", filestore().getStoragePath(blob_6MB.hash).exists());
		
	}
	
	@Test
	public void testAnonymousAccess() throws Exception {
		
		FileUtils.delete(filestore().getStorageFolder());
		filestore().clearFilestoreCache();
		
		RepositoryModel r = new RepositoryModel("myrepo.git", null, null, new Date());
		r.authorizationControl = AuthorizationControl.NAMED;
		r.accessRestriction = AccessRestrictionType.CLONE;

		ByteArrayOutputStream streamOut = new ByteArrayOutputStream();

		UserModel u = UserModel.ANONYMOUS;

		//No upload limit
		settings().overrideSetting(Keys.filestore.maxUploadSize, download_limit_default);
		
		//Invalid hash tests
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_major, u, r, streamOut));
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_regex_attack, u, r, streamOut));
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_one_long, u, r, streamOut));
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_one_short, u, r, streamOut));
		
		// Download prior to upload
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		
		//Bad input is rejected with no upload taking place
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(invalid_hash_empty, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(invalid_hash_major, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(invalid_hash_regex_attack, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(invalid_hash_one_long, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(invalid_hash_one_short, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_512KB.hash, -1, u, r, new ByteArrayInputStream(blob_512KB.blob)));

		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_512KB.hash, 0, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length-1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length+1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_zero.hash, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));

		//Confirm no upload with bad input
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		
		//Good input will accept the upload
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		
		//Subsequent failed uploads do not affect file
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length-1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length+1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		
		//Zero length upload is valid
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_zero.hash, blob_zero.length, u, r, new ByteArrayInputStream(blob_zero.blob)));
		
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_zero.hash, u, r, streamOut));
		
		
		//Pre-informed upload identifies identical errors as immediate upload
		assertEquals(Status.AuthenticationRequired, filestore().addObject(blob_6MB.hash, blob_6MB.length, u, r));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(invalid_hash_empty, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(invalid_hash_major, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(invalid_hash_regex_attack, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(invalid_hash_one_long, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(invalid_hash_one_short, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_6MB.hash, -1, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_6MB.hash, 0, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length-1, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length+1, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		
		//Good input will accept the upload
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		
		//Confirm the relevant files do not exist
		assertFalse("Anonymous user saved zero length file!", filestore().getStoragePath(blob_zero.hash).exists());
		assertFalse("Anonymous user 512KB file!", filestore().getStoragePath(blob_512KB.hash).exists());
		assertFalse("Anonymous user 6MB file!", filestore().getStoragePath(blob_6MB.hash).exists());
		
		//Clear the files and cache to test upload limit property
		FileUtils.delete(filestore().getStorageFolder());
		filestore().clearFilestoreCache();
		
		settings().overrideSetting(Keys.filestore.maxUploadSize, download_limit_test);
		
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		
		assertEquals(Status.AuthenticationRequired, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		
		assertFalse("Anonymous user saved 512KB file!", filestore().getStoragePath(blob_512KB.hash).exists());
		assertFalse("Anonymous user saved 6MB file (over filesize limit)!", filestore().getStoragePath(blob_6MB.hash).exists());
		
	}
	
	@Test
	public void testUnauthorizedAccess() throws Exception {
		
		FileUtils.delete(filestore().getStorageFolder());
		filestore().clearFilestoreCache();
		
		RepositoryModel r = new RepositoryModel("myrepo.git", null, null, new Date());
		r.authorizationControl = AuthorizationControl.NAMED;
		r.accessRestriction = AccessRestrictionType.VIEW;
		
		ByteArrayOutputStream streamOut = new ByteArrayOutputStream();
		
		UserModel u = new UserModel("test");
		u.setRepositoryPermission(r.name, AccessPermission.CLONE);
		
		//No upload limit
		settings().overrideSetting(Keys.filestore.maxUploadSize, download_limit_default);
		
		//Invalid hash tests
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_major, u, r, streamOut));
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_regex_attack, u, r, streamOut));
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_one_long, u, r, streamOut));
		assertEquals(Status.Error_Invalid_Oid, filestore().downloadBlob(invalid_hash_one_short, u, r, streamOut));
		
		// Download prior to upload
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		
		//Bad input is rejected with no upload taking place
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(invalid_hash_major, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(invalid_hash_regex_attack, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(invalid_hash_one_long, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(invalid_hash_empty, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(invalid_hash_one_short, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_512KB.hash, -1, u, r, new ByteArrayInputStream(blob_512KB.blob)));

		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_512KB.hash, 0, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length-1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length+1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_zero.hash, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));

		//Confirm no upload with bad input
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		
		//Good input will accept the upload
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		
		//Subsequent failed uploads do not affect file
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length-1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length+1, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		
		//Zero length upload is valid
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_zero.hash, blob_zero.length, u, r, new ByteArrayInputStream(blob_zero.blob)));
		
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_zero.hash, u, r, streamOut));
		
		
		//Pre-informed upload identifies identical errors as immediate upload
		assertEquals(Status.Error_Unauthorized, filestore().addObject(blob_6MB.hash, blob_6MB.length, u, r));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(invalid_hash_empty, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(invalid_hash_major, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(invalid_hash_regex_attack, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(invalid_hash_one_long, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(invalid_hash_one_short, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_6MB.hash, -1, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_6MB.hash, 0, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length-1, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length+1, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		
		//Good input will accept the upload
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		
		//Confirm the relevant files exist
		assertFalse("Unauthorized user saved zero length file!", filestore().getStoragePath(blob_zero.hash).exists());
		assertFalse("Unauthorized user saved 512KB file!", filestore().getStoragePath(blob_512KB.hash).exists());
		assertFalse("Unauthorized user saved 6MB file!", filestore().getStoragePath(blob_6MB.hash).exists());
		
		//Clear the files and cache to test upload limit property
		FileUtils.delete(filestore().getStorageFolder());
		filestore().clearFilestoreCache();
		
		settings().overrideSetting(Keys.filestore.maxUploadSize, download_limit_test);
		
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_512KB.hash, blob_512KB.length, u, r, new ByteArrayInputStream(blob_512KB.blob)));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_512KB.hash, u, r, streamOut));
		
		assertEquals(Status.Error_Unauthorized, filestore().uploadBlob(blob_6MB.hash, blob_6MB.length, u, r, new ByteArrayInputStream(blob_6MB.blob)));
		streamOut.reset();
		assertEquals(Status.Unavailable, filestore().downloadBlob(blob_6MB.hash, u, r, streamOut));
		
		assertFalse("Unauthorized user saved 512KB file!", filestore().getStoragePath(blob_512KB.hash).exists());
		assertFalse("Unauthorized user saved 6MB file (over filesize limit)!", filestore().getStoragePath(blob_6MB.hash).exists());
		
	}
	
}

/*
 * Test helper structure to create blobs of a given size
 */
final class BlobInfo {
	public byte[] blob;
	public String hash;
	public int length;
	
	public BlobInfo(int nBytes) {
		blob = new byte[nBytes];
		new java.util.Random().nextBytes(blob);
		hash = DigestUtils.sha256Hex(blob);
		length = nBytes;
	}
}