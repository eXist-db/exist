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

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a facet definition nested inside a lucene index configuration element.
 * A facet has a dimension and content returned by an XQuery expression.
 *
 * @author Wolfgang Meier
 */
public class LuceneFacetConfig extends AbstractFieldConfig {

    public final static String DIMENSION = "dimension";
    public final static String HIERARCHICAL = "hierarchical";

    protected String dimension;

    protected boolean isHierarchical;

    public LuceneFacetConfig(LuceneConfig config, Element configElement, Map<String, String> namespaces) throws DatabaseConfigurationException {
        super(config, configElement, namespaces);
        dimension = configElement.getAttribute(DIMENSION);
        if (dimension.isEmpty()) {
            throw new DatabaseConfigurationException("Attribute 'dimension' on facet configuration should not be empty");
        }
        final String hierarchicalOpt = configElement.getAttribute(HIERARCHICAL);
        isHierarchical = !hierarchicalOpt.isEmpty() && ("true".equalsIgnoreCase(hierarchicalOpt) || "yes".equalsIgnoreCase(hierarchicalOpt));

        config.facetsConfig.setHierarchical(dimension, isHierarchical);
        config.facetsConfig.setMultiValued(dimension, true);
    }

    @Nonnull
    public String getDimension() {
        return dimension;
    }

    @Override
    protected void processResult(final Sequence result, final Document luceneDoc) throws XPathException {
        if (isHierarchical) {
            // hierarchical facets may be multi-valued, so if we receive an array,
            // create one hierarchical facet for each member
            if (result.hasOne() && result.getItemType() == Type.ARRAY_ITEM) {
                final ArrayType array = (ArrayType) result.itemAt(0);
                for (Sequence seq : array.toArray()) {
                    createHierarchicalFacet(luceneDoc, seq);
                }
            } else {
                // otherwise create a single hierarchical facet
                createHierarchicalFacet(luceneDoc, result);
            }
        } else {
            for (SequenceIterator i = result.unorderedIterator(); i.hasNext(); ) {
                final String value = i.nextItem().getStringValue();
                if (!value.isEmpty()) {
                    luceneDoc.add(new FacetField(dimension, value));
                }
            }
        }
    }

    private void createHierarchicalFacet(Document luceneDoc, Sequence seq) throws XPathException {
        final List<String> paths = new ArrayList<>(seq.getItemCount());
        for (SequenceIterator i = seq.unorderedIterator(); i.hasNext(); ) {
            final String value = i.nextItem().getStringValue();
            if (!value.isEmpty()) {
                paths.add(value);
            }
        }
        if (!paths.isEmpty()) {
            luceneDoc.add(new FacetField(dimension, paths.toArray(new String[0])));
        }
    }

    @Override
    protected void processText(CharSequence text, Document luceneDoc) {
        if (!text.isEmpty()) {
            luceneDoc.add(new FacetField(dimension, text.toString()));
        }
    }

    public void build(DBBroker broker, DocumentImpl document, NodeId nodeId, Document luceneDoc, CharSequence text) {
        try {
            doBuild(broker, document, nodeId, luceneDoc, text);
        } catch (PermissionDeniedException e) {
            LOG.warn("Permission denied while evaluating expression for facet '{}': {}", dimension, expression, e);
        } catch (XPathException e) {
            LOG.warn("XPath error while evaluating expression for facet '{}': {}: {}", dimension, expression, e.getMessage(), e);
        }
    }
}
