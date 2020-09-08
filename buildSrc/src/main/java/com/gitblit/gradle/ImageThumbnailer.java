/*
 * Copyright 2019 Tue Ton
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
package com.gitblit.gradle;

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

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates thumbnails from all images in the source folder and saves
 * them to the destination folder.
 * This class is a copy of org.moxie.ant.MxThumbs.java and
 * modified to be a Gradle task class.
 */
public class ImageThumbnailer extends DefaultTask {

	private static Logger logger = LoggerFactory.getLogger(ImageThumbnailer.class.getName());

	String inputType = "png";
	String outputType = "png";
	int maxDimension;
	File sourceDir;
	File destDir;

	@Input
	public String getInputType() {
		return inputType;
	}

	@Input
	public String getOutputType() {
		return outputType;
	}

	@Input
	public int getMaxDimension() {
		return maxDimension;
	}

	@InputDirectory
	public File getSourceDir() {
		return sourceDir;
	}

	@OutputDirectory
	public File getDestDir() {
		return destDir;
	}

	public void setInputType(String inputType) {
		this.inputType = inputType;
	}

	public void setOutputType(String outputType) {
		this.outputType = outputType;
	}

	public void setMaxDimension(int maxDimension) {
		this.maxDimension = maxDimension;
	}

	public void setSourceDir(File sourceDir) {
		this.sourceDir = sourceDir;
	}

	public void setDestDir(File destDir) {
		this.destDir = destDir;
	}

	@TaskAction
	public void generateThumbnails() {
		File[] sourceFiles = sourceDir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith("." + inputType);
			}
		});
		
		if (sourceFiles != null) {
			logger.info("Generating {0} {1}", sourceFiles.length, sourceFiles.length == 1 ? "thumbnail" : "thumbnails");
			for (File sourceFile : sourceFiles) {
				String name = sourceFile.getName();
				name = name.substring(0, name.lastIndexOf('.') + 1) + outputType;
				File destinationFile = new File(destDir, name);
				try {
					Dimension sz = getImageDimensions(sourceFile);
					int w = 0;
					int h = 0;
					if (sz.width > maxDimension) {
						// Scale to Width
						w = maxDimension;
						float f = maxDimension;
						// normalize height
						h = (int) ((f / sz.width) * sz.height);
					} else if (sz.height > maxDimension) {
						// Scale to Height
						h = maxDimension;
						float f = maxDimension;
						// normalize width
						w = (int) ((f / sz.height) * sz.width);
					}
					logger.debug("thumbnail for {0} as ({1,number,#}, {2,number,#})",
							sourceFile.getName(), w, h);
					BufferedImage image = ImageIO.read(sourceFile);
					Image scaledImage = image.getScaledInstance(w, h, BufferedImage.SCALE_SMOOTH);
					BufferedImage destImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
					destImage.createGraphics().drawImage(scaledImage, 0, 0, null);
					FileOutputStream fos = new FileOutputStream(destinationFile);
					ImageIO.write(destImage, outputType, fos);
					fos.flush();
					fos.getFD().sync();
					fos.close();
				} catch (Throwable t) {
					logger.error("failed to generate thumbnail for " + sourceFile, t);
				}
			}
		}
	}

	/**
	 * Return the dimensions of the specified image file.
	 * 
	 * @param file
	 * @return dimensions of the image
	 * @throws IOException
	 */
	Dimension getImageDimensions(File file) throws IOException {
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
			if (in != null) {
				in.close();
			}
		}
		return null;
	}

}
