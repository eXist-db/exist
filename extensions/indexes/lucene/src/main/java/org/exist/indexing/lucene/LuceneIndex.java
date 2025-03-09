/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.lucene;

import com.evolvedbinary.j8fu.function.Function2E;
import com.evolvedbinary.j8fu.function.FunctionE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.facet.taxonomy.SearcherTaxonomyManager;
import org.apache.lucene.facet.taxonomy.TaxonomyWriter;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.*;
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
import org.exist.util.FileUtils;
import org.exist.xquery.XPathException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class LuceneIndex extends AbstractIndex implements RawBackupSupport {
    
    public final static Version LUCENE_VERSION_IN_USE = Version.LUCENE_4_10_4;

    private static final Logger LOG = LogManager.getLogger(LuceneIndexWorker.class);

    public final static String ID = LuceneIndex.class.getName();

	private static final String DIR_NAME = "lucene";
	private static final String TAXONOMY_DIR_NAME = "taxonomy";

    protected Directory directory;
    protected Directory taxoDirectory;

    protected Analyzer defaultAnalyzer;

    protected double bufferSize = IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB;

    protected IndexWriter cachedWriter = null;
    protected DirectoryTaxonomyWriter cachedTaxonomyWriter = null;

    protected SearcherTaxonomyManager searcherManager = null;
    protected ReaderManager readerManager = null;

    public String getDirName() {
        return DIR_NAME;
    }

    @Override
    public void configure(BrokerPool pool, Path dataDir, Element config) throws DatabaseConfigurationException {
        super.configure(pool, dataDir, config);
        if (LOG.isDebugEnabled())
            LOG.debug("Configuring Lucene index");

        String bufferSizeParam = config.getAttribute("buffer");
        if (bufferSizeParam != null)
            try {
                bufferSize = Double.parseDouble(bufferSizeParam);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid buffer size setting for Lucene index: {}", bufferSizeParam, e);
            }

        if (LOG.isDebugEnabled())
            LOG.debug("Using buffer size: {}", bufferSize);
        
        NodeList nl = config.getElementsByTagName("analyzer");
        if (nl.getLength() > 0) {
            Element node = (Element) nl.item(0);
            defaultAnalyzer = AnalyzerConfig.configureAnalyzer(node);
        }

        if (defaultAnalyzer == null)
            defaultAnalyzer = new StandardAnalyzer(LUCENE_VERSION_IN_USE);
        if (LOG.isDebugEnabled())
            LOG.debug("Using default analyzer: {}", defaultAnalyzer.getClass().getName());
    }

    @Override
    public void open() throws DatabaseConfigurationException {
        Path dir = getDataDir().resolve(getDirName());
        Path taxoDir = dir.resolve(TAXONOMY_DIR_NAME);
        if (LOG.isDebugEnabled())
            LOG.debug("Opening Lucene index directory: {}", dir.toAbsolutePath().toString());

        IndexWriter writer = null;
        try {
            if (Files.exists(dir)) {
                if (!Files.isDirectory(dir))
                    throw new DatabaseConfigurationException("Lucene index location is not a directory: " +
                            dir.toAbsolutePath());
            } else {
                Files.createDirectories(taxoDir);
            }

            directory = FSDirectory.open(dir.toFile());
            taxoDirectory = FSDirectory.open(taxoDir.toFile());

            final IndexWriterConfig idxWriterConfig = new IndexWriterConfig(LUCENE_VERSION_IN_USE, defaultAnalyzer);
            idxWriterConfig.setRAMBufferSizeMB(bufferSize);
            cachedWriter = new IndexWriter(directory, idxWriterConfig);
            cachedTaxonomyWriter = new DirectoryTaxonomyWriter(taxoDirectory);

            searcherManager = new SearcherTaxonomyManager(cachedWriter, true, null, cachedTaxonomyWriter);
            readerManager = new ReaderManager(cachedWriter, true);
        } catch (IOException e) {
            throw new DatabaseConfigurationException("Exception while reading Lucene index directory: " +
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

            taxoDirectory.close();
            directory.close();
        } catch (IOException e) {
            throw new DBException("Caught exception while closing Lucene indexes: " + e.getMessage());
        }
    }

    @Override
    public synchronized void sync() throws DBException {
        //Nothing special to do
        commit();
    }

    @Override
    public void remove() throws DBException {
        close();
        Path dir = getDataDir().resolve(getDirName());
        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(FileUtils::deleteQuietly);
        } catch (Exception e) {
            // never abort at this point, so recovery can continue
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

    public TaxonomyWriter getTaxonomyWriter() {
        return cachedTaxonomyWriter;
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
                LOG.debug("Committing Lucene index");
            }
        	if (cachedWriter != null) {
                cachedTaxonomyWriter.commit();
                cachedWriter.commit();
            }
            needsCommit = false;
        } catch(CorruptIndexException cie) {
            LOG.error("Detected corrupt Lucene index on writer release and commit: {}", cie.getMessage(), cie);
        } catch(IOException ioe) {
            LOG.error("Detected Lucene index issue on writer release and commit: {}", ioe.getMessage(), ioe);
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

    public <R> R withSearcher(final Function2E<SearcherTaxonomyManager.SearcherAndTaxonomy, R, IOException, XPathException> consumer) throws IOException, XPathException {
        searcherManager.maybeRefreshBlocking();
        final SearcherTaxonomyManager.SearcherAndTaxonomy searcher = searcherManager.acquire();
        try {
            return consumer.apply(searcher);
        } finally {
            searcherManager.release(searcher);
        }
    }

	@Override
	public void backupToArchive(final RawDataBackup backup) throws IOException {
		for (final String name : directory.listAll()) {
			final String path = getDirName() + "/" + name;

            // do not use try-with-resources here, closing the OutputStream will close the entire backup
//            try(final OutputStream os = backup.newEntry(path)) {
            try {
                final OutputStream os = backup.newEntry(path);
                Files.copy(getDataDir().resolve(path), os);
            } finally {
                backup.closeEntry();
            }
		}
	}
}
