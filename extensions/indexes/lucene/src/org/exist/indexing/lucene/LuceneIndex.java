/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.lucene;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.TaxonomyWriter;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.exist.backup.RawDataBackup;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.RawBackupSupport;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.function.Function2E;
import org.exist.util.function.FunctionE;
import org.exist.xquery.XPathException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;

public class LuceneIndex extends AbstractIndex implements RawBackupSupport {
    
    public final static Version LUCENE_VERSION_IN_USE = Version.LUCENE_44;

    private static final Logger LOG = LogManager.getLogger(LuceneIndexWorker.class);

    public final static String ID = LuceneIndex.class.getName();

	private static final String DIR_NAME = "lucene";

    protected Directory directory;
    protected Analyzer defaultAnalyzer;

    protected double bufferSize = IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB;

    protected IndexWriter cachedWriter = null;

    protected SearcherManager searcherManager = null;
    protected ReaderManager readerManager = null;

    //Taxonomy staff
    protected Directory taxonomyDirectory;

    protected TaxonomyWriter cachedTaxonomyWriter = null;

    protected TaxonomyReader cachedTaxonomyReader = null;

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

            final IndexWriterConfig idxWriterConfig = new IndexWriterConfig(LUCENE_VERSION_IN_USE, defaultAnalyzer);
            idxWriterConfig.setRAMBufferSizeMB(bufferSize);
            cachedWriter = new IndexWriter(directory, idxWriterConfig);

            searcherManager = new SearcherManager(cachedWriter, true, null);
            readerManager = new ReaderManager(cachedWriter, true);

            cachedTaxonomyWriter = new DirectoryTaxonomyWriter(taxonomyDirectory);
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
            if (searcherManager != null) {
                searcherManager.close();
                searcherManager = null;
            }
            if (readerManager != null) {
                readerManager.close();
                readerManager = null;
            }
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
            for (String file : files) {
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

    public IndexWriter getWriter(boolean exclusive) throws IOException {
        return cachedWriter;
    }

    public synchronized void releaseWriter(IndexWriter writer) {
        if (writer == null)
            return;
        needsCommit = true;
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

    public <R> R withReader(FunctionE<IndexReader, R, IOException> fn) throws IOException {
        readerManager.maybeRefreshBlocking();
        final DirectoryReader reader = readerManager.acquire();
        try {
            return fn.apply(reader);
        } finally {
            readerManager.release(reader);
        }
    }

    public <R> R withSearcher(Function2E<IndexSearcher, R, IOException, XPathException> consumer) throws IOException, XPathException {
        searcherManager.maybeRefreshBlocking();
        final IndexSearcher searcher = searcherManager.acquire();
        try {
            return consumer.apply(searcher);
        } finally {
            searcherManager.release(searcher);
        }
    }

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
