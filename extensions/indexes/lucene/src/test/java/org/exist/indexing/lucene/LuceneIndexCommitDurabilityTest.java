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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexDeletionPolicy;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.exist.storage.btree.DBException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A write that is released while {@code commit()} is running must not be forgotten: it is not part of the
 * commit that was already in progress, so the index has to stay marked as needing a commit. Otherwise the
 * following {@code sync()} (or any later query) has nothing to do and the write is only in memory; an
 * unclean stop then loses it from the Lucene index while the database keeps the document.
 *
 * <p>The interleaving is made deterministic with a deletion policy: its {@code onCommit} callback runs inside
 * {@code IndexWriter.commit()}, after the commit point was written and before {@code LuceneIndex.commit()}
 * clears {@code needsCommit}, which is exactly where another thread's write would land.</p>
 */
public class LuceneIndexCommitDurabilityTest {

    @Test
    @Timeout(60)
    public void writeReleasedDuringCommitIsDurableAfterTheNextSync() throws IOException, DBException {
        try (final Directory directory = new ByteBuffersDirectory();
                final Directory taxonomyDirectory = new ByteBuffersDirectory()) {
            final LuceneIndex index = new LuceneIndex();
            final AtomicBoolean writeDuringCommit = new AtomicBoolean(false);
            final IndexWriter[] writerHolder = new IndexWriter[1];

            final IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
            config.setIndexDeletionPolicy(new IndexDeletionPolicy() {
                @Override
                public void onInit(final List<? extends IndexCommit> commits) {
                    deleteAllButLast(commits);
                }

                @Override
                public void onCommit(final List<? extends IndexCommit> commits) throws IOException {
                    deleteAllButLast(commits);
                    if (writeDuringCommit.getAndSet(false)) {
                        // another thread adds a document and releases the writer while this commit is running
                        writerHolder[0].addDocument(document("written-during-commit"));
                        index.releaseWriter(writerHolder[0]);
                    }
                }
            });

            final IndexWriter writer = new IndexWriter(directory, config);
            writerHolder[0] = writer;
            index.directory = directory;
            index.cachedWriter = writer;
            index.cachedTaxonomyWriter = new DirectoryTaxonomyWriter(taxonomyDirectory);

            writer.addDocument(document("written-before-commit"));
            index.releaseWriter(writer);

            writeDuringCommit.set(true);
            index.commit();
            index.sync();

            try (final DirectoryReader reader = DirectoryReader.open(directory)) {
                assertEquals(2, reader.numDocs(),
                        "the document released while the commit was running is not in the last commit");
            } finally {
                index.cachedTaxonomyWriter.close();
                writer.close();
            }
        }
    }

    private static void deleteAllButLast(final List<? extends IndexCommit> commits) {
        for (int i = 0; i < commits.size() - 1; i++) {
            commits.get(i).delete();
        }
    }

    private static Document document(final String id) {
        final Document document = new Document();
        document.add(new StringField("id", id, Field.Store.YES));
        return document;
    }
}
