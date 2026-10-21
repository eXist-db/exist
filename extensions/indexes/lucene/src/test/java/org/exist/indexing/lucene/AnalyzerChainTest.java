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
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.exist.indexing.lucene.analyzers.IndexQueryAnalyzer;
import org.exist.indexing.lucene.analyzers.MetaAnalyzer;
import org.exist.util.DatabaseConfigurationException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyzerChainTest {

    private static final String NS = "xmlns=\"http://exist-db.org/collection-config/1.0\"";

    @Test
    void chainOfComponents() throws Exception {
        final Analyzer analyzer = configure("""
                <analyzer %s id="cased">
                    <tokenizer name="standard"/>
                    <filter name="englishPossessive"/>
                    <filter name="asciiFolding"/>
                </analyzer>""");
        assertInstanceOf(CustomAnalyzer.class, analyzer);
        assertEquals(List.of("GOA", "Banquo", "Cafe"), terms(analyzer, "GOA Banquo’s Café"));
    }

    @Test
    void attributesAreArguments() throws Exception {
        final Analyzer analyzer = configure("""
                <analyzer %s id="literal">
                    <tokenizer name="whitespace"/>
                    <filter name="patternReplace" pattern="^\\p{Punct}+|\\p{Punct}+$" replacement=""/>
                </analyzer>""");
        assertEquals(List.of("Goa", "F-16", "S/S"), terms(analyzer, "\"Goa,\" F-16 S/S."));
    }

    @Test
    void charFilters() throws Exception {
        final Analyzer analyzer = configure("""
                <analyzer %s id="stripped">
                    <char-filter name="htmlStrip"/>
                    <tokenizer name="whitespace"/>
                </analyzer>""");
        assertEquals(List.of("bold", "text"), terms(analyzer, "<b>bold</b> text"));
    }

    @Test
    void indexAndQueryChains() throws Exception {
        final Analyzer analyzer = configure("""
                <analyzer %s id="split">
                    <index>
                        <tokenizer name="standard"/>
                        <filter name="lowercase"/>
                    </index>
                    <query>
                        <tokenizer name="standard"/>
                    </query>
                </analyzer>""");
        final IndexQueryAnalyzer indexQuery = assertInstanceOf(IndexQueryAnalyzer.class, analyzer);
        assertEquals(List.of("goa"), terms(analyzer, "Goa"));
        assertEquals(List.of("Goa"), terms(IndexQueryAnalyzer.forQuery(analyzer), "Goa"));
        assertSame(indexQuery.getQueryAnalyzer(), IndexQueryAnalyzer.forQuery(analyzer));
    }

    @Test
    void queryChainsOfFields() throws Exception {
        final Analyzer split = configure("""
                <analyzer %s id="split">
                    <index>
                        <tokenizer name="standard"/>
                        <filter name="lowercase"/>
                    </index>
                    <query>
                        <tokenizer name="standard"/>
                    </query>
                </analyzer>""");
        final Analyzer plain = configure("""
                <analyzer %s id="plain">
                    <tokenizer name="whitespace"/>
                </analyzer>""");
        final MetaAnalyzer meta = new MetaAnalyzer(plain);
        meta.addAnalyzer("split", split);
        final Analyzer query = IndexQueryAnalyzer.forQuery(meta);
        assertEquals(List.of("Goa"), terms(query, "split", "Goa"));
        assertEquals(List.of("goa"), terms(meta, "split", "Goa"));
        assertSame(plain, ((MetaAnalyzer) query).analyzerFor("other"));
        assertSame(query, IndexQueryAnalyzer.forQuery(meta));
    }

    @Test
    void withoutQueryChainsTheSameAnalyzer() throws Exception {
        final MetaAnalyzer meta = new MetaAnalyzer(configure("""
                <analyzer %s id="plain">
                    <tokenizer name="whitespace"/>
                </analyzer>"""));
        assertSame(meta, IndexQueryAnalyzer.forQuery(meta));
    }

    @Test
    void classAnalyzerUnchanged() throws Exception {
        final Analyzer analyzer = configure("""
                <analyzer %s class="org.apache.lucene.analysis.core.WhitespaceAnalyzer"/>""");
        assertEquals(List.of("Goa,", "GOA"), terms(analyzer, "Goa, GOA"));
    }

    @Test
    void unknownFilter() {
        assertError("<filter name=\"noSuchFilter\">", """
                <analyzer %s id="bad">
                    <tokenizer name="standard"/>
                    <filter name="noSuchFilter"/>
                </analyzer>""");
    }

    @Test
    void unknownTokenizer() {
        assertError("<tokenizer name=\"noSuchTokenizer\">", """
                <analyzer %s id="bad">
                    <tokenizer name="noSuchTokenizer"/>
                </analyzer>""");
    }

    @Test
    void unknownArgument() {
        assertError("Unknown parameters: {bogus=1}", """
                <analyzer %s id="bad">
                    <tokenizer name="standard"/>
                    <filter name="lowercase" bogus="1"/>
                </analyzer>""");
    }

    @Test
    void missingTokenizer() {
        assertError("Lucene analyzer 'bad' needs a <tokenizer>", """
                <analyzer %s id="bad">
                    <char-filter name="htmlStrip"/>
                </analyzer>""");
    }

    @Test
    void filterBeforeTokenizer() {
        assertError("<filter name=\"lowercase\"> must come after the tokenizer", """
                <analyzer %s id="bad">
                    <filter name="lowercase"/>
                    <tokenizer name="standard"/>
                </analyzer>""");
    }

    @Test
    void twoTokenizers() {
        assertError("has more than one tokenizer", """
                <analyzer %s id="bad">
                    <tokenizer name="standard"/>
                    <tokenizer name="whitespace"/>
                </analyzer>""");
    }

    @Test
    void componentWithoutName() {
        assertError("<filter> needs a name", """
                <analyzer %s id="bad">
                    <tokenizer name="standard"/>
                    <filter/>
                </analyzer>""");
    }

    @Test
    void indexChainWithoutQueryChain() {
        assertError("needs both an <index> and a <query> chain", """
                <analyzer %s id="bad">
                    <index>
                        <tokenizer name="standard"/>
                    </index>
                </analyzer>""");
    }

    @Test
    void componentsOutsideIndexAndQueryChains() {
        assertError("<filter> must be inside its <index> or <query> chain", """
                <analyzer %s id="bad">
                    <index>
                        <tokenizer name="standard"/>
                    </index>
                    <query>
                        <tokenizer name="standard"/>
                    </query>
                    <filter name="lowercase"/>
                </analyzer>""");
    }

    @Test
    void classAndChain() {
        assertError("has a class and a chain of components", """
                <analyzer %s class="org.apache.lucene.analysis.core.WhitespaceAnalyzer">
                    <tokenizer name="standard"/>
                </analyzer>""");
    }

    private static void assertError(final String expected, final String config) {
        final DatabaseConfigurationException e = assertThrows(DatabaseConfigurationException.class,
                () -> configure(config));
        assertTrue(e.getMessage().contains(expected), () -> "Expected \"" + expected + "\" in: " + e.getMessage());
    }

    private static Analyzer configure(final String config) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final Element element = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(config.formatted(NS)))).getDocumentElement();
        return AnalyzerConfig.configureAnalyzer(element);
    }

    private static List<String> terms(final Analyzer analyzer, final String text) throws IOException {
        return terms(analyzer, "f", text);
    }

    private static List<String> terms(final Analyzer analyzer, final String field, final String text)
            throws IOException {
        final List<String> terms = new ArrayList<>();
        try (TokenStream stream = analyzer.tokenStream(field, text)) {
            final CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                terms.add(term.toString());
            }
            stream.end();
        }
        return terms;
    }
}
