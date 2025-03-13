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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.AttributeSource.State;
import org.exist.Namespaces;
import org.exist.dom.memtree.MemTreeBuilder;

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
				if (offset.startOffset() > lastOffset)
					builder.characters(content.substring(lastOffset, offset.startOffset()));
				builder.startElement(Namespaces.EXIST_NS, "match", "exist:match", null);
				builder.characters(content.substring(offset.startOffset(), offset.endOffset()));
				builder.endElement();
				lastOffset = offset.endOffset();
			}
			if (lastOffset < content.length())
				builder.characters(content.substring(lastOffset));
		}
	}
	
	public List<Offset> getOffsets(String content, Analyzer analyzer) throws IOException {
		TokenStream tokenStream = analyzer.tokenStream(null, new StringReader(content));
        tokenStream.reset();
        MarkableTokenFilter stream = new MarkableTokenFilter(tokenStream);
        //Token token;
        List<Offset> offsets = null;
        try {
            int lastOffset = 0;
            while (stream.incrementToken()) {
                String text = stream.getAttribute(CharTermAttribute.class).toString();
                Query termQuery = termMap.get(text);
                if (termQuery != null) {
                    // phrase queries need to be handled differently to filter
                    // out wrong matches: only the phrase should be marked, not single
                    // words which may also occur elsewhere in the document
                    if (termQuery instanceof PhraseQuery phraseQuery) {
                        Term[] terms = phraseQuery.getTerms();
                        if (text.equals(terms[0].text())) {
                            // scan the following text and collect tokens to see if
                            // they are part of the phrase
                            stream.mark();
                            int t = 1;
                            List<State> stateList = new ArrayList<>(terms.length);
                            stateList.add(stream.captureState());
                            while (stream.incrementToken() && t < terms.length) {
                                // DW: what does this do
                                text = stream.getAttribute(CharTermAttribute.class).toString();
                                if (text.equals(terms[t].text())) {
                                    stateList.add(stream.captureState());
                                    if (++t == terms.length) {
                                        break;
                                    }
                                } else {
                                    stream.reset();
                                    break;
                                }
                            }
                            if (stateList.size() == terms.length) {
                                if (offsets == null)
                                    offsets = new ArrayList<>();


                                stream.restoreState(stateList.getFirst());
                                int start = stream.getAttribute(OffsetAttribute.class).startOffset();
                                stream.restoreState(stateList.get(terms.length - 1));
                                int end = stream.getAttribute(OffsetAttribute.class).endOffset();
                                offsets.add(new Offset(start, end));

                                //restore state as before
                                stream.restoreState(stateList.getLast());
                            }
                        }
                    } else {
                        if (offsets == null)
                            offsets = new ArrayList<>();

                        OffsetAttribute offsetAttr = stream.getAttribute(OffsetAttribute.class);
                        offsets.add(new Offset(offsetAttr.startOffset(), offsetAttr.endOffset()));
                    }
                }
            }
        } catch (IOException e) {
        	e.printStackTrace();
        } finally {
            stream.close();
        }
        return offsets;
	}
	
	public static class Offset {
		
		protected int startOffset, endOffset;
		
		Offset(int start, int end) {
			this.startOffset = start;
			this.endOffset = end;
		}
		
		public int startOffset() { return startOffset; }
		public int endOffset() { return endOffset; }
	}
}
