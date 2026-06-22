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
package org.exist.validation;

import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.SchemaReader;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.ValidationDriver;
import com.thaiopensource.validate.rng.CompactSchemaReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.exist.Namespaces;
import org.exist.resolver.ResolverFactory;
import org.exist.resolver.XercesXmlResolverAdapter;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.ExistSAXParserFactory;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.validation.resolver.AnyUriResolver;
import org.exist.validation.resolver.SearchResourceResolver;
import org.xml.sax.*;
import org.xmlresolver.Resolver;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.ValidatorHandler;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;

/**
 * Validate XML documents with their grammars (DTD's and Schemas).
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class Validator {

    private static final Logger logger = LogManager.getLogger(Validator.class);

    private static final String XSD_1_1_NS = "http://www.w3.org/XML/XMLSchema/v1.1";

    /**
     * Generous upper bound on how much of the document's prolog (up to and including the root
     * start tag) {@link #validateParse(InputStream, String, String)}'s XSD 1.1 peek may read
     * before giving up on {@code mark()}/{@code reset()} -- real documents' prologs are at most a
     * few KB even with many namespace declarations/attributes.
     */
    private static final int XSD11_PEEK_MARK_LIMIT = 65536;

    private final BrokerPool brokerPool;
    private final Subject subject;
    private final GrammarPool grammarPool;
    private final Resolver systemCatalogResolver;

    /**
     * Setup Validator object with Broker Pool as db connection.
     *
     * @param brokerPool brokerPool the broker pool
     * @param subject    the subject to use when accessing resources from the database
     */
    public Validator(final BrokerPool brokerPool, final Subject subject) {
        logger.info("Initializing Validator.");

        this.brokerPool = brokerPool;
        this.subject = subject;

        // Check xerces version        
        final StringBuilder xmlLibMessage = new StringBuilder();
        if (!XmlLibraryChecker.hasValidParser(xmlLibMessage)) {
            logger.error(xmlLibMessage);
        }

        final Configuration config = brokerPool.getConfiguration();

        // setup grammar brokerPool
        this.grammarPool = (GrammarPool) config.getProperty(GrammarPool.GRAMMAR_POOL_ELEMENT);

        // setup system wide catalog resolver
        this.systemCatalogResolver = (Resolver) config.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER);

    }

    /**
     * Validate XML data using system catalog. XSD and DTD only.
     *
     * @param stream XML input.
     * @return Validation report containing all validation info.
     */
    public ValidationReport validate(final InputStream stream) {
        return validate(stream, null);
    }

    /**
     * Validate XML data from reader using specified grammar.
     *
     * @param grammarUrl User supplied path to grammar, or null.
     * @param stream     XML input.
     * @return Validation report containing all validation info.
     */
    public ValidationReport validate(final InputStream stream, @Nullable String grammarUrl) {
        return validate(stream, grammarUrl, null);
    }

    /**
     * Validate XML data from reader using specified grammar.
     *
     * @param grammarUrl User supplied path to grammar, or null.
     * @param stream     XML input.
     * @param documentBaseUri the base URI of {@code stream}'s document (e.g. the stored document's
     *                        own URI), or {@code null} if unknown -- used only to resolve the
     *                        instance's own {@code xsi:schemaLocation} hint (see {@link
     *                        Xsd11SchemaDetection#detectXsd11ViaSchemaLocation}) when deciding
     *                        whether an XSD 1.1-capable validator is needed; validation against an
     *                        explicitly-supplied {@code grammarUrl} does not otherwise depend on it.
     * @return Validation report containing all validation info.
     */
    public ValidationReport validate(final InputStream stream, @Nullable String grammarUrl, @Nullable final String documentBaseUri) {

        // repair path to local resource
        if (grammarUrl != null) {
            grammarUrl = ResolverFactory.fixupExistCatalogUri(grammarUrl);
        }

        if (grammarUrl != null &&
                (grammarUrl.endsWith(".rng") || grammarUrl.endsWith(".rnc") ||
                        grammarUrl.endsWith(".nvdl") || grammarUrl.endsWith(".sch"))) {
            // Validate with Jing
            return validateJing(stream, grammarUrl);

        } else {
            // Validate with Xerces
            return validateParse(stream, grammarUrl, documentBaseUri);
        }

    }

    /**
     * Validate XML data from reader using specified grammar with Jing.
     *
     * @param stream     XML input document.
     * @param grammarUrl User supplied path to grammar.
     * @return Validation report containing all validation info.
     */
    public ValidationReport validateJing(final InputStream stream, final String grammarUrl) {

        final ValidationReport report = new ValidationReport();
        try {
            report.start();

            // Setup validation properties. see Jing interface
            final PropertyMapBuilder properties = new PropertyMapBuilder();
            ValidateProperty.ERROR_HANDLER.put(properties, report);

            // Copied from Jing code ; the Compact syntax seem to have a different
            // Schema reader. To be investigated. http://www.thaiopensource.com/relaxng/api/jing/index.html
            final SchemaReader schemaReader = grammarUrl.endsWith(".rnc") ? CompactSchemaReader.getInstance() : null;

            // Setup driver
            final ValidationDriver driver = new ValidationDriver(properties.toPropertyMap(), schemaReader);

            // Load schema
            driver.loadSchema(new InputSource(grammarUrl));

            // Validate XML instance
            driver.validate(new InputSource(stream));

        } catch (final IOException ex) {
            logger.error(ex);
            report.setThrowable(ex);

        } catch (final SAXException ex) {
            logger.debug(ex);
            report.setThrowable(ex);

        } finally {
            report.stop();
        }
        return report;
    }

    /**
     * Validate XML data using system catalog. XSD and DTD only.
     *
     * @param stream XML input.
     * @return Validation report containing all validation info.
     */
    public ValidationReport validateParse(final InputStream stream) {
        return validateParse(stream, null);
    }

    /**
     * Validate XML data from reader using specified grammar.
     *
     * @param grammarUrl User supplied path to grammar.
     * @param stream     XML input.
     * @return Validation report containing all validation info.
     */
    public ValidationReport validateParse(final InputStream stream, final String grammarUrl) {
        return validateParse(stream, grammarUrl, null);
    }

    /**
     * Validate XML data from reader using specified grammar.
     *
     * @param grammarUrl User supplied path to grammar.
     * @param stream     XML input.
     * @param documentBaseUri see {@link #validate(InputStream, String, String)}.
     * @return Validation report containing all validation info.
     */
    public ValidationReport validateParse(final InputStream stream, final String grammarUrl, @Nullable final String documentBaseUri) {

        logger.debug("Start validation.");

        final ValidationReport report = new ValidationReport();
        final ValidationContentHandler contenthandler = new ValidationContentHandler();

        final BufferedInputStream bufferedStream = new BufferedInputStream(stream, XSD11_PEEK_MARK_LIMIT);
        final ValidationReport xsd11Report = tryValidateWithXsd11Schema(bufferedStream, documentBaseUri, contenthandler, report);
        if (xsd11Report != null) {
            return xsd11Report;
        }

        return validateParseDefault(bufferedStream, grammarUrl, contenthandler, report);
    }

    /**
     * The XSD 1.0/DTD dynamic-discovery SAX pipeline {@link #validateParse} falls through to when
     * no XSD 1.1 hint was detected (or could be resolved) -- unchanged behavior, just extracted
     * out of {@code validateParse} to keep that method's own branching to one decision (XSD 1.1
     * or not).
     */
    private ValidationReport validateParseDefault(final InputStream stream, String grammarUrl, final ValidationContentHandler contenthandler, final ValidationReport report) {
        try {

            final XMLReader xmlReader = getXMLReader(contenthandler, report);

            if (grammarUrl == null) {

                // Scenario 1 : no params - use system catalog
                if (logger.isDebugEnabled()) {
                    logger.debug("Validation using system catalog.");
                }
                XercesXmlResolverAdapter.setXmlReaderEntityResolver(xmlReader, systemCatalogResolver);

            } else if (grammarUrl.endsWith(".xml")) {
                // Scenario 2 : path to catalog (xml)
                if (logger.isDebugEnabled()) {
                    logger.debug("Validation using user specified catalog '{}'.", grammarUrl);
                }
                final Resolver resolver = ResolverFactory.newResolver(List.of(Tuple(grammarUrl, Optional.empty())));
                XercesXmlResolverAdapter.setXmlReaderEntityResolver(xmlReader, resolver);

            } else if (grammarUrl.endsWith("/")) {
                // Scenario 3 : path to collection ("/"): search.
                if (logger.isDebugEnabled()) {
                    logger.debug("Validation using searched grammar, start from '{}'.", grammarUrl);
                }
                final XMLEntityResolver resolver = new SearchResourceResolver(brokerPool, subject, grammarUrl);
                XercesXmlResolverAdapter.setXmlReaderEntityResolver(xmlReader, resolver);

            } else {
                if (grammarUrl.startsWith("/db")) {
                    grammarUrl = "xmldb://" + grammarUrl;
                }

                // Scenario 4 : path to grammar (xsd, dtd) specified.
                if (logger.isDebugEnabled()) {
                    logger.debug("Validation using specified grammar '{}'.", grammarUrl);
                }
                final AnyUriResolver resolver = new AnyUriResolver(grammarUrl);
                xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_ENTITYRESOLVER, resolver);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Validation started.");
            }
            report.start();
            final InputSource source = new InputSource(stream);
            xmlReader.parse(source);
            if (logger.isDebugEnabled()) {
                logger.debug("Validation stopped.");
            }

            report.stop();

            report.setNamespaceUri(contenthandler.getNamespaceUri());

            if (!report.isValid() && logger.isDebugEnabled()) {
                logger.debug("Document is not valid.");
            }

        } catch (final ParserConfigurationException | SAXException | IOException | URISyntaxException ex) {
            logger.error(ex);
            report.setThrowable(ex);

        } finally {
            report.stop();

            logger.debug("Validation performed in {} msec.", report.getValidationDuration());

        }

        return report;
    }

    /**
     * The bundled Xerces fork's XSD 1.1 support is only wired into the JAXP
     * SchemaFactory/Validator API, never into the dynamic-discovery SAX pipeline {@code
     * validateParse}'s default path uses -- same limitation {@code org.exist.collections.
     * MutableCollection}'s store-time validation and {@code validation:jaxp()} already work
     * around. Peeks the instance's own {@code xsi:schemaLocation}/{@code
     * noNamespaceSchemaLocation} hint up front (mark/reset on {@code bufferedStream} since
     * callers hand in a single-use {@link InputStream}, not a re-readable {@link InputSource})
     * and, if it resolves to a schema declaring {@code vc:minVersion="1.1"}, validates with an
     * XSD 1.1-capable {@link ValidatorHandler} instead.
     *
     * @return the completed {@link ValidationReport} if the XSD 1.1 path was taken, or {@code
     *         null} if {@code documentBaseUri} is unknown (needed to resolve the hint) or no hint
     *         was found -- the caller should fall through to the default pipeline in that case
     *         (same accepted, documented limitation as the other two call sites).
     */
    @Nullable
    private ValidationReport tryValidateWithXsd11Schema(final BufferedInputStream bufferedStream, @Nullable final String documentBaseUri,
            final ValidationContentHandler contenthandler, final ValidationReport report) {
        if (documentBaseUri == null) {
            return null;
        }
        try {
            bufferedStream.mark(XSD11_PEEK_MARK_LIMIT);
            final InputSource peekSource = new InputSource(bufferedStream);
            final Xsd11SchemaDetection.RootElementInfo rootElement = Xsd11SchemaDetection.peekRootElement(peekSource);
            bufferedStream.reset();

            if (Xsd11SchemaDetection.detectXsd11ViaSchemaLocation(subject.getName(), rootElement, documentBaseUri)) {
                logger.debug("Detected XSD 1.1 schema (vc:minVersion) via the instance's schemaLocation hint; using the XSD 1.1 validator directly.");
                return validateWithXsd11Schema(bufferedStream, documentBaseUri, contenthandler, report);
            }
        } catch (final IOException ex) {
            // Mark/reset failure (e.g. the prolog before the root start tag exceeded
            // XSD11_PEEK_MARK_LIMIT) -- not a validation failure, just means the peek couldn't
            // run; fall through to the default pipeline exactly as if no hint had been found.
            logger.debug("Could not peek root element for XSD 1.1 detection: {}", ex.getMessage());
        }
        return null;
    }

    /**
     * Validates {@code stream} against a no-pre-supplied-source XSD 1.1 {@link Schema} (dynamic
     * discovery follows the instance's own {@code xsi:schemaLocation} hint, the same way the
     * default SAX pipeline follows it for XSD 1.0). Drives a plain, non-validating {@link
     * XMLReader} rather than reusing {@link #getXMLReader} -- this class builds a fresh reader per
     * call rather than reusing a pooled one, so there's no risk of it independently
     * (mis)validating the document via its own XSD-1.0-only dynamic discovery in parallel with
     * this {@link ValidatorHandler} (the failure mode {@code MutableCollection}'s equivalent fix
     * had to specifically guard against, since it reuses a pooled, already-validating reader).
     * {@code contenthandler} doesn't implement {@link org.xml.sax.ext.LexicalHandler}, so unlike
     * {@code MutableCollection}'s fix, no lexical-event forwarder is needed here.
     */
    private ValidationReport validateWithXsd11Schema(final InputStream stream, final String documentBaseUri, final ValidationContentHandler contenthandler, final ValidationReport report) {
        try {
            final ValidatorHandler validatorHandler = getXsd11DynamicDiscoverySchema().newValidatorHandler();
            if (systemCatalogResolver != null) {
                validatorHandler.setResourceResolver(systemCatalogResolver);
            }
            validatorHandler.setErrorHandler(report);
            validatorHandler.setContentHandler(contenthandler);

            final SAXParserFactory saxFactory = ExistSAXParserFactory.getSAXParserFactory();
            saxFactory.setNamespaceAware(true);
            final XMLReader xmlReader = saxFactory.newSAXParser().getXMLReader();
            xmlReader.setFeature(FEATURE_SECURE_PROCESSING, true);
            xmlReader.setContentHandler(validatorHandler);

            // Dynamic discovery resolves the instance's own xsi:schemaLocation hint against the
            // InputSource's systemId during the parse -- without it, a relative hint falls back
            // to the JVM's current working directory instead of documentBaseUri (confirmed
            // empirically: this was the actual cause of an early version of this fix silently
            // falling through to "schema document not found" instead of validating).
            final InputSource source = new InputSource(stream);
            source.setSystemId(documentBaseUri);

            report.start();
            xmlReader.parse(source);
            report.stop();

            report.setNamespaceUri(contenthandler.getNamespaceUri());
        } catch (final ParserConfigurationException | SAXException | IOException ex) {
            logger.error(ex);
            report.setThrowable(ex);
        } finally {
            report.stop();
            logger.debug("Validation performed in {} msec.", report.getValidationDuration());
        }
        return report;
    }

    /**
     * Lazily compiles, once per JVM, the no-pre-supplied-source XSD 1.1 {@link Schema} used by
     * {@link #validateWithXsd11Schema(InputStream, ValidationContentHandler, ValidationReport)}.
     * Initialization-on-demand holder idiom: thread-safe with no explicit synchronization.
     */
    private static Schema getXsd11DynamicDiscoverySchema() {
        return Xsd11DynamicDiscoverySchemaHolder.INSTANCE;
    }

    private static final class Xsd11DynamicDiscoverySchemaHolder {
        private static final Schema INSTANCE;
        static {
            try {
                INSTANCE = SchemaFactory.newInstance(XSD_1_1_NS).newSchema();
            } catch (final SAXException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    private XMLReader getXMLReader(final ContentHandler contentHandler,
                                   final ErrorHandler errorHandler) throws ParserConfigurationException, SAXException {

        // setup sax factory ; be sure just one instance!
        final SAXParserFactory saxFactory = ExistSAXParserFactory.getSAXParserFactory();

        // Enable validation stuff
        saxFactory.setValidating(true);
        saxFactory.setNamespaceAware(true);

        // Create xml reader
        final SAXParser saxParser = saxFactory.newSAXParser();
        final XMLReader xmlReader = saxParser.getXMLReader();

        xmlReader.setFeature(FEATURE_SECURE_PROCESSING, true);

        // Setup xmlreader
        xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL, grammarPool);

        xmlReader.setFeature(Namespaces.SAX_VALIDATION, true);
        xmlReader.setFeature(Namespaces.SAX_VALIDATION_DYNAMIC, false);
        xmlReader.setFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA, true);
        xmlReader.setFeature(XMLReaderObjectFactory.APACHE_PROPERTIES_LOAD_EXT_DTD, true);
        xmlReader.setFeature(Namespaces.SAX_NAMESPACES_PREFIXES, true);

        xmlReader.setContentHandler(contentHandler);
        xmlReader.setErrorHandler(errorHandler);

        return xmlReader;
    }
}
