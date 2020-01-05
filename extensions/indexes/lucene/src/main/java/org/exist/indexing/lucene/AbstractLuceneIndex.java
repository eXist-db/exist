/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2019 The eXist Project
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

import com.evolvedbinary.j8fu.function.Function2E;
import com.evolvedbinary.j8fu.function.FunctionE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.*;
import org.apache.lucene.search.ReferenceManager;
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

/**
 * Abstract base class for all lucene-based indexes.
 *
 * @author wolfgang
 * @param <S> Class of the searcher manager to use. Implementations may need different searcher managers, depending
 *           on e.g. if taxonomies are used or not.
 */
public abstract class AbstractLuceneIndex<S> extends AbstractIndex implements RawBackupSupport {

    public final static Version LUCENE_VERSION_IN_USE = Version.LUCENE_4_10_4;

    private static final Logger LOG = LogManager.getLogger(AbstractLuceneIndex.class);

    public final static String ID = LuceneIndex.class.getName();

    private static final String DIR_NAME = "lucene";

    protected Directory directory;

    protected Analyzer defaultAnalyzer;

    protected double bufferSize = IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB;

    protected IndexWriter cachedWriter = null;

    protected ReferenceManager<S> searcherManager = null;
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

    /**
     * Get an {@link IndexWorker} to be owned by the specified broker.
     *
     * @param broker The DBBroker that owns this worker
     * @return a new index worker
     */
    @Override
    public abstract IndexWorker getWorker(DBBroker broker);

    /**
     * Called during {@link #open()} to create the actual searcher manager.
     *
     * @param writer the writer created
     * @return a searcher manager
     * @throws DatabaseConfigurationException in case the searcher could not be created
     */
    public abstract ReferenceManager<S> createSearcherManager(IndexWriter writer) throws DatabaseConfigurationException;

    /**
     * Called before the writer is committed. Can be overwritten by subclasses to
     * commit additional resources.
     *
     * @throws IOException in case writing fails
     */
    protected void beforeCommit() throws IOException {
        // do nothing by default
    }

    /**
     * Called after the writer is committed. Can be overwritten by subclasses to
     * perform additional actions, e.g. update suggestions.
     *
     * @throws IOException in case writing fails
     */
    protected void afterCommit() throws IOException {
        // do nothing by default
    }

    /**
     * Called by {@link #close()} before the writer is closed. Can be overwritten
     * by subclasses to properly close other resources first.
     * @throws IOException
     */
    protected void beforeWriterClose() throws IOException {
        // do nothing by default
    }

    @Override
    public void open() throws DatabaseConfigurationException {
        Path dir = getDataDir().resolve(getDirName());
        if (LOG.isDebugEnabled())
            LOG.debug("Opening Lucene index directory: " + dir.toAbsolutePath().toString());

        IndexWriter writer = null;
        try {
            if (Files.exists(dir)) {
                if (!Files.isDirectory(dir))
                    throw new DatabaseConfigurationException("Lucene index location is not a directory: " +
                            dir.toAbsolutePath().toString());
            } else {
                Files.createDirectories(dir);
            }

            directory = FSDirectory.open(dir.toFile());

            final IndexWriterConfig idxWriterConfig = new IndexWriterConfig(LUCENE_VERSION_IN_USE, defaultAnalyzer);
            idxWriterConfig.setRAMBufferSizeMB(bufferSize);
            cachedWriter = new IndexWriter(directory, idxWriterConfig);

            searcherManager = createSearcherManager(cachedWriter);
            readerManager = new ReaderManager(cachedWriter, true);
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
                beforeWriterClose();
                cachedWriter.close();
                cachedWriter = null;
            }
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
        close();
        Path dir = getDataDir().resolve(getDirName());
        try {
            Files.list(dir).forEach(FileUtils::deleteQuietly);
        } catch (Exception e) {
            // never abort at this point, so recovery can continue
            LOG.warn(e.getMessage(), e);
        }
    }

    @Override
    public boolean checkIndex(DBBroker broker) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Analyzer getDefaultAnalyzer() {
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

    public void commit() {
        if (!needsCommit) {
            return;
        }
        try {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Committing lucene index");
            }
            beforeCommit();
            if (cachedWriter != null) {
                cachedWriter.commit();
            }
            afterCommit();
            needsCommit = false;
        } catch(CorruptIndexException cie) {
            LOG.error("Detected corrupt Lucence index on writer release and commit: " + cie.getMessage(), cie);
        } catch(IOException ioe) {
            LOG.error("Detected Lucence index issue on writer release and commit: " + ioe.getMessage(), ioe);
        }
    }

    public <R> R withReader(FunctionE<IndexReader, R, IOException> fn) throws IOException {
        if (readerManager == null) {
            return null;
        }
        readerManager.maybeRefreshBlocking();
        final DirectoryReader reader = readerManager.acquire();
        try {
            return fn.apply(reader);
        } finally {
            readerManager.release(reader);
        }
    }

    public <R> R withSearcher(Function2E<S, R, IOException, XPathException> consumer) throws IOException, XPathException {
        searcherManager.maybeRefreshBlocking();
        final S searcher = searcherManager.acquire();
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
