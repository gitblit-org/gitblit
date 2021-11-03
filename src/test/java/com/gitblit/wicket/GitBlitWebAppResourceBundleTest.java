package com.gitblit.wicket;

import org.junit.Test;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.Assert.*;

public class GitBlitWebAppResourceBundleTest
{
    @Test
    public void testDefaultResource()
    {
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp");
        assertNotNull(bundle);
        assertEquals("default", bundle.getString("gb.loadLang"));
    }

    @Test
    public void testCsResource()
    {
        Locale l = Locale.forLanguageTag("cs");
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        assertEquals("čeština", bundle.getString("gb.loadLang"));
    }

    @Test
    public void testDeResource()
    {
        Locale l = Locale.GERMAN;
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        assertEquals("Deutsch", bundle.getString("gb.loadLang"));
    }

    @Test
    public void testEnResource()
    {
        Locale l = Locale.ENGLISH;
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        // The "en" file is just a placeholder for the default one.
        assertEquals("default", bundle.getString("gb.loadLang"));
    }

    @Test
    public void testEsResource()
    {
        Locale l = Locale.forLanguageTag("es");
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        assertEquals("Español", bundle.getString("gb.loadLang"));
    }

    @Test
    public void testFrResource() throws Exception
    {
        Locale l = Locale.FRENCH;
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        assertEquals("français", bundle.getString("gb.loadLang"));
    }

    @Test
    public void testItResource() throws Exception
    {
        Locale l = Locale.ITALIAN;
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        assertEquals("Italiano", bundle.getString("gb.loadLang"));
    }

    @Test
    public void testJaResource() throws Exception
    {
        Locale l = Locale.JAPANESE;
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        assertEquals("にほんご", bundle.getString("gb.loadLang"));
    }

    @Test
    public void testKoResource() throws Exception
    {
        Locale l = Locale.KOREAN;
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        assertEquals("한국어", bundle.getString("gb.loadLang"));
    }

    @Test
    public void testNlResource() throws Exception
    {
        Locale l = Locale.forLanguageTag("nl");
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        assertEquals("Nederlands", bundle.getString("gb.loadLang"));
    }

    @Test
    public void testNoResource() throws Exception
    {
        Locale l = Locale.forLanguageTag("no");
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        assertEquals("Norsk", bundle.getString("gb.loadLang"));
    }

    @Test
    public void testPlResource() throws Exception
    {
        Locale l = Locale.forLanguageTag("pl");
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        assertEquals("polszczyzna", bundle.getString("gb.loadLang"));
    }

    @Test
    public void testPtBrResource() throws Exception
    {
        Locale l = Locale.forLanguageTag("pt-BR");
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        assertEquals("Português", bundle.getString("gb.loadLang"));
    }


    @Test
    public void testRuResource() throws Exception
    {
        Locale l = Locale.forLanguageTag("ru");
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        assertEquals("Русский", bundle.getString("gb.loadLang"));
    }

    @Test
    public void testZhCnResource() throws Exception
    {
        Locale l = Locale.SIMPLIFIED_CHINESE;
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        assertEquals("汉字", bundle.getString("gb.loadLang"));
    }

    @Test
    public void testZhTwResource() throws Exception
    {
        Locale l = Locale.TRADITIONAL_CHINESE;
        ResourceBundle bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", l);
        assertNotNull(bundle);
        assertEquals("漢字", bundle.getString("gb.loadLang"));
    }
}