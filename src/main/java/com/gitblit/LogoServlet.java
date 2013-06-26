/*
 * Copyright 2013 gitblit.com.
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
package com.gitblit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles requests for logo.png
 * 
 * @author James Moger
 * 
 */
public class LogoServlet extends HttpServlet {
	
	private static final long serialVersionUID = 1L;
	
	private static final long lastModified = System.currentTimeMillis();

	public LogoServlet() {
		super();
	}
	
	@Override
	protected long getLastModified(HttpServletRequest req) {
		File file = GitBlit.getFileOrFolder(Keys.web.headerLogo, "${baseFolder}/logo.png");
		if (file.exists()) {
			return file.lastModified();
		} else {
			return lastModified;
		}
	}
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		InputStream is = null;
		try {
			String contentType = null;
			File file = GitBlit.getFileOrFolder(Keys.web.headerLogo, "${baseFolder}/logo.png");
			if (file.exists()) {
				// custom logo
				ServletContext context = request.getSession().getServletContext();
				contentType = context.getMimeType(file.getName());
				response.setContentLength((int) file.length());
				response.setDateHeader("Last-Modified", file.lastModified());
				is = new FileInputStream(file);
			} else {
				// default logo
				response.setDateHeader("Last-Modified", lastModified);
				is = getClass().getResourceAsStream("/logo.png");
			}			
			if (contentType == null) {
				contentType = "image/png";
			}
			response.setContentType(contentType);
			OutputStream os = response.getOutputStream();
			byte[] buf = new byte[4096];
			int bytesRead = is.read(buf);
			while (bytesRead != -1) {
				os.write(buf, 0, bytesRead);
				bytesRead = is.read(buf);
			}
			os.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			is.close();
		}
	}
}
