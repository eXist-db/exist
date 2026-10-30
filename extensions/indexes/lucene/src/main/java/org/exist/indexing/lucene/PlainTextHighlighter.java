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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.exist.Namespaces;
import org.exist.dom.memtree.MemTreeBuilder;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Highlights the matches of a query in a stored field's text, as {@link MatchLocator} locates them.
 */
public class PlainTextHighlighter {

    private final MatchLocator locator;

    public PlainTextHighlighter(final Query query) {
        this.locator = new MatchLocator(List.of(query));
    }

    public void highlight(final String content, final List<Offset> offsets, final MemTreeBuilder builder) {
        if (offsets == null || offsets.isEmpty()) {
            builder.characters(content);
        } else {
            int lastOffset = 0;
            for (final Offset offset : offsets) {
                if (offset.startOffset() > lastOffset) {
                    builder.characters(content.substring(lastOffset, offset.startOffset()));
                }
                builder.startElement(Namespaces.EXIST_NS, "match", "exist:match", null);
                builder.characters(content.substring(offset.startOffset(), offset.endOffset()));
                builder.endElement();
                lastOffset = offset.endOffset();
            }
            if (lastOffset < content.length()) {
                builder.characters(content.substring(lastOffset));
            }
        }
    }

    /**
     * @param field the field the content is stored in
     * @param content the field's content
     * @param analyzer the analyzer the field was indexed with
     * @return the matches of the query in the content, in its order, or null if there are none
     * @throws IOException if the content cannot be analyzed
     */
    public @Nullable List<Offset> getOffsets(final String field, final String content, final Analyzer analyzer) throws IOException {
        final List<MatchLocator.Span> spans = locator.locate(Set.of(field), content, analyzer);
        return spans.isEmpty() ? null : spans.stream().map(span -> new Offset(span.start(), span.end())).toList();
    }

    public static class Offset {
        protected int startOffset;
        protected int endOffset;

        Offset(final int start, final int end) {
            this.startOffset = start;
            this.endOffset = end;
        }

        public int startOffset() { return startOffset; }

        public int endOffset() { return endOffset; }
    }
}
