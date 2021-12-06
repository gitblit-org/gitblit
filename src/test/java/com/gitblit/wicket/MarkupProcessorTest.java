package com.gitblit.wicket;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gitblit.manager.IManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.PluginRegistry;
import com.gitblit.tests.mock.MockGitblitContext;
import com.gitblit.tests.mock.MockRuntimeManager;
import org.apache.wicket.util.tester.WicketTester;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import com.gitblit.Keys;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.utils.JSoupXssFilter;
import com.gitblit.wicket.MarkupProcessor.MarkupDocument;
import ro.fortsoft.pf4j.PluginState;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.Version;

public class MarkupProcessorTest
{
	private WicketTester tester;
	private MockGitblitContext gbctx;

	@Before
	public void setUp()
	{
		IRuntimeManager rm = new MockRuntimeManager(getSettings());
		gbctx = new MockGitblitContext();
		gbctx.addManager(rm);
		tester = new WicketTester(new GitBlitWebApp(null, null,
													rm,
													getPluginManager(),
													null, null, null,
													null, null, null,
													null, null, null));
	}



	/*
	 * The unit tests for MarkupProcessor have two major goals.
	 * One is to check that links are rendered correctly, and
	 * the second one is that XSS protection is working.
	 *
	 * The proper rendering of markup for the various Wiki/Markdown
	 * languages is not in focus. This, as a secondary goal, the Wiki/Md
	 * syntax rendering can be tested to make sure that when switching to
	 * a new or updated wiki syntax library nothing breaks and the pages
	 * are still rendered correctly.
	 * Or, to make sure things actually render correctly, because currently
	 * they don't as cen be seen with reference images and wiki links,
	 * for example.
	 */



	@Test
	public void testParseMdRepoRelativeLink()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: [a page](file.md)";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","doc/testrepo/12345abcde/file.md", href);
	}

	@Test
	public void testParseMdRepoRelativeLinkSubfolder()
	{
		String psep ="%2F";
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: [a file](folder/file.md)";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","doc/testrepo/12345abcde/folder"+psep+"file.md", href);
	}

	@Test
	public void testParseMdRepoRelativeLinkSubSubfolder()
	{
		String psep ="%2F";
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: [a file](sub/folder/file.md)";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","doc/testrepo/12345abcde/sub"+psep+"folder"+psep+"file.md", href);
	}

	@Test
	public void testParseMdRepoRelativeLinkUtf8()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: [a page](日本語.md)";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","doc/testrepo/12345abcde/%E6%97%A5%E6%9C%AC%E8%AA%9E.md", href);
	}

	@Test
	public void testParseMdRepoRelativeLinkSubfolderUtf8()
	{
		String psep ="%2F";
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: [a file](folder/receitas_de_culinária/file.md)";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","doc/testrepo/12345abcde/folder"+psep+"receitas_de_culin%C3%A1ria"+psep+"file.md", href);
	}


 	@Test
	public void testParseMdExternalLink()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: [a website](http://example.com/page.html)";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","http://example.com/page.html", href);
	}

	@Test
	public void testParseMdExternalLinkBare()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: <http://example.com/page.html>";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","http://example.com/page.html", href);
	}


	// We leave it up to the document author to write working links in the document
	@Test
	public void testParseMdExternalLinkUtf8()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: [Japanese](http://example.com/lang/日本語.html)";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","http://example.com/lang/日本語.html", href);
	}



	@Test
	public void testParseMdRepoRelativeRefLink()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: [a page][1]\n\n[1]: file.md";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","doc/testrepo/12345abcde/file.md", href);
	}

	@Test
	public void testParseMdRepoRelativeRefLinkSubfolder()
	{
		String psep ="%2F";
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: [a file][file]\n\n[file]: folder/file.md";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","doc/testrepo/12345abcde/folder"+psep+"file.md", href);
	}

	@Test
	public void testParseMdRepoRelativeRefLinkSubSubfolder()
	{
		String psep ="%2F";
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: [a file][l1] \n\n[l1]: sub/folder/file.md";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","doc/testrepo/12345abcde/sub"+psep+"folder"+psep+"file.md", href);
	}

	@Test
	public void testParseMdRepoRelativeRefLinkUtf8()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: [a page][x]\n\n[x]: 日本語.md";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","doc/testrepo/12345abcde/%E6%97%A5%E6%9C%AC%E8%AA%9E.md", href);
	}

	@Test
	public void testParseMdRepoRelativeRefLinkSubfolderUtf8()
	{
		String psep ="%2F";
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: [a file][xy]\n\n[xy]: folder/receitas_de_culinária/file.md";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","doc/testrepo/12345abcde/folder"+psep+"receitas_de_culin%C3%A1ria"+psep+"file.md", href);
	}


	@Test
	public void testParseMdExternalRefLink()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: [a website][ex]\n\n[ex]: http://example.com/page.html";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","http://example.com/page.html", href);
	}





	/*
	 * Apparently wiki style links currently do not work in Markdown.

	@Test
	public void testParseMdWikiLink()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "link: [[page]]";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","doc/testrepo/12345abcde/page", href);
	}
	 */


	@Test
	public void testParseMdRepoRelativeImage()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "image: ![a graphic](graphic.gif)";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/graphic.gif", ref);

		markup =  "image: ![a graphic](graphic.gif \"Some graphic\")";
		mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		doc = Jsoup.parseBodyFragment(mdoc.html);
		ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/graphic.gif", ref);
	}

	@Test
	public void testParseMdRepoRelativeImageUtf8()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "image: ![look the dog](ドッグ.gif)";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/%E3%83%89%E3%83%83%E3%82%B0.gif", ref);

		markup =  "image: ![look the dog](ドッグ.gif \"シーバ\")";
		mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		doc = Jsoup.parseBodyFragment(mdoc.html);
		ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/%E3%83%89%E3%83%83%E3%82%B0.gif", ref);
	}

	@Test
	public void testParseMdRepoRelativeImageSubfolder()
	{
		String psep ="!";
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "image: ![a graphic](results/graphic.gif)";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/results"+psep+"graphic.gif", ref);

		markup =  "image: ![a graphic](results/graphic.gif \"Some graphic\")";
		mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		doc = Jsoup.parseBodyFragment(mdoc.html);
		ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/results"+psep+"graphic.gif", ref);
	}

	@Test
	public void testParseMdRepoRelativeImageSubfolderUtf8()
	{
		String psep ="!";
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "image: ![a cat](folder/картинки/cat.jpg)";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/folder"+psep+"%D0%BA%D0%B0%D1%80%D1%82%D0%B8%D0%BD%D0%BA%D0%B8"+psep+"cat.jpg", ref);

		markup =  "image: ![a cat](folder/картинки/cat.jpg \"Кошка\")";
		mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		doc = Jsoup.parseBodyFragment(mdoc.html);
		ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/folder"+psep+"%D0%BA%D0%B0%D1%80%D1%82%D0%B8%D0%BD%D0%BA%D0%B8"+psep+"cat.jpg", ref);
	}


	@Test
	public void testParseMdExternalImage()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "image: ![a cat](http://example.com/cats/meow.jpg)";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","http://example.com/cats/meow.jpg", ref);

		markup =  "image: ![a cat](http://example.com/cats/meow.jpg \"Miau\")";
		mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		doc = Jsoup.parseBodyFragment(mdoc.html);
		ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","http://example.com/cats/meow.jpg", ref);
	}






	@Test
	public void testParseMdRepoRelativeRefImage()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "image: ![a graphic][1]\n\n[1]: graphic.gif";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/graphic.gif", ref);

		markup =  "image: ![a graphic][2]\n\n[2]: graphic.gif \"Some graphic\"";
		mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		doc = Jsoup.parseBodyFragment(mdoc.html);
		ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/graphic.gif", ref);
	}

	@Test
	public void testParseMdRepoRelativeRefImageUtf8()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "image: ![look the dog][1]\n\n[1]: ドッグ.gif";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/%E3%83%89%E3%83%83%E3%82%B0.gif", ref);

		markup =  "image: ![look the dog][1]\n\n[1]: ドッグ.gif \"シーバ\"";
		mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		doc = Jsoup.parseBodyFragment(mdoc.html);
		ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/%E3%83%89%E3%83%83%E3%82%B0.gif", ref);
	}

	@Test
	public void testParseMdRepoRelativeRefImageSubfolder()
	{
		String psep ="!";
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "image: ![a cat][cat]\n\n[cat]: folder/cat.jpg";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/folder"+psep+"cat.jpg", ref);

		markup =  "image: ![a cat][cat]\n\n[cat]: folder/cat.jpg \"Кошка\"";
		mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		doc = Jsoup.parseBodyFragment(mdoc.html);
		ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/folder"+psep+"cat.jpg", ref);
	}

	@Test
	public void testParseMdRepoRelativeRefImageSubfolderUtf8()
	{
		String psep ="!";
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "image: ![a cat][i1]\n\n[i1]: folder/картинки/cat.jpg";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/folder"+psep+"%D0%BA%D0%B0%D1%80%D1%82%D0%B8%D0%BD%D0%BA%D0%B8"+psep+"cat.jpg", ref);

		markup =  "image: ![a cat][i1]\n\n[i1]: folder/картинки/cat.jpg \"Кошка\"";
		mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		doc = Jsoup.parseBodyFragment(mdoc.html);
		ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","../raw/testrepo/12345abcde/folder"+psep+"%D0%BA%D0%B0%D1%80%D1%82%D0%B8%D0%BD%D0%BA%D0%B8"+psep+"cat.jpg", ref);
	}


	@Test
	public void testParseMdExternalRefImage()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String markup =  "image: ![a cat][1]\n\n[1]: http://example.com/cats/meow.jpg";
		MarkupDocument mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","http://example.com/cats/meow.jpg", ref);

		markup =  "image: ![a cat][1]\n\n[1]: http://example.com/cats/meow.jpg \"Miau\"";
		mdoc = mp.parse("testrepo", "12345abcde", "main.md", markup);
		doc = Jsoup.parseBodyFragment(mdoc.html);
		ref = doc.getElementsByAttribute("src").attr("src");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect image src rendered","http://example.com/cats/meow.jpg", ref);
	}





	@Test
	public void testParseMediaWikiRepoRelativeLink()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String psep = "%2F";
		String markup =  "link: [[document]]";
		MarkupDocument mdoc = mp.parse("wikirepo", "12345abcde", "main.mw", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String ref = doc.getElementsByAttribute("href").attr("href");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect link href rendered","doc/wikirepo/12345abcde/wiki"+ psep + "document", ref);

	}

	@Test
	public void testParseMediaWikiRepoRelativeLinkUtf8()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String psep = "%2F";
		String markup =  "link: [[日本語]]";
		MarkupDocument mdoc = mp.parse("wikirepo", "12345abcde", "main.mw", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","doc/wikirepo/12345abcde/wiki" + psep + "%E6%97%A5%E6%9C%AC%E8%AA%9E", href);
	}


	@Test
	public void testParseMediaWikiExternalLink()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String psep = "%2F";
		String markup =  "link: [http://example.com/some/document.html document]";
		MarkupDocument mdoc = mp.parse("wikirepo", "12345abcde", "main.mw", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String ref = doc.getElementsByAttribute("href").attr("href");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect link href rendered","http://example.com/some/document.html", ref);

	}

	@Test
	public void testParseMediaWikiExternalLinkNumbered()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String psep = "%2F";
		String markup =  "link: [http://example.com/some/document.html]";
		MarkupDocument mdoc = mp.parse("wikirepo", "12345abcde", "main.mw", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String ref = doc.getElementsByAttribute("href").attr("href");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect link href rendered","http://example.com/some/document.html", ref);

	}

	@Test
	public void testParseMediaWikiExternalLinkBare()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String psep = "%2F";
		String markup =  "link: http://example.com/some/document.html";
		MarkupDocument mdoc = mp.parse("wikirepo", "12345abcde", "main.mw", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String ref = doc.getElementsByAttribute("href").attr("href");
		assertFalse("No reference attribute found: " + mdoc.html, ref.isEmpty());
		assertEquals("Incorrect link href rendered","http://example.com/some/document.html", ref);

	}

	// We leave it up to the document author to write working links in the document
	@Test
	public void testParseMediaWikiExternalLinkUtf8()
	{
		MarkupProcessor mp = new MarkupProcessor(getSettings(), new JSoupXssFilter());

		String psep = "%2F";
		String markup =  "link: [http://example.com/lang/日本語.html Japanese]";
		MarkupDocument mdoc = mp.parse("wikirepo", "12345abcde", "main.mw", markup);
		Document doc = Jsoup.parseBodyFragment(mdoc.html);
		String href = doc.getElementsByAttribute("href").attr("href");
		assertEquals("Incorrect link rendered","http://example.com/lang/日本語.html", href);
	}




	private MemorySettings getSettings()
	{
		Map<String, Object> backingMap = new HashMap<String, Object>();

		backingMap.put(Keys.web.documents, "readme");
		backingMap.put(Keys.web.blobEncodings, "UTF-8 ISO-8859-1");
		backingMap.put(Keys.web.confluenceExtensions, "confluence");
		backingMap.put(Keys.web.markdownExtensions, "md mkd markdown MD MKD");
		backingMap.put(Keys.web.mediawikiExtensions, "mw mediawiki");
		backingMap.put(Keys.web.textileExtensions, "textile");
		backingMap.put(Keys.web.tracwikiExtensions, "tracwiki");
		backingMap.put(Keys.web.twikiExtensions, "twiki");
		backingMap.put(Keys.web.forwardSlashCharacter, "/");
		backingMap.put(Keys.web.mountParameters, true);

		MemorySettings ms = new MemorySettings(backingMap);
		return ms;
	}


	private IPluginManager getPluginManager()
	{
		return new IPluginManager()
		{
			@Override
			public Version getSystemVersion()
			{
				return null;
			}

			@Override
			public void startPlugins()
			{

			}

			@Override
			public void stopPlugins()
			{

			}

			@Override
			public PluginState startPlugin(String pluginId)
			{
				return null;
			}

			@Override
			public PluginState stopPlugin(String pluginId)
			{
				return null;
			}

			@Override
			public List<Class<?>> getExtensionClasses(String pluginId)
			{
				return null;
			}

			@Override
			public <T> List<T> getExtensions(Class<T> type)
			{
				return null;
			}

			@Override
			public List<PluginWrapper> getPlugins()
			{
				return Collections.emptyList();
			}

			@Override
			public PluginWrapper getPlugin(String pluginId)
			{
				return null;
			}

			@Override
			public PluginWrapper whichPlugin(Class<?> clazz)
			{
				return null;
			}

			@Override
			public boolean disablePlugin(String pluginId)
			{
				return false;
			}

			@Override
			public boolean enablePlugin(String pluginId)
			{
				return false;
			}

			@Override
			public boolean uninstallPlugin(String pluginId)
			{
				return false;
			}

			@Override
			public boolean refreshRegistry(boolean verifyChecksum)
			{
				return false;
			}

			@Override
			public boolean installPlugin(String url, boolean verifyChecksum) throws IOException
			{
				return false;
			}

			@Override
			public boolean upgradePlugin(String pluginId, String url, boolean verifyChecksum) throws IOException
			{
				return false;
			}

			@Override
			public List<PluginRegistry.PluginRegistration> getRegisteredPlugins()
			{
				return null;
			}

			@Override
			public List<PluginRegistry.PluginRegistration> getRegisteredPlugins(PluginRegistry.InstallState state)
			{
				return null;
			}

			@Override
			public PluginRegistry.PluginRegistration lookupPlugin(String idOrName)
			{
				return null;
			}

			@Override
			public PluginRegistry.PluginRelease lookupRelease(String idOrName, String version)
			{
				return null;
			}

			@Override
			public IManager start()
			{
				return null;
			}

			@Override
			public IManager stop()
			{
				return null;
			}
		};
	}

}
