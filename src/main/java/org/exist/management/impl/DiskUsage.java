/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.management.impl;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.storage.journal.Journal;
import org.exist.util.Configuration;
import org.exist.util.FileUtils;
import com.evolvedbinary.j8fu.function.FunctionE;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * Class DiskUsage. Retrieves data from the java File object
 *
 * @author dizzzz@exist-db.org
 */
public class DiskUsage implements DiskUsageMXBean {

    private final static Logger LOG = LogManager.getLogger(DiskUsage.class);

    private final String instanceId;
    private final Optional<Path> journalDir;
    private final Optional<Path> dataDir;

    public DiskUsage(final BrokerPool pool) {
        this.instanceId = pool.getId();

        final Configuration config = pool.getConfiguration();

        this.journalDir = Optional.ofNullable((Path) config.getProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR))
                .filter(Files::isDirectory);

        this.dataDir = Optional.ofNullable((Path) config.getProperty(BrokerPool.PROPERTY_DATA_DIR))
                .filter(Files::isDirectory);
    }

    public static String getAllInstancesQuery() {
        return getName("*");
    }

    private static String getName(final String instanceId) {
        return "org.exist.management." + instanceId + ":type=DiskUsage";
    }

    @Override
    public ObjectName getName() throws MalformedObjectNameException {
        return new ObjectName(getName(instanceId));
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public String getDataDirectory() {
        return dataDir.map(d -> d.toAbsolutePath().toString()).orElse(NOT_CONFIGURED);
    }

    @Override
    public String getJournalDirectory() {
        return journalDir.map(d -> d.toAbsolutePath().toString()).orElse(NOT_CONFIGURED);
    }

    private long measureFileStore(final Optional<Path> path, final FunctionE<FileStore, Long, IOException> measurer) {
        return path
                .map(p -> FileUtils.measureFileStore(p, measurer))
                .orElse(NO_VALUE);
    }

    @Override
    public long getDataDirectoryTotalSpace() {
        return measureFileStore(dataDir, FileStore::getTotalSpace);
    }

    @Override
    public long getDataDirectoryUsableSpace() {
        return measureFileStore(dataDir, FileStore::getUsableSpace);
    }

    @Override
    public long getJournalDirectoryTotalSpace() {
        return measureFileStore(journalDir, FileStore::getTotalSpace);
    }

    @Override
    public long getJournalDirectoryUsableSpace() {
        return measureFileStore(journalDir, FileStore::getUsableSpace);
    }

    @Override
    public long getDataDirectoryUsedSpace() {
        return dataDir.map(d -> {
            try (final Stream<Path> files = Files.list(d)) {
                return files
                        .filter(this::isDbxFile)
                        .mapToLong(p -> {
                            final long size = FileUtils.sizeQuietly(p);
                            return size == NO_VALUE ? 0 : size;
                        })
                        .sum();
            } catch (final IOException ioe) {
                LOG.error(ioe);
                return NO_VALUE;
            }
        }).orElse(NO_VALUE);
    }

    @Override
    public long getJournalDirectoryUsedSpace() {
        return dataDir.map(d -> {
            try (final Stream<Path> files = Files.list(d)) {
                return files
                        .filter(this::isJournalFile)
                        .mapToLong(p -> {
                            final long size = FileUtils.sizeQuietly(p);
                            return size == NO_VALUE ? 0 : size;
                        })
                        .sum();
            } catch (final IOException ioe) {
                LOG.error(ioe);
                return NO_VALUE;
            }
        }).orElse(NO_VALUE);
    }

    @Override
    public long getJournalDirectoryNumberOfFiles() {
        return journalDir.map(j -> {
            try (final Stream<Path> files = Files.list(j)) {
                return files
                        .filter(this::isJournalFile)
                        .count();
            } catch (final IOException ioe) {
                LOG.error(ioe);
                return NO_VALUE;
            }
        }).orElse(NO_VALUE);

    }

    private boolean isJournalFile(final Path path) {
        return FileUtils.fileName(path).endsWith(".log");
    }

    private boolean isDbxFile(final Path path) {
        return FileUtils.fileName(path).endsWith(".dbx");
    }
}
