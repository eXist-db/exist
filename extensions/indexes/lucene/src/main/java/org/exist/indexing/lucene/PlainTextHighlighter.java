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
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.AttributeSource.State;
import org.exist.Namespaces;
import org.exist.dom.memtree.MemTreeBuilder;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PlainTextHighlighter {

    private final Map<Object, Query> termMap = new TreeMap<>();

    public PlainTextHighlighter(Query query, IndexReader reader) throws IOException {
        LuceneUtil.extractTerms(query, termMap, reader, false);
    }

    public void highlight(String content, List<Offset> offsets, MemTreeBuilder builder) {
        if (offsets == null || offsets.isEmpty()) {
            builder.characters(content);
        } else {
            int lastOffset = 0;
            for (Offset offset : offsets) {
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

    public List<Offset> getOffsets(String content, Analyzer analyzer) throws IOException {
        List<Offset> offsets = null;
        try (TokenStream tokenStream = analyzer.tokenStream(null, new StringReader(content));
             MarkableTokenFilter stream = new MarkableTokenFilter(tokenStream)) {
            stream.reset();
            while (stream.incrementToken()) {
                String text = stream.getAttribute(CharTermAttribute.class).toString();
                final Query termQuery = termMap.get(text);
                if (termQuery != null) {
                    // Phrase and near/proximity queries need special handling to avoid marking
                    // partial matches; both forms are matched the same slop-aware way so that
                    // string ('"a b"~n') and XML (<near slop="n">) queries highlight identically,
                    // see #833.
                    final LuceneUtil.ProximityTerms proximity = LuceneUtil.asProximityTerms(termQuery);
                    if (proximity != null) {
                        final int firstMatchedIndex = proximity.inOrder()
                                ? (text.equals(proximity.terms().getFirst()) ? 0 : -1)
                                : proximity.terms().indexOf(text);
                        if (firstMatchedIndex >= 0) {
                            // Cache lookahead tokens so a failed attempt can be replayed to the
                            // outer scan instead of losing those tokens (matches original phrase
                            // handling here, unlike LuceneMatchListener's simpler best-effort scan).
                            stream.mark();
                            final List<State> stateList = LuceneUtil.matchProximityWindow(stream, proximity, firstMatchedIndex);
                            if (stateList != null) {
                                if (offsets == null) {
                                    offsets = new ArrayList<>();
                                }
                                stream.restoreState(stateList.getFirst());
                                final int start = stream.getAttribute(OffsetAttribute.class).startOffset();
                                stream.restoreState(stateList.getLast());
                                final int end = stream.getAttribute(OffsetAttribute.class).endOffset();
                                offsets.add(new Offset(start, end));
                            } else {
                                stream.rewindToMark();
                            }
                        }
                    } else {
                        if (offsets == null) {
                            offsets = new ArrayList<>();
                        }
                        final OffsetAttribute offsetAttr = stream.getAttribute(OffsetAttribute.class);
                        offsets.add(new Offset(offsetAttr.startOffset(), offsetAttr.endOffset()));
                    }
                }
            }
        }
        return offsets;
    }

    public static class Offset {

        protected int startOffset;
        protected int endOffset;

        Offset(int start, int end) {
            this.startOffset = start;
            this.endOffset = end;
        }

        public int startOffset() { return startOffset; }
        public int endOffset() { return endOffset; }
    }
}
