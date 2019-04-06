/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.indexing.lucene;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
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
        if (StringUtils.isEmpty(dimension)) {
            throw new DatabaseConfigurationException("Attribute 'dimension' on facet configuration should not be empty");
        }
        final String hierarchicalOpt = configElement.getAttribute(HIERARCHICAL);
        isHierarchical = hierarchicalOpt != null &&
                (hierarchicalOpt.equalsIgnoreCase("true") || hierarchicalOpt.equalsIgnoreCase("yes"));

        config.facetsConfig.setHierarchical(dimension, isHierarchical);
        config.facetsConfig.setMultiValued(dimension, !isHierarchical);
    }

    @Nonnull
    public String getDimension() {
        return dimension;
    }

    @Override
    protected void processResult(Sequence result, Document luceneDoc) throws XPathException {
        if (isHierarchical) {
            String paths[] = new String[result.getItemCount()];
            int j = 0;
            for (SequenceIterator i = result.unorderedIterator(); i.hasNext(); j++) {
                final String value = i.nextItem().getStringValue();
                if (value.length() > 0) {
                    paths[j] = value;
                }
            }
            luceneDoc.add(new FacetField(dimension, paths));
        } else {
            for (SequenceIterator i = result.unorderedIterator(); i.hasNext(); ) {
                final String value = i.nextItem().getStringValue();
                if (value.length() > 0) {
                    luceneDoc.add(new FacetField(dimension, value));
                }
            }
        }
    }

    @Override
    protected void processText(CharSequence text, Document luceneDoc) {
        if (text.length() > 0) {
            luceneDoc.add(new FacetField(dimension, text.toString()));
        }
    }

    public void build(DBBroker broker, DocumentImpl document, NodeId nodeId, Document luceneDoc, CharSequence text) {
        try {
            doBuild(broker, document, nodeId, luceneDoc, text);
        } catch (PermissionDeniedException e) {
            LOG.warn("Permission denied while evaluating expression for facet '" + dimension + "': " + expression, e);
        } catch (XPathException e) {
            LOG.warn("XPath error while evaluating expression for facet '" + dimension + "': " + expression +
                    ": " + e.getMessage(), e);
        }
    }
}
