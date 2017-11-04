package com.gitblit.wicket.panels;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.StringUtils;

public class TreeNodeModel implements Serializable {

    private static final long serialVersionUID = 1L;
    final TreeNodeModel parent;
    final String name;
    private final List<TreeNodeModel> subFolders = new ArrayList<>();
    private final List<RepositoryModel> repositories = new ArrayList<>();

    /**
     * Create a new tree root
     */
    public TreeNodeModel() {
        this.name = "";
        this.parent = null;
    }

    protected TreeNodeModel(String name, TreeNodeModel parent) {
        this.name = name;
        this.parent = parent;
    }

    public int getDepth() {
        if(parent == null) {
            return 0;
        }else {
            return parent.getDepth() +1;
        }
    }

    /**
     * Add a new sub folder to the current folder
     *
     * @param subFolder the subFolder to create
     * @return the newly created folder to allow chaining
     */
    public TreeNodeModel add(String subFolder) {
        TreeNodeModel n = new TreeNodeModel(subFolder, this);
        subFolders.add(n);
        return n;
    }

    /**
     * Add the given repo to the current folder
     *
     * @param repo
     */
    public void add(RepositoryModel repo) {
        repositories.add(repo);
    }

    /**
     * Adds the given repository model within the given path. Creates parent folders if they do not exist
     *
     * @param path
     * @param model
     */
    public void add(String path, RepositoryModel model) {
        TreeNodeModel folder = getSubTreeNode(this, path, true);
        folder.add(model);
    }

    @Override
    public String toString() {
        String string = name + "\n";
        for(TreeNodeModel n : subFolders) {
            string += "f";
            for(int i = 0; i < n.getDepth(); i++) {
                string += "-";
            }
            string += n.toString();
        }

        for(RepositoryModel n : repositories) {
            string += "r";
            for(int i = 0; i < getDepth()+1; i++) {
                string += "-";
            }
            string += n.toString() + "\n";
        }

        return string;
    }

    public boolean containsSubFolder(String path) {
        return containsSubFolder(this, path);
    }

    public TreeNodeModel getSubFolder(String path) {
        return getSubTreeNode(this, path, false);
    }




    private static TreeNodeModel getSubTreeNode(TreeNodeModel node, String path, boolean create) {
        if(!StringUtils.isEmpty(path)) {
            boolean isPathInCurrentHierarchyLevel = path.lastIndexOf('/') < 0;
            if(isPathInCurrentHierarchyLevel) {
                for(TreeNodeModel t : node.subFolders) {
                    if(t.name.equals(path) ) {
                        return t;
                    }
                }

                if(create) {
                    node.add(path);
                    return getSubTreeNode(node, path, true);
                }
            }else {
                //traverse into subFolder
                String folderInCurrentHierarchyLevel = path.substring(0, path.indexOf('/'));

                for(TreeNodeModel t : node.subFolders) {
                    if(t.name.equals(folderInCurrentHierarchyLevel) ) {
                        String folderInNextHierarchyLevel = path.substring(path.indexOf('/') + 1, path.length());
                        return getSubTreeNode(t, folderInNextHierarchyLevel, create);
                    }
                }

                if(create) {
                    String folderInNextHierarchyLevel = path.substring(path.indexOf('/') + 1, path.length());
                    return getSubTreeNode(node.add(folderInCurrentHierarchyLevel), folderInNextHierarchyLevel, true);
                }
            }
        }

        return null;
    }

    private static boolean containsSubFolder(TreeNodeModel node, String path) {
        return getSubTreeNode(node, path, false) != null;
    }
}
