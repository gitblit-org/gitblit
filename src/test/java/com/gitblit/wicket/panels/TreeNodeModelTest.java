package com.gitblit.wicket.panels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.gitblit.models.RepositoryModel;

public class TreeNodeModelTest {

    @Test
    public void testContainsSubFolder() {
        TreeNodeModel tree = new TreeNodeModel();
        tree.add("foo").add("bar").add("baz");

        assertTrue(tree.containsSubFolder("foo/bar/baz"));
        assertTrue(tree.containsSubFolder("foo/bar"));
        assertFalse(tree.containsSubFolder("foo/bar/blub"));
    }

    @Test
    public void testAddInHierarchy() {
        TreeNodeModel tree = new TreeNodeModel();
        tree.add("foo").add("bar");

        RepositoryModel model = new RepositoryModel("test","","",null);

        // add model to non-existing folder. should be created automatically
        tree.add("foo/bar/baz", model);
        tree.add("another/non/existing/folder", model);

        assertTrue(tree.containsSubFolder("foo/bar/baz"));
        assertTrue(tree.containsSubFolder("another/non/existing/folder"));
    }

    @Test
    public void testGetDepth() {
        TreeNodeModel tree = new TreeNodeModel();
        TreeNodeModel bar = tree.add("foo").add("bar").add("baz");

        assertEquals(0, tree.getDepth());
        assertEquals(3, bar.getDepth());
    }


}
