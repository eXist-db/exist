package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.NodeImpl;
import org.exist.memtree.SAXAdapter;
import org.exist.xquery.*;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;

public class Parse extends BasicFunction {

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName( "parse", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Parses the passed string value into an XML fragment. The string has to be " +
            "well-formed XML. An empty sequence is returned if the argument is an " +
            "empty string or sequence.",
            new SequenceType[] {
                new SequenceType( Type.STRING, Cardinality.ZERO_OR_ONE )
            },
            new SequenceType( Type.NODE, Cardinality.ZERO_OR_MORE )
        ),
        new FunctionSignature(
            new QName( "parse-html", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Parses the passed string value into an XML fragment. The HTML string may not be " +
            "well-formed XML. It will be passed through the Neko HTML parser to make it well-formed. " +
            "An empty sequence is returned if the argument is an " +
            "empty string or sequence.",
            new SequenceType[] {
                new SequenceType( Type.STRING, Cardinality.ZERO_OR_ONE )
            },
            new SequenceType( Type.NODE, Cardinality.ZERO_OR_MORE )
        )
    };

    public Parse(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (args[0].getItemCount() == 0)
            return Sequence.EMPTY_SEQUENCE;
        String xmlContent = args[0].itemAt(0).getStringValue();
        if (xmlContent.length() == 0)
            return Sequence.EMPTY_SEQUENCE;
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
            xr.parse(src);

            DocumentImpl doc = (DocumentImpl) adapter.getDocument();
            if (doc.getChildCount() == 1)
                return (NodeImpl) doc.getFirstChild();
            else {
                ValueSequence result = new ValueSequence();
                NodeImpl node = (NodeImpl) doc.getFirstChild();
                while (node != null) {
                    result.add(node);
                    node = (NodeImpl) node.getNextSibling();
                }
                return result;
            }
        } catch (ParserConfigurationException e) {
            throw new XPathException(getASTNode(), "Error while constructing XML parser: " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new XPathException(getASTNode(), "Error while parsing XML: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new XPathException(getASTNode(), "Error while parsing XML: " + e.getMessage(), e);
        }
    }
}
