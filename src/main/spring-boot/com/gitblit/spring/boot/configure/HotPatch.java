package com.gitblit.spring.boot.configure;

import java.io.IOException;
import java.net.URL;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.models.FilestoreModel;
import com.gitblit.models.PathModel;
import com.gitblit.utils.PathUtils;
import com.google.common.base.Strings;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

public class HotPatch {
    static final Logger JGitUtilsLogger = LoggerFactory.getLogger(HotPatch.class);
    private static void error(Logger logger,Throwable t, Repository repository, String pattern, Object... objects) {
        List<Object> parameters = new ArrayList<Object>();
        if (objects != null && objects.length > 0) {
            for (Object o : objects) {
                parameters.add(o);
            }
        }
        if (repository != null) {
            parameters.add(0, repository.getDirectory().getAbsolutePath());
        }
        logger.error(MessageFormat.format(pattern, parameters.toArray()), t);
    }
    
    private static PathModel getPathModel(Repository repo, String path, String filter, RevCommit commit)
            throws IOException {

        long size = 0;
        FilestoreModel filestoreItem = null;
        TreeWalk tw = TreeWalk.forPath(repo, path, commit.getTree());
        String pathString = path;

        if (!tw.isSubtree() && (tw.getFileMode(0) != FileMode.GITLINK)) {

            pathString = PathUtils.getLastPathComponent(pathString);
            
            size = tw.getObjectReader().getObjectSize(tw.getObjectId(0), org.eclipse.jgit.lib.Constants.OBJ_BLOB);
            
            if (com.gitblit.utils.JGitUtils.isPossibleFilestoreItem(size)) {
                filestoreItem = com.gitblit.utils.JGitUtils.getFilestoreItem(tw.getObjectReader().open(tw.getObjectId(0)));
            }
        } else if (tw.isSubtree()) {

            // do not display dirs that are behind in the path
            if (!Strings.isNullOrEmpty(filter)) {
                pathString = path.replaceFirst(filter + "/", "");
            }

            // remove the last slash from path in displayed link
            if (pathString != null && pathString.charAt(pathString.length()-1) == '/') {
                pathString = pathString.substring(0, pathString.length()-1);
            }
        }

        return new PathModel(pathString, tw.getPathString(), filestoreItem, size, tw.getFileMode(0).getBits(),
                tw.getObjectId(0).getName(), commit.getName());

    }
    public static List<PathModel> getFilesInPath2(Repository repository, String path, RevCommit commit) {

        List<PathModel> list = new ArrayList<PathModel>();
        if (!com.gitblit.utils.JGitUtils.hasCommits(repository)) {
            return list;
        }
        if (commit == null) {
            commit = com.gitblit.utils.JGitUtils.getCommit(repository, null);
        }
        final TreeWalk tw = new TreeWalk(repository);
        try {

            tw.addTree(commit.getTree());
            final boolean isPathEmpty = Strings.isNullOrEmpty(path);

            if (!isPathEmpty) {
                PathFilter f = PathFilter.create(path);
                tw.setFilter(f);
            }

            tw.setRecursive(true);
            List<String> paths = new ArrayList<>();

            /**
            while (tw.next()) {
                    String child = isPathEmpty ? tw.getPathString()
                            : tw.getPathString().replaceFirst(String.format("%s/", path), "");
                    paths.add(child);
            }*/
            /**
             * FIXME 使用Apache Lang3替换原有的替换过程，以防止正则问题。
             */
            while (tw.next()) {
                    String child = "";
                    if(isPathEmpty) {
                        child = tw.getPathString();
                    }else {
                        child =org.apache.commons.lang3.StringUtils.replace(tw.getPathString(), String.format("%s/", path), "", 1);
                        //child = tw.getPathString().replaceFirst(String.format("%s/", path), "");
                    }
                    paths.add(child);
            }

            for(String p: PathUtils.compressPaths(paths)) {
                String pathString = isPathEmpty ? p : String.format("%s/%s", path, p);
                list.add(getPathModel(repository, pathString, path, commit));
            }

        } catch (IOException e) {
            error(JGitUtilsLogger,e, repository, "{0} failed to get files for commit {1}", commit.getName());
        } finally {
            tw.close();
        }
        Collections.sort(list);
        return list;
    }
    protected static String getRelativePath(HttpServletRequest request) {
        if (request.getAttribute("javax.servlet.include.request_uri") != null) {
            String result = (String) request.getAttribute("javax.servlet.include.path_info");
            if (result == null) {
                result = (String) request.getAttribute("javax.servlet.include.servlet_path");
            } else {
                result = (String) request.getAttribute("javax.servlet.include.servlet_path") + result;
            }
            if ((result == null) || (result.equals(""))) {
                result = "/";
            }
            return result;
        }
        String result = request.getPathInfo();
        if (result == null) {
            result = request.getServletPath();
        } else {
            result = request.getServletPath() + result;
        }
        if ((result == null) || (result.equals(""))) {
            result = "/";
        }
        return result;
    }
    public static String getFullUrl(HttpServletRequest httpRequest) {
        //fixme 修复在Springboot中无法正确得到servlet信息的问题，暂时使用遍历的方式来取得合法路径。
        String result = getRelativePath(httpRequest);

        String getContextPath = httpRequest.getContextPath();
        String info = httpRequest.getPathInfo();
        String getServletPath = httpRequest.getServletPath();
        String servletUrl = getContextPath + getServletPath;
        String getRequestURI = httpRequest.getRequestURI();
        if (servletUrl.startsWith(Constants.R_PATH)) {
            servletUrl = Constants.R_PATH;
        }
        if (servletUrl.startsWith(Constants.RAW_PATH)) {
            servletUrl = Constants.RAW_PATH;
        }
        String url = getRequestURI.substring(servletUrl.length());
        String params = httpRequest.getQueryString();
        if (url.length() > 0 && url.charAt(0) == '/') {
            url = url.substring(1);
        }
        String fullUrl = url + (StringUtils.isEmpty(params) ? "" : ("?" + params));
        return fullUrl;
    }
    static {
        System.err.println("gitblit hotfix start");
        ClassPool classPool = ClassPool.getDefault();
        Class<? extends HotPatch> rootClass = new HotPatch().getClass();
        classPool.insertClassPath(new ClassClassPath(rootClass));
        List<URL> hotfix_urls = new ArrayList<>();
        List<CtClass> hotfix = new ArrayList<>();
        try {
            //getFullUrl    (Ljavax/servlet/http/HttpServletRequest;)Ljava/lang/String;
            CtClass ctClass = classPool.get("com.gitblit.utils.JGitUtils");
             
            CtMethod c1 = ctClass.getMethod("getFilesInPath2", "(Lorg/eclipse/jgit/lib/Repository;Ljava/lang/String;Lorg/eclipse/jgit/revwalk/RevCommit;)Ljava/util/List;");
            c1.setBody("return com.gitblit.spring.boot.configure.HotPatch.getFilesInPath2($1,$2,$3);");

            CodeSource cs = new CodeSource(ctClass.getURL(), (java.security.cert.Certificate[]) null);
            PermissionCollection pc = Policy.getPolicy().getPermissions(cs);
            ctClass.toClass(rootClass.getClassLoader(), new ProtectionDomain(cs, pc));
            hotfix.add(ctClass);
            hotfix_urls.add(ctClass.getURL());
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        try {
            //getFullUrl    (Ljavax/servlet/http/HttpServletRequest;)Ljava/lang/String;
            CtClass ctClass = classPool.get("com.gitblit.servlet.AuthenticationFilter");
            for(CtMethod test1 : ctClass.getMethods()) {
                System.err.println(test1.getName()+"\t"+test1.getSignature());
            }
            CtMethod c1 = ctClass.getMethod("getFullUrl", "(Ljavax/servlet/http/HttpServletRequest;)Ljava/lang/String;");
            c1.setBody("return com.gitblit.spring.boot.configure.HotPatch.getFullUrl($1);");

            CodeSource cs = new CodeSource(ctClass.getURL(), (java.security.cert.Certificate[]) null);
            PermissionCollection pc = Policy.getPolicy().getPermissions(cs);
            ctClass.toClass(rootClass.getClassLoader(), new ProtectionDomain(cs, pc));
            hotfix.add(ctClass);
            hotfix_urls.add(ctClass.getURL());
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        
        for (int i = 0; i < hotfix.size(); i++) {
            CtClass ct = hotfix.get(i);
            System.err.println("\thotfix:\t" + ct.getName());
            System.err.println("\t\t" + hotfix_urls.get(i));
        }
        System.err.println("gitblit 1.8.0 hotfix end");
    }
}
