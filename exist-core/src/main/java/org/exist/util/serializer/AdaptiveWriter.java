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

package org.exist.util.serializer;

import io.lacuna.bifurcan.IEntry;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.NamespaceNode;
import org.exist.storage.DBBroker;
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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Iterator;
import java.util.Locale;
import java.util.Properties;

/**
 * Writer for the adaptive serialization method.
 */
public class AdaptiveWriter extends IndentingXMLWriter {

    private final SerializerWriter textWriter;
    private final DBBroker broker;

    public AdaptiveWriter(final DBBroker broker, final Properties outputProperties,
                          final SerializerWriter textWriter) {
        this.broker = broker;
        this.textWriter = textWriter;
        setWriter(textWriter.getWriter());
        setOutputProperties(outputProperties);
    }

    /**
     * Serialize the given sequence using adaptive output mode.
     *
     * @param sequence input sequence
     * @param itemSep separator string to output between items in the sequence
     * @param enclose if set to true: enclose sequences of items into parentheses
     * @throws SAXException if an error occurs during serialization
     * @throws XPathException if an XPath error occurs
     * @throws TransformerException if an error occurs whilst transforming
     */
    public void write(final Sequence sequence, final String itemSep, final boolean enclose) throws SAXException, XPathException, TransformerException {
        try {
            if (enclose && sequence.getItemCount() != 1) {
                writer.write('(');
            }
            for (final SequenceIterator si = sequence.iterate(); si.hasNext(); ) {
                final Item item = si.nextItem();
                switch(item.getType()) {
                    case Type.DOCUMENT:
                    case Type.ELEMENT:
                    case Type.TEXT:
                    case Type.COMMENT:
                    case Type.CDATA_SECTION:
                    case Type.PROCESSING_INSTRUCTION:
                        writeXML(item);
                        break;
                    case Type.ATTRIBUTE:
                        final Attr node = (Attr)((NodeValue)item).getNode();
                        writeText(node.getName() + "=\"" + node.getValue() + '"');
                        break;
                    case Type.NAMESPACE:
                        final NamespaceNode ns = (NamespaceNode)item;
                        writeText(ns.getName() + "=\"" + ns.getValue() + '"');
                        break;
                    case Type.STRING:
                    case Type.NORMALIZED_STRING:
                    case Type.TOKEN:
                    case Type.LANGUAGE:
                    case Type.NMTOKEN:
                    case Type.NAME:
                    case Type.NCNAME:
                    case Type.ID:
                    case Type.IDREF:
                    case Type.ENTITY:
                    case Type.UNTYPED_ATOMIC:
                    case Type.ANY_URI:
                        final String v = item.getStringValue();
                        writeText('"' + escapeQuotes(v) + '"');
                        break;
                    case Type.INTEGER:
                    case Type.DECIMAL:
                    case Type.INT:
                    case Type.LONG:
                    case Type.SHORT:
                    case Type.BYTE:
                    case Type.UNSIGNED_LONG:
                    case Type.UNSIGNED_INT:
                    case Type.UNSIGNED_SHORT:
                    case Type.UNSIGNED_BYTE:
                    case Type.NON_NEGATIVE_INTEGER:
                    case Type.NON_POSITIVE_INTEGER:
                    case Type.POSITIVE_INTEGER:
                    case Type.NEGATIVE_INTEGER:
                        writeText(item.getStringValue());
                        break;
                    case Type.DOUBLE:
                        writeDouble((DoubleValue) item);
                        break;
                    case Type.BOOLEAN:
                        writeText(item.getStringValue() + "()");
                        break;
                    case Type.QNAME:
                    case Type.NOTATION:
                        final QName qn = ((QNameValue)item).getQName();
                        writeText("Q{" + qn.getNamespaceURI() + '}' + qn.getLocalPart());
                        break;
                    case Type.ARRAY_ITEM:
                        writeArray((ArrayType)item);
                        break;
                    case Type.MAP_ITEM:
                        writeMap((AbstractMapType)item);
                        break;
                    case Type.FUNCTION:
                        writeFunctionItem((FunctionReference) item);
                        break;
                    default:
                        writeAtomic(item.atomize());
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

    private void writeText(CharSequence str) throws SAXException {
        try {
            textWriter.characters(str);
        } catch (TransformerException e) {
            throw new SAXException(e.getMessage());
        }
    }

    private void writeAtomic(AtomicValue value) throws IOException, SAXException, XPathException {
        final StringBuilder sb = new StringBuilder();
        sb.append(Type.getTypeName(value.getType()));
        sb.append("(\"");
        sb.append(value.getStringValue());
        sb.append("\")");
        writeText(sb);
    }

    private void writeDouble(final DoubleValue item) throws SAXException {
        final DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        symbols.setExponentSeparator("e");
        final DecimalFormat df = new DecimalFormat("0.0##########################E0", symbols);
        writeText(df.format(item.getDouble()));
    }

    private void writeArray(final ArrayType array) throws XPathException, SAXException, TransformerException {
        try {
            writer.write('[');

            for (int i = 0; i < array.getSize(); i++) {
                if (i > 0) {
                    writer.write(',');
                }
                final Sequence member = array.get(i);
                write(member, ",", true);
            }
            writer.write(']');
        } catch (IOException e) {
            throw new SAXException(e.getMessage());
        }
    }

    private void writeMap(final AbstractMapType map) throws SAXException, XPathException, TransformerException {
        try {
            writer.write("map");
            addSpaceIfIndent();
            writer.write('{');
            addIndent();
            indent();
            for (final Iterator<IEntry<AtomicValue, Sequence>> i = map.iterator(); i.hasNext(); ) {
                final IEntry<AtomicValue, Sequence> entry = i.next();
                write(entry.key(), "", false);
                writer.write(':');
                addSpaceIfIndent();
                write(entry.value(), ",", true);
                if (i.hasNext()) {
                    writer.write(',');
                    indent();
                }
            }
            endIndent(null, null);
            indent();
            writer.write('}');

        } catch (IOException e) {
            throw new SAXException(e.getMessage());
        }
    }

    private void writeFunctionItem(final FunctionReference item) throws SAXException {
        final FunctionSignature signature = item.getSignature();
        final QName fn = signature.getName();
        final String name;
        if (fn == InlineFunction.INLINE_FUNCTION_QNAME) {
            name = "(anonymous-function)";
        } else {
            name = switch (fn.getNamespaceURI()) {
                case Namespaces.XPATH_FUNCTIONS_NS -> "fn:" + fn.getLocalPart();
                case Namespaces.XPATH_FUNCTIONS_MATH_NS -> MathModule.PREFIX + ':' + fn.getLocalPart();
                case MapModule.NAMESPACE_URI -> MapModule.PREFIX + ':' + fn.getLocalPart();
                case ArrayModule.NAMESPACE_URI -> ArrayModule.PREFIX + ':' + fn.getLocalPart();
                case Namespaces.SCHEMA_NS -> "xs:" + fn.getLocalPart();
                default -> "Q{" + fn.getNamespaceURI() + '}' + fn.getLocalPart();
            };
        }
        writeText(name + '#' + signature.getArgumentCount());
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

    private void writeXML(final Item item) throws SAXException {
        final Properties xmlProperties = new Properties(outputProperties);
        xmlProperties.setProperty(OutputKeys.METHOD, "xml");
        final Serializer serializer = broker.borrowSerializer();
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
            broker.returnSerializer(serializer);
        }
    }
}
