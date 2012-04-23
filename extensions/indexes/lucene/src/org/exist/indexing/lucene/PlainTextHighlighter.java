package org.exist.indexing.lucene;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
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
import org.exist.memtree.MemTreeBuilder;

public class PlainTextHighlighter {

	private TreeMap<Object, Query> termMap;
	
	public PlainTextHighlighter(Query query, IndexReader reader) throws IOException {
		this.termMap = new TreeMap<Object, Query>();
		LuceneUtil.extractTerms(query, termMap, reader, false);
	}
	
	public void highlight(String content, List<Offset> offsets, MemTreeBuilder builder) {
		if (offsets == null || offsets.size() == 0) {
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
                    if (termQuery instanceof PhraseQuery) {
                        PhraseQuery phraseQuery = (PhraseQuery) termQuery;
                        Term[] terms = phraseQuery.getTerms();
                        if (text.equals(terms[0].text())) {
                            // scan the following text and collect tokens to see if
                            // they are part of the phrase
                            stream.mark();
                            int t = 1;
                            List<State> stateList = new ArrayList<State>(terms.length);
                            stateList.add(stream.captureState());
                            while(stream.incrementToken() && t < terms.length) {
                                text = text = stream.getAttribute(CharTermAttribute.class).toString();
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
                            		offsets = new ArrayList<Offset>();
                            	
                                
                                
                                stream.restoreState(stateList.get(0));
                                int start = stream.getAttribute(OffsetAttribute.class).startOffset();
                                stream.restoreState(stateList.get(terms.length - 1));
                            	int end = stream.getAttribute(OffsetAttribute.class).endOffset();
                            	offsets.add(new Offset(start, end));
                                
                                //restore state as before
                                stream.restoreState(stateList.get(stateList.size() -1));
                            }
                        }
                    } else {
                    	if (offsets == null)
                    		offsets = new ArrayList<Offset>();
                        
                        OffsetAttribute offsetAttr = stream.getAttribute(OffsetAttribute.class);
                        offsets.add(new Offset(offsetAttr.startOffset(), offsetAttr.endOffset()));
                    }
                }
            }
        } catch (IOException e) {
        	e.printStackTrace();
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
