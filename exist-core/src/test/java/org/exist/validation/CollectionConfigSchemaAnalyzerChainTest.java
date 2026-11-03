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
package org.exist.validation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The collection configuration schema accepts a Lucene analyzer configured as a class, as a chain of analysis
 * components, or as a chain to index with and one to query with.
 */
class CollectionConfigSchemaAnalyzerChainTest {

    private static Schema schema;

    @BeforeAll
    static void loadSchema() throws SAXException {
        final Path schemaPath = resolveSchemaPath();
        assertTrue(Files.exists(schemaPath), "Schema not found at " + schemaPath);
        // the schema uses vc:minVersion="1.1"
        schema = SchemaFactory.newInstance("http://www.w3.org/XML/XMLSchema/v1.1").newSchema(schemaPath.toFile());
    }

    @Test
    void analyzerClass() {
        assertDoesNotThrow(() -> validate("""
                <analyzer id="nostop" class="org.apache.lucene.analysis.en.EnglishAnalyzer">
                    <param name="stopwords" type="org.apache.lucene.analysis.CharArraySet"/>
                </analyzer>"""));
    }

    @Test
    void chain() {
        assertDoesNotThrow(() -> validate("""
                <analyzer id="literal">
                    <char-filter name="htmlStrip"/>
                    <tokenizer name="whitespace"/>
                    <filter name="patternReplace" pattern="^\\p{Punct}+|\\p{Punct}+$" replacement=""/>
                    <filter name="asciiFolding"/>
                </analyzer>"""));
    }

    @Test
    void indexAndQueryChains() {
        assertDoesNotThrow(() -> validate("""
                <analyzer id="delimited">
                    <index>
                        <tokenizer name="whitespace"/>
                        <filter name="wordDelimiterGraph" preserveOriginal="1" catenateAll="1"/>
                        <filter name="flattenGraph"/>
                    </index>
                    <query>
                        <tokenizer name="whitespace"/>
                        <filter name="wordDelimiterGraph" preserveOriginal="1"/>
                    </query>
                </analyzer>"""));
    }

    @Test
    void chainWithoutTokenizer() {
        assertThrows(SAXException.class, () -> validate("""
                <analyzer id="bad">
                    <filter name="lowercase"/>
                </analyzer>"""));
    }

    @Test
    void componentWithoutName() {
        assertThrows(SAXException.class, () -> validate("""
                <analyzer id="bad">
                    <tokenizer/>
                </analyzer>"""));
    }

    private static void validate(final String analyzer) throws SAXException, IOException {
        final String config = """
                <collection xmlns="http://exist-db.org/collection-config/1.0">
                    <index>
                        <lucene>
                            %s
                            <text qname="p"/>
                        </lucene>
                    </index>
                </collection>""".formatted(analyzer);
        schema.newValidator().validate(new StreamSource(new StringReader(config)));
    }

    private static Path resolveSchemaPath() {
        final Path base = Path.of(System.getProperty("user.dir"));
        final Path path = base.resolve("schema").resolve("collection.xconf.xsd");
        return Files.exists(path) ? path : base.getParent().resolve("schema").resolve("collection.xconf.xsd");
    }
}
