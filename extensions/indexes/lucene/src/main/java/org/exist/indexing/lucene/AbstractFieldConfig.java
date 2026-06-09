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
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
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
            sb.append(moduleImport.at);
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

    protected abstract void build(DBBroker broker, NodeProxy contextNode, Document luceneDoc, CharSequence text);

    protected void doBuild(DBBroker broker, NodeProxy contextNode, Document luceneDoc, CharSequence text)
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
        try {
            Sequence result = xquery.execute(broker, compiled, contextNode);

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

    /**
     * Resolves {@code at} on {@code &lt;module&gt;} for {@code import module} when compiling field/facet
     * expressions. Must be called with the {@code &lt;module&gt;} element (not {@code &lt;field&gt;}).
     * <p>
     * Relative paths are resolved to {@code xmldb:exist:///db/…/module.xql} using the mirrored layout
     * {@code /db/system/config/db/&lt;col&gt;/collection.xconf} → data collection {@code /db/&lt;col&gt;}.
     */
    public static String resolveModuleImportAt(final Element moduleElement, final String location) {
        if (location == null || location.isEmpty()) {
            return "";
        }
        try {
            if (new URI(location).isAbsolute()) {
                return location;
            }
        } catch (final URISyntaxException e) {
            // treat as relative
        }
        if (moduleElement.getOwnerDocument() instanceof final DocumentImpl docImpl) {
            final Collection configColl = docImpl.getCollection();
            if (configColl != null) {
                final String fromColl = resolveRelativeAtFromConfigCollection(configColl.getURI(), location);
                if (fromColl != null) {
                    return fromColl;
                }
            }
        }
        final String baseURI = indexConfigFragmentBaseURI(moduleElement);
        final String rootedBase = toCollectionConfigBasePath(baseURI);
        final String fromDom = resolveRelativeAtFromRootedConfigPath(rootedBase, location);
        return fromDom != null ? fromDom : location;
    }

    @Nullable
    private static String resolveRelativeAtFromConfigCollection(@Nullable final XmldbURI configCollectionUri, final String location) {
        if (configCollectionUri == null || location.isEmpty()) {
            return null;
        }
        try {
            if (new URI(location).isAbsolute()) {
                return null;
            }
        } catch (final URISyntaxException e) {
            return null;
        }
        final String cp = configCollectionUri.getCollectionPath();
        if (cp == null) {
            return null;
        }
        final String prefix =
                CollectionConfigurationManager.CONFIG_COLLECTION + '/' + XmldbURI.ROOT_COLLECTION_NAME + '/';
        if (!cp.startsWith(prefix)) {
            return null;
        }
        final String tail = cp.substring(prefix.length());
        return XmldbURI.EMBEDDED_SERVER_URI_PREFIX + XmldbURI.ROOT_COLLECTION + '/' + tail + '/' + location;
    }

    @Nullable
    private static String resolveRelativeAtFromRootedConfigPath(@Nullable final String rootedBase, final String location) {
        if (rootedBase == null || rootedBase.isEmpty()) {
            return null;
        }
        try {
            final URI uri = new URI(location);
            if (uri.isAbsolute()) {
                return null;
            }
            if (!rootedBase.startsWith(CollectionConfigurationManager.CONFIG_COLLECTION)) {
                return null;
            }
            String base = rootedBase.substring(CollectionConfigurationManager.CONFIG_COLLECTION.length());
            final int lastSlash = base.lastIndexOf('/');
            if (lastSlash > -1) {
                base = base.substring(0, lastSlash);
            }
            return XmldbURI.EMBEDDED_SERVER_URI_PREFIX + base + '/' + location;
        } catch (final URISyntaxException e) {
            return null;
        }
    }

    /**
     * Base URI for DOM fragments: nested nodes (e.g. {@code &lt;module&gt;}) may have an empty
     * {@link org.w3c.dom.Node#getBaseURI()}; fall back to the owner document.
     */
    private static String indexConfigFragmentBaseURI(final Element configElement) {
        String baseURI = configElement.getBaseURI();
        if (baseURI != null && !baseURI.isEmpty()) {
            return baseURI;
        }
        final org.w3c.dom.Document doc = configElement.getOwnerDocument();
        if (doc == null) {
            return "";
        }
        baseURI = doc.getDocumentURI();
        if (baseURI != null && !baseURI.isEmpty()) {
            return baseURI;
        }
        final Element root = doc.getDocumentElement();
        if (root != null) {
            baseURI = root.getBaseURI();
            if (baseURI != null && !baseURI.isEmpty()) {
                return baseURI;
            }
        }
        return "";
    }

    /**
     * {@link org.w3c.dom.Node#getBaseURI()} for stored collection.xconf documents is typically an
     * {@code xmldb:} URI. Relative {@code at=} paths in {@code <module>} must still resolve against
     * the mirrored {@code /db/...} collection like plain {@code /db/system/config/...} bases.
     */
    @Nullable
    private static String toCollectionConfigBasePath(@Nullable final String baseURI) {
        if (baseURI == null || baseURI.isEmpty()) {
            return baseURI;
        }
        if (baseURI.startsWith(CollectionConfigurationManager.CONFIG_COLLECTION)) {
            return baseURI;
        }
        if (baseURI.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
            try {
                final String collPath = XmldbURI.create(baseURI).getCollectionPath();
                if (collPath != null && collPath.startsWith(CollectionConfigurationManager.CONFIG_COLLECTION)) {
                    return collPath;
                }
            } catch (final IllegalArgumentException ignored) {
                // fall through
            }
        }
        return baseURI;
    }
}
