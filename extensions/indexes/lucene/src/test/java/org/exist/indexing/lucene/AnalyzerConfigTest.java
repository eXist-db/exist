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
import org.apache.lucene.util.Version;
import org.exist.util.StringInputSource;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class AnalyzerConfigTest {

    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();
    static {
        DOCUMENT_BUILDER_FACTORY.setNamespaceAware(true);
    }

    @Test
    public void parameterFromCharArray() throws ParserConfigurationException, IOException, SAXException, AnalyzerConfig.ParameterException {
        final String strParam =
                "<param xmlns=\"http://exist-db.org/collection-config/1.0\" name=\"punctuationDictionary\" type=\"char[]\">\n" +
                "    <value>'</value>\n" +
                "    <value>-</value>\n" +
                "    <value>’</value>\n" +
                "</param>";

        final Element elemParam = parse(strParam).getDocumentElement();
        final AnalyzerConfig.KeyTypedValue<?> constructorParameter = AnalyzerConfig.getConstructorParameter(elemParam);

        assertEquals("punctuationDictionary", constructorParameter.key());
        assertEquals(char[].class, constructorParameter.valueClass());
        assertArrayEquals(new char[] {'\'', '-', '’'}, (char[])constructorParameter.value());
    }

    @Test(expected = AnalyzerConfig.ParameterException.class)
    public void parameterFromInvalidCharArray() throws ParserConfigurationException, IOException, SAXException, AnalyzerConfig.ParameterException {
        final String strParam =
                "<param xmlns=\"http://exist-db.org/collection-config/1.0\" name=\"punctuationDictionary\" type=\"char[]\">\n" +
                        "    <value>'</value>\n" +
                        "    <value/>\n" +
                        "    <value>’</value>\n" +
                        "</param>";

        final Element elemParam = parse(strParam).getDocumentElement();
        AnalyzerConfig.getConstructorParameter(elemParam);
    }

    @Test
    public void parameterFromStringArray() throws ParserConfigurationException, IOException, SAXException, AnalyzerConfig.ParameterException {
        final String strParam =
                "<param xmlns=\"http://exist-db.org/collection-config/1.0\" name=\"dictionary\" type=\"java.lang.String[]\">\n" +
                "    <value>hello</value>\n" +
                "    <value>hi</value>\n" +
                "    <value/>\n" +
                "    <value>goodbye</value>\n" +
                "</param>";

        final Element elemParam = parse(strParam).getDocumentElement();
        final AnalyzerConfig.KeyTypedValue<?> constructorParameter = AnalyzerConfig.getConstructorParameter(elemParam);

        assertEquals("dictionary", constructorParameter.key());
        assertEquals(String[].class, constructorParameter.valueClass());
        assertArrayEquals(new String[] {"hello", "hi", "", "goodbye"}, (String[])constructorParameter.value());
    }

    @Test
    public void allParametersIntegerAndSet() throws ParserConfigurationException, IOException, SAXException, AnalyzerConfig.ParameterException {
        final String strAnalyzer =
        "<analyzer xmlns=\"http://exist-db.org/collection-config/1.0\" id=\"cus\" class=\"ExampleAnalyzer\">\n" +
        "      <param name=\"minimumTermLength\" type=\"java.lang.Integer\" value=\"2\"/>\n" +
        "      <param name=\"punctuationDictionary\" type=\"java.util.Set\">\n" +
        "          <value>'</value>\n" +
        "          <value>-</value>\n" +
        "      </param>\n" +
        "</analyzer>";

        final Element elemAnalyzer = parse(strAnalyzer).getDocumentElement();
        final List<AnalyzerConfig.KeyTypedValue<?>> extractedConstructorArgs = AnalyzerConfig.getAllConstructorParameters(elemAnalyzer);

        assertEquals(2, extractedConstructorArgs.size());

        final AnalyzerConfig.KeyTypedValue<?> extractedConstructorArg1 = extractedConstructorArgs.getFirst();
        assertEquals("minimumTermLength", extractedConstructorArg1.key());
        assertEquals(Integer.class, extractedConstructorArg1.valueClass());
        assertEquals(Integer.valueOf(2), extractedConstructorArg1.value());

        final AnalyzerConfig.KeyTypedValue<?> extractedConstructorArg2 = extractedConstructorArgs.get(1);
        assertEquals("punctuationDictionary", extractedConstructorArg2.key());
        assertEquals(Set.class, extractedConstructorArg2.valueClass());
        assertTrue(extractedConstructorArg2.value() instanceof HashSet);
        assertEquals(2, ((Set<Character>)extractedConstructorArg2.value()).size());
    }

    @Test
    public void allParametersIntAndSet() throws ParserConfigurationException, IOException, SAXException, AnalyzerConfig.ParameterException {
        final String strAnalyzer =
                "<analyzer xmlns=\"http://exist-db.org/collection-config/1.0\" id=\"cus\" class=\"ExampleAnalyzer\">\n" +
                "      <param name=\"minimumTermLength\" type=\"int\" value=\"2\"/>\n" +
                "      <param name=\"punctuationDictionary\" type=\"java.util.Set\">\n" +
                "          <value>'</value>\n" +
                "          <value>-</value>\n" +
                "      </param>\n" +
                "</analyzer>";

        final Element elemAnalyzer = parse(strAnalyzer).getDocumentElement();
        final List<AnalyzerConfig.KeyTypedValue<?>> extractedConstructorArgs = AnalyzerConfig.getAllConstructorParameters(elemAnalyzer);

        assertEquals(2, extractedConstructorArgs.size());

        final AnalyzerConfig.KeyTypedValue<?> extractedConstructorArg1 = extractedConstructorArgs.getFirst();
        assertEquals("minimumTermLength", extractedConstructorArg1.key());
        assertEquals(int.class, extractedConstructorArg1.valueClass());
        assertEquals(2, extractedConstructorArg1.value());

        final AnalyzerConfig.KeyTypedValue<?> extractedConstructorArg2 = extractedConstructorArgs.get(1);
        assertEquals("punctuationDictionary", extractedConstructorArg2.key());
        assertEquals(Set.class, extractedConstructorArg2.valueClass());
        assertTrue(extractedConstructorArg2.value() instanceof HashSet);
        assertEquals(2, ((Set<Character>)extractedConstructorArg2.value()).size());
    }

    @Test
    public void allParametersBooleanAndSet() throws ParserConfigurationException, IOException, SAXException, AnalyzerConfig.ParameterException {
        final String strAnalyzer =
                "<analyzer xmlns=\"http://exist-db.org/collection-config/1.0\" id=\"cus\" class=\"ExampleAnalyzer\">\n" +
                        "      <param name=\"minimumTermLength\" type=\"java.lang.Boolean\" value=\"true\"/>\n" +
                        "      <param name=\"punctuationDictionary\" type=\"java.util.Set\">\n" +
                        "          <value>'</value>\n" +
                        "          <value>-</value>\n" +
                        "      </param>\n" +
                        "</analyzer>";

        final Element elemAnalyzer = parse(strAnalyzer).getDocumentElement();
        final List<AnalyzerConfig.KeyTypedValue<?>> extractedConstructorArgs = AnalyzerConfig.getAllConstructorParameters(elemAnalyzer);

        assertEquals(2, extractedConstructorArgs.size());

        final AnalyzerConfig.KeyTypedValue<?> extractedConstructorArg1 = extractedConstructorArgs.getFirst();
        assertEquals("minimumTermLength", extractedConstructorArg1.key());
        assertEquals(Boolean.class, extractedConstructorArg1.valueClass());
        assertEquals(Boolean.TRUE, extractedConstructorArg1.value());

        final AnalyzerConfig.KeyTypedValue<?> extractedConstructorArg2 = extractedConstructorArgs.get(1);
        assertEquals("punctuationDictionary", extractedConstructorArg2.key());
        assertEquals(Set.class, extractedConstructorArg2.valueClass());
        assertTrue(extractedConstructorArg2.value() instanceof HashSet);
        assertEquals(2, ((Set<Character>)extractedConstructorArg2.value()).size());
    }

    @Test
    public void allParametersPrimitiveBooleanAndSet() throws ParserConfigurationException, IOException, SAXException, AnalyzerConfig.ParameterException {
        final String strAnalyzer =
                "<analyzer xmlns=\"http://exist-db.org/collection-config/1.0\" id=\"cus\" class=\"ExampleAnalyzer\">\n" +
                        "      <param name=\"minimumTermLength\" type=\"boolean\" value=\"true\"/>\n" +
                        "      <param name=\"punctuationDictionary\" type=\"java.util.Set\">\n" +
                        "          <value>'</value>\n" +
                        "          <value>-</value>\n" +
                        "      </param>\n" +
                        "</analyzer>";

        final Element elemAnalyzer = parse(strAnalyzer).getDocumentElement();
        final List<AnalyzerConfig.KeyTypedValue<?>> extractedConstructorArgs = AnalyzerConfig.getAllConstructorParameters(elemAnalyzer);

        assertEquals(2, extractedConstructorArgs.size());

        final AnalyzerConfig.KeyTypedValue<?> extractedConstructorArg1 = extractedConstructorArgs.getFirst();
        assertEquals("minimumTermLength", extractedConstructorArg1.key());
        assertEquals(boolean.class, extractedConstructorArg1.valueClass());
        assertEquals(true, extractedConstructorArg1.value());

        final AnalyzerConfig.KeyTypedValue<?> extractedConstructorArg2 = extractedConstructorArgs.get(1);
        assertEquals("punctuationDictionary", extractedConstructorArg2.key());
        assertEquals(Set.class, extractedConstructorArg2.valueClass());
        assertTrue(extractedConstructorArg2.value() instanceof HashSet);
        assertEquals(2, ((Set<Character>)extractedConstructorArg2.value()).size());
    }

    @Test
    public void allParametersCharArray() throws ParserConfigurationException, IOException, SAXException, AnalyzerConfig.ParameterException {
        final String strAnalyzer =
                "<analyzer xmlns=\"http://exist-db.org/collection-config/1.0\" id=\"cus\" class=\"ExampleAnalyzer\">\n" +
                        "      <param name=\"punctuationDictionary\" type=\"char[]\">\n" +
                        "          <value>'</value>\n" +
                        "          <value>-</value>\n" +
                        "      </param>\n" +
                        "</analyzer>";

        final Element elemAnalyzer = parse(strAnalyzer).getDocumentElement();
        final List<AnalyzerConfig.KeyTypedValue<?>> extractedConstructorArgs = AnalyzerConfig.getAllConstructorParameters(elemAnalyzer);

        assertEquals(1, extractedConstructorArgs.size());

        final AnalyzerConfig.KeyTypedValue<?> extractedConstructorArg1 = extractedConstructorArgs.getFirst();
        assertEquals("punctuationDictionary", extractedConstructorArg1.key());
        assertEquals(char[].class, extractedConstructorArg1.valueClass());
        assertArrayEquals(new char[] {'\'', '-'}, (char[])extractedConstructorArg1.value());
    }

    @Test
    public void allParametersStringArray() throws ParserConfigurationException, IOException, SAXException, AnalyzerConfig.ParameterException {
        final String strAnalyzer =
                "<analyzer xmlns=\"http://exist-db.org/collection-config/1.0\" id=\"cus\" class=\"ExampleAnalyzer\">\n" +
                        "      <param name=\"punctuationDictionary\" type=\"java.lang.String[]\">\n" +
                        "          <value>abc</value>\n" +
                        "          <value>def</value>\n" +
                        "      </param>\n" +
                        "</analyzer>";

        final Element elemAnalyzer = parse(strAnalyzer).getDocumentElement();
        final List<AnalyzerConfig.KeyTypedValue<?>> extractedConstructorArgs = AnalyzerConfig.getAllConstructorParameters(elemAnalyzer);

        assertEquals(1, extractedConstructorArgs.size());

        final AnalyzerConfig.KeyTypedValue<?> extractedConstructorArg1 = extractedConstructorArgs.getFirst();
        assertEquals("punctuationDictionary", extractedConstructorArg1.key());
        assertEquals(String[].class, extractedConstructorArg1.valueClass());
        assertArrayEquals(new String[] {"abc", "def"}, (String[])extractedConstructorArg1.value());
    }

    @Test
    public void constructIntegerAndSetMockAnalyzerWithoutVersion() {
        final Class<IntegerAndSetConstructorMockAnalyzer> analyerClass = IntegerAndSetConstructorMockAnalyzer.class;
        final Class<?>[] vcParamClasses = new Class[] {
                Integer.class,
                Set.class
        };
        final Object[] vcParamValues = {
                12345,
                new HashSet<>(Arrays.asList("s1, s2"))
        };

        final IntegerAndSetConstructorMockAnalyzer mockAnalyzer = AnalyzerConfig.createInstance(analyerClass, vcParamClasses, vcParamValues, true);
        assertNotNull(mockAnalyzer);
        assertNull(mockAnalyzer.luceneVersion);
        assertEquals(vcParamValues[0], mockAnalyzer.arg1);
        assertEquals(vcParamValues[1], mockAnalyzer.arg2);
    }

    @Test
    public void constructIntegerAndSetMockAnalyzerWithVersion() {
        final Class<IntegerAndSetConstructorMockAnalyzer> analyerClass = IntegerAndSetConstructorMockAnalyzer.class;
        final Class<?>[] vcParamClasses = new Class[] {
                Version.class,
                Integer.class,
                Set.class
        };
        final Object[] vcParamValues = {
                LuceneIndex.LUCENE_VERSION_IN_USE,
                12345,
                new HashSet<>(Arrays.asList("s1, s2"))
        };

        final IntegerAndSetConstructorMockAnalyzer mockAnalyzer = AnalyzerConfig.createInstance(analyerClass, vcParamClasses, vcParamValues, true);
        assertNotNull(mockAnalyzer);
        assertEquals(vcParamValues[0], mockAnalyzer.luceneVersion);
        assertEquals(vcParamValues[1], mockAnalyzer.arg1);
        assertEquals(vcParamValues[2], mockAnalyzer.arg2);
    }

    @Test
    public void constructIntAndSetMockAnalyzerWithoutVersion() {
        final Class<IntAndSetConstructorMockAnalyzer> analyerClass = IntAndSetConstructorMockAnalyzer.class;
        final Class<?>[] vcParamClasses = new Class[] {
                int.class,
                Set.class
        };
        final Object[] vcParamValues = {
                12345,
                new HashSet<>(Arrays.asList("s1, s2"))
        };

        final IntAndSetConstructorMockAnalyzer mockAnalyzer = AnalyzerConfig.createInstance(analyerClass, vcParamClasses, vcParamValues, true);
        assertNotNull(mockAnalyzer);
        assertNull(mockAnalyzer.luceneVersion);
        assertEquals(vcParamValues[0], mockAnalyzer.arg1);
        assertEquals(vcParamValues[1], mockAnalyzer.arg2);
    }

    @Test
    public void constructIntAndSetMockAnalyzerWithVersion() {
        final Class<IntAndSetConstructorMockAnalyzer> analyerClass = IntAndSetConstructorMockAnalyzer.class;
        final Class<?>[] vcParamClasses = new Class[] {
                Version.class,
                int.class,
                Set.class
        };
        final Object[] vcParamValues = {
                LuceneIndex.LUCENE_VERSION_IN_USE,
                12345,
                new HashSet<>(Arrays.asList("s1, s2"))
        };

        final IntAndSetConstructorMockAnalyzer mockAnalyzer = AnalyzerConfig.createInstance(analyerClass, vcParamClasses, vcParamValues, true);
        assertNotNull(mockAnalyzer);
        assertEquals(vcParamValues[0], mockAnalyzer.luceneVersion);
        assertEquals(vcParamValues[1], mockAnalyzer.arg1);
        assertEquals(vcParamValues[2], mockAnalyzer.arg2);
    }

    @Test
    public void constructBooleanAndSetMockAnalyzerWithoutVersion() {
        final Class<BooleanAndSetConstructorMockAnalyzer> analyerClass = BooleanAndSetConstructorMockAnalyzer.class;
        final Class<?>[] vcParamClasses = new Class[] {
                Boolean.class,
                Set.class
        };
        final Object[] vcParamValues = {
                Boolean.TRUE,
                new HashSet<>(Arrays.asList("s1, s2"))
        };

        final BooleanAndSetConstructorMockAnalyzer mockAnalyzer = AnalyzerConfig.createInstance(analyerClass, vcParamClasses, vcParamValues, true);
        assertNotNull(mockAnalyzer);
        assertNull(mockAnalyzer.luceneVersion);
        assertEquals(vcParamValues[0], mockAnalyzer.arg1);
        assertEquals(vcParamValues[1], mockAnalyzer.arg2);
    }

    @Test
    public void constructBooleanAndSetMockAnalyzerWithVersion() {
        final Class<BooleanAndSetConstructorMockAnalyzer> analyerClass = BooleanAndSetConstructorMockAnalyzer.class;
        final Class<?>[] vcParamClasses = new Class[] {
                Version.class,
                Boolean.class,
                Set.class
        };
        final Object[] vcParamValues = {
                LuceneIndex.LUCENE_VERSION_IN_USE,
                Boolean.TRUE,
                new HashSet<>(Arrays.asList("s1, s2"))
        };

        final BooleanAndSetConstructorMockAnalyzer mockAnalyzer = AnalyzerConfig.createInstance(analyerClass, vcParamClasses, vcParamValues, true);
        assertNotNull(mockAnalyzer);
        assertEquals(vcParamValues[0], mockAnalyzer.luceneVersion);
        assertEquals(vcParamValues[1], mockAnalyzer.arg1);
        assertEquals(vcParamValues[2], mockAnalyzer.arg2);
    }

    @Test
    public void constructPrimitiveAndSetMockAnalyzerWithoutVersion() {
        final Class<PrimitiveBooleanAndSetConstructorMockAnalyzer> analyerClass = PrimitiveBooleanAndSetConstructorMockAnalyzer.class;
        final Class<?>[] vcParamClasses = new Class[] {
                boolean.class,
                Set.class
        };
        final Object[] vcParamValues = {
                true,
                new HashSet<>(Arrays.asList("s1, s2"))
        };

        final PrimitiveBooleanAndSetConstructorMockAnalyzer mockAnalyzer = AnalyzerConfig.createInstance(analyerClass, vcParamClasses, vcParamValues, true);
        assertNotNull(mockAnalyzer);
        assertNull(mockAnalyzer.luceneVersion);
        assertEquals(vcParamValues[0], mockAnalyzer.arg1);
        assertEquals(vcParamValues[1], mockAnalyzer.arg2);
    }

    @Test
    public void constructPrimitiveBooleanAndSetMockAnalyzerWithVersion() {
        final Class<PrimitiveBooleanAndSetConstructorMockAnalyzer> analyerClass = PrimitiveBooleanAndSetConstructorMockAnalyzer.class;
        final Class<?>[] vcParamClasses = new Class[] {
                Version.class,
                boolean.class,
                Set.class
        };
        final Object[] vcParamValues = {
                LuceneIndex.LUCENE_VERSION_IN_USE,
                true,
                new HashSet<>(Arrays.asList("s1, s2"))
        };

        final PrimitiveBooleanAndSetConstructorMockAnalyzer mockAnalyzer = AnalyzerConfig.createInstance(analyerClass, vcParamClasses, vcParamValues, true);
        assertNotNull(mockAnalyzer);
        assertEquals(vcParamValues[0], mockAnalyzer.luceneVersion);
        assertEquals(vcParamValues[1], mockAnalyzer.arg1);
        assertEquals(vcParamValues[2], mockAnalyzer.arg2);
    }

    @Test
    public void constructCharArrayMockAnalyzerWithoutVersion() {
        final Class<CharArrayConstructorMockAnalyzer> analyerClass = CharArrayConstructorMockAnalyzer.class;
        final Class<?>[] vcParamClasses = new Class[] {
                char[].class,
        };
        final Object[] vcParamValues = {
                new char[] {'\'', '-'}
        };

        final CharArrayConstructorMockAnalyzer mockAnalyzer = AnalyzerConfig.createInstance(analyerClass, vcParamClasses, vcParamValues, true);
        assertNotNull(mockAnalyzer);
        assertNull(mockAnalyzer.luceneVersion);
        assertArrayEquals((char[])vcParamValues[0], mockAnalyzer.arg1);
    }

    @Test
    public void constructCharArrayMockAnalyzerWithVersion() {
        final Class<CharArrayConstructorMockAnalyzer> analyerClass = CharArrayConstructorMockAnalyzer.class;
        final Class<?>[] vcParamClasses = new Class[] {
                Version.class,
                char[].class,
        };
        final Object[] vcParamValues = {
                LuceneIndex.LUCENE_VERSION_IN_USE,
                new char[] {'\'', '-'}
        };

        final CharArrayConstructorMockAnalyzer mockAnalyzer = AnalyzerConfig.createInstance(analyerClass, vcParamClasses, vcParamValues, true);
        assertNotNull(mockAnalyzer);
        assertEquals(vcParamValues[0], mockAnalyzer.luceneVersion);
        assertArrayEquals((char[])vcParamValues[1], mockAnalyzer.arg1);
    }

    @Test
    public void constructStringArrayMockAnalyzerWithoutVersion() {
        final Class<StringArrayConstructorMockAnalyzer> analyerClass = StringArrayConstructorMockAnalyzer.class;
        final Class<?>[] vcParamClasses = new Class[] {
                String[].class,
        };
        final Object[] vcParamValues = {
                new String[] {"abc", "def"}
        };

        final StringArrayConstructorMockAnalyzer mockAnalyzer = AnalyzerConfig.createInstance(analyerClass, vcParamClasses, vcParamValues, true);
        assertNotNull(mockAnalyzer);
        assertNull(mockAnalyzer.luceneVersion);
        assertArrayEquals((String[])vcParamValues[0], mockAnalyzer.arg1);
    }

    @Test
    public void constructStringArrayMockAnalyzerWithVersion() {
        final Class<StringArrayConstructorMockAnalyzer> analyerClass = StringArrayConstructorMockAnalyzer.class;
        final Class<?>[] vcParamClasses = new Class[] {
                Version.class,
                String[].class,
        };
        final Object[] vcParamValues = {
                LuceneIndex.LUCENE_VERSION_IN_USE,
                new String[] {"abc", "def"}
        };

        final StringArrayConstructorMockAnalyzer mockAnalyzer = AnalyzerConfig.createInstance(analyerClass, vcParamClasses, vcParamValues, true);
        assertNotNull(mockAnalyzer);
        assertEquals(vcParamValues[0], mockAnalyzer.luceneVersion);
        assertArrayEquals((String[])vcParamValues[1], mockAnalyzer.arg1);
    }

    private Document parse(final String strXml) throws ParserConfigurationException, IOException, SAXException {
        final DocumentBuilder documentBuilder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        return documentBuilder.parse(new StringInputSource(strXml));
    }

    static class IntegerAndSetConstructorMockAnalyzer extends Analyzer {
        final Version luceneVersion;
        final Integer arg1;
        final Set<String> arg2;

        public IntegerAndSetConstructorMockAnalyzer(final Integer arg1, final Set<String> arg2) {
            this(null, arg1, arg2);
        }

        public IntegerAndSetConstructorMockAnalyzer(final Version luceneVersion, final Integer arg1, final Set<String> arg2) {
            this.luceneVersion = luceneVersion;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        @Override
        protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
            throw new UnsupportedOperationException("This analyzer is a mock for testing");
        }
    }

    static class IntAndSetConstructorMockAnalyzer extends Analyzer {
        final Version luceneVersion;
        final int arg1;
        final Set<String> arg2;

        public IntAndSetConstructorMockAnalyzer(final int arg1, final Set<String> arg2) {
            this(null, arg1, arg2);
        }

        public IntAndSetConstructorMockAnalyzer(final Version luceneVersion, final int arg1, final Set<String> arg2) {
            this.luceneVersion = luceneVersion;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        @Override
        protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
            throw new UnsupportedOperationException("This analyzer is a mock for testing");
        }
    }

    static class BooleanAndSetConstructorMockAnalyzer extends Analyzer {
        final Version luceneVersion;
        final Boolean arg1;
        final Set<String> arg2;

        public BooleanAndSetConstructorMockAnalyzer(final Boolean arg1, final Set<String> arg2) {
            this(null, arg1, arg2);
        }

        public BooleanAndSetConstructorMockAnalyzer(final Version luceneVersion, final Boolean arg1, final Set<String> arg2) {
            this.luceneVersion = luceneVersion;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        @Override
        protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
            throw new UnsupportedOperationException("This analyzer is a mock for testing");
        }
    }

    static class PrimitiveBooleanAndSetConstructorMockAnalyzer extends Analyzer {
        final Version luceneVersion;
        final boolean arg1;
        final Set<String> arg2;

        public PrimitiveBooleanAndSetConstructorMockAnalyzer(final boolean arg1, final Set<String> arg2) {
            this(null, arg1, arg2);
        }

        public PrimitiveBooleanAndSetConstructorMockAnalyzer(final Version luceneVersion, final boolean arg1, final Set<String> arg2) {
            this.luceneVersion = luceneVersion;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        @Override
        protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
            throw new UnsupportedOperationException("This analyzer is a mock for testing");
        }
    }

    static class CharArrayConstructorMockAnalyzer extends Analyzer {
        final Version luceneVersion;
        final char[] arg1;

        public CharArrayConstructorMockAnalyzer(final char[] arg1) {
            this(null, arg1);
        }

        public CharArrayConstructorMockAnalyzer(final Version luceneVersion, final char[] arg1) {
            this.luceneVersion = luceneVersion;
            this.arg1 = arg1;
        }

        @Override
        protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
            throw new UnsupportedOperationException("This analyzer is a mock for testing");
        }
    }

    static class StringArrayConstructorMockAnalyzer extends Analyzer {
        final Version luceneVersion;
        final String[] arg1;

        public StringArrayConstructorMockAnalyzer(final String[] arg1) {
            this(null, arg1);
        }

        public StringArrayConstructorMockAnalyzer(final Version luceneVersion, final String[] arg1) {
            this.luceneVersion = luceneVersion;
            this.arg1 = arg1;
        }

        @Override
        protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
            throw new UnsupportedOperationException("This analyzer is a mock for testing");
        }
    }
}
