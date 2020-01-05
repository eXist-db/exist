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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.spell.Dictionary;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.suggest.DocumentDictionary;
import org.apache.lucene.search.suggest.Lookup;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public abstract class Suggester {

    public final String USE_ATTR = "use";
    public final String USE_STORED = "stored";

    private final String field;
    private final String id;
    private boolean entireContent = false;

    public Suggester(String id, String field, Element config, Path indexDir, Analyzer analyzer) {
        this.id = id;
        this.field = field;

        final String useOpt = config.getAttribute(USE_ATTR);
        if (StringUtils.isNotEmpty(useOpt)) {
            entireContent = useOpt.equalsIgnoreCase(USE_STORED);
        }
    }

    String getField() {
        return field;
    }

    Dictionary getDictionary(IndexReader reader, String field) {
        if (entireContent) {
            return new DocumentDictionary(reader, field, null);
        }
        return new LuceneDictionary(reader, field);
    }

    abstract List<Lookup.LookupResult> lookup(CharSequence key, boolean onlyMorePopular, int num) throws IOException;

    abstract void build(IndexReader reader, String field) throws IOException;

    abstract void close() throws IOException;

    abstract void remove() throws IOException;
}
