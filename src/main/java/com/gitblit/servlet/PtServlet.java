/*
 * Copyright 2014 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.servlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.eclipse.jgit.lib.FileMode;

import com.gitblit.manager.IRuntimeManager;

/**
 * Handles requests for the Barnum pt (patchset tool).
 *
 * The user-agent determines the content and compression format.
 *
 * @author James Moger
 *
 */
@Singleton
public class PtServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final long lastModified = System.currentTimeMillis();

	private IRuntimeManager runtimeManager;

	@Inject
	public PtServlet(IRuntimeManager runtimeManager) {
		this.runtimeManager = runtimeManager;
	}

	@Override
	protected long getLastModified(HttpServletRequest req) {
		File file = runtimeManager.getFileOrFolder("tickets.pt", "${baseFolder}/pt.py");
		if (file.exists()) {
			return Math.max(lastModified, file.lastModified());
		} else {
			return lastModified;
		}
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
			response.setContentType("application/octet-stream");
			response.setDateHeader("Last-Modified", lastModified);
			response.setHeader("Cache-Control", "none");
			response.setHeader("Pragma", "no-cache");
			response.setDateHeader("Expires", 0);

			boolean windows = false;
			try {
				String useragent = request.getHeader("user-agent").toString();
				windows = useragent.toLowerCase().contains("windows");
			} catch (Exception e) {
			}

			byte[] pyBytes;
			File file = runtimeManager.getFileOrFolder("tickets.pt", "${baseFolder}/pt.py");
			if (file.exists()) {
				// custom script
				pyBytes = readAll(new FileInputStream(file));
			} else {
				// default script
				pyBytes = readAll(getClass().getResourceAsStream("/pt.py"));
			}

			if (windows) {
				// windows: download zip file with pt.py and pt.cmd
				response.setHeader("Content-Disposition", "attachment; filename=\"pt.zip\"");

				OutputStream os = response.getOutputStream();
				ZipArchiveOutputStream zos = new ZipArchiveOutputStream(os);

				// add the Python script
				ZipArchiveEntry pyEntry = new ZipArchiveEntry("pt.py");
				pyEntry.setSize(pyBytes.length);
				pyEntry.setUnixMode(FileMode.EXECUTABLE_FILE.getBits());
				pyEntry.setTime(lastModified);
				zos.putArchiveEntry(pyEntry);
				zos.write(pyBytes);
				zos.closeArchiveEntry();

				// add a Python launch cmd file
				byte [] cmdBytes = readAll(getClass().getResourceAsStream("/pt.cmd"));
				ZipArchiveEntry cmdEntry = new ZipArchiveEntry("pt.cmd");
				cmdEntry.setSize(cmdBytes.length);
				cmdEntry.setUnixMode(FileMode.REGULAR_FILE.getBits());
				cmdEntry.setTime(lastModified);
				zos.putArchiveEntry(cmdEntry);
				zos.write(cmdBytes);
				zos.closeArchiveEntry();

				// add a brief readme
				byte [] txtBytes = readAll(getClass().getResourceAsStream("/pt.txt"));
				ZipArchiveEntry txtEntry = new ZipArchiveEntry("readme.txt");
				txtEntry.setSize(txtBytes.length);
				txtEntry.setUnixMode(FileMode.REGULAR_FILE.getBits());
				txtEntry.setTime(lastModified);
				zos.putArchiveEntry(txtEntry);
				zos.write(txtBytes);
				zos.closeArchiveEntry();

				// cleanup
				zos.finish();
				zos.close();
				os.flush();
			} else {
				// unix: download a tar.gz file with pt.py set with execute permissions
				response.setHeader("Content-Disposition", "attachment; filename=\"pt.tar.gz\"");

				OutputStream os = response.getOutputStream();
				CompressorOutputStream cos = new CompressorStreamFactory().createCompressorOutputStream(CompressorStreamFactory.GZIP, os);
				TarArchiveOutputStream tos = new TarArchiveOutputStream(cos);
				tos.setAddPaxHeadersForNonAsciiNames(true);
				tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

				// add the Python script
				TarArchiveEntry pyEntry = new TarArchiveEntry("pt");
				pyEntry.setMode(FileMode.EXECUTABLE_FILE.getBits());
				pyEntry.setModTime(lastModified);
				pyEntry.setSize(pyBytes.length);
				tos.putArchiveEntry(pyEntry);
				tos.write(pyBytes);
				tos.closeArchiveEntry();

				// add a brief readme
				byte [] txtBytes = readAll(getClass().getResourceAsStream("/pt.txt"));
				TarArchiveEntry txtEntry = new TarArchiveEntry("README");
				txtEntry.setMode(FileMode.REGULAR_FILE.getBits());
				txtEntry.setModTime(lastModified);
				txtEntry.setSize(txtBytes.length);
				tos.putArchiveEntry(txtEntry);
				tos.write(txtBytes);
				tos.closeArchiveEntry();

				// cleanup
				tos.finish();
				tos.close();
				cos.close();
				os.flush();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	byte [] readAll(InputStream is) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			byte [] buffer = new byte[4096];
			int len = 0;
			while ((len = is.read(buffer)) > -1) {
				os.write(buffer, 0, len);
			}
			return os.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				os.close();
				is.close();
			} catch (Exception e) {
				// ignore
			}
		}
		return new byte[0];
	}
}
