/*
 * Copyright 2014 Tom <tw201207@gmail.com>
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
package com.gitblit.wicket.pages;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.wicket.protocol.http.WicketURLEncoder;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.Side;
import org.jsoup.nodes.Element;

import com.gitblit.servlet.RawServlet;
import com.gitblit.utils.DiffUtils;
import com.gitblit.utils.HtmlBuilder;

/**
 * A {@link DiffUtils.BinaryDiffHandler BinaryDiffHandler} for images.
 *
 * @author Tom <tw201207@gmail.com>
 */
public class ImageDiffHandler implements DiffUtils.BinaryDiffHandler {

	private final String oldCommitId;
	private final String newCommitId;
	private final String repositoryName;
	private final String baseUrl;
	private final List<String> imageExtensions;

	private int imgDiffCount = 0;

	public ImageDiffHandler(final String baseUrl, final String repositoryName, final String oldCommitId,
			final String newCommitId, final List<String> imageExtensions) {
		this.baseUrl = baseUrl;
		this.repositoryName = repositoryName;
		this.oldCommitId = oldCommitId;
		this.newCommitId = newCommitId;
		this.imageExtensions = imageExtensions;
	}

	/** {@inheritDoc} */
	@Override
	public String renderBinaryDiff(DiffEntry diffEntry) {
		switch (diffEntry.getChangeType()) {
		case MODIFY:
		case RENAME:
		case COPY:
			// TODO: for very small images such as icons, the slider doesn't really help. Two possible
			// approaches: either upscale them for display (may show blurry upscaled images), or show
			// them side by side (may still be too small to really make out the differences).
			String oldUrl = getImageUrl(diffEntry, Side.OLD);
			String newUrl = getImageUrl(diffEntry, Side.NEW);
			if (oldUrl != null && newUrl != null) {
				imgDiffCount++;
				String id = "imgdiff" + imgDiffCount;
				HtmlBuilder builder = new HtmlBuilder("div");
				Element container = builder.root().attr("align", "center").appendElement("div").attr("class", "imgdiff");
				Element resizeable = container.appendElement("div").attr("class", "imgdiff-left");
				// style='max-width:640px;' is necessary for ensuring that the browser limits large images
				// to some reasonable width, and to override the "img { max-width: 100%; }" from bootstrap.css,
				// which would scale the left image to the width of its resizeable container, which isn't what
				// we want here. Note that the max-width must be defined directly as inline style on the element,
				// otherwise browsers ignore it if the image is larger, and we end up with an image display that
				// is too wide.
				// XXX: Maybe add a max-height, too, to limit portrait-oriented images to some reasonable height?
				// (Like a 300x10000px image...)
				resizeable.appendElement("img").attr("class", "imgdiff-left").attr("id", id).attr("style", "max-width:640px;").attr("src", oldUrl);
				container.appendElement("img").attr("class", "imgdiff").attr("style", "max-width:640px;").attr("src", newUrl);
				builder.root().appendElement("br");
				Element slider = builder.root().appendElement("div").attr("class", "imgdiff-slider").attr("id", "slider-" + id);
				slider.appendElement("div").attr("class", "imgdiff-slider-inner");
				return builder.toString();
			}
			break;
		case ADD:
			String url = getImageUrl(diffEntry, Side.NEW);
			if (url != null) {
				return new HtmlBuilder("img").root().attr("class", "diff-img").attr("src", url).toString();
			}
			break;
		default:
			break;
		}
		return null;
	}

	/** Returns the number of image diffs generated so far by this {@link ImageDiffHandler}. */
	public int getImgDiffCount() {
		return imgDiffCount;
	}

	/**
	 * Constructs a URL that will fetch the designated resource in the git repository. The returned string will
	 * contain the URL fully URL-escaped, but note that it may still contain unescaped ampersands, so the result
	 * must still be run through HTML escaping if it is to be used in HTML.
	 *
	 * @return the URL to the image, if the given {@link DiffEntry} and {@link Side} refers to an image, or {@code null} otherwise.
	 */
	protected String getImageUrl(DiffEntry entry, Side side) {
		String path = entry.getPath(side);
		int i = path.lastIndexOf('.');
		if (i > 0) {
			String extension = path.substring(i + 1);
			for (String ext : imageExtensions) {
				if (ext.equalsIgnoreCase(extension)) {
					String commitId = Side.NEW.equals(side) ? newCommitId : oldCommitId;
					if (commitId != null) {
						return RawServlet.asLink(baseUrl, urlencode(repositoryName), commitId, urlencode(path));
					} else {
						return null;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Encode a URL component of a {@link RawServlet} URL in the special way that the servlet expects it. Note that
	 * the %-encoding used does not encode '&amp;' or '&lt;'. Slashes are not encoded in the result.
	 *
	 * @param component
	 *            to encode using %-encoding
	 * @return the encoded component
	 */
	protected String urlencode(final String component) {
		// RawServlet handles slashes itself. Note that only the PATH_INSTANCE fits the bill here: it encodes
		// spaces as %20, and we just have to correct for encoded slashes. Java's standard URLEncoder would
		// encode spaces as '+', and I don't know what effects that would have on other parts of GitBlit. It
		// would also be wrong for path components (but fine for a query part), so we'd have to correct it, too.
		//
		// Actually, this should be done in RawServlet.asLink(). As it is now, this may be incorrect if that
		// operation ever uses query parameters instead of paths, or if it is fixed to urlencode its path
		// components. But I don't want to touch that static method in RawServlet.
		return WicketURLEncoder.PATH_INSTANCE.encode(component, StandardCharsets.UTF_8.name()).replaceAll("%2[fF]", "/");
	}
}
