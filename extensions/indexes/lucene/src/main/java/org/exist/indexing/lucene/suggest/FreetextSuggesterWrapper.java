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
package org.exist.indexing.lucene.suggest;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.spell.Dictionary;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.analyzing.FreeTextSuggester;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class FreetextSuggesterWrapper extends Suggester {

    private final FreeTextSuggester suggester;
    private final Path storage;

    public FreetextSuggesterWrapper(String id, String field, Element config, Path indexDir, Analyzer analyzer) throws DatabaseConfigurationException {
        super(id, field, config, indexDir, analyzer);

        suggester = new FreeTextSuggester(analyzer);

        storage = indexDir.resolve("suggest_" + id);
        try {
            if (Files.exists(storage)) {
                if (Files.isDirectory(storage)) {
                    FileUtils.delete(storage);
                } else {
                    suggester.load(Files.newInputStream(storage, StandardOpenOption.READ));
                }
            }
        } catch (IOException e) {
            throw new DatabaseConfigurationException("Error initializing fuzzy suggester: " + e.getMessage(), e);
        }
    }

    @Override
    List<Lookup.LookupResult> lookup(CharSequence key, boolean onlyMorePopular, int num) throws IOException {
        return suggester.lookup(key, true, num);
    }

    @Override
    void build(Dictionary dictionary) throws IOException {
        suggester.build(dictionary);
        suggester.store(Files.newOutputStream(storage, StandardOpenOption.WRITE, StandardOpenOption.CREATE));
    }

    @Override
    void close() {
    }

    @Override
    void remove() throws IOException {
        Files.deleteIfExists(storage);
    }
}
