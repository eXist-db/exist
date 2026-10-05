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
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The analyzer of the index writer must be the one the thread that adds a document asked for, whatever other
 * threads register for the same field name while the document is being added: the index is shared by all
 * collections, and every collection that indexes {@code p} elements writes into the same field.
 * <p>
 * The text "Foo Bar" is told apart by its analyzer: the standard analyzer gives "foo" and "bar", the keyword
 * analyzer the whole text.
 */
public class FieldAnalyzerWrapperTest {

    private static final String TEXT = "Foo Bar";
    private static final List<String> STANDARD = List.of("foo", "bar");
    private static final List<String> KEYWORD = List.of("Foo Bar");

    private final Analyzer standard = new StandardAnalyzer();
    private final Analyzer keyword = new KeywordAnalyzer();

    private static List<String> tokens(final Analyzer analyzer, final String field) throws IOException {
        final List<String> tokens = new ArrayList<>();
        try (final TokenStream stream = analyzer.tokenStream(field, TEXT)) {
            final CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                tokens.add(term.toString());
            }
            stream.end();
        }
        return tokens;
    }

    @Test
    public void defaultAnalyzerIsUsedForAFieldWithoutOne() throws IOException {
        assertEquals(STANDARD, tokens(new FieldAnalyzerWrapper(standard), "p"));
    }

    @Test
    public void registeredAnalyzerIsUsedWhenThreadGaveNone() throws IOException {
        final FieldAnalyzerWrapper wrapper = new FieldAnalyzerWrapper(standard);
        wrapper.addAnalyzer("p", keyword);
        assertEquals(KEYWORD, tokens(wrapper, "p"));
        assertEquals(STANDARD, tokens(wrapper, "other"));
    }

    @Test
    public void analyzerOfThreadWinsOverRegisteredAnalyzer() throws IOException {
        final FieldAnalyzerWrapper wrapper = new FieldAnalyzerWrapper(standard);
        wrapper.addAnalyzer("p", keyword);

        final List<List<String>> inside = new ArrayList<>();
        wrapper.withAnalyzers(Map.of("p", standard), () -> inside.add(tokens(wrapper, "p")));

        assertEquals(List.of(STANDARD), inside);
    }

    /**
     * What went wrong: the analyzer was put into the shared map, and by the time the writer got to analyze the
     * document (it may wait for a flush that another thread started) another thread had put its own there.
     */
    @Test
    public void analyzerOfThreadSurvivesAnotherThreadRegisteringOneForTheSameField() throws Exception {
        final FieldAnalyzerWrapper wrapper = new FieldAnalyzerWrapper(standard);
        wrapper.addAnalyzer("p", standard);

        final List<List<String>> inside = new ArrayList<>();
        wrapper.withAnalyzers(Map.of("p", standard), () -> {
            // another thread, which indexes into another collection, registers its analyzer for the same field
            final Thread other = new Thread(() -> wrapper.addAnalyzer("p", keyword));
            other.start();
            try {
                other.join();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            inside.add(tokens(wrapper, "p"));
        });

        assertEquals(List.of(STANDARD), inside, "the document is analyzed with the analyzer of the thread that adds it");
        assertEquals(KEYWORD, tokens(wrapper, "p"), "outside of the call the registered analyzer is the one the other thread put");
    }

    @Test
    public void analyzerOfThreadIsNotSeenByOtherThreads() throws Exception {
        final FieldAnalyzerWrapper wrapper = new FieldAnalyzerWrapper(standard);
        wrapper.addAnalyzer("p", keyword);

        final AtomicReference<List<String>> seenByOtherThread = new AtomicReference<>();
        wrapper.withAnalyzers(Map.of("p", standard), () -> {
            final Thread other = new Thread(() -> {
                try {
                    seenByOtherThread.set(tokens(wrapper, "p"));
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            other.start();
            try {
                other.join();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertEquals(KEYWORD, seenByOtherThread.get());
    }

    @Test
    public void analyzersOfThreadAreRestoredAfterTheCallAndNest() throws IOException {
        final FieldAnalyzerWrapper wrapper = new FieldAnalyzerWrapper(standard);
        wrapper.addAnalyzer("p", keyword);

        final List<List<String>> seen = new ArrayList<>();
        wrapper.withAnalyzers(Map.of("p", standard), () -> {
            seen.add(tokens(wrapper, "p"));
            wrapper.withAnalyzers(Map.of("p", keyword), () -> seen.add(tokens(wrapper, "p")));
            seen.add(tokens(wrapper, "p"));
        });
        seen.add(tokens(wrapper, "p"));

        assertEquals(List.of(STANDARD, KEYWORD, STANDARD, KEYWORD), seen);
    }

    @Test
    public void analyzersOfThreadAreRemovedWhenTheActionFails() {
        final FieldAnalyzerWrapper wrapper = new FieldAnalyzerWrapper(standard);
        wrapper.addAnalyzer("p", keyword);

        try {
            wrapper.withAnalyzers(Map.of("p", standard), () -> {
                throw new IOException("the document cannot be added");
            });
        } catch (final IOException expected) {
            // expected
        }

        try {
            assertEquals(KEYWORD, tokens(wrapper, "p"));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
