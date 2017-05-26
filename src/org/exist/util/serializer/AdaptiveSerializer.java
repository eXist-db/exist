/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.util.serializer;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.NamespaceNode;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.InlineFunction;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.array.ArrayModule;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.functions.map.MapModule;
import org.exist.xquery.functions.math.MathModule;
import org.exist.xquery.value.*;
import org.w3c.dom.Attr;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.Writer;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Implements adaptive serialization from the <a href="https://www.w3.org/TR/xslt-xquery-serialization-31/">XSLT and
 * XQuery Serialization 3.1</a> specification.
 *
 * @author Wolfgang
 */
public class AdaptiveSerializer extends AbstractSerializer {

    private final static String DEFAULT_ITEM_SEPARATOR = "\n";

    private final DBBroker broker;
    private Writer writer;

    public AdaptiveSerializer(final DBBroker broker) {
        super();
        this.broker = broker;
    }

    @Override
    public void setOutput(Writer writer, Properties properties) {
        if (properties == null) {
            outputProperties = defaultProperties;
        } else {
            outputProperties = properties;
        }
        this.writer = writer;
        for (XMLWriter w: writers) {
            w.setWriter(writer);
            w.setOutputProperties(outputProperties);
        }
    }

    public void serialize(final Sequence sequence) throws XPathException, SAXException {
        final String itemSep = outputProperties.getProperty(EXistOutputKeys.ITEM_SEPARATOR, DEFAULT_ITEM_SEPARATOR);
        serialize(sequence, itemSep, false);
    }

    public void serialize(final Sequence sequence, final String itemSep, final boolean enclose)
            throws XPathException, SAXException {
        try {
            if (enclose && sequence.getItemCount() != 1) {
                writer.write('(');
            }
            for (final SequenceIterator si = sequence.iterate(); si.hasNext(); ) {
                final Item item = si.nextItem();
                switch (item.getType()) {
                    case Type.DOCUMENT:
                    case Type.ELEMENT:
                    case Type.TEXT:
                    case Type.COMMENT:
                    case Type.CDATA_SECTION:
                    case Type.PROCESSING_INSTRUCTION:
                        serializeXML(item);
                        break;
                    case Type.ATTRIBUTE:
                        final Attr node = (Attr)((NodeValue)item).getNode();
                        serializeText(node.getName() + "=\"" + node.getValue() + '"');
                        break;
                    case Type.NAMESPACE:
                        final NamespaceNode ns = (NamespaceNode)item;
                        serializeText(ns.getName() + "=\"" + ns.getValue() + '"');
                        break;
                    case Type.STRING:
                    case Type.UNTYPED_ATOMIC:
                    case Type.ANY_URI:
                        final String v = item.getStringValue();
                        serializeText('"' + escapeQuotes(v) + '"');
                        break;
                    case Type.DOUBLE:
                        serializeDouble((DoubleValue) item);
                        break;
                    case Type.BOOLEAN:
                        serializeText(item.getStringValue() + "()");
                        break;
                    case Type.QNAME:
                        final QName qn = ((QNameValue)item).getQName();
                        serializeText("Q{" + qn.getNamespaceURI() + '}' + qn.getLocalPart());
                        break;
                    case Type.ARRAY:
                        serializeArray((ArrayType)item);
                        break;
                    case Type.MAP:
                        serializeMap((AbstractMapType)item);
                        break;
                    case Type.FUNCTION_REFERENCE:
                        serializeFunctionItem((FunctionReference) item);
                        break;
                    default:
                        serializeText(item.getStringValue());
                        break;
                }
                if (si.hasNext()) {
                    try {
                        writer.write(itemSep);
                    } catch (IOException e) {
                        throw new SAXException(e.getMessage());
                    }
                }
            }
            if (enclose && sequence.getItemCount() != 1) {
                writer.write(')');
            }
        } catch (IOException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    private void serializeDouble(final DoubleValue item) throws XPathException, SAXException {
        final DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        symbols.setExponentSeparator("e");
        final DecimalFormat df = new DecimalFormat("0.0##########################E0", symbols);
        serializeText(df.format(item.getDouble()));
    }

    private void serializeArray(final ArrayType array) throws XPathException, SAXException {
        try {
            writer.write('[');

            for (int i = 0; i < array.getSize(); i++) {
                if (i > 0) {
                    writer.write(',');
                }
                final Sequence member = array.get(i);
                serialize(member, ",", true);
            }
            writer.write(']');
        } catch (IOException e) {
            throw new SAXException(e.getMessage());
        }
    }

    private void serializeMap(final AbstractMapType map) throws SAXException, XPathException {
        try {
            writer.write("map{");
            for (final Iterator<Map.Entry<AtomicValue, Sequence>> i = map.iterator(); i.hasNext(); ) {
                final Map.Entry<AtomicValue, Sequence> entry = i.next();
                serialize(entry.getKey());
                writer.write(':');
                serialize(entry.getValue(), ",", true);
                if (i.hasNext()) {
                    writer.write(',');
                }
            }
            writer.write('}');
        } catch (IOException e) {
            throw new SAXException(e.getMessage());
        }
    }

    private void serializeFunctionItem(final FunctionReference item) throws XPathException, SAXException {
        final FunctionReference ref = item;
        final FunctionSignature signature = ref.getSignature();
        final QName fn = signature.getName();
        final String name;
        if (fn == InlineFunction.INLINE_FUNCTION_QNAME) {
            name = "(anonymous-function)";
        } else {
            switch (fn.getNamespaceURI()) {
                case Namespaces.XPATH_FUNCTIONS_NS:
                    name = "fn:" + fn.getLocalPart();
                    break;
                case Namespaces.XPATH_FUNCTIONS_MATH_NS:
                    name = MathModule.PREFIX + ':' + fn.getLocalPart();
                    break;
                case MapModule.NAMESPACE_URI:
                    name = MapModule.PREFIX + ':' + fn.getLocalPart();
                    break;
                case ArrayModule.NAMESPACE_URI:
                    name = ArrayModule.PREFIX + ':' + fn.getLocalPart();
                    break;
                case Namespaces.SCHEMA_NS:
                    name = "xs:" + fn.getLocalPart();
                    break;
                default:
                    name = "Q{" + fn.getNamespaceURI() + '}' + fn.getLocalPart();
                    break;
            }
        }
        serializeText(name + '#' + signature.getArgumentCount());
    }

    private String escapeQuotes(String value) {
        final StringBuilder sb = new StringBuilder(value.length() + 5);
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            switch (ch) {
                case '"':
                    sb.append(ch);
                    sb.append(ch);
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        return sb.toString();
    }

    private void serializeXML(final Item item) throws SAXException {
        final Properties xmlProperties = new Properties(outputProperties);
        xmlProperties.setProperty(OutputKeys.METHOD, "xml");
        final Serializer serializer = broker.getSerializer();
        serializer.reset();
        SAXSerializer sax = null;
        try {
            sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(
                    SAXSerializer.class);
            sax.setOutput(writer, xmlProperties);
            serializer.setProperties(xmlProperties);
            serializer.setSAXHandlers(sax, sax);
            serializer.toSAX(item, false, false);
        } catch (SAXNotSupportedException | SAXNotRecognizedException e) {
            throw new SAXException(e.getMessage(), e);
        } finally {
            if (sax != null) {
                SerializerPool.getInstance().returnObject(sax);
            }
        }
    }

    private void serializeText(final String str) throws XPathException, SAXException {
        final XMLWriter xmlWriter = writers[TEXT_WRITER];
        try {
            xmlWriter.characters(str);
        } catch (TransformerException e) {
            throw new SAXException(e.getMessage());
        }
    }
}
