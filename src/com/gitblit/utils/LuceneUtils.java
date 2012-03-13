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
package com.gitblit.utils;

import static org.eclipse.jgit.treewalk.filter.TreeFilter.ANY_DIFF;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import org.apache.lucene.search.TopScoreDocCollector;
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

import com.gitblit.models.IssueModel;
import com.gitblit.models.IssueModel.Attachment;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RefModel;
import com.gitblit.models.SearchResult;

/**
 * A collection of utility methods for indexing and querying a Lucene repository
 * index.
 * 
 * @author James Moger
 * 
 */
public class LuceneUtils {

	/**
	 * The types of objects that can be indexed and queried.
	 */
	public static enum ObjectType {
		commit, blob, issue;

		static ObjectType fromName(String name) {
			for (ObjectType value : values()) {
				if (value.name().equals(name)) {
					return value;
				}
			}
			return null;
		}
	}

	private static final Version LUCENE_VERSION = Version.LUCENE_35;
	private static final int INDEX_VERSION = 1;

	private static final String FIELD_OBJECT_TYPE = "type";
	private static final String FIELD_OBJECT_ID = "id";
	private static final String FIELD_BRANCH = "branch";
	private static final String FIELD_REPOSITORY = "repository";
	private static final String FIELD_SUMMARY = "summary";
	private static final String FIELD_CONTENT = "content";
	private static final String FIELD_AUTHOR = "author";
	private static final String FIELD_COMMITTER = "committer";
	private static final String FIELD_DATE = "date";
	private static final String FIELD_TAG = "tag";
	private static final String FIELD_LABEL = "label";
	private static final String FIELD_ATTACHMENT = "attachment";

	private static Set<String> excludedExtensions = new TreeSet<String>(Arrays.asList("7z", "arc",
			"arj", "bin", "bmp", "dll", "doc", "docx", "exe", "gif", "gz", "jar", "jpg", "lib",
			"lzh", "odg", "pdf", "ppt", "png", "so", "swf", "xcf", "xls", "xlsx", "zip"));

	private static Set<String> excludedBranches = new TreeSet<String>(
			Arrays.asList("/refs/heads/gb-issues"));

	private static final Map<File, IndexSearcher> SEARCHERS = new ConcurrentHashMap<File, IndexSearcher>();
	private static final Map<File, IndexWriter> WRITERS = new ConcurrentHashMap<File, IndexWriter>();

	private static final String LUCENE_DIR = "lucene";
	private static final String CONF_FILE = "lucene.conf";
	private static final String CONF_INDEX = "index";
	private static final String CONF_VERSION = "version";
	private static final String CONF_ALIAS = "aliases";
	private static final String CONF_BRANCH = "branches";
	
	/**
	 * Returns the author for the commit, if this information is available.
	 * 
	 * @param commit
	 * @return an author or unknown
	 */
	private static String getAuthor(RevCommit commit) {
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
	private static String getCommitter(RevCommit commit) {
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
	 * Construct a keyname from the branch.
	 * 
	 * @param branchName
	 * @return a keyname appropriate for the Git config file format
	 */
	private static String getBranchKey(String branchName) {
		return StringUtils.getSHA1(branchName);
	}

	/**
	 * Returns the Lucene configuration for the specified repository.
	 * 
	 * @param repository
	 * @return a config object
	 */
	private static FileBasedConfig getConfig(Repository repository) {
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
	public static boolean shouldReindex(Repository repository) {
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
	 * Deletes the Lucene index for the specified repository.
	 * 
	 * @param repository
	 * @return true, if successful
	 */
	public static boolean deleteIndex(Repository repository) {
		try {
			File luceneIndex = new File(repository.getDirectory(), LUCENE_DIR);
			if (luceneIndex.exists()) {
				org.eclipse.jgit.util.FileUtils.delete(luceneIndex,
						org.eclipse.jgit.util.FileUtils.RECURSIVE);
			}
			File luceneConfig = new File(repository.getDirectory(), CONF_FILE);
			if (luceneConfig.exists()) {
				luceneConfig.delete();
			}
			return true;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * This completely indexes the repository and will destroy any existing
	 * index.
	 * 
	 * @param repositoryName
	 * @param repository
	 * @return IndexResult
	 */
	public static IndexResult reindex(String repositoryName, Repository repository) {
		IndexResult result = new IndexResult();
		if (!LuceneUtils.deleteIndex(repository)) {
			return result;
		}
		try {			
			FileBasedConfig config = getConfig(repository);
			Set<String> indexedCommits = new TreeSet<String>();
			IndexWriter writer = getIndexWriter(repository, true);
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
				if (excludedBranches.contains(branch.getName())) {
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
						doc.add(new Field(FIELD_OBJECT_TYPE, ObjectType.blob.name(), Store.YES, Index.NOT_ANALYZED_NO_NORMS));
						doc.add(new Field(FIELD_REPOSITORY, repositoryName, Store.YES, Index.ANALYZED));
						doc.add(new Field(FIELD_BRANCH, branchName, Store.YES, Index.ANALYZED));
						doc.add(new Field(FIELD_OBJECT_ID, path, Store.YES, Index.ANALYZED));
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
							doc.add(new Field(FIELD_CONTENT, str, Store.NO, Index.ANALYZED));
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
					doc.add(new Field(FIELD_REPOSITORY, repositoryName, Store.YES, Index.ANALYZED));
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
						doc.add(new Field(FIELD_REPOSITORY, repositoryName, Store.YES, Index.ANALYZED));
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
					doc.add(new Field(FIELD_REPOSITORY, repositoryName, Store.YES, Index.ANALYZED));
					writer.addDocument(doc);
				}
			}

			// commit all changes and reset the searcher
			config.setInt(CONF_INDEX, null, CONF_VERSION, INDEX_VERSION);
			config.save();
			resetIndexSearcher(repository);
			writer.commit();
			result.success = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
	
	/**
	 * Get the tree associated with the given commit.
	 *
	 * @param walk
	 * @param commit
	 * @return tree
	 * @throws IOException
	 */
	protected static RevTree getTree(final RevWalk walk, final RevCommit commit)
			throws IOException {
		final RevTree tree = commit.getTree();
		if (tree != null)
			return tree;
		walk.parseHeaders(commit);
		return commit.getTree();
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
	private static IndexResult index(String repositoryName, Repository repository, 
			String branch, RevCommit commit) {
		IndexResult result = new IndexResult();
		try {
			if (excludedBranches.contains(branch)) {
				if (IssueUtils.GB_ISSUES.equals(branch)) {
					// index an issue
					String issueId = commit.getShortMessage().substring(2).trim();
					IssueModel issue = IssueUtils.getIssue(repository, issueId);
					if (issue == null) {
						// issue was deleted, remove from index
						IndexWriter writer = getIndexWriter(repository, false);
						writer.deleteDocuments(
								new Term(FIELD_OBJECT_TYPE, ObjectType.issue.name()), new Term(
										FIELD_OBJECT_ID, issueId));
						writer.commit();
						result.success = true;
						return result;
					}
					result.success = index(repositoryName, repository, issue);
					result.issueCount++;
					return result;
					
				}
				return result;
			}
			List<PathChangeModel> changedPaths = JGitUtils.getFilesInCommit(repository, commit);
			String revDate = DateTools.timeToString(commit.getCommitTime() * 1000L,
					Resolution.MINUTE);
			IndexWriter writer = getIndexWriter(repository, false);
			for (PathChangeModel path : changedPaths) {
				// delete the indexed blob
				writer.deleteDocuments(new Term(FIELD_OBJECT_TYPE, ObjectType.blob.name()),
						new Term(FIELD_BRANCH, branch), new Term(FIELD_OBJECT_ID, path.path));

				// re-index the blob
				if (!ChangeType.DELETE.equals(path.changeType)) {
					result.blobCount++;
					Document doc = new Document();
					doc.add(new Field(FIELD_OBJECT_TYPE, ObjectType.blob.name(), Store.YES,
							Index.NOT_ANALYZED));
					doc.add(new Field(FIELD_REPOSITORY, repositoryName, Store.YES, Index.ANALYZED));
					doc.add(new Field(FIELD_BRANCH, branch, Store.YES, Index.ANALYZED));
					doc.add(new Field(FIELD_OBJECT_ID, path.path, Store.YES, Index.ANALYZED));
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
						doc.add(new Field(FIELD_CONTENT, str, Store.NO, Index.ANALYZED));
						writer.addDocument(doc);
					}
				}
			}
			writer.commit();

			Document doc = createDocument(commit, null);
			result.commitCount++;
			result.success = index(repositoryName, repository, doc);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * Incrementally update the index with the specified issue for the
	 * repository.
	 * 
	 * @param repository
	 * @param issue
	 * @return true, if successful
	 */
	public static boolean index(String repositoryName, Repository repository, IssueModel issue) {
		try {
			// delete the old issue from the index, if exists
			IndexWriter writer = getIndexWriter(repository, false);
			writer.deleteDocuments(new Term(FIELD_OBJECT_TYPE, ObjectType.issue.name()), new Term(
					FIELD_OBJECT_ID, String.valueOf(issue.id)));
			writer.commit();

			Document doc = createDocument(issue);
			return index(repositoryName, repository, doc);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Updates a repository index incrementally from the last indexed commits.
	 * 
	 * @param repositoryName
	 * @param repository
	 * @return IndexResult
	 */
	public static IndexResult updateIndex(String repositoryName, Repository repository) {
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
				
				// reverse the list of commits so we start with the first commit				
				Collections.reverse(revs);
				for (RevCommit commit : revs) {
					result.add(index(repositoryName, repository, branchName, commit));					
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
					IndexWriter writer = getIndexWriter(repository, false);
					writer.deleteDocuments(new Term(FIELD_BRANCH, branch));
					writer.commit();
				}
			}
			result.success = true;
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return result;
	}

	/**
	 * Creates a Lucene document from an issue.
	 * 
	 * @param issue
	 * @return a Lucene document
	 */
	private static Document createDocument(IssueModel issue) {
		Document doc = new Document();
		doc.add(new Field(FIELD_OBJECT_TYPE, ObjectType.issue.name(), Store.YES,
				Field.Index.NOT_ANALYZED));
		doc.add(new Field(FIELD_OBJECT_ID, issue.id, Store.YES, Index.ANALYZED));
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
	private static Document createDocument(RevCommit commit, List<String> tags) {
		Document doc = new Document();
		doc.add(new Field(FIELD_OBJECT_TYPE, ObjectType.commit.name(), Store.YES,
				Index.NOT_ANALYZED));
		doc.add(new Field(FIELD_OBJECT_ID, commit.getName(), Store.YES, Index.ANALYZED));
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
	 * @param repository
	 * @param doc
	 * @return true, if successful
	 */
	private static boolean index(String repositoryName, Repository repository, Document doc) {
		try {			
			doc.add(new Field(FIELD_REPOSITORY, repositoryName, Store.YES, Index.NOT_ANALYZED));
			IndexWriter writer = getIndexWriter(repository, false);
			writer.addDocument(doc);
			resetIndexSearcher(repository);
			writer.commit();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	private static SearchResult createSearchResult(Document doc, float score) throws ParseException {
		SearchResult result = new SearchResult();
		result.score = score;
		result.date = DateTools.stringToDate(doc.get(FIELD_DATE));
		result.summary = doc.get(FIELD_SUMMARY);
		result.content = doc.get(FIELD_CONTENT);
		result.author = doc.get(FIELD_AUTHOR);
		result.committer = doc.get(FIELD_COMMITTER);
		result.type = ObjectType.fromName(doc.get(FIELD_OBJECT_TYPE));
		result.repository = doc.get(FIELD_REPOSITORY);
		result.branch = doc.get(FIELD_BRANCH);
		result.id = doc.get(FIELD_OBJECT_ID);
		if (doc.get(FIELD_TAG) != null) {
			result.tags = StringUtils.getStringsFromValue(doc.get(FIELD_TAG));
		}
		if (doc.get(FIELD_LABEL) != null) {
			result.labels = StringUtils.getStringsFromValue(doc.get(FIELD_LABEL));
		}
		return result;
	}

	private static void resetIndexSearcher(Repository repository) throws IOException {
		IndexSearcher searcher = SEARCHERS.get(repository.getDirectory());
		if (searcher != null) {
			SEARCHERS.remove(repository.getDirectory());
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
	private static IndexSearcher getIndexSearcher(Repository repository) throws IOException {
		IndexSearcher searcher = SEARCHERS.get(repository.getDirectory());
		if (searcher == null) {
			IndexWriter writer = getIndexWriter(repository, false);
			searcher = new IndexSearcher(IndexReader.open(writer, true));
			SEARCHERS.put(repository.getDirectory(), searcher);
		}
		return searcher;
	}

	/**
	 * Gets an index writer for the repository. The index will be created if it
	 * does not already exist or if forceCreate is specified.
	 * 
	 * @param repository
	 * @param forceCreate
	 * @return an IndexWriter
	 * @throws IOException
	 */
	private static IndexWriter getIndexWriter(Repository repository, boolean forceCreate)
			throws IOException {
		IndexWriter indexWriter = WRITERS.get(repository.getDirectory());
		File indexFolder = new File(repository.getDirectory(), LUCENE_DIR);
		Directory directory = FSDirectory.open(indexFolder);
		if (forceCreate || !indexFolder.exists()) {
			// if the writer is going to blow away the existing index and create
			// a new one then it should not be cached. instead, close any open
			// writer, create a new one, and return.
			if (indexWriter != null) {
				indexWriter.close();
				indexWriter = null;
				WRITERS.remove(repository.getDirectory());
			}
			indexFolder.mkdirs();
			IndexWriterConfig config = new IndexWriterConfig(LUCENE_VERSION, new StandardAnalyzer(
					LUCENE_VERSION));
			config.setOpenMode(OpenMode.CREATE);
			IndexWriter writer = new IndexWriter(directory, config);
			writer.close();
		}

		if (indexWriter == null) {
			IndexWriterConfig config = new IndexWriterConfig(LUCENE_VERSION, new StandardAnalyzer(
					LUCENE_VERSION));
			config.setOpenMode(OpenMode.APPEND);
			indexWriter = new IndexWriter(directory, config);
			WRITERS.put(repository.getDirectory(), indexWriter);
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
	public static List<SearchResult> search(String text, int maximumHits,
			Repository... repositories) {
		if (StringUtils.isEmpty(text)) {
			return null;
		}
		if (repositories.length == 0) {
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
				for (Repository repository : repositories) {
					IndexSearcher repositoryIndex = getIndexSearcher(repository);
					readers.add(repositoryIndex.getIndexReader());
				}
				IndexReader[] rdrs = readers.toArray(new IndexReader[readers.size()]);
				MultiReader reader = new MultiReader(rdrs);
				searcher = new IndexSearcher(reader);
			}
			Query rewrittenQuery = searcher.rewrite(query);
			TopScoreDocCollector collector = TopScoreDocCollector.create(maximumHits, true);
			searcher.search(rewrittenQuery, collector);
			ScoreDoc[] hits = collector.topDocs().scoreDocs;
			for (int i = 0; i < hits.length; i++) {
				int docId = hits[i].doc;
				Document doc = searcher.doc(docId);
				SearchResult result = createSearchResult(doc, hits[i].score);
				results.add(result);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new ArrayList<SearchResult>(results);
	}

	/**
	 * Close all the index writers and searchers
	 */
	public static void close() {
		// close writers
		for (File file : WRITERS.keySet()) {
			try {
				WRITERS.get(file).close(true);
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
		WRITERS.clear();

		// close searchers
		for (File file : SEARCHERS.keySet()) {
			try {
				SEARCHERS.get(file).close();
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
		SEARCHERS.clear();
	}

	public static class IndexResult {
		public boolean success;
		public int branchCount;
		public int commitCount;
		public int blobCount;
		public int issueCount;
		
		public void add(IndexResult result) {
			this.branchCount += result.branchCount;
			this.commitCount += result.commitCount;
			this.blobCount += result.blobCount;
			this.issueCount += result.issueCount;			
		}
	}
}
