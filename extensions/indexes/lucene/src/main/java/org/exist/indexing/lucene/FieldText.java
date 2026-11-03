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

import org.apache.lucene.index.IndexableField;
import org.exist.dom.persistent.NodeProxy;
import org.exist.security.PermissionDeniedException;
import org.exist.stax.IEmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.xquery.XPathException;
import org.w3c.dom.Node;

import javax.annotation.Nullable;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The text of a field for a node, as it was indexed: the stored copy of a stored field, or, for a field that isn't
 * stored, the text computed from the node as indexing computes it.
 */
public final class FieldText {

    private FieldText() {
    }

    /**
     * A field's configuration, and the configuration of the index it's defined in.
     *
     * @param index the index's configuration
     * @param field the field's configuration
     */
    public record Config(LuceneIndexConfig index, LuceneFieldConfig field) {
    }

    /**
     * Finds the configuration of a field of the index that applies to a node.
     *
     * @param config the Lucene configuration of the node's collection
     * @param path the node's path
     * @param field the field's name
     * @return the field's configuration, or null if no index for the node has the field
     */
    public static @Nullable Config config(final LuceneConfig config, final NodePath path, final String field) {
        for (final Iterator<LuceneIndexConfig> i = config.getConfig(path); i != null && i.hasNext(); ) {
            final LuceneIndexConfig indexConfig = i.next();
            if (!indexConfig.match(path)) {
                continue;
            }
            for (final AbstractFieldConfig fieldConfig : indexConfig.getFacetsAndFields()) {
                if (fieldConfig instanceof LuceneFieldConfig luceneField && field.equals(luceneField.getName())) {
                    return new Config(indexConfig, luceneField);
                }
            }
        }
        return null;
    }

    /**
     * The values of a field for a node: those stored, or, for a field that isn't stored, those indexing computes.
     *
     * @param worker the index worker, to read stored values with
     * @param broker the broker to read and evaluate with
     * @param config the Lucene configuration of the node's collection
     * @param node the node the field is defined on
     * @param field the field's name
     * @return the values
     * @throws IOException if the index or the node cannot be read
     * @throws XPathException if the field's expression or condition cannot be evaluated
     * @throws PermissionDeniedException if the user may not evaluate them
     */
    public static List<String> values(final LuceneIndexWorker worker, final DBBroker broker, final LuceneConfig config,
            final NodeProxy node, final String field) throws IOException, XPathException, PermissionDeniedException {
        final Config fieldConfig = config(config, LuceneMatchListener.getPath(node), field);
        if (fieldConfig == null || fieldConfig.field().isStore()) {
            return stored(worker, node, field);
        }
        try {
            return fieldConfig.field().values(broker, node, () -> {
                try {
                    return indexedText(broker, config, fieldConfig.index(), node);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static List<String> stored(final LuceneIndexWorker worker, final NodeProxy node, final String field)
            throws IOException {
        final List<String> values = new ArrayList<>();
        for (final IndexableField stored : worker.getFieldByExistDocId(node.getOwnerDocument().getDocId(),
                node.getNodeId(), field)) {
            if (stored.stringValue() != null) {
                values.add(stored.stringValue());
            } else if (stored.numericValue() != null) {
                values.add(stored.numericValue().toString());
            }
        }
        return values;
    }

    /**
     * The text of a node as an index indexes it: its descendants' text, without the elements the configuration
     * ignores, and with a space between the text of elements it doesn't declare inline, as
     * {@link DefaultTextExtractor} builds it when indexing.
     *
     * @param broker the broker to read the node with
     * @param config the Lucene configuration of the node's collection
     * @param indexConfig the configuration of the index
     * @param node the node
     * @return the text
     * @throws IOException if the node cannot be read
     */
    public static String indexedText(final DBBroker broker, final LuceneConfig config,
            final LuceneIndexConfig indexConfig, final NodeProxy node) throws IOException {
        if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
            return node.getNode().getNodeValue();
        }
        final TextExtractor extractor = new DefaultTextExtractor();
        extractor.configure(config, indexConfig);
        try {
            // a reader of its own: the broker's shared reader may be in use by the query
            final IEmbeddedXMLStreamReader reader = broker.newXMLStreamReader(node, false);
            try {
                readText(reader, extractor);
            } finally {
                reader.close();
            }
        } catch (final XMLStreamException e) {
            throw new IOException(e.getMessage(), e);
        }
        return extractor.getText().toString();
    }

    private static void readText(final IEmbeddedXMLStreamReader reader, final TextExtractor extractor) throws XMLStreamException {
        int level = 0;
        while (reader.hasNext()) {
            final int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                // as when indexing, the node's own start is not given to the extractor
                if (level > 0) {
                    extractor.startElement(reader.getQName());
                }
                level++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                extractor.endElement(reader.getQName());
                // the reader goes on to the node's following siblings (#4835)
                if (--level <= 0) {
                    break;
                }
            } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                extractor.beforeCharacters();
                extractor.characters(reader.getXMLText());
            }
        }
    }
}
