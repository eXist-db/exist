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
package org.exist.collections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.storage.DBBroker;
import org.exist.util.SaxonConfiguration;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.validation.Xsd11SchemaDetection;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xmlresolver.Resolver;

import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.ValidatorHandler;
import java.io.IOException;
import java.util.Optional;

/**
 * Store-time XSD 1.1 validation infrastructure for {@link MutableCollection}: resolving/caching
 * the XSD 1.1 {@link Schema} a document needs (if any), and driving validation against it.
 * <p>
 * Package-private and static-only -- this is validation/schema infrastructure, not Collection
 * state, split out of {@link MutableCollection} per review discussion on
 * <a href="https://github.com/eXist-db/exist/pull/6530">#6530</a>/<a
 * href="https://github.com/eXist-db/exist/pull/6551">#6551</a>.
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/5541">#5541</a>
 */
final class Xsd11ValidationHelper {

    private static final Logger LOG = LogManager.getLogger(Xsd11ValidationHelper.class);

    private Xsd11ValidationHelper() {
    }

    /**
     * Discards all cached {@link #resolveXsd11SchemaForNamespace} results -- called alongside
     * {@code Jaxp.clearXsd11DetectionCache()} by {@code validation:clear-grammar-cache()} (via
     * {@link MutableCollection#clearXsd11SchemaByNamespaceCache()}) so one admin action clears both
     * XSD-1.1-detection caches, not just the schemaLocation-hint one.
     */
    static void clearSchemaCache() {
        Xsd11SchemaCache.clear();
    }

    /**
     * Lazily compiles, once per JVM, the no-pre-supplied-source XSD 1.1 {@link Schema} used for the
     * case where the instance carries its own {@code xsi:schemaLocation}/{@code
     * noNamespaceSchemaLocation} hint that needs XSD 1.1 to load (detected via
     * {@link Xsd11SchemaDetection#detectXsd11ViaSchemaLocation}): unlike
     * {@link #resolveXsd11SchemaForNamespace(Resolver, String)}, no pre-supplied Source is needed
     * here, since dynamic discovery can follow the instance's own hint itself, the same way the
     * default SAX pipeline follows it for XSD 1.0.
     * <p>
     * Initialization-on-demand holder idiom: thread-safe with no explicit synchronization, relying
     * on the JVM's class-initialization guarantees instead of manual double-checked locking.
     */
    private static final class Xsd11DynamicDiscoverySchemaHolder {
        private static final Schema INSTANCE;
        static {
            try {
                INSTANCE = SchemaFactory.newInstance(Namespaces.XSD_1_1_NS).newSchema();
            } catch (final SAXException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    private static Schema getXsd11DynamicDiscoverySchema() {
        return Xsd11DynamicDiscoverySchemaHolder.INSTANCE;
    }

    /**
     * Resolves the system catalog's grammar for {@code namespace} and, only if that grammar
     * actually requires XSD 1.1 to load, compiles and caches an explicit-Source XSD 1.1
     * {@link Schema} for it; returns {@code null} when the resolved grammar loads fine under the
     * standard XSD 1.0 {@link SchemaFactory} (the overwhelmingly common case), so the default SAX
     * pipeline keeps handling that case exactly as before.
     *
     * <p>"Does it need 1.1?" is decided empirically -- attempt the cheap, side-effect-free compile
     * via the plain (1.0) {@code SchemaFactory} first; if that throws, the grammar needs an
     * XSD-1.1-aware loader. This is the only reliable signal here: the W3C XSD 1.1 meta-schema
     * itself (the motivating case, see {@code https://github.com/eXist-db/exist/issues/5541}) does
     * not self-declare {@code vc:minVersion} the way a hand-authored 1.1 schema would, so peeking
     * for that attribute (as {@link Xsd11SchemaDetection} does for the
     * schemaLocation-hint case below) does not apply to namespace-only resolution.</p>
     *
     * <p>A no-pre-supplied-source {@code Schema}'s dynamic discovery only resolves grammars via an
     * instance's own {@code xsi:schemaLocation} hint, not via "this document's root namespace has
     * no hint at all, but happens to be a namespace the catalog can resolve" (confirmed
     * empirically -- a no-source {@code Schema} fails with {@code cvc-elt.1.a} for exactly this
     * case, even with the resolver wired on) -- hence the explicit {@code Source} here.</p>
     */
    @Nullable
    private static Schema resolveXsd11SchemaForNamespace(final Resolver catalogResolver, final String namespace) throws SAXException {
        final Optional<Schema> cached = Xsd11SchemaCache.get(namespace);
        if (cached != null) {
            return cached.orElse(null);
        }

        Schema result = null;
        try {
            final Source probeSource = catalogResolver.resolve(namespace, null);
            if (probeSource != null) {
                try {
                    SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(probeSource);
                    // Loads fine under the standard (1.0) pipeline -- nothing special needed.
                } catch (final SAXException loadsAs10Failure) {
                    final Source compileSource = catalogResolver.resolve(namespace, null);
                    if (compileSource != null) {
                        final SchemaFactory xsd11Factory = SchemaFactory.newInstance(Namespaces.XSD_1_1_NS);
                        xsd11Factory.setResourceResolver(catalogResolver);
                        result = xsd11Factory.newSchema(compileSource);
                    }
                }
            }
        } catch (final TransformerException e) {
            throw new SAXException(e);
        }

        Xsd11SchemaCache.put(namespace, Optional.ofNullable(result));
        return result;
    }

    /**
     * Parses/validates {@code source} via {@code xmlReader1} as before, unless the document needs
     * an XSD 1.1-capable loader -- the bundled Xerces fork's XSD 1.1 support is only wired into
     * the JAXP {@code SchemaFactory}/{@code Validator} API, never into this dynamic-discovery SAX
     * pipeline (confirmed empirically: setting Xerces' internal {@code schema/version} property on
     * a standard {@code XMLReader} throws {@code SAXNotRecognizedException}). Two independent ways
     * this can be needed, checked up front from a single peek of the root element via
     * {@link Xsd11SchemaDetection#peekRootElement(InputSource)} (never via
     * retry-after-failure: by the time a SAX parse fails, the {@link IndexInfo}'s
     * {@link org.exist.Indexer}/triggers have already received partial events for an aborted
     * document, so re-feeding them via a second pass is not safe):
     *
     * <ol>
     *   <li>The document being stored is itself a schema document (root element in the W3C XML
     *   Schema namespace, no {@code schemaLocation} hint at all): see
     *   {@link #resolveXsd11SchemaForNamespace(Resolver, String)}.</li>
     *   <li>The instance carries its own {@code xsi:schemaLocation}/{@code
     *   noNamespaceSchemaLocation} hint that resolves to a schema declaring {@code
     *   vc:minVersion="1.1"}: see {@link Xsd11SchemaDetection}, shared with
     *   {@code validation:jaxp()}'s own up-front peek.</li>
     * </ol>
     *
     * <p>Anything neither case catches (most prominently: a catalog-mediated {@code
     * schemaLocation} hint whose target doesn't self-declare {@code vc:minVersion}) falls through
     * to the default pipeline unchanged, and may still fail with {@code cvc-elt.1.a} -- a known,
     * accepted limitation of peek-only detection with no retry safety net.</p>
     *
     * <p>The peek above, and the validate/store double-invocation of this method itself (once via
     * {@code validatorFn}, once via {@code parserFn} in {@code MutableCollection.storeXmlDocument}),
     * both read {@code source} more than once via the same {@link InputSource#getByteStream()}/
     * {@link InputSource#getCharacterStream()} methods. Safe because every {@link InputSource}
     * actually reaching {@code storeDocument()}/{@code storeXmlDocument()} today (see {@code
     * org.exist.util.StringInputSource} and its siblings) vends a fresh stream per call by
     * convention -- a precondition of this method, not something it (or its callers) re-validates.</p>
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/5541">#5541</a>
     */
    static void parseOrValidateXmlSource(final DBBroker broker, final XMLReader xmlReader1, final IndexInfo indexInfo, final InputSource source) throws SAXException, IOException {
        indexInfo.setReader(xmlReader1, null);

        boolean schemaValidationEnabled;
        try {
            schemaValidationEnabled = xmlReader1.getFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA);
        } catch (final SAXException e) {
            schemaValidationEnabled = false;
            LOG.debug("Could not determine if schema validation is enabled, assuming disabled: {}", e.getMessage());
        }

        if (schemaValidationEnabled) {
            final Resolver catalogResolver = SaxonConfiguration.resolveCatalogResolver(broker.getBrokerPool().getConfiguration());

            // One StAX pass yields both the root namespace (case 1) and, if case 1 doesn't apply,
            // the root attributes detectXsd11ViaSchemaLocation needs (case 2) -- avoids peeking the
            // same root start tag twice.
            final Xsd11SchemaDetection.RootElementInfo rootElement = Xsd11SchemaDetection.peekRootElement(source);
            final String rootNamespace = rootElement == null ? null : rootElement.namespaceUri();
            if (catalogResolver != null && rootNamespace != null) {
                final Schema schema = resolveXsd11SchemaForNamespace(catalogResolver, rootNamespace);
                if (schema != null) {
                    validateWithXsd11Schema(xmlReader1, schema, catalogResolver, indexInfo, source);
                    return;
                }
            }

            if (Xsd11SchemaDetection.detectXsd11ViaSchemaLocation(broker.getCurrentSubject().getName(), rootElement, source.getSystemId())) {
                validateWithXsd11Schema(xmlReader1, getXsd11DynamicDiscoverySchema(), catalogResolver, indexInfo, source);
                return;
            }
        }

        xmlReader1.parse(source);
    }

    /**
     * Validates {@code source} against {@code schema}, feeding the resulting SAX events into
     * {@code indexInfo}'s indexing/trigger pipeline.
     * <p>
     * Uses {@link Schema#newValidatorHandler()} driven by {@code xmlReader1.parse(source)} rather
     * than {@link Schema#newValidator()}'s {@code validate(Source, Result)} -- a {@link
     * javax.xml.transform.sax.SAXResult SAXResult} has no lexical-handler hook (confirmed by
     * inspecting the bundled Xerces fork's {@code ValidatorImpl}: it wires a {@code SAXResult}'s
     * {@code ContentHandler} but never a {@code LexicalHandler}), so comments/CDATA sections would
     * otherwise be silently dropped on this path, unlike the default {@code xmlReader1.parse(source)}
     * path which {@link IndexInfo#setReader} already wires with both.
     * <p>
     * {@link ValidatorHandler} itself has no public API to register a downstream
     * {@link LexicalHandler} (confirmed empirically: it does not forward comment()/startCDATA()/
     * endCDATA() to its registered {@code ContentHandler} even when that handler also implements
     * {@code LexicalHandler}), so {@link Xsd11LexicalHandlerForwarder} sits in front of it,
     * forwarding content events to the validator (so validation/indexing both still happen) and
     * lexical events directly to {@code indexInfo}'s real lexical handler, bypassing the validator
     * for those (comments/CDATA boundaries are not part of any XSD content model -- CDATA's
     * character content is still validated normally, via the ordinary {@code characters()} event).
     * <p>
     * Reuses the caller's already-configured {@code xmlReader1} (rather than constructing a fresh
     * {@link XMLReader}) purely to drive the parse; its content/lexical handlers are repointed at
     * {@link Xsd11LexicalHandlerForwarder} for the duration of this call.
     */
    private static void validateWithXsd11Schema(final XMLReader xmlReader1, final Schema schema, @Nullable final Resolver catalogResolver, final IndexInfo indexInfo, final InputSource source) throws SAXException, IOException {
        final ValidatorHandler validatorHandler = schema.newValidatorHandler();
        if (catalogResolver != null) {
            validatorHandler.setResourceResolver(catalogResolver);
        }
        validatorHandler.setErrorHandler(indexInfo.getIndexer());
        validatorHandler.setContentHandler(indexInfo.getContentHandler());
        final Xsd11LexicalHandlerForwarder forwarder = new Xsd11LexicalHandlerForwarder(validatorHandler, indexInfo.getLexicalHandler());

        // xmlReader1 is the pooled, dynamic-discovery-validating reader -- its own schema
        // validation feature must be off for this call, or it independently (mis)validates the
        // document against the default XSD 1.0 pipeline in parallel with validatorHandler's XSD
        // 1.1 validation, producing spurious cvc-elt.1.a/s4s-att-not-allowed errors. Saved and
        // restored rather than left disabled, since storeXmlDocument() reuses this same xmlReader1
        // instance for a second, separate parseOrValidateXmlSource() call (the store phase).
        final boolean wasValidating = xmlReader1.getFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA);
        try {
            xmlReader1.setFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA, false);
            xmlReader1.setFeature(Namespaces.SAX_VALIDATION, false);
            xmlReader1.setFeature(Namespaces.SAX_VALIDATION_DYNAMIC, false);

            xmlReader1.setContentHandler(forwarder);
            xmlReader1.setProperty(Namespaces.SAX_LEXICAL_HANDLER, forwarder);
            xmlReader1.parse(source);
        } finally {
            xmlReader1.setFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA, wasValidating);
            xmlReader1.setFeature(Namespaces.SAX_VALIDATION, wasValidating);
            xmlReader1.setFeature(Namespaces.SAX_VALIDATION_DYNAMIC, wasValidating);
        }
    }

    /**
     * Splits incoming SAX events from a single source (an {@link XMLReader}) across two
     * downstream consumers: content events go to {@code contentDelegate} (a {@link
     * ValidatorHandler}, so schema validation still happens), lexical events go directly to
     * {@code lexicalDelegate}, bypassing the validator -- see {@link
     * #validateWithXsd11Schema(XMLReader, Schema, Resolver, IndexInfo, InputSource)} for why.
     */
    private record Xsd11LexicalHandlerForwarder(ContentHandler contentDelegate, LexicalHandler lexicalDelegate)
            implements ContentHandler, LexicalHandler {

        @Override
        public void setDocumentLocator(final Locator locator) {
            contentDelegate.setDocumentLocator(locator);
        }

        @Override
        public void startDocument() throws SAXException {
            contentDelegate.startDocument();
        }

        @Override
        public void endDocument() throws SAXException {
            contentDelegate.endDocument();
        }

        @Override
        public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
            contentDelegate.startPrefixMapping(prefix, uri);
        }

        @Override
        public void endPrefixMapping(final String prefix) throws SAXException {
            contentDelegate.endPrefixMapping(prefix);
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes atts) throws SAXException {
            contentDelegate.startElement(uri, localName, qName, atts);
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) throws SAXException {
            contentDelegate.endElement(uri, localName, qName);
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
            contentDelegate.characters(ch, start, length);
        }

        @Override
        public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
            contentDelegate.ignorableWhitespace(ch, start, length);
        }

        @Override
        public void processingInstruction(final String target, final String data) throws SAXException {
            contentDelegate.processingInstruction(target, data);
        }

        @Override
        public void skippedEntity(final String name) throws SAXException {
            contentDelegate.skippedEntity(name);
        }

        @Override
        public void startDTD(final String name, final String publicId, final String systemId) throws SAXException {
            lexicalDelegate.startDTD(name, publicId, systemId);
        }

        @Override
        public void endDTD() throws SAXException {
            lexicalDelegate.endDTD();
        }

        @Override
        public void startEntity(final String name) throws SAXException {
            lexicalDelegate.startEntity(name);
        }

        @Override
        public void endEntity(final String name) throws SAXException {
            lexicalDelegate.endEntity(name);
        }

        @Override
        public void startCDATA() throws SAXException {
            lexicalDelegate.startCDATA();
        }

        @Override
        public void endCDATA() throws SAXException {
            lexicalDelegate.endCDATA();
        }

        @Override
        public void comment(final char[] ch, final int start, final int length) throws SAXException {
            lexicalDelegate.comment(ch, start, length);
        }
    }
}
