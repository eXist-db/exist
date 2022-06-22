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

package org.exist.xquery.functions.fn;

import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.type.BuiltInAtomicType;
import org.exist.dom.QName;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Type conversion to and from Saxon
 * <p>
 * TODO (AP) not yet remotely complete.
 * /p>
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

    static class ToExist {

        static Sequence of(final XdmValue xdmValue) throws XPathException {
            if (xdmValue.size() == 0) {
                return Sequence.EMPTY_SEQUENCE;
            }

            XdmItem item = null;
            if (xdmValue instanceof XdmItem) {
                item = (XdmItem)xdmValue;
            } else if (xdmValue.size() == 1) {
                item = xdmValue.itemAt(0);
            }
            if (item != null) {
                return ToExist.ofItem(item);
            }

            throw new XPathException(ErrorCodes.XPTY0004,
                    "XdmValue " + xdmValue +
                            " could not be converted to an eXist Sequence");
        }

        static Sequence ofItem(final XdmItem xdmItem) throws XPathException {

            if (xdmItem.isAtomicValue()) {
                final net.sf.saxon.value.AtomicValue atomicValue = (net.sf.saxon.value.AtomicValue) xdmItem.getUnderlyingValue();
                final BuiltInAtomicType atomicType = atomicValue.getPrimitiveType();
                if (atomicType == BuiltInAtomicType.INTEGER) {
                    return new IntegerValue(atomicValue.getStringValue());
                } else {
                    // TODO (AP)
                    throw new XPathException(ErrorCodes.XPTY0004,
                            "net.sf.saxon.value.AtomicValue " + atomicValue +
                                    " could not be converted to an eXist Sequence");
                }
            } else if (xdmItem instanceof XdmNode) {
                final XdmNode xdmNode = (XdmNode)xdmItem;
                final Sequence sequence = new ValueSequence();
                for (final XdmNode child : xdmNode.children()) {
                    //TODO (AP) sequence.add(ofItem(child));
                }
                return sequence;
            }

            throw new XPathException(ErrorCodes.XPTY0004,
                    "XdmItem " + xdmItem +
                            " could not be converted to an eXist Sequence");
        }
    }

    static class ToSaxon {

        static net.sf.saxon.s9api.QName of(final QName qName) {
            return new net.sf.saxon.s9api.QName(qName.getPrefix() == null ? "" : qName.getPrefix(), qName.getNamespaceURI(), qName.getLocalPart());
        }

        static net.sf.saxon.s9api.QName of(final QNameValue qName) {
            return of(qName.getQName());
        }

        static XdmValue of(final Item item) throws XPathException {
            final int itemType = item.getType();
            if (Type.subTypeOf(itemType, Type.ATOMIC)) {
                final AtomicValue atomicValue = (AtomicValue) item;
                if (Type.subTypeOf(itemType, Type.INTEGER)) {
                    return XdmValue.makeValue(((IntegerValue) atomicValue).getInt());
                } else if (Type.subTypeOf(itemType, Type.NUMBER)) {
                    return XdmValue.makeValue(((NumericValue) atomicValue).getDouble());
                } else if (Type.subTypeOf(itemType, Type.BOOLEAN)) {
                    return XdmValue.makeValue(((BooleanValue) atomicValue).getValue());
                } else if (Type.subTypeOf(itemType, Type.STRING)) {
                    return XdmValue.makeValue(((StringValue) atomicValue).getStringValue());
                }
            }
            throw new XPathException(ErrorCodes.XPTY0004,
                    "Item " + item + " of type " + Type.getTypeName(itemType) +
                            " could not be converted to an XdmValue");
        }

        static XdmValue[] of(final ArrayType values) throws XPathException {
            final int size = values.getSize();
            final XdmValue[] result = new XdmValue[size];
            for (int i = 0; i < size; i++) {
                final Sequence sequence = values.get(i);
                result[i] = XdmValue.makeValue(ToSaxon.listOf(sequence));
            }
            return result;
        }

        static XdmValue of(final Sequence value) throws XPathException {
            return XdmValue.makeSequence(ToSaxon.listOf(value));
        }

        static private List<Object> listOf(final Sequence value) throws XPathException {
            final int size = value.getItemCount();
            final List<Object> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                result.add(ToSaxon.of(value.itemAt(i)));
            }
            return result;
        }
    }
}
