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
package org.exist.xquery.functions.validation;

import java.net.MalformedURLException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.storage.BrokerPool;
import org.exist.validation.ValidationReport;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import org.w3c.dom.ls.LSResourceResolver;

/**
 *   xQuery function for validation of XML instance documents
 * using grammars like XSDs and DTDs.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class Jaxv extends BasicFunction  {

    private static final String extendedFunctionTxt = """
        Validate document specified by $instance using the schemas in $grammars.
        Based on functionality provided by 'javax.xml.validation.Validator'. Only
        '.xsd' grammars are supported.""";

    private static final String instanceText = """
            The document referenced as xs:anyURI, a node (element or returned by fn:doc())
            or as a Java file object.""";

    private static final String grammarText = """
            One of more XML Schema documents (.xsd),
            referenced as xs:anyURI, a node (element or returned by fn:doc())
            or as Java file objects.""";

    private static final String languageText = """
            The namespace URI to designate a schema language. Depending on the
            jaxv.SchemaFactory implementation the following values are valid:
            (XSD 1.0) http://www.w3.org/2001/XMLSchema http://www.w3.org/XML/XMLSchema/v1.0,
            (XSD 1.1) http://www.w3.org/XML/XMLSchema/v1.1,
            (RELAX NG 1.0) http://relaxng.org/ns/structure/1.0""";

    private static final String catalogTxt = """
            The catalogs referenced as xs:anyURI's. An empty \
            sequence uses the system catalog. A directory-search catalog (a collection URI ending \
            in '/') searches that collection for an XSD whose target namespace matches an import.""";

    // Setup function signature
    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
                new QName("jaxv", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
                extendedFunctionTxt,
                new SequenceType[]{
                    new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
                        instanceText),
                    new FunctionParameterSequenceType("grammars", Type.ITEM, Cardinality.ONE_OR_MORE,
                        languageText)
                },
                new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                    Shared.simplereportText)
            ),

        new FunctionSignature(
                new QName("jaxv", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
                extendedFunctionTxt,
                new SequenceType[]{
                    new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
                        instanceText),
                    new FunctionParameterSequenceType("grammars", Type.ITEM, Cardinality.ONE_OR_MORE,
                        grammarText),
                    new FunctionParameterSequenceType("language", Type.STRING, Cardinality.EXACTLY_ONE,
                        languageText),
                },
                new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                    Shared.simplereportText)
            ),

        new FunctionSignature(
                new QName("jaxv", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
                extendedFunctionTxt + " Optionally an XML catalog can be specified for schema/entity resolution.",
                new SequenceType[]{
                    new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
                        instanceText),
                    new FunctionParameterSequenceType("grammars", Type.ITEM, Cardinality.ONE_OR_MORE,
                        grammarText),
                    new FunctionParameterSequenceType("language", Type.STRING, Cardinality.EXACTLY_ONE,
                        languageText),
                    new FunctionParameterSequenceType("catalogs", Type.ITEM, Cardinality.ZERO_OR_MORE,
                        catalogTxt),
                },
                new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                    Shared.simplereportText)
            ),

        new FunctionSignature(
                new QName("jaxv-report", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
                extendedFunctionTxt + " An XML report is returned.",
                new SequenceType[]{
                    new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
                        instanceText),
                    new FunctionParameterSequenceType("grammars", Type.ITEM, Cardinality.ONE_OR_MORE,
                        grammarText),
                   },
                new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
                    Shared.xmlreportText)
            ),

        new FunctionSignature(
                new QName("jaxv-report", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
                extendedFunctionTxt + " An XML report is returned.",
                new SequenceType[]{
                    new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
                        instanceText),
                    new FunctionParameterSequenceType("grammars", Type.ITEM, Cardinality.ONE_OR_MORE,
                        grammarText),
                    new FunctionParameterSequenceType("language", Type.STRING, Cardinality.EXACTLY_ONE,
                        languageText),
                   },
                new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
                    Shared.xmlreportText)
            ),

        new FunctionSignature(
                new QName("jaxv-report", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
                extendedFunctionTxt + " An XML report is returned. Optionally an XML catalog can be specified for schema/entity resolution.",
                new SequenceType[]{
                    new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
                        instanceText),
                    new FunctionParameterSequenceType("grammars", Type.ITEM, Cardinality.ONE_OR_MORE,
                        grammarText),
                    new FunctionParameterSequenceType("language", Type.STRING, Cardinality.EXACTLY_ONE,
                        languageText),
                    new FunctionParameterSequenceType("catalogs", Type.ITEM, Cardinality.ZERO_OR_MORE,
                        catalogTxt),
                },
                new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
                    Shared.xmlreportText)
            )

    };

    private final BrokerPool brokerPool;

    public Jaxv(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
        brokerPool = context.getBroker().getBrokerPool();
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        // Check input parameters
        if (args.length != 2 && args.length != 3 && args.length != 4) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final ValidationReport report = new ValidationReport();
        StreamSource instance = null;
        StreamSource[] grammars = null;
        String schemaLang = XMLConstants.W3C_XML_SCHEMA_NS_URI;

        try {
            report.start();

            // Get inputstream for instance document
            instance=Shared.getStreamSource(args[0].itemAt(0), context);

            // Validate using resource speciefied in second parameter
            grammars = Shared.getStreamSource(args[1], context);

            // Check input
            for (final StreamSource grammar : grammars) {
                final String grammarUrl = grammar.getSystemId();
                if (grammarUrl != null && !grammarUrl.endsWith(".xsd") && !grammarUrl.endsWith(".rng")) {
                    throw new XPathException(this, "Only XML schemas (.xsd) and RELAXNG grammars (.rng) are supported"
                            + ", depending on the used XML parser.");
                }
            }

            // Fetch third argument if available, and override defailt value
            if (args.length >= 3) {
                schemaLang = args[2].getStringValue();
            }

            // Get language specific factory
            SchemaFactory factory = null;
            try {
                factory = SchemaFactory.newInstance(schemaLang);

            } catch (final IllegalArgumentException ex) {
                final String msg = "Schema language '" + schemaLang + "' is not supported. " + ex.getMessage();
                LOG.error(msg);
                throw new XPathException(this, msg);
            }

            // Handle catalog (fourth argument). Must be set on the SchemaFactory, and
            // BEFORE newSchema() is called: that's when xs:import/xs:include resolution
            // happens (schema compilation), not at validate() time. javax.xml.validation
            // only accepts an LSResourceResolver -- org.xmlresolver.Resolver implements it
            // directly, and SearchResourceResolver also implements it (in addition to the
            // Xerces-specific XMLEntityResolver/XNI interface it uses for validation:jaxp()'s
            // SAX pipeline), so the directory-search/collection case (a catalog URL ending
            // in '/') works here too.
            if (args.length == 4) {
                final LSResourceResolver resolver = Shared.resolveCatalogArgument(this, brokerPool, context.getBroker(), context.getSubject(), args[3]);
                factory.setResourceResolver(resolver);
            }

            // Create grammar -- xs:import/xs:include resolution (via the resolver se
            // above, if any) happens here, during schema compilation.
            final Schema schema = factory.newSchema(grammars);

            // Setup validator
            final Validator validator = schema.newValidator();
            validator.setErrorHandler(report);

            // Perform validation
            validator.validate(instance);

        } catch (final MalformedURLException ex) {
            LOG.error(ex.getMessage());
            report.setException(ex);

        } catch (final Throwable ex) {
            LOG.error(ex);
            report.setException(ex);

        } finally {
            report.stop();

            Shared.closeStreamSource(instance);
            Shared.closeStreamSources(grammars);
        }

        // Create response
        if (isCalledAs("jaxv")) {
            final Sequence result = new ValueSequence();
            result.add(new BooleanValue(this, report.isValid()));
            return result;

        } else /* isCalledAs("jaxv-report") */ {
            context.pushDocumentContext();
            try {
                final MemTreeBuilder builder = context.getDocumentBuilder();
                final NodeImpl result = Shared.writeReport(report, builder);
                return result;
            } finally {
                context.popDocumentContext();
            }
        }
    }
}
