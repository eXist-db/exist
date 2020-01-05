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

import org.apache.lucene.facet.taxonomy.SearcherTaxonomyManager;
import org.apache.lucene.facet.taxonomy.TaxonomyWriter;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.lucene.suggest.LuceneSuggest;
import org.exist.storage.DBBroker;
import org.exist.util.DatabaseConfigurationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LuceneIndex extends AbstractLuceneIndex<SearcherTaxonomyManager.SearcherAndTaxonomy> {

    public final static String ID = LuceneIndex.class.getName();

	private static final String DIR_NAME = "lucene";
	private static final String TAXONOMY_DIR_NAME = "taxonomy";

    protected Directory taxoDirectory;

    protected DirectoryTaxonomyWriter cachedTaxonomyWriter = null;

    protected LuceneSuggest suggestService;

    public String getDirName() {
        return DIR_NAME;
    }

    @Override
    public ReferenceManager<SearcherTaxonomyManager.SearcherAndTaxonomy> createSearcherManager(IndexWriter writer) throws DatabaseConfigurationException {
        Path dir = getDataDir().resolve(getDirName());
        Path taxoDir = dir.resolve(TAXONOMY_DIR_NAME);
        try {
            if (Files.exists(taxoDir)) {
                if (!Files.isDirectory(taxoDir))
                    throw new DatabaseConfigurationException("Lucene index location for taxonomy is not a directory: " +
                            taxoDir.toAbsolutePath());
            } else {
                Files.createDirectories(taxoDir);
            }
            taxoDirectory = FSDirectory.open(taxoDir.toFile());
            cachedTaxonomyWriter = new DirectoryTaxonomyWriter(taxoDirectory);
            suggestService = new LuceneSuggest(this, dir);
            return new SearcherTaxonomyManager(writer, true, null, cachedTaxonomyWriter);
        } catch (IOException e) {
            throw new DatabaseConfigurationException("IO error while creating searcher manager: " + e.getMessage(), e);
        }
    }

    @Override
    public IndexWorker getWorker(DBBroker broker) {
        return new LuceneIndexWorker(this, broker);
    }

    public TaxonomyWriter getTaxonomyWriter() {
        return cachedTaxonomyWriter;
    }

    @Override
    protected void beforeWriterClose() throws IOException {
        cachedTaxonomyWriter.close();
        cachedTaxonomyWriter = null;
        suggestService.close();
    }

    @Override
    protected void beforeCommit() throws IOException {
        if (cachedTaxonomyWriter != null) {
            cachedTaxonomyWriter.commit();
        }
    }

    @Override
    protected void afterCommit() throws IOException {
        suggestService.rebuild();
    }
}
