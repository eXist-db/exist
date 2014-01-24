package org.exist.indexing.lucene;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.TaxonomyWriter;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.exist.backup.RawDataBackup;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.RawBackupSupport;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.lucene.util.Version;

public class LuceneIndex extends AbstractIndex implements RawBackupSupport {
    
    public final static Version LUCENE_VERSION_IN_USE = Version.LUCENE_44;

    private static final Logger LOG = Logger.getLogger(LuceneIndexWorker.class);

    public final static String ID = LuceneIndex.class.getName();

	private static final String DIR_NAME = "lucene";

    protected Directory directory;
    protected Analyzer defaultAnalyzer;

    protected double bufferSize = IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB;

    protected IndexWriter cachedWriter = null;
    protected int writerUseCount = 0;
    protected IndexReader cachedReader = null;
    protected int readerUseCount = 0;
    protected IndexReader cachedWritingReader = null;
    protected int writingReaderUseCount = 0;
    protected IndexSearcher cachedSearcher = null;
    protected int searcherUseCount = 0;
    
    protected boolean singleWriter = false;

    //Taxonomy staff
    protected Directory taxonomyDirectory;

    protected TaxonomyWriter cachedTaxonomyWriter = null;
    protected int taxonomyWriterUseCount = 0;

    protected TaxonomyReader cachedTaxonomyReader = null;
    protected int taxonomyReaderUseCount = 0;

    public LuceneIndex() {
        //Nothing special to do
    }

    public String getDirName() {
        return DIR_NAME;
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
            defaultAnalyzer = new StandardAnalyzer(LUCENE_VERSION_IN_USE);
        if (LOG.isDebugEnabled())
            LOG.debug("Using default analyzer: " + defaultAnalyzer.getClass().getName());
    }

    @Override
    public void open() throws DatabaseConfigurationException {
        File dir = new File(getDataDir(), getDirName());
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
            directory = FSDirectory.open(dir);
            taxonomyDirectory = FSDirectory.open(new File(dir, "taxonomy"));

            writer = getWriter();
            
        } catch (IOException e) {
            throw new DatabaseConfigurationException("Exception while reading lucene index directory: " +
                e.getMessage(), e);
        } finally {
            releaseWriter(writer);
        }
    }

    @Override
    public synchronized void close() throws DBException {
        try {
            if (cachedWriter != null) {
            	commit();
            	
            	cachedTaxonomyWriter.close();
                cachedWriter.close();
                
                cachedTaxonomyWriter = null;
                cachedWriter = null;
            }
            taxonomyDirectory.close();
            directory.close();
        } catch (IOException e) {
            throw new DBException("Caught exception while closing lucene indexes: " + e.getMessage());
        }
    }

    @Override
    public synchronized void sync() throws DBException {
        //Nothing special to do
        commit();
    }

    @Override
    public void remove() throws DBException {
        try {
            String[] files = directory.listAll();
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
    
    protected boolean needsCommit = false;

    public IndexWriter getWriter() throws IOException {
        return getWriter(false);
    }

    public synchronized IndexWriter getWriter(boolean exclusive) throws IOException {
        while (writingReaderUseCount > 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                //Nothing special to do
            }
        }
        if (singleWriter) {
            while (writerUseCount > 0) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    //Nothing special to do
                }
            }
        }
        singleWriter = exclusive;
        if (cachedWriter != null) {
            writerUseCount++;
        } else {
            final IndexWriterConfig idxWriterConfig = new IndexWriterConfig(LUCENE_VERSION_IN_USE, defaultAnalyzer);
            idxWriterConfig.setRAMBufferSizeMB(bufferSize);
            
            /**
             With Lucene 2.9.4 we had auto-commit = true set on the IndexWriter constructor here,
             now we have to commit ourselves, this is done manually in releaseWriter()
             */
            cachedWriter = new IndexWriter(directory, idxWriterConfig);
            cachedTaxonomyWriter = new DirectoryTaxonomyWriter(taxonomyDirectory);
            writerUseCount = 1;
        }
        notifyAll();
        return cachedWriter;
    }

    public synchronized void releaseWriter(IndexWriter writer) {
        if (writer == null)
            return;
        if (writer != cachedWriter)
            throw new IllegalStateException("IndexWriter was not obtained from getWriter().");

        needsCommit = true;
        writerUseCount--;
        notifyAll();

        waitForReadersAndReopen();
    }

    protected void commit() {
    	if (!needsCommit) {
            return;
        }
        try {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Committing lucene index");
            }
            
        	if (cachedWriter != null) {
        	    cachedTaxonomyWriter.commit();
                cachedWriter.commit();
            }
            needsCommit = false;
        } catch(CorruptIndexException cie) {
            LOG.error("Detected corrupt Lucence index on writer release and commit: " + cie.getMessage(), cie);
        } catch(IOException ioe) {
            LOG.error("Detected Lucence index issue on writer release and commit: " + ioe.getMessage(), ioe);
        }
    }
    
    public synchronized IndexReader getReader() throws IOException {
    	commit();
        if (cachedReader != null) {
            readerUseCount++;
        } else {
            cachedReader = DirectoryReader.open(directory);
            cachedTaxonomyReader = new DirectoryTaxonomyReader(taxonomyDirectory);
            readerUseCount = 1;
        }
        return cachedReader;
    }

    public synchronized void releaseReader(IndexReader reader) {
        if (reader == null)
            return;
        if (reader != cachedReader)
            throw new IllegalStateException("IndexReader was not obtained from getReader().");
        readerUseCount--;
        notifyAll();
    }

    protected synchronized IndexReader getWritingReader() throws IOException {
        while (writerUseCount > 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                //Nothing special to do
            }
        }
        if (cachedWriter != null) {
        	try {
                cachedWriter.close();
            } catch (IOException e) {
                LOG.warn("Exception while closing lucene index: " + e.getMessage(), e);
            } finally {
                cachedWriter = null;
            }
        }
        if (cachedWritingReader != null) {
            writingReaderUseCount++;
        } else {
            cachedWritingReader = DirectoryReader.open(directory);
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
        try {
            cachedTaxonomyReader.close();
        	cachedReader.close();
        	
        	cachedTaxonomyReader = null;
        	cachedReader = null;
  
        	//XXX: understand - is it right to comment close out? ... Closed by "cachedReader.close();"?
//        	if (cachedSearcher != null)
//        		cachedSearcher.close();
        	cachedSearcher = null;
        } catch (IOException e) {
            LOG.warn("Exception while refreshing lucene index: " + e.getMessage(), e);
        }
    }

    public synchronized IndexSearcher getSearcher() throws IOException {
    	commit();
        if (cachedSearcher != null) {
            searcherUseCount++;
        } else {
            cachedSearcher = new IndexSearcher(getReader());
            readerUseCount--;
            searcherUseCount = 1;
        }
        return cachedSearcher;
    }

    public synchronized void releaseSearcher(IndexSearcher searcher) {
        if (searcher == null)
            return;
        if (searcher != cachedSearcher)
            throw new IllegalStateException("IndexSearcher was not obtained from getWritingReader().");
        searcherUseCount--;
        notifyAll();
    }
    
    //DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);
    
    public synchronized TaxonomyWriter getTaxonomyWriter() throws IOException {
        return cachedTaxonomyWriter;
    }

    public synchronized TaxonomyReader getTaxonomyReader() throws IOException {
        return cachedTaxonomyReader;
    }

	@Override
	public void backupToArchive(RawDataBackup backup) throws IOException {
		for (String name : directory.listAll()) {
			String path = getDirName() + "/" + name;
			OutputStream os = backup.newEntry(path);
			InputStream is = new FileInputStream(new File(getDataDir(), path));
	        byte[] buf = new byte[4096];
	        int len;
	        while ((len = is.read(buf)) > 0) {
	            os.write(buf, 0, len);
	        }
	        is.close();
	        backup.closeEntry();
		}
	}
	
}
