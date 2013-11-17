package de.akquinet.devops;

import java.util.concurrent.TimeUnit;

import com.gitblit.GitBlit;

public class GitBlit4UITests extends GitBlit {

	private boolean luceneIndexingEnabled;

	public GitBlit4UITests(boolean luceneIndexingEnabled) {
		super(null);
		this.luceneIndexingEnabled = luceneIndexingEnabled;
	}

	@Override
	protected void configureLuceneIndexing() {
		if (luceneIndexingEnabled) {
			getScheduledExecutor().scheduleAtFixedRate(getLuceneExecutor(), 1,
					2, TimeUnit.MINUTES);
			getLogger()
					.info("Lucene executor is scheduled to process indexed branches every 2 minutes.");
		}
	}

}
