/*
 * Copyright 2011 gitblit.com.
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

import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

public class BuildThumbnails {

	public static void main(String[] args) {
		Params params = new Params();
		JCommander jc = new JCommander(params);
		try {
			jc.parse(args);
		} catch (ParameterException t) {
			System.err.println(t.getMessage());
			jc.usage();
		}
		createImageThumbnail(params.sourceFolder, params.destinationFolder, params.maximumDimension);
	}

	public static void createImageThumbnail(String sourceFolder, String destinationFolder,
			int maxDimension) {
		if (maxDimension <= 0)
			return;
		File source = new File(sourceFolder);
		File destination = new File(destinationFolder);
		destination.mkdirs();
		File[] sourceFiles = source.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".png");
			}
		});

		for (File sourceFile : sourceFiles) {
			File destinationFile = new File(destination, sourceFile.getName());
			try {
				Dimension sz = getImageDimensions(sourceFile);
				int w = 0;
				int h = 0;
				if (sz.width > maxDimension) {
					// Scale to Width
					w = maxDimension;
					float f = maxDimension;
					h = (int) ((f / sz.width) * sz.height); // normalize height
				} else if (sz.height > maxDimension) {
					// Scale to Height
					h = maxDimension;
					float f = maxDimension;
					w = (int) ((f / sz.height) * sz.width); // normalize width
				} else {
					// No thumbnail
					return;
				}
				System.out.println("Generating thumbnail for " + sourceFile.getName() + " as (" + w
						+ "," + h + ")");
				BufferedImage image = ImageIO.read(sourceFile);
				Image scaledImage = image.getScaledInstance(w, h, BufferedImage.SCALE_SMOOTH);
				BufferedImage destImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
				destImage.createGraphics().drawImage(scaledImage, 0, 0, null);
				FileOutputStream fos = new FileOutputStream(destinationFile);
				ImageIO.write(destImage, "png", fos);
				fos.flush();
				fos.getFD().sync();
				fos.close();
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}

	public static Dimension getImageDimensions(File file) throws IOException {
		ImageInputStream in = ImageIO.createImageInputStream(file);
		try {
			final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (readers.hasNext()) {
				ImageReader reader = readers.next();
				try {
					reader.setInput(in);
					return new Dimension(reader.getWidth(0), reader.getHeight(0));
				} finally {
					reader.dispose();
				}
			}
		} finally {
			if (in != null)
				in.close();
		}
		return null;
	}

	@Parameters(separators = " ")
	private static class Params {

		@Parameter(names = { "--sourceFolder" }, description = "Source folder for raw images", required = true)
		public String sourceFolder;

		@Parameter(names = { "--destinationFolder" }, description = "Destination folder for thumbnails", required = true)
		public String destinationFolder;

		@Parameter(names = { "--maximumDimension" }, description = "Maximum width or height for thumbnail", required = true)
		public int maximumDimension;

	}
}
