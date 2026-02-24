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
package org.exist.indexing.lucene.analyzers;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.StopwordAnalyzerBase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * StandardAnalyzer with English stopwords.
 * Lucene 8+ removed stopwords from StandardAnalyzer; this restores the
 * pre-Lucene-8 behaviour for backward compatibility.
 */
public class EnglishStopwordsStandardAnalyzer extends StopwordAnalyzerBase {

    public static final CharArraySet STOP_WORDS_SET = org.apache.lucene.analysis.en.EnglishAnalyzer.ENGLISH_STOP_WORDS_SET;

    private static final int DEFAULT_MAX_TOKEN_LENGTH = 255;
    private int maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH;

    public EnglishStopwordsStandardAnalyzer() {
        super(STOP_WORDS_SET);
    }

    public EnglishStopwordsStandardAnalyzer(final CharArraySet stopWords) {
        super(stopWords);
    }

    @Override
    protected TokenStreamComponents createComponents(final String fieldName) {
        final StandardTokenizer src = new StandardTokenizer();
        src.setMaxTokenLength(maxTokenLength);
        TokenStream tok = new LowerCaseFilter(src);
        tok = new StopFilter(tok, stopwords);
        return new Analyzer.TokenStreamComponents(src, tok);
    }
}
