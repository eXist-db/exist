/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.functions.validation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;


import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.xerces.xni.parser.XMLEntityResolver;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.io.ExistIOException;
import org.exist.util.Configuration;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.validation.ValidationContentHandler;
import org.exist.validation.ValidationReport;
import org.exist.validation.resolver.SearchResourceResolver;
import org.exist.validation.resolver.eXistXMLCatalogResolver;
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

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 *   xQuery function for validation of XML instance documents
 * using grammars like XSDs and DTDs.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class Parse extends BasicFunction {

    private static final String extendedFunctionTxt =
            "Validate document by parsing $instance. Optionally" +
            "a location of a grammar file (xsd, dtd) can be set and" +
            "a xml catalog.";
    private final BrokerPool brokerPool;
    // Setup function signature
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
        new QName("parse", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
        extendedFunctionTxt,
        new SequenceType[]{
            new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
            "Document referenced as xs:anyURI or a node (element or returned by fn:doc())"),
            new FunctionParameterSequenceType("grammar", Type.ANY_URI, Cardinality.ZERO_OR_ONE,
            "Location of grammar file."),
            new FunctionParameterSequenceType("catalog", Type.ITEM, Cardinality.ZERO_OR_ONE,
            "Catalog or location of XML catalog."),},
        new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
        Shared.simplereportText)),
        new FunctionSignature(
        new QName("parse-report", ValidationModule.NAMESPACE_URI, ValidationModule.PREFIX),
        extendedFunctionTxt + " An xml report is returned.",
        new SequenceType[]{
            new FunctionParameterSequenceType("instance", Type.ITEM, Cardinality.EXACTLY_ONE,
            "Document referenced as xs:anyURI or a node (element or returned by fn:doc())"),
            new FunctionParameterSequenceType("grammar", Type.ANY_URI, Cardinality.ZERO_OR_ONE,
            "Location of grammar file."),
            new FunctionParameterSequenceType("catalog", Type.ITEM, Cardinality.ZERO_OR_ONE,
            "Catalog or location of XML catalog."),},
        new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
        Shared.xmlreportText))
    };

    public Parse(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
        brokerPool = context.getBroker().getBrokerPool();
    }

    /**
     * @throws org.exist.xquery.XPathException 
     * @see BasicFunction#eval(Sequence[], Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {

        XMLEntityResolver entityResolver = null;
        InputSource instance = null;
        File tmpFile = null;
        String grammarUrl = null;
        ValidationReport report = new ValidationReport();
        ValidationContentHandler contenthandler = new ValidationContentHandler();

        try {
            report.start();

            // Get initialized parser
            XMLReader xmlReader = getXMLReader();

            // Setup validation reporting
            xmlReader.setContentHandler(contenthandler);
            xmlReader.setErrorHandler(report);

            // Get inputstream for instance document
            instance = Shared.getInputSource(args[0].itemAt(0), context);

            // Prepare grammar
            if (args[1].hasOne()) {
                // Get URL for grammar
                grammarUrl = Shared.getUrl(args[1].itemAt(0));

                // Special case for DTD, the document needs to be rewritten.
                if (grammarUrl.endsWith(".dtd")) {
                    StreamSource newInstance = Shared.getStreamSource(instance);
                    tmpFile = preparseDTD(newInstance, grammarUrl);
                    instance = new InputSource(new FileInputStream(tmpFile));

                } else if (grammarUrl.endsWith(".xsd")) {
                    xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_NONAMESPACESCHEMALOCATION, grammarUrl);

                } else {
                    throw new XPathException("Grammar type not supported.");
                }
            }

            // Handle catalog
            if (args[2].hasOne()) {
                // Get URL for catalog
                grammarUrl = Shared.getUrl(args[2].itemAt(0));

                if (grammarUrl.endsWith("/")) {
                    entityResolver = new SearchResourceResolver(grammarUrl, brokerPool);
                    xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_ENTITYRESOLVER, entityResolver);

                } else if (grammarUrl.endsWith(".xml")) {
                    entityResolver = new eXistXMLCatalogResolver();
                    ((eXistXMLCatalogResolver) entityResolver).setCatalogList(new String[]{grammarUrl});
                    xmlReader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_ENTITYRESOLVER, entityResolver);

                } else if (grammarUrl.endsWith("systemcatalog")) {
                    // Get configuration
                    Configuration config = brokerPool.getConfiguration();
                    entityResolver = (eXistXMLCatalogResolver) config.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER);
                }

            }



            // Parse document
            xmlReader.parse(instance);

            // Distill namespace from document
            report.setNamespaceUri(contenthandler.getNamespaceUri());


        } catch (MalformedURLException ex) {
            LOG.error(ex.getMessage());
            report.setException(ex);

        } catch (ExistIOException ex) {
            LOG.error(ex.getCause());
            report.setException(ex);

        } catch (Throwable ex) {
            LOG.error(ex);
            report.setException(ex);

        } finally {
            report.stop();

            if (tmpFile != null) {
                LOG.debug("Remove tmp file " + tmpFile.getAbsolutePath());
                tmpFile.delete();
            }
        }

        // Create response
        if (isCalledAs("parse")) {
            Sequence result = new ValueSequence();
            result.add(new BooleanValue(report.isValid()));
            return result;

        } else /* isCalledAs("parse-report") */ {
            MemTreeBuilder builder = context.getDocumentBuilder();
            NodeImpl result = Shared.writeReport(report, builder);
            return result;
        }
    }

    // ####################################
    private XMLReader getXMLReader() throws ParserConfigurationException, SAXException {

        // setup sax factory ; be sure just one instance!
        SAXParserFactory saxFactory = SAXParserFactory.newInstance();

        // Enable validation stuff
        saxFactory.setValidating(true);
        saxFactory.setNamespaceAware(true);

        // Create xml reader
        SAXParser saxParser = saxFactory.newSAXParser();
        XMLReader xmlReader = saxParser.getXMLReader();

        xmlReader.setFeature(Namespaces.SAX_VALIDATION, true);
        xmlReader.setFeature(Namespaces.SAX_VALIDATION_DYNAMIC, false);
        xmlReader.setFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA, true);
        xmlReader.setFeature(XMLReaderObjectFactory.APACHE_PROPERTIES_LOAD_EXT_DTD, true);
        xmlReader.setFeature(Namespaces.SAX_NAMESPACES_PREFIXES, true);

        return xmlReader;
    }

    // No-go ...processor is in validating mode
    private File preparseDTD(StreamSource instance, String systemId) throws IOException, TransformerConfigurationException, TransformerException {

        // prepare output tmp storage
        File tmp = File.createTempFile("DTDvalidation", "tmp");
        tmp.deleteOnExit();

        StreamResult result = new StreamResult(tmp);

        TransformerFactory tf = TransformerFactory.newInstance();

        Transformer transformer = tf.newTransformer();

        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, systemId);
        transformer.transform(instance, result);

        return tmp;
    }
}
