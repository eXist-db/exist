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
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.exist.indexing.lucene.analyzers.IndexQueryAnalyzer;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An analyzer built from a chain of Lucene's analysis components, named as Lucene's factories are (their SPI
 * names, as in Solr and Elasticsearch), with the component's other attributes as the factory's arguments:
 *
 * <pre>{@code
 * <analyzer id="cased">
 *     <tokenizer name="standard"/>
 *     <filter name="englishPossessive"/>
 *     <filter name="asciiFolding"/>
 * </analyzer>
 * }</pre>
 *
 * <p>A chain has character filters ({@code <char-filter>}), then one tokenizer ({@code <tokenizer>}), then token
 * filters ({@code <filter>}). An analyzer may instead have a chain to index with, in {@code <index>}, and one to
 * query with, in {@code <query>}.</p>
 *
 * <p>Resources the factories read, such as a stopword or mapping file, are looked up on the class path.</p>
 */
final class AnalyzerChain {

    static final String TOKENIZER_ELEMENT = "tokenizer";
    static final String FILTER_ELEMENT = "filter";
    static final String CHAR_FILTER_ELEMENT = "char-filter";
    static final String INDEX_ELEMENT = "index";
    static final String QUERY_ELEMENT = "query";
    private static final String NAME_ATTRIBUTE = "name";

    private AnalyzerChain() {
    }

    /**
     * Whether an analyzer element is configured as a chain.
     *
     * @param config the analyzer element
     * @return true if it has a component of a chain, or an index or query chain
     */
    static boolean isChain(final Element config) {
        for (final Element child : children(config)) {
            switch (localName(child)) {
                case TOKENIZER_ELEMENT, FILTER_ELEMENT, CHAR_FILTER_ELEMENT, INDEX_ELEMENT, QUERY_ELEMENT -> {
                    return true;
                }
                default -> {
                    // a <param> of an analyzer class
                }
            }
        }
        return false;
    }

    /**
     * Builds the analyzer an analyzer element configures as a chain.
     *
     * @param config the analyzer element
     * @return the analyzer
     * @throws DatabaseConfigurationException if the chain isn't valid, or one of its components is unknown or has
     *     arguments it doesn't accept
     */
    static Analyzer build(final Element config) throws DatabaseConfigurationException {
        final String label = label(config);
        Element index = null;
        Element query = null;
        for (final Element child : children(config)) {
            switch (localName(child)) {
                case INDEX_ELEMENT -> index = once(index, child, label);
                case QUERY_ELEMENT -> query = once(query, child, label);
                default -> {
                    // a single chain, checked by chain()
                }
            }
        }
        if (index == null && query == null) {
            return chain(config, label);
        }
        if (index == null || query == null) {
            throw new DatabaseConfigurationException(label + " needs both an <index> and a <query> chain, or neither");
        }
        for (final Element child : children(config)) {
            final String name = localName(child);
            if (!INDEX_ELEMENT.equals(name) && !QUERY_ELEMENT.equals(name)) {
                throw new DatabaseConfigurationException(label + ": <" + name
                        + "> must be inside its <index> or <query> chain");
            }
        }
        return new IndexQueryAnalyzer(chain(index, label + " <index>"), chain(query, label + " <query>"));
    }

    private static Element once(final Element seen, final Element child, final String label)
            throws DatabaseConfigurationException {
        if (seen != null) {
            throw new DatabaseConfigurationException(label + " has more than one <" + localName(child) + "> chain");
        }
        return child;
    }

    private static Analyzer chain(final Element chain, final String label) throws DatabaseConfigurationException {
        final CustomAnalyzer.Builder builder = CustomAnalyzer.builder();
        boolean tokenizer = false;
        for (final Element component : children(chain)) {
            final String kind = localName(component);
            final String name = component.getAttribute(NAME_ATTRIBUTE);
            if (name.isBlank()) {
                throw new DatabaseConfigurationException(label + ": <" + kind + "> needs a name");
            }
            final Map<String, String> args = arguments(component);
            try {
                switch (kind) {
                    case CHAR_FILTER_ELEMENT -> {
                        if (tokenizer) {
                            throw new DatabaseConfigurationException(label + ": <char-filter name=\"" + name
                                    + "\"> must come before the tokenizer");
                        }
                        builder.addCharFilter(name, args);
                    }
                    case TOKENIZER_ELEMENT -> {
                        if (tokenizer) {
                            throw new DatabaseConfigurationException(label + " has more than one tokenizer");
                        }
                        builder.withTokenizer(name, args);
                        tokenizer = true;
                    }
                    case FILTER_ELEMENT -> {
                        if (!tokenizer) {
                            throw new DatabaseConfigurationException(label + ": <filter name=\"" + name
                                    + "\"> must come after the tokenizer");
                        }
                        builder.addTokenFilter(name, args);
                    }
                    default -> throw new DatabaseConfigurationException(label + ": unknown element <" + kind
                            + ">; a chain has <char-filter>, <tokenizer> and <filter> elements");
                }
            } catch (final IllegalArgumentException | IOException e) {
                throw new DatabaseConfigurationException(label + ": <" + kind + " name=\"" + name + "\">: "
                        + e.getMessage(), e);
            }
        }
        if (!tokenizer) {
            throw new DatabaseConfigurationException(label + " needs a <tokenizer>");
        }
        try {
            return builder.build();
        } catch (final IllegalArgumentException | IllegalStateException e) {
            throw new DatabaseConfigurationException(label + ": " + e.getMessage(), e);
        }
    }

    private static Map<String, String> arguments(final Element component) {
        final Map<String, String> args = new HashMap<>();
        final NamedNodeMap attributes = component.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            final Attr attribute = (Attr) attributes.item(i);
            final String name = localName(attribute);
            if (!NAME_ATTRIBUTE.equals(name) && attribute.getNamespaceURI() == null) {
                args.put(name, attribute.getValue());
            }
        }
        return args;
    }

    private static String label(final Element config) {
        final String id = config.getAttribute("id");
        return id.isBlank() ? "Lucene default analyzer" : "Lucene analyzer '" + id + "'";
    }

    private static List<Element> children(final Element parent) {
        final List<Element> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                children.add((Element) child);
            }
        }
        return children;
    }

    private static String localName(final Node node) {
        final String localName = node.getLocalName();
        return localName != null ? localName : node.getNodeName();
    }
}
