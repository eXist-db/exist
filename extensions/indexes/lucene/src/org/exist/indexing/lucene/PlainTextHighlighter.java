package org.exist.indexing.lucene;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
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
        Token token;
        List<Offset> offsets = null;
        try {
        	int lastOffset = 0;
            while ((token = stream.next()) != null) {
                String text = token.term();
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
                            List<Token> tokenList = new ArrayList<Token>(terms.length);
                            tokenList.add(token);
                            while ((token = stream.next()) != null && t < terms.length) {
                                text = token.term();
                                if (text.equals(terms[t].text())) {
                                    tokenList.add(token);
                                    if (++t == terms.length) {
                                        break;
                                    }
                                } else {
                                    stream.reset();
                                    break;
                                }
                            }
                            if (tokenList.size() == terms.length) {
                            	if (offsets == null)
                            		offsets = new ArrayList<Offset>();
                            	int start = tokenList.get(0).startOffset();
                            	int end = tokenList.get(terms.length - 1).endOffset();
                            	offsets.add(new Offset(start, end));
                            }
                        }
                    } else {
                    	if (offsets == null)
                    		offsets = new ArrayList<Offset>();
                    	offsets.add(new Offset(token.startOffset(), token.endOffset()));
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
