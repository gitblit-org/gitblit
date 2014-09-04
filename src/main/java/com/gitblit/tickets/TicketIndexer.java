/*
 * Copyright 2014 gitblit.com.
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
package com.gitblit.tickets;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortField.Type;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Keys;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Attachment;
import com.gitblit.models.TicketModel.Patchset;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.utils.FileUtils;
import com.gitblit.utils.StringUtils;

/**
 * Indexes tickets in a Lucene database.
 *
 * @author James Moger
 *
 */
public class TicketIndexer {

	/**
	 * Fields in the Lucene index
	 */
	public static enum Lucene {

		rid(Type.STRING),
		did(Type.STRING),
		project(Type.STRING),
		repository(Type.STRING),
		number(Type.LONG),
		title(Type.STRING),
		body(Type.STRING),
		topic(Type.STRING),
		created(Type.LONG),
		createdby(Type.STRING),
		updated(Type.LONG),
		updatedby(Type.STRING),
		responsible(Type.STRING),
		milestone(Type.STRING),
		status(Type.STRING),
		type(Type.STRING),
		labels(Type.STRING),
		participants(Type.STRING),
		watchedby(Type.STRING),
		mentions(Type.STRING),
		attachments(Type.INT),
		content(Type.STRING),
		patchset(Type.STRING),
		comments(Type.INT),
		mergesha(Type.STRING),
		mergeto(Type.STRING),
		patchsets(Type.INT),
		votes(Type.INT);

		final Type fieldType;

		Lucene(Type fieldType) {
			this.fieldType = fieldType;
		}

		public String colon() {
			return name() + ":";
		}

		public String matches(String value) {
			if (StringUtils.isEmpty(value)) {
				return "";
			}
			boolean not = value.charAt(0) == '!';
			if (not) {
				return "!" + name() + ":" + escape(value.substring(1));
			}
			return name() + ":" + escape(value);
		}

		public String doesNotMatch(String value) {
			if (StringUtils.isEmpty(value)) {
				return "";
			}
			return "NOT " + name() + ":" + escape(value);
		}

		public String isNotNull() {
			return matches("[* TO *]");
		}

		public SortField asSortField(boolean descending) {
			return new SortField(name(), fieldType, descending);
		}

		private String escape(String value) {
			if (value.charAt(0) != '"') {
				for (char c : value.toCharArray()) {
					if (!Character.isLetterOrDigit(c)) {
						return "\"" + value + "\"";
					}
				}
			}
			return value;
		}

		public static Lucene fromString(String value) {
			for (Lucene field : values()) {
				if (field.name().equalsIgnoreCase(value)) {
					return field;
				}
			}
			return created;
		}
	}

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Version luceneVersion = Version.LUCENE_46;

	private final File luceneDir;

	private IndexWriter writer;

	private IndexSearcher searcher;

	public TicketIndexer(IRuntimeManager runtimeManager) {
		this.luceneDir = runtimeManager.getFileOrFolder(Keys.tickets.indexFolder, "${baseFolder}/tickets/lucene");
	}

	/**
	 * Close all writers and searchers used by the ticket indexer.
	 */
	public void close() {
		closeSearcher();
		closeWriter();
	}

	/**
	 * Deletes the entire ticket index for all repositories.
	 */
	public void deleteAll() {
		close();
		FileUtils.delete(luceneDir);
	}

	/**
	 * Deletes all tickets for the the repository from the index.
	 */
	public boolean deleteAll(RepositoryModel repository) {
		try {
			IndexWriter writer = getWriter();
			StandardAnalyzer analyzer = new StandardAnalyzer(luceneVersion);
			QueryParser qp = new QueryParser(luceneVersion, Lucene.rid.name(), analyzer);
			BooleanQuery query = new BooleanQuery();
			query.add(qp.parse(repository.getRID()), Occur.MUST);

			int numDocsBefore = writer.numDocs();
			writer.deleteDocuments(query);
			writer.commit();
			closeSearcher();
			int numDocsAfter = writer.numDocs();
			if (numDocsBefore == numDocsAfter) {
				log.debug(MessageFormat.format("no records found to delete in {0}", repository));
				return false;
			} else {
				log.debug(MessageFormat.format("deleted {0} records in {1}", numDocsBefore - numDocsAfter, repository));
				return true;
			}
		} catch (Exception e) {
			log.error("error", e);
		}
		return false;
	}

	/**
	 * Bulk Add/Update tickets in the Lucene index
	 *
	 * @param tickets
	 */
	public void index(List<TicketModel> tickets) {
		try {
			IndexWriter writer = getWriter();
			for (TicketModel ticket : tickets) {
				Document doc = ticketToDoc(ticket);
				writer.addDocument(doc);
			}
			writer.commit();
			closeSearcher();
		} catch (Exception e) {
			log.error("error", e);
		}
	}

	/**
	 * Add/Update a ticket in the Lucene index
	 *
	 * @param ticket
	 */
	public void index(TicketModel ticket) {
		try {
			IndexWriter writer = getWriter();
			delete(ticket.repository, ticket.number, writer);
			Document doc = ticketToDoc(ticket);
			writer.addDocument(doc);
			writer.commit();
			closeSearcher();
		} catch (Exception e) {
			log.error("error", e);
		}
	}

	/**
	 * Delete a ticket from the Lucene index.
	 *
	 * @param ticket
	 * @throws Exception
	 * @return true, if deleted, false if no record was deleted
	 */
	public boolean delete(TicketModel ticket) {
		try {
			IndexWriter writer = getWriter();
			return delete(ticket.repository, ticket.number, writer);
		} catch (Exception e) {
			log.error("Failed to delete ticket " + ticket.number, e);
		}
		return false;
	}

	/**
	 * Delete a ticket from the Lucene index.
	 *
	 * @param repository
	 * @param ticketId
	 * @throws Exception
	 * @return true, if deleted, false if no record was deleted
	 */
	private boolean delete(String repository, long ticketId, IndexWriter writer) throws Exception {
		StandardAnalyzer analyzer = new StandardAnalyzer(luceneVersion);
		QueryParser qp = new QueryParser(luceneVersion, Lucene.did.name(), analyzer);
		BooleanQuery query = new BooleanQuery();
		query.add(qp.parse(StringUtils.getSHA1(repository + ticketId)), Occur.MUST);

		int numDocsBefore = writer.numDocs();
		writer.deleteDocuments(query);
		writer.commit();
		closeSearcher();
		int numDocsAfter = writer.numDocs();
		if (numDocsBefore == numDocsAfter) {
			log.debug(MessageFormat.format("no records found to delete in {0}", repository));
			return false;
		} else {
			log.debug(MessageFormat.format("deleted {0} records in {1}", numDocsBefore - numDocsAfter, repository));
			return true;
		}
	}

	/**
	 * Returns true if the repository has tickets in the index.
	 *
	 * @param repository
	 * @return true if there are indexed tickets
	 */
	public boolean hasTickets(RepositoryModel repository) {
		return !queryFor(Lucene.rid.matches(repository.getRID()), 1, 0, null, true).isEmpty();
	}

	/**
	 * Search for tickets matching the query.  The returned tickets are
	 * shadows of the real ticket, but suitable for a results list.
	 *
	 * @param repository
	 * @param text
	 * @param page
	 * @param pageSize
	 * @return search results
	 */
	public List<QueryResult> searchFor(RepositoryModel repository, String text, int page, int pageSize) {
		if (StringUtils.isEmpty(text)) {
			return Collections.emptyList();
		}
		Set<QueryResult> results = new LinkedHashSet<QueryResult>();
		StandardAnalyzer analyzer = new StandardAnalyzer(luceneVersion);
		try {
			// search the title, description and content
			BooleanQuery query = new BooleanQuery();
			QueryParser qp;

			qp = new QueryParser(luceneVersion, Lucene.title.name(), analyzer);
			qp.setAllowLeadingWildcard(true);
			query.add(qp.parse(text), Occur.SHOULD);

			qp = new QueryParser(luceneVersion, Lucene.body.name(), analyzer);
			qp.setAllowLeadingWildcard(true);
			query.add(qp.parse(text), Occur.SHOULD);

			qp = new QueryParser(luceneVersion, Lucene.content.name(), analyzer);
			qp.setAllowLeadingWildcard(true);
			query.add(qp.parse(text), Occur.SHOULD);

			IndexSearcher searcher = getSearcher();
			Query rewrittenQuery = searcher.rewrite(query);

			log.debug(rewrittenQuery.toString());

			TopScoreDocCollector collector = TopScoreDocCollector.create(5000, true);
			searcher.search(rewrittenQuery, collector);
			int offset = Math.max(0, (page - 1) * pageSize);
			ScoreDoc[] hits = collector.topDocs(offset, pageSize).scoreDocs;
			for (int i = 0; i < hits.length; i++) {
				int docId = hits[i].doc;
				Document doc = searcher.doc(docId);
				QueryResult result = docToQueryResult(doc);
				if (repository != null) {
					if (!result.repository.equalsIgnoreCase(repository.name)) {
						continue;
					}
				}
				results.add(result);
			}
		} catch (Exception e) {
			log.error(MessageFormat.format("Exception while searching for {0}", text), e);
		}
		return new ArrayList<QueryResult>(results);
	}

	/**
	 * Search for tickets matching the query.  The returned tickets are
	 * shadows of the real ticket, but suitable for a results list.
	 *
	 * @param text
	 * @param page
	 * @param pageSize
	 * @param sortBy
	 * @param desc
	 * @return
	 */
	public List<QueryResult> queryFor(String queryText, int page, int pageSize, String sortBy, boolean desc) {
		if (StringUtils.isEmpty(queryText)) {
			return Collections.emptyList();
		}

		Set<QueryResult> results = new LinkedHashSet<QueryResult>();
		StandardAnalyzer analyzer = new StandardAnalyzer(luceneVersion);
		try {
			QueryParser qp = new QueryParser(luceneVersion, Lucene.content.name(), analyzer);
			Query query = qp.parse(queryText);

			IndexSearcher searcher = getSearcher();
			Query rewrittenQuery = searcher.rewrite(query);

			log.debug(rewrittenQuery.toString());

			Sort sort;
			if (sortBy == null) {
				sort = new Sort(Lucene.created.asSortField(desc));
			} else {
				sort = new Sort(Lucene.fromString(sortBy).asSortField(desc));
			}
			int maxSize = 5000;
			TopFieldDocs docs = searcher.search(rewrittenQuery, null, maxSize, sort, false, false);
			int size = (pageSize <= 0) ? maxSize : pageSize;
			int offset = Math.max(0, (page - 1) * size);
			ScoreDoc[] hits = subset(docs.scoreDocs, offset, size);
			for (int i = 0; i < hits.length; i++) {
				int docId = hits[i].doc;
				Document doc = searcher.doc(docId);
				QueryResult result = docToQueryResult(doc);
				result.docId = docId;
				result.totalResults = docs.totalHits;
				results.add(result);
			}
		} catch (Exception e) {
			log.error(MessageFormat.format("Exception while searching for {0}", queryText), e);
		}
		return new ArrayList<QueryResult>(results);
	}

	private ScoreDoc [] subset(ScoreDoc [] docs, int offset, int size) {
		if (docs.length >= (offset + size)) {
			ScoreDoc [] set = new ScoreDoc[size];
			System.arraycopy(docs, offset, set, 0, set.length);
			return set;
		} else if (docs.length >= offset) {
			ScoreDoc [] set = new ScoreDoc[docs.length - offset];
			System.arraycopy(docs, offset, set, 0, set.length);
			return set;
		} else {
			return new ScoreDoc[0];
		}
	}

	private IndexWriter getWriter() throws IOException {
		if (writer == null) {
			Directory directory = FSDirectory.open(luceneDir);

			if (!luceneDir.exists()) {
				luceneDir.mkdirs();
			}

			StandardAnalyzer analyzer = new StandardAnalyzer(luceneVersion);
			IndexWriterConfig config = new IndexWriterConfig(luceneVersion, analyzer);
			config.setOpenMode(OpenMode.CREATE_OR_APPEND);
			writer = new IndexWriter(directory, config);
		}
		return writer;
	}

	private synchronized void closeWriter() {
		try {
			if (writer != null) {
				writer.close();
			}
		} catch (Exception e) {
			log.error("failed to close writer!", e);
		} finally {
			writer = null;
		}
	}

	private IndexSearcher getSearcher() throws IOException {
		if (searcher == null) {
			searcher = new IndexSearcher(DirectoryReader.open(getWriter(), true));
		}
		return searcher;
	}

	private synchronized void closeSearcher() {
		try {
			if (searcher != null) {
				searcher.getIndexReader().close();
			}
		} catch (Exception e) {
			log.error("failed to close searcher!", e);
		} finally {
			searcher = null;
		}
	}

	/**
	 * Creates a Lucene document from a ticket.
	 *
	 * @param ticket
	 * @return a Lucene document
	 */
	private Document ticketToDoc(TicketModel ticket) {
		Document doc = new Document();
		// repository and document ids for Lucene querying
		toDocField(doc, Lucene.rid, StringUtils.getSHA1(ticket.repository));
		toDocField(doc, Lucene.did, StringUtils.getSHA1(ticket.repository + ticket.number));

		toDocField(doc, Lucene.project, ticket.project);
		toDocField(doc, Lucene.repository, ticket.repository);
		toDocField(doc, Lucene.number, ticket.number);
		toDocField(doc, Lucene.title, ticket.title);
		toDocField(doc, Lucene.body, ticket.body);
		toDocField(doc, Lucene.created, ticket.created);
		toDocField(doc, Lucene.createdby, ticket.createdBy);
		toDocField(doc, Lucene.updated, ticket.updated);
		toDocField(doc, Lucene.updatedby, ticket.updatedBy);
		toDocField(doc, Lucene.responsible, ticket.responsible);
		toDocField(doc, Lucene.milestone, ticket.milestone);
		toDocField(doc, Lucene.topic, ticket.topic);
		toDocField(doc, Lucene.status, ticket.status.name());
		toDocField(doc, Lucene.comments, ticket.getComments().size());
		toDocField(doc, Lucene.type, ticket.type == null ? null : ticket.type.name());
		toDocField(doc, Lucene.mergesha, ticket.mergeSha);
		toDocField(doc, Lucene.mergeto, ticket.mergeTo);
		toDocField(doc, Lucene.labels, StringUtils.flattenStrings(ticket.getLabels(), ";").toLowerCase());
		toDocField(doc, Lucene.participants, StringUtils.flattenStrings(ticket.getParticipants(), ";").toLowerCase());
		toDocField(doc, Lucene.watchedby, StringUtils.flattenStrings(ticket.getWatchers(), ";").toLowerCase());
		toDocField(doc, Lucene.mentions, StringUtils.flattenStrings(ticket.getMentions(), ";").toLowerCase());
		toDocField(doc, Lucene.votes, ticket.getVoters().size());

		List<String> attachments = new ArrayList<String>();
		for (Attachment attachment : ticket.getAttachments()) {
			attachments.add(attachment.name.toLowerCase());
		}
		toDocField(doc, Lucene.attachments, StringUtils.flattenStrings(attachments, ";"));

		List<Patchset> patches = ticket.getPatchsets();
		if (!patches.isEmpty()) {
			toDocField(doc, Lucene.patchsets, patches.size());
			Patchset patchset = patches.get(patches.size() - 1);
			String flat =
					patchset.number + ":" +
					patchset.rev + ":" +
					patchset.tip + ":" +
					patchset.base + ":" +
					patchset.commits;
			doc.add(new org.apache.lucene.document.Field(Lucene.patchset.name(), flat, TextField.TYPE_STORED));
		}

		doc.add(new TextField(Lucene.content.name(), ticket.toIndexableString(), Store.NO));

		return doc;
	}

	private void toDocField(Document doc, Lucene lucene, Date value) {
		if (value == null) {
			return;
		}
		doc.add(new LongField(lucene.name(), value.getTime(), Store.YES));
	}

	private void toDocField(Document doc, Lucene lucene, long value) {
		doc.add(new LongField(lucene.name(), value, Store.YES));
	}

	private void toDocField(Document doc, Lucene lucene, int value) {
		doc.add(new IntField(lucene.name(), value, Store.YES));
	}

	private void toDocField(Document doc, Lucene lucene, String value) {
		if (StringUtils.isEmpty(value)) {
			return;
		}
		doc.add(new org.apache.lucene.document.Field(lucene.name(), value, TextField.TYPE_STORED));
	}

	/**
	 * Creates a query result from the Lucene document.  This result is
	 * not a high-fidelity representation of the real ticket, but it is
	 * suitable for display in a table of search results.
	 *
	 * @param doc
	 * @return a query result
	 * @throws ParseException
	 */
	private QueryResult docToQueryResult(Document doc) throws ParseException {
		QueryResult result = new QueryResult();
		result.project = unpackString(doc, Lucene.project);
		result.repository = unpackString(doc, Lucene.repository);
		result.number = unpackLong(doc, Lucene.number);
		result.createdBy = unpackString(doc, Lucene.createdby);
		result.createdAt = unpackDate(doc, Lucene.created);
		result.updatedBy = unpackString(doc, Lucene.updatedby);
		result.updatedAt = unpackDate(doc, Lucene.updated);
		result.title = unpackString(doc, Lucene.title);
		result.body = unpackString(doc, Lucene.body);
		result.status = Status.fromObject(unpackString(doc, Lucene.status), Status.New);
		result.responsible = unpackString(doc, Lucene.responsible);
		result.milestone = unpackString(doc, Lucene.milestone);
		result.topic = unpackString(doc, Lucene.topic);
		result.type = TicketModel.Type.fromObject(unpackString(doc, Lucene.type), TicketModel.Type.defaultType);
		result.mergeSha = unpackString(doc, Lucene.mergesha);
		result.mergeTo = unpackString(doc, Lucene.mergeto);
		result.commentsCount = unpackInt(doc, Lucene.comments);
		result.votesCount = unpackInt(doc, Lucene.votes);
		result.attachments = unpackStrings(doc, Lucene.attachments);
		result.labels = unpackStrings(doc, Lucene.labels);
		result.participants = unpackStrings(doc, Lucene.participants);
		result.watchedby = unpackStrings(doc, Lucene.watchedby);
		result.mentions = unpackStrings(doc, Lucene.mentions);

		if (!StringUtils.isEmpty(doc.get(Lucene.patchset.name()))) {
			// unpack most recent patchset
			String [] values = doc.get(Lucene.patchset.name()).split(":", 5);

			Patchset patchset = new Patchset();
			patchset.number = Integer.parseInt(values[0]);
			patchset.rev = Integer.parseInt(values[1]);
			patchset.tip = values[2];
			patchset.base = values[3];
			patchset.commits = Integer.parseInt(values[4]);

			result.patchset = patchset;
		}

		return result;
	}

	private String unpackString(Document doc, Lucene lucene) {
		return doc.get(lucene.name());
	}

	private List<String> unpackStrings(Document doc, Lucene lucene) {
		if (!StringUtils.isEmpty(doc.get(lucene.name()))) {
			return StringUtils.getStringsFromValue(doc.get(lucene.name()), ";");
		}
		return null;
	}

	private Date unpackDate(Document doc, Lucene lucene) {
		String val = doc.get(lucene.name());
		if (!StringUtils.isEmpty(val)) {
			long time = Long.parseLong(val);
			Date date = new Date(time);
			return date;
		}
		return null;
	}

	private long unpackLong(Document doc, Lucene lucene) {
		String val = doc.get(lucene.name());
		if (StringUtils.isEmpty(val)) {
			return 0;
		}
		long l = Long.parseLong(val);
		return l;
	}

	private int unpackInt(Document doc, Lucene lucene) {
		String val = doc.get(lucene.name());
		if (StringUtils.isEmpty(val)) {
			return 0;
		}
		int i = Integer.parseInt(val);
		return i;
	}
}