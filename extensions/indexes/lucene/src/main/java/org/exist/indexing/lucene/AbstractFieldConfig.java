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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Element;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract configuration corresponding to either a field or facet element nested inside
 * an index definition 'text' element. Adds the possibility to create index content based
 * on an arbitrary XQuery expression.
 *
 * @author Wolfgang Meier
 */
public abstract class AbstractFieldConfig {

    public final static String XPATH_ATTR = "expression";

    protected static final Logger LOG = LogManager.getLogger(AbstractFieldConfig.class);

    protected final Optional<String> expression;
    protected boolean isValid = true;
    private CompiledXQuery compiled = null;

    public AbstractFieldConfig(final LuceneConfig config, final Element configElement, final Map<String, String> namespaces) {
        final String xpath = configElement.getAttribute(XPATH_ATTR);
        if (xpath.isEmpty()) {
            expression = Optional.empty();
            return;
        }

        final StringBuilder sb = new StringBuilder();
        namespaces.forEach((prefix, uri) -> {
            if (!"xml".equals(prefix)) {
                sb.append("declare namespace ").append(prefix);
                sb.append("=\"").append(uri).append("\";\n");
            }
        });
        config.getImports().ifPresent(moduleImports -> moduleImports.forEach((moduleImport -> {
            sb.append("import module namespace ");
            sb.append(moduleImport.prefix);
            sb.append("=\"");
            sb.append(moduleImport.uri);
            sb.append("\" at \"");
            sb.append(resolveURI(configElement.getBaseURI(), moduleImport.at));
            sb.append("\";\n");
        })));
        sb.append(xpath);

        expression = Optional.of(sb.toString());
    }

    @Nullable
    public Analyzer getAnalyzer() {
        return null;
    }

    protected abstract void processResult(final Sequence result, final Document luceneDoc) throws XPathException;

    protected abstract void processText(CharSequence text, Document luceneDoc);

    protected abstract void build(DBBroker broker, DocumentImpl document, NodeId nodeId, Document luceneDoc, CharSequence text);

    protected void doBuild(DBBroker broker, DocumentImpl document, NodeId nodeId, Document luceneDoc, CharSequence text)
            throws PermissionDeniedException, XPathException {
        if (expression.isEmpty()) {
            processText(text, luceneDoc);
            return;
        }

        compile(broker);

        if (!isValid) {
            return;
        }

        final XQuery xquery = broker.getBrokerPool().getXQueryService();
        final NodeProxy currentNode = new NodeProxy(null, document, nodeId);
        try {
            Sequence result = xquery.execute(broker, compiled, currentNode);

            if (!result.isEmpty()) {
                processResult(result, luceneDoc);
            }
        } catch (PermissionDeniedException | XPathException e) {
            isValid = false;
            throw e;
        } finally {
            compiled.reset();
            compiled.getContext().reset();
        }
    }

    private void compile(final DBBroker broker) {
        if (compiled == null && isValid) {
            expression.ifPresent((code) -> compiled = compile(broker, code));
        }
    }

    protected CompiledXQuery compile(final DBBroker broker, final String code) {
        final XQuery xquery = broker.getBrokerPool().getXQueryService();
        final XQueryContext context = new XQueryContext(broker.getBrokerPool());
        try {
            return xquery.compile(context, code);
        } catch (XPathException | PermissionDeniedException e) {
            LOG.error("Failed to compile expression: {}: {}", code, e.getMessage(), e);
            isValid = false;
            return null;
        }
    }

    private String resolveURI(final String baseURI, final String location) {
        try {
            final URI uri = new URI(location);
            if (!uri.isAbsolute() && baseURI != null && baseURI.startsWith(CollectionConfigurationManager.CONFIG_COLLECTION)) {
                String base = baseURI.substring(CollectionConfigurationManager.CONFIG_COLLECTION.length());
                final int lastSlash = base.lastIndexOf('/');
                if (lastSlash > -1) {
                    base = base.substring(0, lastSlash);
                }
                return XmldbURI.EMBEDDED_SERVER_URI_PREFIX + base + '/' + location;
            }
        } catch (URISyntaxException e) {
            // ignore and return location
        }
        return location;
    }
}
