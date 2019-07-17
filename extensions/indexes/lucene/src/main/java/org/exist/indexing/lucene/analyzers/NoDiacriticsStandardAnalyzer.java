/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
package org.exist.indexing.lucene.analyzers;

import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.icu.*;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.StopwordAnalyzerBase;
import org.apache.lucene.analysis.util.WordlistLoader;
import org.apache.lucene.util.Version;
import org.exist.indexing.lucene.LuceneIndex;

import java.io.IOException;
import java.io.Reader;

/**
 * A copy of StandardAnalyzer using an additional ASCIIFoldingFilter to
 * strip diacritics.
 */
public class NoDiacriticsStandardAnalyzer extends StopwordAnalyzerBase {

    /**
     * Default maximum allowed token length
     */
    public static final int DEFAULT_MAX_TOKEN_LENGTH = 255;

    private int maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH;

    /**
     * Specifies whether deprecated acronyms should be replaced with HOST type.
     * See {@linkplain "https://issues.apache.org/jira/browse/LUCENE-1068"}
     */
    private final boolean replaceInvalidAcronym;

    /**
     * An unmodifiable set containing some common English words that are usually not
     * useful for searching.
     */
    public static final CharArraySet STOP_WORDS_SET = StopAnalyzer.ENGLISH_STOP_WORDS_SET;

    /**
     * Builds an analyzer with the given stop words.
     *
     * @param stopWords stop words
     */
    public NoDiacriticsStandardAnalyzer(final CharArraySet stopWords) {
        super(stopWords);
        replaceInvalidAcronym = true;
    }

    /**
     * Builds an analyzer with the given stop words.
     *
     * @param matchVersion Lucene version to match, see {@link StopwordAnalyzerBase#version}
     * @param stopWords stop words
     *
     * @deprecated Use {@link #NoDiacriticsStandardAnalyzer(CharArraySet)}
     */
    @Deprecated
    public NoDiacriticsStandardAnalyzer(final Version matchVersion, final CharArraySet stopWords) {
        super(matchVersion, stopWords);
        replaceInvalidAcronym = matchVersion.onOrAfter(LuceneIndex.LUCENE_VERSION_IN_USE);
    }

     /**
      * Builds an analyzer with the default stop words. ({@link #STOP_WORDS_SET}).
      */
    protected NoDiacriticsStandardAnalyzer() {
        this((CharArraySet)null);
    }

    /**
     * Builds an analyzer with the default stop words ({@link #STOP_WORDS_SET}).
     *
     * @param matchVersion Lucene version to match See {@link StopwordAnalyzerBase#version}.
     *
     * @deprecated Use {@link #NoDiacriticsStandardAnalyzer()}
     */
    @Deprecated
    public NoDiacriticsStandardAnalyzer(final Version matchVersion) {
        this(matchVersion, STOP_WORDS_SET);
    }

    /**
     * Builds an analyzer with the stop words from the given reader.
     *
     * See {@link WordlistLoader#getWordSet(Reader, Version)}
     *
     * @param matchVersion Lucene version to match See {@link StopwordAnalyzerBase#version}.
     * @param stopwords Reader to read stop words from
     *
     * @throws IOException if an I/O error occurs
     */
    public NoDiacriticsStandardAnalyzer(Version matchVersion, Reader stopwords) throws IOException {
        this(matchVersion, WordlistLoader.getWordSet(stopwords, matchVersion));
    }

    /**
     * Set maximum allowed token length.  If a token is seen
     * that exceeds this length then it is discarded.  This
     * setting only takes effect the next time tokenStream or
     * reusableTokenStream is called.
     *
     * @param length the max token length.
     */
    public void setMaxTokenLength(int length) {
        maxTokenLength = length;
    }

    /**
     * Get the maximum allowed token depth.
     *
     * @return the maximum allowed token depth.
     */
    public int getMaxTokenLength() {
        return maxTokenLength;
    }

    @Override
    protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
        final StandardTokenizer src = new StandardTokenizer(getVersion(), reader);
        src.setMaxTokenLength(maxTokenLength);
//        src.setReplaceInvalidAcronym(replaceInvalidAcronym);
        TokenStream tok = new StandardFilter(getVersion(), src);
        tok = new ICUFoldingFilter(tok);
        tok = new LowerCaseFilter(getVersion(), tok);
        tok = new StopFilter(getVersion(), tok, stopwords);
        return new TokenStreamComponents(src, tok);
//        {
//            @Override
//            protected boolean reset(final Reader reader) throws IOException {
//                src.setMaxTokenLength(NoDiacriticsStandardAnalyzer.this.maxTokenLength);
//                return super.reset(reader);
//            }
//        };
    }
}
