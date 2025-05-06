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

package org.exist.xquery.functions.fn.transform;

import net.sf.saxon.s9api.*;
import net.sf.saxon.type.BuiltInAtomicType;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.fn.FnTransform;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.transform.dom.DOMSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Type conversion to and from Saxon
 *
 * <p>
 *     Used to convert values to/from Saxon when we use Saxon as the XSLT transformer
 *     as in the fn:transform implementation {@link FnTransform}
 *     A very minimal set of conversions where they are absolutely needed.
 *     Most conversion is carried out via a document, but that is insufficient in a few cases
 *     (where the delivery-format is raw and a template has a specified output type
 *     e.g. <xsl:template name='main' as='xs:integer'></xsl:template>
 *
 *     The correct path would be to make this conversion comprehensive and use it in all cases.
 *     It's not clear how easy or hard that would be.
 * </p>
 */
class Convert {

    private Convert() {
        super();
    }

    static class ToExist {

        private ToExist() { super(); }

        static Sequence of(final XdmValue xdmValue) throws XPathException {
            if (xdmValue.size() == 0) {
                return Sequence.EMPTY_SEQUENCE;
            }

            final ValueSequence valueSequence = new ValueSequence();
            for (final XdmItem xdmItem : xdmValue) {

                valueSequence.add(ToExist.ofItem(xdmItem));
            }
            return valueSequence;
        }

        static Item ofItem(final XdmItem xdmItem) throws XPathException {

            if (xdmItem.isAtomicValue()) {
                final net.sf.saxon.value.AtomicValue atomicValue = (net.sf.saxon.value.AtomicValue) xdmItem.getUnderlyingValue();
                final BuiltInAtomicType atomicType = atomicValue.getPrimitiveType();
                if (atomicType == BuiltInAtomicType.INTEGER) {
                    return new IntegerValue(atomicValue.getStringValue());
                } else if (atomicType == BuiltInAtomicType.DOUBLE) {
                    return new DoubleValue(atomicValue.getStringValue());
                } else {
                    throw new XPathException(ErrorCodes.XPTY0004,
                            "net.sf.saxon.value.AtomicValue " + atomicValue + COULD_NOT_BE_CONVERTED + "atomic value");
                }
            } else if (xdmItem instanceof XdmNode) {
                return ToExist.ofNode((XdmNode)xdmItem);
            }

            throw new XPathException(ErrorCodes.XPTY0004,
                    "XdmItem " + xdmItem + COULD_NOT_BE_CONVERTED + "Sequence");
        }

        static NodeValue ofNode(final XdmNode xdmNode) throws XPathException {

            throw new XPathException(ErrorCodes.XPTY0004,
                    "XdmNode " + xdmNode + COULD_NOT_BE_CONVERTED + " Node");
        }
    }

    static final private String COULD_NOT_BE_CONVERTED = " could not be converted to an eXist ";

    abstract static class ToSaxon {

        abstract DocumentBuilder newDocumentBuilder();

        static net.sf.saxon.s9api.QName of(final QName qName) {
            return new net.sf.saxon.s9api.QName(qName.getPrefix() == null ? "" : qName.getPrefix(), qName.getNamespaceURI(), qName.getLocalPart());
        }

        static net.sf.saxon.s9api.QName of(final QNameValue qName) {
            return of(qName.getQName());
        }

        XdmValue of(final Item item) throws XPathException {
            if (item instanceof NodeProxy) {
              return ofNode(((NodeProxy) item).getNode());
            }
            final int itemType = item.getType();
            if (Type.subTypeOf(itemType, Type.ATOMIC)) {
                return ofAtomic((AtomicValue) item);
            } else if (Type.subTypeOf(itemType, Type.NODE)) {
                return ofNode((Node) item);
            }
            throw new XPathException(ErrorCodes.XPTY0004,
                    "Item " + item + " of type " + Type.getTypeName(itemType) + COULD_NOT_BE_CONVERTED + "XdmValue");
        }

        static private XdmValue ofAtomic(final AtomicValue atomicValue) throws XPathException {
            final int itemType = atomicValue.getType();
            if (Type.subTypeOf(itemType, Type.INTEGER)) {
                return XdmValue.makeValue(((IntegerValue) atomicValue).getInt());
            } else if (Type.subTypeOf(itemType, Type.NUMBER)) {
                return XdmValue.makeValue(((NumericValue) atomicValue).getDouble());
            } else if (Type.subTypeOf(itemType, Type.BOOLEAN)) {
                return XdmValue.makeValue(((BooleanValue) atomicValue).getValue());
            } else if (Type.subTypeOf(itemType, Type.STRING)) {
                return XdmValue.makeValue(((StringValue) atomicValue).getStringValue());
            }

            throw new XPathException(ErrorCodes.XPTY0004,
                    "Atomic value " + atomicValue + " of type " + Type.getTypeName(itemType) +
                            COULD_NOT_BE_CONVERTED + "XdmValue");
        }

        private XdmValue ofNode(final Node node) throws XPathException {

            final DocumentBuilder sourceBuilder = newDocumentBuilder();
            try {
                if (node instanceof DocumentImpl) {
                    return sourceBuilder.build(new DOMSource(node));
                } else {
                    //The source must be part of a document
                    final Document document = node.getOwnerDocument();
                    if (document == null) {
                        throw new XPathException(ErrorCodes.XPTY0004, "Node " + node + COULD_NOT_BE_CONVERTED + "XdmValue, as it is not part of a document.");
                    }
                    final List<Integer> nodeIndex = TreeUtils.treeIndex(node);
                    final XdmNode xdmDocument = sourceBuilder.build(new DOMSource(document));
                    return TreeUtils.xdmNodeAtIndex(xdmDocument, nodeIndex);
                }
            } catch (final SaxonApiException e) {
                throw new XPathException(ErrorCodes.XPTY0004, "Node " + node + COULD_NOT_BE_CONVERTED + "XdmValue", e);
            }
        }

        XdmValue[] of(final ArrayType values) throws XPathException {
            final int size = values.getSize();
            final XdmValue[] result = new XdmValue[size];
            for (int i = 0; i < size; i++) {
                final Sequence sequence = values.get(i);
                result[i] = XdmValue.makeValue(listOf(sequence));
            }
            return result;
        }

        XdmValue of(final Sequence value) throws XPathException {
            return XdmValue.makeSequence(listOf(value));
        }

        private List<XdmValue> listOf(final Sequence value) throws XPathException {
            final int size = value.getItemCount();
            final List<XdmValue> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                result.add(of(value.itemAt(i)));
            }
            return result;
        }
    }
}
