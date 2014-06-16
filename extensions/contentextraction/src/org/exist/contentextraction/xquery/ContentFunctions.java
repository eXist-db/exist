/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
package org.exist.contentextraction.xquery;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.exist.contentextraction.ContentExtraction;
import org.exist.contentextraction.ContentExtractionException;
import org.exist.contentextraction.ContentReceiver;
import org.exist.dom.QName;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.storage.NodePath;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * @author Dulip Withanage <dulip.withanage@gmail.com>
 * @version 1.0
 */
public class ContentFunctions extends BasicFunction {


    public final static FunctionSignature getMeatadata = new FunctionSignature(
        new QName("get-metadata", ContentExtractionModule.NAMESPACE_URI, ContentExtractionModule.PREFIX),
        "extracts the metadata",
        new SequenceType[]{
            new FunctionParameterSequenceType("binary", Type.BASE64_BINARY, Cardinality.ONE, "The binary data to extract from")
        },
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "Extracted metadata")
    );

    public final static FunctionSignature getMetadataAndContent = new FunctionSignature(
        new QName("get-metadata-and-content", ContentExtractionModule.NAMESPACE_URI, ContentExtractionModule.PREFIX),
        "extracts the metadata and contents",
        new SequenceType[]{
            new FunctionParameterSequenceType("binary", Type.BASE64_BINARY, Cardinality.ONE, "The binary data to extract from")
        },
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "Extracted content and metadata")
    );

    public final static FunctionSignature streamContent = new FunctionSignature(
        new QName("stream-content", ContentExtractionModule.NAMESPACE_URI, ContentExtractionModule.PREFIX),
        "extracts the metadata",
        new SequenceType[]{
            new FunctionParameterSequenceType("binary", Type.BASE64_BINARY, Cardinality.ONE, "The binary data to extract from"),
            new FunctionParameterSequenceType("paths", Type.STRING, Cardinality.ZERO_OR_MORE, 
            		"A sequence of (simple) node paths which should be passed to the callback function"),
            new FunctionParameterSequenceType("callback", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, 
            		"The callback function. Expected signature: " +
					"callback($node as node(), $userData as item()*, $retValue as item()*)," +
					"where $node is the currently processed node, $userData contains the data supplied in the " +
					"$userData parameter of stream-content, and $retValue is the return value of the previous " +
					"call to the callback function. The last two parameters are used for passing information " +
					"between the calling function and subsequent invocations of the callback function."),
            new FunctionParameterSequenceType("namespaces", Type.ELEMENT, Cardinality.ZERO_OR_ONE,
            		"Prefix/namespace mappings to be used for matching the paths. Pass an XML fragment with the following " +
            		"structure: <namespaces><namespace prefix=\"prefix\" uri=\"uri\"/></namespaces>."),
            new FunctionParameterSequenceType("userData", Type.ITEM, Cardinality.ZERO_OR_MORE,
            		"Additional data which will be passed to the callback function.")
        },
        new FunctionReturnSequenceType(Type.EMPTY, Cardinality.EMPTY, "Returns empty sequence")
    );
    
    public ContentFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        // is argument the empty sequence?
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        DocumentBuilderReceiver builder = new DocumentBuilderReceiver();
        ContentExtraction ce = new ContentExtraction();

        if (isCalledAs("stream-content")) {

            /* binary content */
            BinaryValue binary = (BinaryValue) args[0].itemAt(0);

            /* callback function */
            FunctionReference ref = (FunctionReference) args[2].itemAt(0);

            Map<String, String> mappings = new HashMap<>();
            if (args[3].hasOne()) {
                NodeValue namespaces = (NodeValue) args[3].itemAt(0);
                parseMappings(namespaces, mappings);
            }

            return streamContent(ce, binary, args[1], ref, mappings, args[4]);

        } else {

            try {
                if (isCalledAs("get-metadata")) {
                    ce.extractMetadata((BinaryValue) args[0].itemAt(0), (ContentHandler) builder);

                } else {
                    ce.extractContentAndMetadata((BinaryValue) args[0].itemAt(0), (ContentHandler) builder);
                }

                return (NodeValue) builder.getDocument();

            } catch (IOException | SAXException | ContentExtractionException ex) {
                LOG.error(ex.getMessage(), ex);
                throw new XPathException(this, ex.getMessage(), ex);

            }
        }
    }

    private void parseMappings(NodeValue namespaces, Map<String, String> mappings) throws XPathException {

        try {
            XMLStreamReader reader = context.getXMLStreamReader(namespaces);
            reader.next();

            while (reader.hasNext()) {

                int status = reader.next();
                if (status == XMLStreamReader.START_ELEMENT && reader.getLocalName().equals("namespace")) {
                    String prefix = reader.getAttributeValue("", "prefix");
                    String uri = reader.getAttributeValue("", "uri");
                    mappings.put(prefix, uri);
                }
            }

        } catch (XMLStreamException | IOException e) {
            throw new XPathException(this, "Error while parsing namespace mappings: " + e.getMessage(), e);

        }
    }

    private Sequence streamContent(ContentExtraction ce, BinaryValue binary, Sequence pathSeq,
            FunctionReference ref, Map<String, String> mappings, Sequence data) throws XPathException {

        
        NodePath[] paths = new NodePath[pathSeq.getItemCount()];
        int i = 0;

        for (SequenceIterator iter = pathSeq.iterate(); iter.hasNext(); i++) {
            String path = iter.nextItem().getStringValue();
            paths[i] = new NodePath(mappings, path, false);
        }

        ContentReceiver receiver = new ContentReceiver(context, paths, ref, data);

        try {
            ce.extractContentAndMetadata(binary, receiver);

        } catch (IOException | SAXException | ContentExtractionException ex) {
            LOG.error(ex.getMessage(), ex);
            throw new XPathException(this, ex.getMessage(), ex);

        }

        return receiver.getResult();
    }
}
