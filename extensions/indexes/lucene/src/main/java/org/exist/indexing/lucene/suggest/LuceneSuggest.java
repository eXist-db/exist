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

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.suggest.Lookup;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.analyzers.MetaAnalyzer;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// TODO: make configurable when rebuild is triggered
// TODO: phrases?
public class LuceneSuggest {

    public final static String ELEMENT_SUGGEST = "suggest";
    public final static String ATTR_ID = "id";
    public final static String ATTR_TYPE = "type";

    private enum Suggesters {
        ANALYZING,
        FUZZY,
        FREETEXT
    }

    private Path indexDir;

    private Map<String, Suggester> suggesters = new ConcurrentHashMap<>();
    private LuceneIndex parent;

    public LuceneSuggest(LuceneIndex index, Path indexDir) {
        this.parent = index;
        this.indexDir = indexDir;
    }

    @Nullable
    public List<Lookup.LookupResult> lookup(String id, CharSequence key, boolean onlyMorePopular, int num) throws IOException {
        parent.commit();
        final Suggester config = suggesters.get(id);
        return config == null ? null : config.lookup(key, onlyMorePopular, num);
    }

    public void configure(String field, Element child, Analyzer analyzer) throws DatabaseConfigurationException {
        final String id = child.getAttribute(ATTR_ID);
        if (StringUtils.isEmpty(id)) {
            throw new DatabaseConfigurationException("Child element <suggest> lacks required id attribute");
        }

        final Suggester oldSuggester = suggesters.get(id);
        if (oldSuggester != null) {
            try {
                oldSuggester.remove();
            } catch (IOException e) {
                throw new DatabaseConfigurationException("Failed to stop and delete existing suggester: " + e.getMessage(), e);
            }
        }

        Suggesters type = Suggesters.ANALYZING;
        final String typeAttr = child.getAttribute(ATTR_TYPE);
        if (StringUtils.isNotEmpty(typeAttr)) {
            try {
                type = Suggesters.valueOf(typeAttr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new DatabaseConfigurationException("Unknown suggester type specified: " + typeAttr);
            }
        }

        Analyzer suggestAnalyzer = analyzer == null ? parent.getDefaultAnalyzer() : analyzer;
        if (suggestAnalyzer instanceof MetaAnalyzer) {
            suggestAnalyzer = ((MetaAnalyzer)suggestAnalyzer).getWrappedAnalyzer(field);
        }
        Suggester suggester;
        switch (type) {
            case FUZZY:
                suggester = new FuzzySuggesterWrapper(id, field, child, indexDir, suggestAnalyzer);
                break;
            case FREETEXT:
                suggester = new FreetextSuggesterWrapper(id, field, child, indexDir, suggestAnalyzer);
                break;
            default:
                suggester = new AnalyzingInfixSuggesterWrapper(id, field, child, indexDir, suggestAnalyzer);
                break;
        }
        suggesters.put(id, suggester);
    }

    public void rebuild() throws IOException {
        for (Suggester entry : suggesters.values()) {
            parent.withReader((reader) -> {
                entry.build(reader, entry.getField());
                return null;
            });
        }
    }

    public void close() {
        for (Suggester entry : suggesters.values()) {
            try {
                entry.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
