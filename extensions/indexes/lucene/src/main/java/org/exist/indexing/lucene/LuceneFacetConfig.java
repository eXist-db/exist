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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetsConfig;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.w3c.dom.Element;

import java.util.Map;

public class LuceneFacetConfig {

    static final Logger LOG = LogManager.getLogger(LuceneFacetConfig.class);

    public final static String DIMENSION = "dimension";
    public final static String XPATH_ATTR = "expression";
    public final static String HIERARCHICAL = "hierarchical";

    protected String dimension;

    protected String expression;

    protected boolean isHierarchical = false;

    private boolean isValid = true;

    private CompiledXQuery compiled = null;

    public LuceneFacetConfig(Element configElement, FacetsConfig facetsConfig, Map<String, String> namespaces) throws DatabaseConfigurationException {
        this.dimension = configElement.getAttribute(DIMENSION);

        final String xpath = configElement.getAttribute(XPATH_ATTR);
        if (xpath == null || xpath.isEmpty()) {
            throw new DatabaseConfigurationException("facet definition needs an attribute 'xpath': " + configElement.toString());
        }

        final String hierarchicalOpt = configElement.getAttribute(HIERARCHICAL);
        isHierarchical = hierarchicalOpt != null &&
                (hierarchicalOpt.equalsIgnoreCase("true") || hierarchicalOpt.equalsIgnoreCase("yes"));

        final StringBuilder sb = new StringBuilder();
        namespaces.forEach((prefix, uri) -> {
            if (!prefix.equals("xml")) {
                sb.append("declare namespace ").append(prefix);
                sb.append("=\"").append(uri).append("\";\n");
            }
        });
        sb.append(xpath);

        this.expression = sb.toString();

        facetsConfig.setHierarchical(dimension, isHierarchical);
        facetsConfig.setMultiValued(dimension, !isHierarchical);
    }

    public String getDimension() {
        return dimension;
    }

    public String getExpression() {
        return expression;
    }

    public void build(DBBroker broker, DocumentImpl document, NodeId nodeId, Document luceneDoc) {
        compile(broker);

        if (!isValid) {
            return;
        }

        final XQuery xquery = broker.getBrokerPool().getXQueryService();
        final NodeProxy currentNode = new NodeProxy(document, nodeId);
        try {
            Sequence result = xquery.execute(broker, compiled, currentNode);

            if (!result.isEmpty()) {
                if (isHierarchical) {
                    String paths[] = new String[result.getItemCount()];
                    int j = 0;
                    for (SequenceIterator i = result.unorderedIterator(); i.hasNext(); j++) {
                        paths[j] = i.nextItem().getStringValue();
                    }
                    luceneDoc.add(new FacetField(dimension, paths));
                } else {
                    for (SequenceIterator i = result.unorderedIterator(); i.hasNext(); ) {
                        luceneDoc.add(new FacetField(dimension, i.nextItem().getStringValue()));
                    }
                }
            }
        } catch (PermissionDeniedException e) {
            e.printStackTrace();
        } catch (XPathException e) {
            e.printStackTrace();
        }
    }

    private void compile(DBBroker broker) {
        if (compiled == null && isValid) {
            final XQuery xquery = broker.getBrokerPool().getXQueryService();
            final XQueryContext context = new XQueryContext(broker.getBrokerPool());
            try {
                this.compiled = xquery.compile(broker, context, expression);
            } catch (XPathException | PermissionDeniedException e) {
                LOG.error("Failed to compile facet expression: " + expression + ": " + e.getMessage(), e);
                isValid = false;
            }
        }
    }
}
