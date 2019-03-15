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
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;

public class FacetsBuilder {

    static final Logger LOG = LogManager.getLogger(FacetsBuilder.class);

    private final LuceneFacetConfig config;

    private boolean isValid = true;

    private CompiledXQuery compiled = null;

    public FacetsBuilder(LuceneFacetConfig config) {
        this.config = config;
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
                for (SequenceIterator i = result.unorderedIterator(); i.hasNext(); ) {
                    luceneDoc.add(new FacetField(config.getCategory(), i.nextItem().getStringValue()));
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
                this.compiled = xquery.compile(broker, context, config.getExpression());
            } catch (XPathException | PermissionDeniedException e) {
                LOG.error("Failed to compile facet expression: " + config.getExpression() + ": " + e.getMessage(), e);
                isValid = false;
            }
        }
    }
}
