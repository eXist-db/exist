package org.exist.indexing.lucene;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
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
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;

public class LuceneIndex extends AbstractIndex {

    private static final Logger LOG = Logger.getLogger(LuceneIndexWorker.class);

    public final static String ID = LuceneIndex.class.getName();

    protected Directory directory;
    protected Analyzer defaultAnalyzer;

    protected double bufferSize = IndexWriter.DEFAULT_RAM_BUFFER_SIZE_MB;

    protected IndexWriter cachedWriter = null;
    protected int writerUseCount = 0;
    protected IndexReader cachedReader = null;
    protected int readerUseCount = 0;
    protected IndexReader cachedWritingReader = null;
    protected int writingReaderUseCount = 0;
    protected IndexSearcher cachedSearcher = null;
    protected int searcherUseCount = 0;
    
    public LuceneIndex() {
        //Nothing special to do
    }

    @Override
    public void configure(BrokerPool pool, String dataDir, Element config) throws DatabaseConfigurationException {
        super.configure(pool, dataDir, config);
        if (LOG.isDebugEnabled())
            LOG.debug("Configuring Lucene index");

        String bufferSizeParam = config.getAttribute("buffer");
        if (bufferSizeParam != null)
            try {
                bufferSize = Double.parseDouble(bufferSizeParam);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid buffer size setting for lucene index: " + bufferSizeParam, e);
            }

        if (LOG.isDebugEnabled())
            LOG.debug("Using buffer size: " + bufferSize);
        
        NodeList nl = config.getElementsByTagName("analyzer");
        if (nl.getLength() > 0) {
            Element node = (Element) nl.item(0);
            defaultAnalyzer = AnalyzerConfig.configureAnalyzer(node);
        }

        if (defaultAnalyzer == null)
            defaultAnalyzer = new StandardAnalyzer();
        if (LOG.isDebugEnabled())
            LOG.debug("Using default analyzer: " + defaultAnalyzer.getClass().getName());
    }

    @Override
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
        IndexWriter writer = null;
        try {
            directory = FSDirectory.getDirectory(dir);
            writer = getWriter();
        } catch (IOException e) {
            throw new DatabaseConfigurationException("Exception while reading lucene index directory: " +
                e.getMessage(), e);
        } finally {
            releaseWriter(writer);
        }
    }

    @Override
    public void close() throws DBException {
        try {
            if (cachedWriter != null)
                cachedWriter.close();
            directory.close();
        } catch (IOException e) {
            throw new DBException("Caught exception while closing lucene indexes: " + e.getMessage());
        }
    }

    @Override
    public void sync() throws DBException {
        //Nothing special to do
    }

    @Override
    public void remove() throws DBException {
        try {
            String[] files = directory.list();
            for (int i = 0; i < files.length; i++) {
                String file = files[i];
                directory.deleteFile(file);
            }
            close();
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
        }
    }

    @Override
    public IndexWorker getWorker(DBBroker broker) {
        return new LuceneIndexWorker(this, broker);
    }

    @Override
    public boolean checkIndex(DBBroker broker) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    protected Analyzer getDefaultAnalyzer() {
        return defaultAnalyzer;
    }
    
    protected synchronized IndexWriter getWriter() throws IOException {
        while (writingReaderUseCount > 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                //Nothing special to do
            }
        }
        if (cachedWriter != null) {
            writerUseCount++;
        } else {
            cachedWriter = new IndexWriter(directory, true, defaultAnalyzer);
            cachedWriter.setRAMBufferSizeMB(bufferSize);
            writerUseCount = 1;
        }
        notifyAll();
        return cachedWriter;
    }

    protected synchronized void releaseWriter(IndexWriter writer) {
        if (writer == null)
            return;
        if (writer != cachedWriter)
            throw new IllegalStateException("IndexWriter was not obtained from getWriter().");
        
        writerUseCount--;
        if (writerUseCount == 0) {
            try {
                cachedWriter.close();
            } catch (IOException e) {
                LOG.warn("Exception while closing lucene index: " + e.getMessage(), e);
            } finally {
                cachedWriter = null;
            }
        }
        notifyAll();

        waitForReadersAndReopen();
    }

    protected synchronized IndexReader getReader() throws IOException {
        if (cachedReader != null) {
            readerUseCount++;
        } else {
            cachedReader = IndexReader.open(directory);
            readerUseCount = 1;
        }
        return cachedReader;
    }

    protected synchronized void releaseReader(IndexReader reader) {
        if (reader == null)
            return;
        if (reader != cachedReader)
            throw new IllegalStateException("IndexReader was not obtained from getReader().");
        readerUseCount--;
        notifyAll();
//        try {
//            cachedReader.close();
//        } catch (IOException e) {
//            LOG.warn("Exception while closing lucene index: " + e.getMessage(), e);
//        } finally {
//            cachedReader = null;
//        }
    }

    protected synchronized IndexReader getWritingReader() throws IOException {
        while (writerUseCount > 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                //Nothing special to do
            }
        }
        if (cachedWritingReader != null) {
            writingReaderUseCount++;
        } else {
            cachedWritingReader = IndexReader.open(directory);
            writingReaderUseCount = 1;
        }
        notifyAll();
        return cachedWritingReader;
    }

    protected synchronized void releaseWritingReader(IndexReader reader) {
        if (reader == null)
            return;
        if (reader != cachedWritingReader)
            throw new IllegalStateException("IndexReader was not obtained from getWritingReader().");
        writingReaderUseCount--;
        if (writingReaderUseCount == 0) {
            try {
                cachedWritingReader.close();
            } catch (IOException e) {
                LOG.warn("Exception while closing lucene index: " + e.getMessage(), e);
            } finally {
                cachedWritingReader = null;
            }
        }
        notifyAll();

        waitForReadersAndReopen();
    }

    private void waitForReadersAndReopen() {
        while (readerUseCount > 0 || searcherUseCount > 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                //Nothing special to do
            }
        }
        reopenReaders();
    }

    private void reopenReaders() {
        if (cachedReader == null)
            return;
        IndexReader oldReader = cachedReader;
        try {
            cachedReader = cachedReader.reopen();
            if (oldReader != cachedReader) {
                oldReader.close();
            }
        } catch (IOException e) {
            LOG.warn("Exception while refreshing lucene index: " + e.getMessage(), e);
        }
        if (cachedSearcher != null)
            cachedSearcher = new IndexSearcher(cachedReader);
    }

    protected synchronized IndexSearcher getSearcher() throws IOException {
        if (cachedSearcher != null) {
            searcherUseCount++;
        } else {
            cachedSearcher = new IndexSearcher(getReader());
            readerUseCount--;
            searcherUseCount = 1;
        }
        return cachedSearcher;
    }

    protected synchronized void releaseSearcher(IndexSearcher searcher) {
        if (searcher == null)
            return;
        if (searcher != cachedSearcher)
            throw new IllegalStateException("IndexSearcher was not obtained from getWritingReader().");
        searcherUseCount--;
        notifyAll();
        //try {
            //cachedSearcher.close();
        //} catch (IOException e) {
            //LOG.warn("Exception while closing lucene index: " + e.getMessage(), e);
        //} finally {
            //cachedSearcher = null;
        //}
    }
}
