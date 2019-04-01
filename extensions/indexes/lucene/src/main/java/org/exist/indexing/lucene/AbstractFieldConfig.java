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
import org.exist.collections.CollectionConfigurationManager;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Element;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public abstract class AbstractFieldConfig {

    public final static String XPATH_ATTR = "expression";

    static final Logger LOG = LogManager.getLogger(AbstractFieldConfig.class);

    protected String expression;
    private boolean isValid = true;
    private CompiledXQuery compiled = null;

    public AbstractFieldConfig(LuceneConfig config, Element configElement, Map<String, String> namespaces) throws DatabaseConfigurationException {
        final String xpath = configElement.getAttribute(XPATH_ATTR);
        if (xpath == null || xpath.isEmpty()) {
            throw new DatabaseConfigurationException("facet definition needs an attribute 'xpath': " + configElement.toString());
        }

        final StringBuilder sb = new StringBuilder();
        namespaces.forEach((prefix, uri) -> {
            if (!prefix.equals("xml")) {
                sb.append("declare namespace ").append(prefix);
                sb.append("=\"").append(uri).append("\";\n");
            }
        });
        if (config.getImports() != null) {
            config.getImports().stream().forEach((moduleImport -> {
                sb.append("import module namespace ");
                sb.append(moduleImport.prefix);
                sb.append("=\"");
                sb.append(moduleImport.uri);
                sb.append("\" at \"");
                sb.append(resolveURI(configElement.getBaseURI(), moduleImport.at));
                sb.append("\";\n");
            }));
        }
        sb.append(xpath);

        this.expression = sb.toString();
    }

    public String getExpression() {
        return expression;
    }

    abstract void processResult(Sequence result, Document luceneDoc) throws XPathException;

    abstract void build(DBBroker broker, DocumentImpl document, NodeId nodeId, Document luceneDoc);

    protected void doBuild(DBBroker broker, DocumentImpl document, NodeId nodeId, Document luceneDoc) throws PermissionDeniedException, XPathException {
        compile(broker);

        if (!isValid) {
            return;
        }

        final XQuery xquery = broker.getBrokerPool().getXQueryService();
        final NodeProxy currentNode = new NodeProxy(document, nodeId);
        try {
            Sequence result = xquery.execute(broker, compiled, currentNode);

            if (!result.isEmpty()) {
                processResult(result, luceneDoc);
            }
        } catch (PermissionDeniedException | XPathException e) {
            isValid = false;
            throw e;
        }
    }

    private void compile(DBBroker broker) {
        if (compiled == null && isValid) {
            final XQuery xquery = broker.getBrokerPool().getXQueryService();
            final XQueryContext context = new XQueryContext(broker.getBrokerPool());
            try {
                this.compiled = xquery.compile(broker, context, expression);
            } catch (XPathException | PermissionDeniedException e) {
                LOG.error("Failed to compile expression: " + expression + ": " + e.getMessage(), e);
                isValid = false;
            }
        }
    }

    private String resolveURI(String baseURI, String location) {
        try {
            final URI uri = new URI(location);
            if (!uri.isAbsolute()) {
                if (baseURI != null && baseURI.startsWith(CollectionConfigurationManager.CONFIG_COLLECTION)) {
                    baseURI = baseURI.substring(CollectionConfigurationManager.CONFIG_COLLECTION.length());
                    final int lastSlash = baseURI.lastIndexOf('/');
                    if (lastSlash > -1) {
                        baseURI = baseURI.substring(0, lastSlash);
                    }
                    return XmldbURI.EMBEDDED_SERVER_URI_PREFIX + baseURI + '/' + location;
                }
            }
        } catch (URISyntaxException e) {
            // ignore and return location
        }
        return location;
    }
}
