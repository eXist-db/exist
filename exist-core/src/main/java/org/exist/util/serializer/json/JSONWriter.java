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
package org.exist.util.serializer.json;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.QName.IllegalQNameException;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.serializer.XMLWriter;

/**
 * This class plugs into eXist's serialization to transform XML to JSON. It is used
 * if the serialization property "method" is set to "json".
 * 
 * The following rules apply for the mapping of XML to JSON:
 * 
 * <ul>
 * 	<li>The root element will be absorbed, i.e. &lt;root&gt;text&lt;/root&gt; becomes "root".</li>
 * 	<li>Sibling elements with the same name are added to an array.</li>
 * 	<li>If an element has attribute and text content, the text content becomes a
 *      property, e.g. '#text': 'my text'.</li>
 *	<li>In mixed content nodes, text nodes will be dropped.</li>
 *	<li>An empty element becomes 'null', i.e. &lt;e/&gt; becomes {"e": null}.</li>
 *	<li>An element with a single text child becomes a property with the value of the text child, i.e.
 *      &lt;e&gt;text&lt;/e&gt; becomes {"e": "text"}<li>
 *  <li>An element with name "json:value" is serialized as a simple value, not an object, i.e.
 *  	&lt;json:value&gt;value&lt;/json:value&gt; just becomes "value".</li>
 * </ul>
 * 
 * Namespace prefixes will be dropped from element and attribute names by default. If the serialization
 * property {@link EXistOutputKeys#JSON_OUTPUT_NS_PREFIX} is set to "yes", namespace prefixes will be
 * added to the resulting JSON property names, replacing the ":" with a "_", i.e. &lt;foo:node&gt; becomes
 * "foo_node".
 * 
 * If an attribute json:array is present on an element it will always be serialized as an array, even if there
 * are no other sibling elements with the same name.
 * 
 * The attribute json:literal indicates that the element's text content should be serialized literally. This is
 * handy for writing boolean or numeric values. By default, text content is serialized as a Javascript string.
 *  
 * @author wolf
 *
 */
public class JSONWriter extends XMLWriter {

    private final static Logger LOG = LogManager.getLogger(JSONWriter.class);
    
    private final static String ARRAY = "array";
    private final static String LITERAL = "literal";
    private final static String VALUE = "value";
    private final static String NAME = "name";
    
    private final static String JSON_ARRAY = "json:" + ARRAY;
    private final static String JSON_LITERAL = "json:" + LITERAL;
    private final static String JSON_VALUE = "json:" + VALUE;
    private final static String JSON_NAME = "json:" + NAME;
    
    public final static String JASON_NS = "http://www.json.org";
	
    protected JSONNode root;
	
    protected final Deque<JSONObject> stack = new ArrayDeque<>();

    protected boolean useNSPrefix = false;
    
    protected boolean prefixAttributes = false;
    protected boolean ignoreWhitespaceTextNodes = false;
    private String jsonp = null;
    private boolean indent = false;
	
    public JSONWriter() {
        // empty
    }

    public JSONWriter(final Writer writer) {
        super(writer);
    }

    @Override
    protected void resetObjectState() { 
        super.resetObjectState();
        stack.clear();
        root = null;
    }

    @Override
    public void setOutputProperties(final Properties properties) {
        super.setOutputProperties(properties);

        final String useNSPrefixProp = properties.getProperty(EXistOutputKeys.JSON_OUTPUT_NS_PREFIX, "no");
        useNSPrefix = "yes".equalsIgnoreCase(useNSPrefixProp);
        final String prefixAttributesProp = properties.getProperty(EXistOutputKeys.JSON_PREFIX_ATTRIBUTES, "no");
        prefixAttributes = "yes".equalsIgnoreCase(prefixAttributesProp);
        final String ignoreWhitespaceTextNodesProp = properties.getProperty(EXistOutputKeys.JSON_IGNORE_WHITESPACE_TEXT_NODES, "no");
        ignoreWhitespaceTextNodes = "yes".equalsIgnoreCase(ignoreWhitespaceTextNodesProp);
        jsonp = properties.getProperty(EXistOutputKeys.JSONP);
        indent = "yes".equalsIgnoreCase(properties.getProperty(OutputKeys.INDENT, "no"));
    }

    @Override
    public void startDocument() throws TransformerException {
    }

    @Override
    public void endDocument() throws TransformerException {
        try {
            if(root != null) {
                if(jsonp != null) {
                    getWriter().write(jsonp + "(");
                }

                root.serialize(getWriter(), true);

                if(jsonp != null) {
                    getWriter().write(")");
                }
            }
        } catch(final IOException ioe) {
            LOG.error(ioe.getMessage(), ioe);
        }
    }

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {
        if(qname.equals(JSON_VALUE)) {
            processStartValue();
        } else if(useNSPrefix) {
            processStartElement(qname.replace(':', '_'), false);
        } else {
            try {
                processStartElement(QName.extractLocalName(qname), false);
            } catch (final IllegalQNameException e) {
                throw new TransformerException(e);
            }
        }
    }

    @Override
    public void startElement(final QName qname) throws TransformerException {
        if(JASON_NS.equals(qname.getNamespaceURI()) && VALUE.equals(qname.getLocalPart())) {
            processStartValue();
        } else if(useNSPrefix) {
            processStartElement(qname.getPrefix() + '_' + qname.getLocalPart(), false);
        } else {
            processStartElement(qname.getLocalPart(), false);
        }
    }

    private void processStartElement(final String localName, boolean simpleValue) {
        final JSONObject obj = new JSONObject(localName);
        obj.setIndent(indent);
        if(root == null) {
            root = obj;
            stack.push(obj);
        } else {
            final JSONObject parent = stack.peek();
            parent.addObject(obj);
            stack.push(obj);
        }
    }

    private void processStartValue() throws TransformerException {
        // a json:value is stored as an unnamed object
        final JSONObject obj = new JSONObject();
        obj.setIndent(indent);
        if(root == null) {
            root = obj;
            stack.push(obj);
        } else {
            final JSONObject parent = stack.peek();
            parent.addObject(obj);
            stack.push(obj);
        }
    }

    @Override
    public void endElement(final String namespaceUri, final String localName, final String qname) throws TransformerException {
        stack.pop();
    }

    @Override
    public void endElement(final QName qname) throws TransformerException {
        stack.pop();
    }

    @Override
    public void namespace(final String prefix, final String nsURI) throws TransformerException {
    }

    @Override
    public void attribute(final String qname, final CharSequence value) throws TransformerException {
        final JSONObject parent = stack.peek();
        switch (qname) {
            case JSON_ARRAY:
                parent.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
                break;
            case JSON_LITERAL:
                parent.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
                break;
            case JSON_NAME:
                parent.setName(value);
                break;
            default:
                final String name = prefixAttributes ? "@" + qname : qname;
                final JSONSimpleProperty obj = new JSONSimpleProperty(name, value);
                obj.setIndent(indent);
                parent.addObject(obj);
                break;
        }
    }

    @Override
    public void attribute(final QName qname, final CharSequence value) throws TransformerException {
        attribute(qname.toString(), value);
    }

    @Override
    public void characters(final CharSequence chars) throws TransformerException {
        if(ignoreWhitespaceTextNodes) {
            final boolean isWhitespace = chars.toString().trim().isEmpty();
            if(isWhitespace) {
                return;
            }
        }

        final JSONObject parent = stack.peek();
        final JSONNode value = new JSONValue(chars.toString());
        value.setIndent(indent);
        value.setSerializationType(parent.getSerializationType());
        value.setSerializationDataType(parent.getSerializationDataType());
        parent.addObject(value);
    }

    @Override
    public void characters(final char[] ch, final int start, final int len) throws TransformerException {
        characters(new String(ch, start, len));
    }

    @Override
    public void processingInstruction(final String target, final String data) throws TransformerException {
        // skip
    }

    @Override
    public void comment(final CharSequence data) throws TransformerException {
        // skip
    }

    @Override
    public void startCdataSection() {
        // empty
    }

    @Override
    public void endCdataSection() {
        // empty
    }

    @Override
    public void cdataSection(final char[] ch, final int start, final int len) throws TransformerException {
        // treat as string content
        characters(ch, start, len);
    }

    @Override
    public void startDocumentType(final String name, final String publicId, final String systemId) {
        // empty
    }

    @Override
    public void endDocumentType() {
        // empty
    }

    @Override
    public void documentType(final String name, final String publicId, final String systemId) throws TransformerException {
        // skip
    }
}
