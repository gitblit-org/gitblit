/*
 * Copyright 2012 gitblit.com.
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

import static org.eclipse.jgit.treewalk.filter.TreeFilter.ANY_DIFF;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.SearchObjectType;
import com.gitblit.models.IssueModel;
import com.gitblit.models.IssueModel.Attachment;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SearchResult;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.IssueUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;

/**
 * The Lucene executor handles indexing and searching repositories.
 * 
 * @author James Moger
 * 
 */
public class LuceneExecutor implements Runnable {
	
		
	private static final int INDEX_VERSION = 2;

	private static final String FIELD_OBJECT_TYPE = "type";
	private static final String FIELD_ISSUE = "issue";
	private static final String FIELD_PATH = "path";
	private static final String FIELD_COMMIT = "commit";
	private static final String FIELD_BRANCH = "branch";
	private static final String FIELD_SUMMARY = "summary";
	private static final String FIELD_CONTENT = "content";
	private static final String FIELD_AUTHOR = "author";
	private static final String FIELD_COMMITTER = "committer";
	private static final String FIELD_DATE = "date";
	private static final String FIELD_TAG = "tag";
	private static final String FIELD_LABEL = "label";
	private static final String FIELD_ATTACHMENT = "attachment";

	private static final String CONF_FILE = "lucene.conf";
	private static final String LUCENE_DIR = "lucene";
	private static final String CONF_INDEX = "index";
	private static final String CONF_VERSION = "version";
	private static final String CONF_ALIAS = "aliases";
	private static final String CONF_BRANCH = "branches";
		
	private static final Version LUCENE_VERSION = Version.LUCENE_35;
	
	private final Logger logger = LoggerFactory.getLogger(LuceneExecutor.class);
	
	private final IStoredSettings storedSettings;
	private final File repositoriesFolder;
	
	private final Map<String, IndexSearcher> searchers = new ConcurrentHashMap<String, IndexSearcher>();
	private final Map<String, IndexWriter> writers = new ConcurrentHashMap<String, IndexWriter>();
	
	private final Set<String> excludedExtensions = new TreeSet<String>(Arrays.asList("7z", "arc",
			"arj", "bin", "bmp", "dll", "doc", "docx", "exe", "gif", "gz", "jar", "jpg", "lib",
			"lzh", "odg", "pdf", "ppt", "png", "so", "swf", "xcf", "xls", "xlsx", "zip"));

	public LuceneExecutor(IStoredSettings settings, File repositoriesFolder) {
		this.storedSettings = settings;
		this.repositoriesFolder = repositoriesFolder;
	}

	/**
	 * Indicates if the Lucene executor can index repositories.
	 * 
	 * @return true if the Lucene executor is ready to index repositories
	 */
	public boolean isReady() {
		return storedSettings.getBoolean(Keys.lucene.enable, false);
	}

	/**
	 * Run is executed by the gitblit executor service at whatever frequency
	 * is specified in the settings.  Because this is called by an executor
	 * service, calls will queue - i.e. there can never be concurrent execution
	 * of repository index updates.
	 */
	@Override
	public void run() {
		if (!isReady()) {
			return;
		}

		for (String repositoryName: GitBlit.self().getRepositoryList()) {
			RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
			if (model.hasCommits && !ArrayUtils.isEmpty(model.indexedBranches)) {
				Repository repository = GitBlit.self().getRepository(model.name);
				index(model, repository);				
				repository.close();
				System.gc();
			}
		}
	}

	/**
	 * Synchronously indexes a repository. This may build a complete index of a
	 * repository or it may update an existing index.
	 * 
	 * @param name
	 *            the name of the repository
	 * @param repository
	 *            the repository object
	 */
	protected void index(RepositoryModel model, Repository repository) {
		try {
			if (shouldReindex(repository)) {
				// (re)build the entire index
				IndexResult result = reindex(model, repository);

				if (result.success) {
					if (result.commitCount > 0) {
						String msg = "Built {0} Lucene index from {1} commits and {2} files across {3} branches in {4} secs";
						logger.info(MessageFormat.format(msg, model.name, result.commitCount,
								result.blobCount, result.branchCount, result.duration()));
					}
				} else {
					String msg = "Could not build {0} Lucene index!";
					logger.error(MessageFormat.format(msg, model.name));
				}
			} else {
				// update the index with latest commits
				IndexResult result = updateIndex(model, repository);
				if (result.success) {
					if (result.commitCount > 0) {
						String msg = "Updated {0} Lucene index with {1} commits and {2} files across {3} branches in {4} secs";
						logger.info(MessageFormat.format(msg, model.name, result.commitCount,
								result.blobCount, result.branchCount, result.duration()));
					}
				} else {
					String msg = "Could not update {0} Lucene index!";
					logger.error(MessageFormat.format(msg, model.name));
				}
			}
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Lucene indexing failure for {0}", model.name), t);
		}
	}
	
	/**
	 * Close the writer/searcher objects for a repository.
	 * 
	 * @param repositoryName
	 */
	public void close(String repositoryName) {
		try {
			IndexWriter writer = writers.remove(repositoryName);
			if (writer != null) {
				writer.close();
			}
		} catch (Exception e) {
			logger.error("Failed to close index writer for " + repositoryName, e);
		}

		try {
			IndexSearcher searcher = searchers.remove(repositoryName);
			if (searcher != null) {
				searcher.close();
			}
		} catch (Exception e) {
			logger.error("Failed to close index searcher for " + repositoryName, e);
		}
	}

	/**
	 * Close all Lucene indexers.
	 * 
	 */
	public void close() {
		// close all writers
		for (String writer : writers.keySet()) {
			try {
				writers.get(writer).close(true);
			} catch (Throwable t) {
				logger.error("Failed to close Lucene writer for " + writer, t);
			}
		}
		writers.clear();

		// close all searchers
		for (String searcher : searchers.keySet()) {
			try {
				searchers.get(searcher).close();
			} catch (Throwable t) {
				logger.error("Failed to close Lucene searcher for " + searcher, t);
			}
		}
		searchers.clear();
	}

	
	/**
	 * Deletes the Lucene index for the specified repository.
	 * 
	 * @param repositoryName
	 * @return true, if successful
	 */
	public boolean deleteIndex(String repositoryName) {
		try {
			// remove the repository index writer from the cache and close it
			IndexWriter writer = writers.remove(repositoryName);
			if (writer != null) {
				writer.close();
				writer = null;
			}
			// remove the repository index searcher from the cache and close it
			IndexSearcher searcher = searchers.remove(repositoryName);
			if (searcher != null) {
				searcher.close();
				searcher = null;
			}
			// delete the index folder
			File repositoryFolder = new File(repositoriesFolder, repositoryName);
			File luceneIndex = new File(repositoryFolder, LUCENE_DIR);
			if (luceneIndex.exists()) {
				org.eclipse.jgit.util.FileUtils.delete(luceneIndex,
						org.eclipse.jgit.util.FileUtils.RECURSIVE);
			}
			// delete the config file
			File luceneConfig = new File(repositoryFolder, CONF_FILE);
			if (luceneConfig.exists()) {
				luceneConfig.delete();
			}
			return true;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	
	/**
	 * Returns the author for the commit, if this information is available.
	 * 
	 * @param commit
	 * @return an author or unknown
	 */
	private String getAuthor(RevCommit commit) {
		String name = "unknown";
		try {
			name = commit.getAuthorIdent().getName();
			if (StringUtils.isEmpty(name)) {
				name = commit.getAuthorIdent().getEmailAddress();
			}
		} catch (NullPointerException n) {						
		}
		return name;
	}
	
	/**
	 * Returns the committer for the commit, if this information is available.
	 * 
	 * @param commit
	 * @return an committer or unknown
	 */
	private String getCommitter(RevCommit commit) {
		String name = "unknown";
		try {
			name = commit.getCommitterIdent().getName();
			if (StringUtils.isEmpty(name)) {
				name = commit.getCommitterIdent().getEmailAddress();
			}
		} catch (NullPointerException n) {						
		}
		return name;
	}
	
	/**
	 * Get the tree associated with the given commit.
	 *
	 * @param walk
	 * @param commit
	 * @return tree
	 * @throws IOException
	 */
	protected RevTree getTree(final RevWalk walk, final RevCommit commit)
			throws IOException {
		final RevTree tree = commit.getTree();
		if (tree != null) {
			return tree;
		}
		walk.parseHeaders(commit);
		return commit.getTree();
	}

	/**
	 * Construct a keyname from the branch.
	 * 
	 * @param branchName
	 * @return a keyname appropriate for the Git config file format
	 */
	private String getBranchKey(String branchName) {
		return StringUtils.getSHA1(branchName);
	}

	/**
	 * Returns the Lucene configuration for the specified repository.
	 * 
	 * @param repository
	 * @return a config object
	 */
	private FileBasedConfig getConfig(Repository repository) {
		File file = new File(repository.getDirectory(), CONF_FILE);
		FileBasedConfig config = new FileBasedConfig(file, FS.detect());
		return config;
	}

	/**
	 * Reads the Lucene config file for the repository to check the index
	 * version. If the index version is different, then rebuild the repository
	 * index.
	 * 
	 * @param repository
	 * @return true of the on-disk index format is different than INDEX_VERSION
	 */
	protected boolean shouldReindex(Repository repository) {
		try {
			FileBasedConfig config = getConfig(repository);
			config.load();
			int indexVersion = config.getInt(CONF_INDEX, CONF_VERSION, 0);
			// reindex if versions do not match
			return indexVersion != INDEX_VERSION;
		} catch (Throwable t) {
		}
		return true;
	}


	/**
	 * This completely indexes the repository and will destroy any existing
	 * index.
	 * 
	 * @param repositoryName
	 * @param repository
	 * @return IndexResult
	 */
	public IndexResult reindex(RepositoryModel model, Repository repository) {
		IndexResult result = new IndexResult();
		if (!deleteIndex(model.name)) {
			return result;
		}
		try {			
			FileBasedConfig config = getConfig(repository);
			Set<String> indexedCommits = new TreeSet<String>();
			IndexWriter writer = getIndexWriter(model.name);
			// build a quick lookup of tags
			Map<String, List<String>> tags = new HashMap<String, List<String>>();
			for (RefModel tag : JGitUtils.getTags(repository, false, -1)) {
				if (!tag.isAnnotatedTag()) {
					// skip non-annotated tags
					continue;
				}
				if (!tags.containsKey(tag.getObjectId())) {
					tags.put(tag.getReferencedObjectId().getName(), new ArrayList<String>());
				}
				tags.get(tag.getReferencedObjectId().getName()).add(tag.displayName);
			}
			
			ObjectReader reader = repository.newObjectReader();

			// get the local branches
			List<RefModel> branches = JGitUtils.getLocalBranches(repository, true, -1);
			
			// sort them by most recently updated
			Collections.sort(branches, new Comparator<RefModel>() {
				@Override
				public int compare(RefModel ref1, RefModel ref2) {
					return ref2.getDate().compareTo(ref1.getDate());
				}
			});
			
			// reorder default branch to first position
			RefModel defaultBranch = null;
			ObjectId defaultBranchId = JGitUtils.getDefaultBranch(repository);
			for (RefModel branch :  branches) {
				if (branch.getObjectId().equals(defaultBranchId)) {
					defaultBranch = branch;					
					break;
				}
			}
			branches.remove(defaultBranch);
			branches.add(0, defaultBranch);
			
			// walk through each branch
			for (RefModel branch : branches) {

				// if this branch is not specifically indexed then skip
				if (!model.indexedBranches.contains(branch.getName())) {
					continue;
				}

				String branchName = branch.getName();
				RevWalk revWalk = new RevWalk(reader);
				RevCommit tip = revWalk.parseCommit(branch.getObjectId());
				String tipId = tip.getId().getName();

				String keyName = getBranchKey(branchName);
				config.setString(CONF_ALIAS, null, keyName, branchName);
				config.setString(CONF_BRANCH, null, keyName, tipId);

				// index the blob contents of the tree
				TreeWalk treeWalk = new TreeWalk(repository);
				treeWalk.addTree(tip.getTree());
				treeWalk.setRecursive(true);								
				
				Map<String, ObjectId> paths = new TreeMap<String, ObjectId>();
				while (treeWalk.next()) {
					paths.put(treeWalk.getPathString(), treeWalk.getObjectId(0));
				}				

				ByteArrayOutputStream os = new ByteArrayOutputStream();
				byte[] tmp = new byte[32767];

				RevWalk commitWalk = new RevWalk(reader);
				commitWalk.markStart(tip);
				
				RevCommit commit;
				while ((paths.size() > 0) && (commit = commitWalk.next()) != null) {
					TreeWalk diffWalk = new TreeWalk(reader);
					int parentCount = commit.getParentCount();
					switch (parentCount) {
					case 0:
						diffWalk.addTree(new EmptyTreeIterator());
						break;
					case 1:
						diffWalk.addTree(getTree(commitWalk, commit.getParent(0)));
						break;
					default:
						// skip merge commits
						continue;
					}
					diffWalk.addTree(getTree(commitWalk, commit));
					diffWalk.setFilter(ANY_DIFF);
					diffWalk.setRecursive(true);
					while ((paths.size() > 0) && diffWalk.next()) {
						String path = diffWalk.getPathString();
						if (!paths.containsKey(path)) {
							continue;
						}
						
						// remove path from set
						ObjectId blobId = paths.remove(path);
						result.blobCount++;
						
						// index the blob metadata
						String blobAuthor = getAuthor(commit);
						String blobCommitter = getCommitter(commit);
						String blobDate = DateTools.timeToString(commit.getCommitTime() * 1000L,
								Resolution.MINUTE);
						
						Document doc = new Document();
						doc.add(new Field(FIELD_OBJECT_TYPE, SearchObjectType.blob.name(), Store.YES, Index.NOT_ANALYZED_NO_NORMS));
						doc.add(new Field(FIELD_BRANCH, branchName, Store.YES, Index.ANALYZED));
						doc.add(new Field(FIELD_COMMIT, commit.getName(), Store.YES, Index.ANALYZED));
						doc.add(new Field(FIELD_PATH, path, Store.YES, Index.ANALYZED));
						doc.add(new Field(FIELD_DATE, blobDate, Store.YES, Index.NO));
						doc.add(new Field(FIELD_AUTHOR, blobAuthor, Store.YES, Index.ANALYZED));
						doc.add(new Field(FIELD_COMMITTER, blobCommitter, Store.YES, Index.ANALYZED));					

						// determine extension to compare to the extension
						// blacklist
						String ext = null;
						String name = path.toLowerCase();
						if (name.indexOf('.') > -1) {
							ext = name.substring(name.lastIndexOf('.') + 1);
						}

						// index the blob content
						if (StringUtils.isEmpty(ext) || !excludedExtensions.contains(ext)) {							
							ObjectLoader ldr = repository.open(blobId, Constants.OBJ_BLOB);
							InputStream in = ldr.openStream();							
							int n;
							while ((n = in.read(tmp)) > 0) {
								os.write(tmp, 0, n);
							}
							in.close();
							byte[] content = os.toByteArray();
							String str = new String(content, Constants.CHARACTER_ENCODING);
							doc.add(new Field(FIELD_CONTENT, str, Store.YES, Index.ANALYZED));
							os.reset();
						}							
						
						// add the blob to the index
						writer.addDocument(doc);
					}
				}

				os.close();

				// index the tip commit object
				if (indexedCommits.add(tipId)) {
					Document doc = createDocument(tip, tags.get(tipId));
					doc.add(new Field(FIELD_BRANCH, branchName, Store.YES, Index.ANALYZED));
					writer.addDocument(doc);
					result.commitCount += 1;
					result.branchCount += 1;
				}

				// traverse the log and index the previous commit objects
				RevWalk historyWalk = new RevWalk(reader);
				historyWalk.markStart(historyWalk.parseCommit(tip.getId()));
				RevCommit rev;
				while ((rev = historyWalk.next()) != null) {
					String hash = rev.getId().getName();
					if (indexedCommits.add(hash)) {
						Document doc = createDocument(rev, tags.get(hash));
						doc.add(new Field(FIELD_BRANCH, branchName, Store.YES, Index.ANALYZED));
						writer.addDocument(doc);
						result.commitCount += 1;
					}
				}
			}

			// finished
			reader.release();
			
			// this repository has a gb-issues branch, index all issues
			if (IssueUtils.getIssuesBranch(repository) != null) {
				List<IssueModel> issues = IssueUtils.getIssues(repository, null);
				if (issues.size() > 0) {
					result.branchCount += 1;
				}
				for (IssueModel issue : issues) {
					result.issueCount++;
					Document doc = createDocument(issue);
					writer.addDocument(doc);
				}
			}

			// commit all changes and reset the searcher
			config.setInt(CONF_INDEX, null, CONF_VERSION, INDEX_VERSION);
			config.save();
			resetIndexSearcher(model.name);
			writer.commit();
			result.success();
		} catch (Exception e) {
			logger.error("Exception while reindexing " + model.name, e);
		}
		return result;
	}
	
	/**
	 * Incrementally update the index with the specified commit for the
	 * repository.
	 * 
	 * @param repositoryName
	 * @param repository
	 * @param branch
	 *            the fully qualified branch name (e.g. refs/heads/master)
	 * @param commit
	 * @return true, if successful
	 */
	private IndexResult index(String repositoryName, Repository repository, 
			String branch, RevCommit commit) {
		IndexResult result = new IndexResult();
		try {
			List<PathChangeModel> changedPaths = JGitUtils.getFilesInCommit(repository, commit);
			String revDate = DateTools.timeToString(commit.getCommitTime() * 1000L,
					Resolution.MINUTE);
			IndexWriter writer = getIndexWriter(repositoryName);
			for (PathChangeModel path : changedPaths) {
				// delete the indexed blob
				deleteBlob(repositoryName, branch, path.path);

				// re-index the blob
				if (!ChangeType.DELETE.equals(path.changeType)) {
					result.blobCount++;
					Document doc = new Document();
					doc.add(new Field(FIELD_OBJECT_TYPE, SearchObjectType.blob.name(), Store.YES,
							Index.NOT_ANALYZED));
					doc.add(new Field(FIELD_BRANCH, branch, Store.YES, Index.ANALYZED));
					doc.add(new Field(FIELD_COMMIT, commit.getName(), Store.YES, Index.ANALYZED));
					doc.add(new Field(FIELD_PATH, path.path, Store.YES, Index.ANALYZED));
					doc.add(new Field(FIELD_DATE, revDate, Store.YES, Index.NO));
					doc.add(new Field(FIELD_AUTHOR, getAuthor(commit), Store.YES, Index.ANALYZED));
					doc.add(new Field(FIELD_COMMITTER, getCommitter(commit), Store.YES, Index.ANALYZED));

					// determine extension to compare to the extension
					// blacklist
					String ext = null;
					String name = path.name.toLowerCase();
					if (name.indexOf('.') > -1) {
						ext = name.substring(name.lastIndexOf('.') + 1);
					}

					if (StringUtils.isEmpty(ext) || !excludedExtensions.contains(ext)) {
						// read the blob content
						String str = JGitUtils.getStringContent(repository, commit.getTree(),
								path.path);
						doc.add(new Field(FIELD_CONTENT, str, Store.YES, Index.ANALYZED));
						writer.addDocument(doc);
					}
				}
			}
			writer.commit();

			Document doc = createDocument(commit, null);
			result.commitCount++;
			result.success = index(repositoryName, doc);
		} catch (Exception e) {
			logger.error(MessageFormat.format("Exception while indexing commit {0} in {1}", commit.getId().getName(), repositoryName), e);
		}
		return result;
	}

	/**
	 * Incrementally update the index with the specified issue for the
	 * repository.
	 * 
	 * @param repositoryName
	 * @param issue
	 * @return true, if successful
	 */
	public boolean index(String repositoryName, IssueModel issue) {
		try {
			// delete the old issue from the index, if exists
			deleteIssue(repositoryName, issue.id);
			Document doc = createDocument(issue);
			return index(repositoryName, doc);
		} catch (Exception e) {
			logger.error(MessageFormat.format("Error while indexing issue {0} in {1}", issue.id, repositoryName), e);
		}
		return false;
	}
	
	/**
	 * Delete an issue from the repository index.
	 * 
	 * @param repositoryName
	 * @param issueId
	 * @throws Exception
	 */
	private void deleteIssue(String repositoryName, String issueId) throws Exception {
		BooleanQuery query = new BooleanQuery();
		Term objectTerm = new Term(FIELD_OBJECT_TYPE, SearchObjectType.issue.name());
		query.add(new TermQuery(objectTerm), Occur.MUST);
		Term issueidTerm = new Term(FIELD_ISSUE, issueId);
		query.add(new TermQuery(issueidTerm), Occur.MUST);
		
		IndexWriter writer = getIndexWriter(repositoryName);
		writer.deleteDocuments(query);
		writer.commit();
	}
	
	/**
	 * Delete a blob from the specified branch of the repository index.
	 * 
	 * @param repositoryName
	 * @param branch
	 * @param path
	 * @throws Exception
	 */
	private void deleteBlob(String repositoryName, String branch, String path) throws Exception {
		BooleanQuery query = new BooleanQuery();
		Term objectTerm = new Term(FIELD_OBJECT_TYPE, SearchObjectType.blob.name());
		query.add(new TermQuery(objectTerm), Occur.MUST);
		Term branchTerm = new Term(FIELD_BRANCH, branch);
		query.add(new TermQuery(branchTerm), Occur.MUST);
		Term pathTerm = new Term(FIELD_PATH, path);
		query.add(new TermQuery(pathTerm), Occur.MUST);
		
		IndexWriter writer = getIndexWriter(repositoryName);
		writer.deleteDocuments(query);
		writer.commit();
	}

	/**
	 * Updates a repository index incrementally from the last indexed commits.
	 * 
	 * @param model
	 * @param repository
	 * @return IndexResult
	 */
	protected IndexResult updateIndex(RepositoryModel model, Repository repository) {
		IndexResult result = new IndexResult();
		try {
			FileBasedConfig config = getConfig(repository);
			config.load();

			// build a quick lookup of annotated tags
			Map<String, List<String>> tags = new HashMap<String, List<String>>();
			for (RefModel tag : JGitUtils.getTags(repository, false, -1)) {
				if (!tag.isAnnotatedTag()) {
					// skip non-annotated tags
					continue;
				}
				if (!tags.containsKey(tag.getObjectId())) {
					tags.put(tag.getReferencedObjectId().getName(), new ArrayList<String>());
				}
				tags.get(tag.getReferencedObjectId().getName()).add(tag.displayName);
			}

			// detect branch deletion
			// first assume all branches are deleted and then remove each
			// existing branch from deletedBranches during indexing
			Set<String> deletedBranches = new TreeSet<String>();
			for (String alias : config.getNames(CONF_ALIAS)) {
				String branch = config.getString(CONF_ALIAS, null, alias);
				deletedBranches.add(branch);
			}

			// walk through each branches
			List<RefModel> branches = JGitUtils.getLocalBranches(repository, true, -1);
			for (RefModel branch : branches) {
				String branchName = branch.getName();

				// determine if we should skip this branch
				if (!IssueUtils.GB_ISSUES.equals(branch)
						&& !model.indexedBranches.contains(branch.getName())) {
					continue;
				}
				
				// remove this branch from the deletedBranches set
				deletedBranches.remove(branchName);

				// determine last commit
				String keyName = getBranchKey(branchName);
				String lastCommit = config.getString(CONF_BRANCH, null, keyName);

				List<RevCommit> revs;
				if (StringUtils.isEmpty(lastCommit)) {
					// new branch/unindexed branch, get all commits on branch
					revs = JGitUtils.getRevLog(repository, branchName, 0, -1);
				} else {
					// pre-existing branch, get changes since last commit
					revs = JGitUtils.getRevLog(repository, lastCommit, branchName);
				}

				if (revs.size() > 0) {
					result.branchCount += 1;
				}
				
				// track the issue ids that we have already indexed
				Set<String> indexedIssues = new TreeSet<String>();
				
				// reverse the list of commits so we start with the first commit				
				Collections.reverse(revs);
				for (RevCommit commit : revs) {					
					if (IssueUtils.GB_ISSUES.equals(branch)) {
						// only index an issue once during updateIndex
						String issueId = commit.getShortMessage().substring(2).trim();
						if (indexedIssues.contains(issueId)) {
							continue;
						}
						indexedIssues.add(issueId);
						
						IssueModel issue = IssueUtils.getIssue(repository, issueId);
						if (issue == null) {
							// issue was deleted, remove from index
							deleteIssue(model.name, issueId);
						} else {
							// issue was updated
							index(model.name, issue);
							result.issueCount++;
						}
					} else {
						// index a commit
						result.add(index(model.name, repository, branchName, commit));
					}
				}

				// update the config
				config.setInt(CONF_INDEX, null, CONF_VERSION, INDEX_VERSION);
				config.setString(CONF_ALIAS, null, keyName, branchName);
				config.setString(CONF_BRANCH, null, keyName, branch.getObjectId().getName());
				config.save();
			}

			// the deletedBranches set will normally be empty by this point
			// unless a branch really was deleted and no longer exists
			if (deletedBranches.size() > 0) {
				for (String branch : deletedBranches) {
					IndexWriter writer = getIndexWriter(model.name);
					writer.deleteDocuments(new Term(FIELD_BRANCH, branch));
					writer.commit();
				}
			}
			result.success = true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Exception while updating {0} Lucene index", model.name), t);
		}
		return result;
	}
	
	/**
	 * Creates a Lucene document from an issue.
	 * 
	 * @param issue
	 * @return a Lucene document
	 */
	private Document createDocument(IssueModel issue) {
		Document doc = new Document();
		doc.add(new Field(FIELD_OBJECT_TYPE, SearchObjectType.issue.name(), Store.YES,
				Field.Index.NOT_ANALYZED));
		doc.add(new Field(FIELD_ISSUE, issue.id, Store.YES, Index.ANALYZED));
		doc.add(new Field(FIELD_BRANCH, IssueUtils.GB_ISSUES, Store.YES, Index.ANALYZED));
		doc.add(new Field(FIELD_DATE, DateTools.dateToString(issue.created, Resolution.MINUTE),
				Store.YES, Field.Index.NO));
		doc.add(new Field(FIELD_AUTHOR, issue.reporter, Store.YES, Index.ANALYZED));
		List<String> attachments = new ArrayList<String>();
		for (Attachment attachment : issue.getAttachments()) {
			attachments.add(attachment.name.toLowerCase());
		}
		doc.add(new Field(FIELD_ATTACHMENT, StringUtils.flattenStrings(attachments), Store.YES,
				Index.ANALYZED));
		doc.add(new Field(FIELD_SUMMARY, issue.summary, Store.YES, Index.ANALYZED));
		doc.add(new Field(FIELD_CONTENT, issue.toString(), Store.YES, Index.ANALYZED));
		doc.add(new Field(FIELD_LABEL, StringUtils.flattenStrings(issue.getLabels()), Store.YES,
				Index.ANALYZED));
		return doc;
	}

	/**
	 * Creates a Lucene document for a commit
	 * 
	 * @param commit
	 * @param tags
	 * @return a Lucene document
	 */
	private Document createDocument(RevCommit commit, List<String> tags) {
		Document doc = new Document();
		doc.add(new Field(FIELD_OBJECT_TYPE, SearchObjectType.commit.name(), Store.YES,
				Index.NOT_ANALYZED));
		doc.add(new Field(FIELD_COMMIT, commit.getName(), Store.YES, Index.ANALYZED));
		doc.add(new Field(FIELD_DATE, DateTools.timeToString(commit.getCommitTime() * 1000L,
				Resolution.MINUTE), Store.YES, Index.NO));
		doc.add(new Field(FIELD_AUTHOR, getAuthor(commit), Store.YES, Index.ANALYZED));
		doc.add(new Field(FIELD_COMMITTER, getCommitter(commit), Store.YES, Index.ANALYZED));
		doc.add(new Field(FIELD_SUMMARY, commit.getShortMessage(), Store.YES, Index.ANALYZED));
		doc.add(new Field(FIELD_CONTENT, commit.getFullMessage(), Store.YES, Index.ANALYZED));
		if (!ArrayUtils.isEmpty(tags)) {
			doc.add(new Field(FIELD_TAG, StringUtils.flattenStrings(tags), Store.YES, Index.ANALYZED));
		}
		return doc;
	}

	/**
	 * Incrementally index an object for the repository.
	 * 
	 * @param repositoryName
	 * @param doc
	 * @return true, if successful
	 */
	private boolean index(String repositoryName, Document doc) {
		try {			
			IndexWriter writer = getIndexWriter(repositoryName);
			writer.addDocument(doc);
			resetIndexSearcher(repositoryName);
			writer.commit();
			return true;
		} catch (Exception e) {
			logger.error(MessageFormat.format("Exception while incrementally updating {0} Lucene index", repositoryName), e);
		}
		return false;
	}

	private SearchResult createSearchResult(Document doc, float score) throws ParseException {
		SearchResult result = new SearchResult();
		result.score = score;
		result.date = DateTools.stringToDate(doc.get(FIELD_DATE));
		result.summary = doc.get(FIELD_SUMMARY);		
		result.author = doc.get(FIELD_AUTHOR);
		result.committer = doc.get(FIELD_COMMITTER);
		result.type = SearchObjectType.fromName(doc.get(FIELD_OBJECT_TYPE));
		result.branch = doc.get(FIELD_BRANCH);
		result.commitId = doc.get(FIELD_COMMIT);
		result.issueId = doc.get(FIELD_ISSUE);
		result.path = doc.get(FIELD_PATH);
		if (doc.get(FIELD_TAG) != null) {
			result.tags = StringUtils.getStringsFromValue(doc.get(FIELD_TAG));
		}
		if (doc.get(FIELD_LABEL) != null) {
			result.labels = StringUtils.getStringsFromValue(doc.get(FIELD_LABEL));
		}
		return result;
	}

	private synchronized void resetIndexSearcher(String repository) throws IOException {
		IndexSearcher searcher = searchers.remove(repository);
		if (searcher != null) {
			searcher.close();
		}
	}

	/**
	 * Gets an index searcher for the repository.
	 * 
	 * @param repository
	 * @return
	 * @throws IOException
	 */
	private IndexSearcher getIndexSearcher(String repository) throws IOException {
		IndexSearcher searcher = searchers.get(repository);
		if (searcher == null) {
			IndexWriter writer = getIndexWriter(repository);
			searcher = new IndexSearcher(IndexReader.open(writer, true));
			searchers.put(repository, searcher);
		}
		return searcher;
	}

	/**
	 * Gets an index writer for the repository. The index will be created if it
	 * does not already exist or if forceCreate is specified.
	 * 
	 * @param repository
	 * @return an IndexWriter
	 * @throws IOException
	 */
	private IndexWriter getIndexWriter(String repository) throws IOException {
		IndexWriter indexWriter = writers.get(repository);		
		File repositoryFolder = new File(repositoriesFolder, repository);
		File indexFolder = new File(repositoryFolder, LUCENE_DIR);
		Directory directory = FSDirectory.open(indexFolder);		

		if (indexWriter == null) {
			if (!indexFolder.exists()) {
				indexFolder.mkdirs();
			}
			StandardAnalyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);
			IndexWriterConfig config = new IndexWriterConfig(LUCENE_VERSION, analyzer);
			config.setOpenMode(OpenMode.CREATE_OR_APPEND);
			indexWriter = new IndexWriter(directory, config);
			writers.put(repository, indexWriter);
		}
		return indexWriter;
	}

	/**
	 * Searches the specified repositories for the given text or query
	 * 
	 * @param text
	 *            if the text is null or empty, null is returned
	 * @param maximumHits
	 *            the maximum number of hits to collect
	 * @param repositories
	 *            a list of repositories to search. if no repositories are
	 *            specified null is returned.
	 * @return a list of SearchResults in order from highest to the lowest score
	 * 
	 */
	public List<SearchResult> search(String text, int maximumHits, List<String> repositories) {
		if (ArrayUtils.isEmpty(repositories)) {
			return null;
		}
		return search(text, maximumHits, repositories.toArray(new String[0]));
	}
	
	/**
	 * Searches the specified repositories for the given text or query
	 * 
	 * @param text
	 *            if the text is null or empty, null is returned
	 * @param maximumHits
	 *            the maximum number of hits to collect
	 * @param repositories
	 *            a list of repositories to search. if no repositories are
	 *            specified null is returned.
	 * @return a list of SearchResults in order from highest to the lowest score
	 * 
	 */	
	public List<SearchResult> search(String text, int maximumHits, String... repositories) {
		if (StringUtils.isEmpty(text)) {
			return null;
		}
		if (ArrayUtils.isEmpty(repositories)) {
			return null;
		}
		Set<SearchResult> results = new LinkedHashSet<SearchResult>();
		StandardAnalyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);
		try {
			// default search checks summary and content
			BooleanQuery query = new BooleanQuery();
			QueryParser qp;
			qp = new QueryParser(LUCENE_VERSION, FIELD_SUMMARY, analyzer);
			qp.setAllowLeadingWildcard(true);
			query.add(qp.parse(text), Occur.SHOULD);

			qp = new QueryParser(LUCENE_VERSION, FIELD_CONTENT, analyzer);
			qp.setAllowLeadingWildcard(true);
			query.add(qp.parse(text), Occur.SHOULD);

			IndexSearcher searcher;
			if (repositories.length == 1) {
				// single repository search
				searcher = getIndexSearcher(repositories[0]);
			} else {
				// multiple repository search
				List<IndexReader> readers = new ArrayList<IndexReader>();
				for (String repository : repositories) {
					IndexSearcher repositoryIndex = getIndexSearcher(repository);
					readers.add(repositoryIndex.getIndexReader());
				}
				IndexReader[] rdrs = readers.toArray(new IndexReader[readers.size()]);
				MultiSourceReader reader = new MultiSourceReader(rdrs);
				searcher = new IndexSearcher(reader);
			}
			Query rewrittenQuery = searcher.rewrite(query);
			TopScoreDocCollector collector = TopScoreDocCollector.create(maximumHits, true);
			searcher.search(rewrittenQuery, collector);
			ScoreDoc[] hits = collector.topDocs().scoreDocs;
			for (int i = 0; i < hits.length; i++) {
				int docId = hits[i].doc;
				Document doc = searcher.doc(docId);
				// TODO identify the source index for the doc, then eliminate FIELD_REPOSITORY				
				SearchResult result = createSearchResult(doc, hits[i].score);
				if (repositories.length == 1) {
					// single repository search
					result.repository = repositories[0];
				} else {
					// multi-repository search
					MultiSourceReader reader = (MultiSourceReader) searcher.getIndexReader();
					int index = reader.getSourceIndex(docId);
					result.repository = repositories[index];
				}
				String content = doc.get(FIELD_CONTENT);				
				result.fragment = getHighlightedFragment(analyzer, query, content, result);
				results.add(result);
			}
		} catch (Exception e) {
			logger.error(MessageFormat.format("Exception while searching for {0}", text), e);
		}
		return new ArrayList<SearchResult>(results);
	}
	
	/**
	 * 
	 * @param analyzer
	 * @param query
	 * @param content
	 * @param result
	 * @return
	 * @throws IOException
	 * @throws InvalidTokenOffsetsException
	 */
	private String getHighlightedFragment(Analyzer analyzer, Query query,
			String content, SearchResult result) throws IOException, InvalidTokenOffsetsException {
		content = content == null ? "":StringUtils.escapeForHtml(content, false);
		
		QueryScorer scorer = new QueryScorer(query, "content");
		Fragmenter fragmenter;
		
		// TODO improve the fragmenter - hopefully on line breaks
		if (SearchObjectType.commit == result.type) {
			fragmenter = new SimpleSpanFragmenter(scorer, 1024); 
		} else {
			fragmenter = new SimpleSpanFragmenter(scorer, 150);
		}

		// use an artificial delimiter for the token
		String termTag = "<!--[";
		String termTagEnd = "]-->";
		SimpleHTMLFormatter formatter = new SimpleHTMLFormatter(termTag, termTagEnd);
		Highlighter highlighter = new Highlighter(formatter, scorer);		
		highlighter.setTextFragmenter(fragmenter);
		
		String [] fragments = highlighter.getBestFragments(analyzer, "content", content, 5);
		if (ArrayUtils.isEmpty(fragments)) {
			if (SearchObjectType.blob  == result.type) {
				return "";
			}
			return "<pre class=\"text\">" + content + "</pre>";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0, len = fragments.length; i < len; i++) {
			String fragment = fragments[i];
			
			// resurrect the raw fragment from removing the artificial delimiters
			String raw = fragment.replace(termTag, "").replace(termTagEnd, "");			
			sb.append(getPreTag(result, raw, content));
			
			// replace the artificial delimiter with html tags
			String html = fragment.replace(termTag, "<span class=\"highlight\">").replace(termTagEnd, "</span>");
			sb.append(html);
			sb.append("</pre>");
			if (i < len - 1) {
				sb.append("<span class=\"ellipses\">...</span><br/>");
			}
		}
		return sb.toString();
	}
	
	/**
	 * Returns the appropriate tag for a fragment. Commit messages are visually
	 * differentiated from blob fragments.
	 * 
	 * @param result
	 * @param fragment
	 * @param content
	 * @return an html tag appropriate for the fragment
	 */
	private String getPreTag(SearchResult result, String fragment, String content) {
		String pre = "<pre class=\"text\">";
		if (SearchObjectType.blob  == result.type) {
			int line = StringUtils.countLines(content.substring(0, content.indexOf(fragment)));			
			int lastDot = result.path.lastIndexOf('.');
			if (lastDot > -1) {
				String ext = result.path.substring(lastDot + 1).toLowerCase();
				pre = MessageFormat.format("<pre class=\"prettyprint linenums:{0,number,0} lang-{1}\">", line, ext);	
			} else {
				pre = MessageFormat.format("<pre class=\"prettyprint linenums:{0,number,0}\">", line);
			}
		}
		return pre;
	}
	
	/**
	 * Simple class to track the results of an index update. 
	 */
	private class IndexResult {
		long startTime = System.currentTimeMillis();
		long endTime = startTime;
		boolean success;
		int branchCount;
		int commitCount;
		int blobCount;
		int issueCount;
		
		void add(IndexResult result) {
			this.branchCount += result.branchCount;
			this.commitCount += result.commitCount;
			this.blobCount += result.blobCount;
			this.issueCount += result.issueCount;			
		}
		
		void success() {
			success = true;
			endTime = System.currentTimeMillis();
		}
		
		float duration() {
			return (endTime - startTime)/1000f;
		}
	}
	
	/**
	 * Custom subclass of MultiReader to identify the source index for a given
	 * doc id.  This would not be necessary of there was a public method to
	 * obtain this information.
	 *  
	 */
	private class MultiSourceReader extends MultiReader {
		
		final Method method;
		
		MultiSourceReader(IndexReader[] subReaders) {
			super(subReaders);
			Method m = null;
			try {
				m = MultiReader.class.getDeclaredMethod("readerIndex", int.class);
				m.setAccessible(true);
			} catch (Exception e) {
				logger.error("Error getting readerIndex method", e);
			}
			method = m;
		}
		
		int getSourceIndex(int docId) {
			int index = -1;
			try {
				Object o = method.invoke(this, docId);
				index = (Integer) o;
			} catch (Exception e) {
				logger.error("Error getting source index", e);
			}
			return index;
		}
	}
}
