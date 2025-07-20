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
package org.exist.storage.statistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.backup.RawDataBackup;
import org.exist.dom.QName;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.RawBackupSupport;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.w3c.dom.Element;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Collects statistics on the distribution of elements in the database.
 * This is not really an index, though it sits in the indexing pipeline to
 * gather its statistics.
 *
 * The class maintains a graph structure which describes the frequency
 * and depth of elements in the database (see @link DataGuide). This forms
 * the basis for advanced query optimizations.
 */
public class IndexStatistics extends AbstractIndex implements RawBackupSupport {

    public final static String ID = IndexStatistics.class.getName();

    protected final static Logger LOG = LogManager.getLogger(IndexStatistics.class);

    private Path dataFile;
    private DataGuide dataGuide = new DataGuide();

    public String getIndexId() {
        return ID;
    }

    public int getMaxParentDepth(QName qname) {
        return dataGuide.getMaxParentDepth(qname);
    }

    protected void mergeStats(DataGuide other) {
        dataGuide = other.mergeInto(dataGuide);
    }

    protected void updateStats(DataGuide newGuide) {
        dataGuide = newGuide;
    }

    public void configure(BrokerPool pool, Path dataDir, Element config) throws DatabaseConfigurationException {
        super.configure(pool, dataDir, config);
        String fileName = "stats.dbx";
        if (config.hasAttribute("file")) {
            fileName = config.getAttribute("file");
        }
        dataFile = dataDir.resolve(fileName);
    }

    public void open() throws DatabaseConfigurationException {
        dataGuide = new DataGuide();
        if (Files.exists(dataFile)) {
            final long start = System.currentTimeMillis();
            try(final SeekableByteChannel chan = Files.newByteChannel(dataFile)) {
                dataGuide.read(chan, getBrokerPool().getSymbols());

                if (LOG.isDebugEnabled())
                    {
                        LOG.debug("Reading {} took {}ms. Size of the graph: {}", FileUtils.fileName(dataFile), System.currentTimeMillis() - start, dataGuide.getSize());}
            } catch (final IOException e) {
                LOG.error(e.getMessage(), e);
                throw new DatabaseConfigurationException("Error while loading " +
                    dataFile.toAbsolutePath() + ": " + e.getMessage(), e);
            }
        }
    }

    public void close() throws DBException {
    }

    public void sync() throws DBException {
        try(final SeekableByteChannel chan = Files.newByteChannel(dataFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            dataGuide.write(chan, getBrokerPool().getSymbols());
        } catch (final IOException e) {
            LOG.error(e.getMessage(), e);
            throw new DBException("Error while writing " + dataFile.toAbsolutePath() +
                    ": " + e.getMessage());
        }
    }

    public void remove() throws DBException {
        FileUtils.deleteQuietly(dataFile);
    }

    public IndexWorker getWorker(DBBroker broker) {
        return new IndexStatisticsWorker(this);
    }

    public boolean checkIndex(DBBroker broker) {
        return true;
    }

    public void toSAX(ContentHandler handler) throws SAXException {
        dataGuide.toSAX(handler);
    }

    public String toString() {
        return dataGuide.toString();
    }

	@Override
	public void backupToArchive(RawDataBackup backup) throws IOException {

        try(final OutputStream os = backup.newEntry(FileUtils.fileName(dataFile))) {
            Files.copy(dataFile, os);
        } finally {
            backup.closeEntry();
        }
	}
	
}
