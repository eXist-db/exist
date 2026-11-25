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
package org.exist.backup;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The sequence of incremental backups that {@link SystemExport#export(String, boolean, int, boolean, java.util.List)}
 * writes: whether a backup is full or incremental, which backup it names as its predecessor and its number in the
 * sequence. A predecessor is a real backup, given an older name, so that it is found as the last one.
 */
public class SystemExportIncrementalTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @TempDir
    Path backups;

    private Path export(final boolean incremental, final int maxInc) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Path backup = new SystemExport(broker, transaction, null, null, false)
                    .export(backups.toAbsolutePath().toString(), incremental, maxInc, false, null);
            transaction.commit();
            assertNotNull(backup);
            return backup;
        }
    }

    private static Properties properties(final Path backup) throws IOException {
        final Properties properties = new Properties();
        try (final InputStream in = Files.newInputStream(backup.resolve(BackupDescriptor.BACKUP_PROPERTIES))) {
            properties.load(in);
        }
        return properties;
    }

    /** What a backup says about itself: full or incremental by its name, then its predecessor, its number and its flag. */
    private static String describe(final Path backup) throws IOException {
        final Properties properties = properties(backup);
        return (backup.getFileName().toString().startsWith("full") ? "full" : "inc")
                + " previous=" + properties.getProperty(BackupDescriptor.PREVIOUS_PROP_NAME, "<none>")
                + " nr=" + properties.getProperty(BackupDescriptor.NUMBER_IN_SEQUENCE_PROP_NAME)
                + " incremental=" + properties.getProperty(BackupDescriptor.INCREMENTAL_PROP_NAME);
    }

    /** A full backup made earlier, with the given number in its sequence (null: as written). */
    private Path predecessor(final String name, final String numberInSequence) throws Exception {
        final Path made = export(false, -1);
        final Path predecessor = Files.move(made, backups.resolve(name));
        if (numberInSequence != null) {
            final Properties properties = properties(predecessor);
            properties.setProperty(BackupDescriptor.NUMBER_IN_SEQUENCE_PROP_NAME, numberInSequence);
            try (final OutputStream out = Files.newOutputStream(predecessor.resolve(BackupDescriptor.BACKUP_PROPERTIES))) {
                properties.store(out, null);
            }
        }
        return predecessor;
    }

    /** Pinned as it is: the backup is named full, and says incremental with an empty predecessor. */
    @Test
    void anIncrementalExportWithoutAPredecessorIsAFullBackupThatSaysIncremental() throws Exception {
        assertEquals("full previous= nr=1 incremental=yes", describe(export(true, 3)));
    }

    @Test
    void anIncrementalExportContinuesTheSequenceOfItsPredecessor() throws Exception {
        predecessor("full20200101-1200", null);
        assertEquals("inc previous=full20200101-1200 nr=2 incremental=yes", describe(export(true, 3)));
    }

    @Test
    void theSequenceEndsAtTheMaximumWithAFullBackupThatStillNamesThePredecessor() throws Exception {
        predecessor("inc20200101-1200", "3");
        assertEquals("full previous=inc20200101-1200 nr=1 incremental=no", describe(export(true, 3)));
    }

    @Test
    void aSequenceBelowTheMaximumGoesOnCounting() throws Exception {
        predecessor("inc20200101-1200", "2");
        assertEquals("inc previous=inc20200101-1200 nr=3 incremental=yes", describe(export(true, 3)));
    }

    @Test
    void withoutAMaximumTheSequenceNeverEnds() throws Exception {
        predecessor("inc20200101-1200", "7");
        assertEquals("inc previous=inc20200101-1200 nr=8 incremental=yes", describe(export(true, -1)));
    }

    @Test
    void aPredecessorWithAnUnreadableNumberRestartsTheCountAtOne() throws Exception {
        predecessor("inc20200101-1200", "not-a-number");
        assertEquals("inc previous=inc20200101-1200 nr=1 incremental=yes", describe(export(true, 3)));
    }

    @Test
    void aFullExportIgnoresThePredecessor() throws Exception {
        predecessor("inc20200101-1200", "2");
        assertEquals("full previous=<none> nr=1 incremental=no", describe(export(false, 3)));
    }
}
