package org.exist.xquery.functions.util;

import org.apache.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.SAXAdapter;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;

public class Parse extends BasicFunction {
	
	protected static final FunctionParameterSequenceType RESULT_TYPE = new FunctionParameterSequenceType( "result", Type.NODE, Cardinality.ZERO_OR_MORE, "the XML fragment parsed from the string" );

	protected static final FunctionParameterSequenceType TO_BE_PARSED_PARAMETER = new FunctionParameterSequenceType( "to-be-parsed", Type.STRING, Cardinality.ZERO_OR_ONE, "the string to be parsed" );

	protected static final Logger logger = Logger.getLogger(Parse.class);

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName( "parse", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Parses the passed string value into an XML fragment. The string has to be " +
            "well-formed XML. An empty sequence is returned if the argument is an " +
            "empty string or sequence.",
            new SequenceType[] { TO_BE_PARSED_PARAMETER },
            RESULT_TYPE
        ),
        new FunctionSignature(
            new QName( "parse-html", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Parses the passed string value into an XML fragment. The HTML string may not be " +
            "well-formed XML. It will be passed through the Neko HTML parser to make it well-formed. " +
            "An empty sequence is returned if the argument is an " +
            "empty string or sequence.",
            new SequenceType[] { TO_BE_PARSED_PARAMETER },
            RESULT_TYPE
        )
    };

    public Parse(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    	logger.info("Entering " + UtilModule.PREFIX + ":" + getName().getLocalName());
    	
        if (args[0].getItemCount() == 0) {
        	logger.info("Exiting " + UtilModule.PREFIX + ":" + getName().getLocalName());
            return Sequence.EMPTY_SEQUENCE;
        }
        String xmlContent = args[0].itemAt(0).getStringValue();
        if (xmlContent.length() == 0) {
        	logger.info("Exiting " + UtilModule.PREFIX + ":" + getName().getLocalName());
            return Sequence.EMPTY_SEQUENCE;
        }
        StringReader reader = new StringReader(xmlContent);
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            InputSource src = new InputSource(reader);

            XMLReader xr = null;
            if (isCalledAs("parse-html")) {
                try {
                    Class clazz = Class.forName( "org.cyberneko.html.parsers.SAXParser" );
                    xr = (XMLReader) clazz.newInstance();
                    //do not modify the case of elements and attributes
                    xr.setProperty("http://cyberneko.org/html/properties/names/elems", "match");
                    xr.setProperty("http://cyberneko.org/html/properties/names/attrs", "no-change");
                } catch (Exception e) {
                    LOG.warn("Could not instantiate neko HTML parser for function util:parse-html, falling back to " +
                            "default XML parser.", e);
                }
            }
            if (xr == null) {
                SAXParser parser = factory.newSAXParser();
                xr = parser.getXMLReader();
            }

            SAXAdapter adapter = new SAXAdapter(context);
            xr.setContentHandler(adapter);
            xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
            xr.parse(src);

        	logger.info("Exiting " + UtilModule.PREFIX + ":" + getName().getLocalName());
            return (DocumentImpl) adapter.getDocument();
        } catch (ParserConfigurationException e) {
            throw new XPathException(this, "Error while constructing XML parser: " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new XPathException(this, "Error while parsing XML: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new XPathException(this, "Error while parsing XML: " + e.getMessage(), e);
        }
    }
}
