package org.exist.indexing.lucene;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.SimpleAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;

public class LuceneIndex extends AbstractIndex {

    private static final Logger LOG = Logger.getLogger(LuceneIndexWorker.class);

    public final static String ID = LuceneIndex.class.getName();

    protected Directory directory;
    protected Analyzer analyzer;

    protected IndexWriter cachedWriter = null;
    protected IndexReader cachedReader = null;
    protected IndexSearcher cachedSearcher = null;

    public LuceneIndex() {
    }

    public void configure(BrokerPool pool, String dataDir, Element config) throws DatabaseConfigurationException {
        super.configure(pool, dataDir, config);
        if (LOG.isDebugEnabled())
            LOG.debug("Configuring Lucene index");
        analyzer = new SimpleAnalyzer();
    }

    public void open() throws DatabaseConfigurationException {
        File dir = new File(getDataDir(), "lucene");
        if (LOG.isDebugEnabled())
            LOG.debug("Opening Lucene index directory: " + dir.getAbsolutePath());
        if (dir.exists()) {
            if (!dir.isDirectory())
                throw new DatabaseConfigurationException("Lucene index location is not a directory: " +
                    dir.getAbsolutePath());
        } else
            dir.mkdirs();
        try {
            directory = FSDirectory.getDirectory(dir);
        } catch (IOException e) {
            throw new DatabaseConfigurationException("Exception while reading lucene index directory: " +
                e.getMessage(), e);
        }
    }

    public void close() throws DBException {
        try {
            if (cachedWriter != null)
                cachedWriter.close();
            directory.close();
        } catch (IOException e) {
            throw new DBException("Caught exception while closing lucene indexes: " + e.getMessage());
        }
    }

    public void sync() throws DBException {
    }

    public void remove() throws DBException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public IndexWorker getWorker(DBBroker broker) {
        return new LuceneIndexWorker(this);
    }

    public boolean checkIndex(DBBroker broker) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    protected Analyzer getAnalyzer() {
        return analyzer;
    }
    
    protected IndexWriter getWriter() throws IOException {
        if (cachedWriter != null)
            return cachedWriter;
        cachedWriter = new IndexWriter(directory, true, analyzer);
        return cachedWriter;
    }

    protected void releaseWriter(IndexWriter writer) {
        try {
            cachedWriter.close();
        } catch (IOException e) {
            LOG.warn("Exception while closing lucene index: " + e.getMessage(), e);
        } finally {
            cachedWriter = null;
        }
    }

    protected IndexReader getReader() throws IOException {
        if (cachedReader != null)
            return cachedReader;
        cachedReader = IndexReader.open(directory);
        return cachedReader;
    }

    protected void releaseReader(IndexReader reader) {
        try {
            cachedReader.close();
        } catch (IOException e) {
            LOG.warn("Exception while closing lucene index: " + e.getMessage(), e);
        } finally {
            cachedReader = null;
        }
    }

    protected IndexSearcher getSearcher() throws IOException {
        if (cachedSearcher != null)
            return cachedSearcher;
        cachedSearcher = new IndexSearcher(directory);
        return cachedSearcher;
    }

    protected void releaseSearcher(IndexSearcher searcher) {
        try {
            cachedSearcher.close();
        } catch (IOException e) {
            LOG.warn("Exception while closing lucene index: " + e.getMessage(), e);
        } finally {
            cachedSearcher = null;
        }
    }
}
