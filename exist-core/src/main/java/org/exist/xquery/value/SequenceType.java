/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.value;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.XPathException;
import org.w3c.dom.Node;

/**
 * Represents an XQuery SequenceType and provides methods to check
 * sequences and items against this type.
 *
 * @author wolf
 */
public class SequenceType {

    private int primaryType = Type.ITEM;
    private int cardinality = Cardinality.EXACTLY_ONE;
    private QName nodeName = null;

    public SequenceType() {
    }

    /**
     * Construct a new SequenceType using the specified
     * primary type and cardinality constants.
     *
     * @param primaryType one of the constants defined in {@link Type}
     * @param cardinality one of the constants defined in {@link Cardinality}
     */
    public SequenceType(int primaryType, int cardinality) {
        this.primaryType = primaryType;
        this.cardinality = cardinality;
    }

    /**
     * Returns the primary type as one of the
     * constants defined in {@link Type}.
     *
     * @return the primary type as one of the constants defined in {@link Type}
     */
    public int getPrimaryType() {
        return primaryType;
    }

    public void setPrimaryType(int type) {
        this.primaryType = type;
    }

    /**
     * Returns the expected cardinality. See the constants
     * defined in {@link Cardinality}.
     *
     * @return expected cardinality, one of {@link Cardinality}
     */
    public int getCardinality() {
        return cardinality;
    }

    public void setCardinality(int cardinality) {
        this.cardinality = cardinality;
    }

    public QName getNodeName() {
        return nodeName;
    }

    public void setNodeName(QName qname) {
        this.nodeName = qname;
    }

    /**
     * Check the specified sequence against this SequenceType.
     *
     * @param seq sequence to check
     * @throws XPathException if check fails for one item in the sequence
     * @return true, if all items of the sequence have the same type as or a subtype of primaryType
     */
    public boolean checkType(Sequence seq) throws XPathException {
        if (nodeName != null) {
            Item next;
            for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                next = i.nextItem();
                if (!checkType(next)) {
                    return false;
                }
            }
            return true;
        } else {
            return Type.subTypeOf(seq.getItemType(), primaryType);
        }
    }

    /**
     * Check a single item against this SequenceType.
     *
     * @param item the item to check
     * @return true, if item is a subtype of primaryType
     */
    public boolean checkType(Item item) {
        Node realNode = null;
        int type = item.getType();
        if (type == Type.NODE) {
            realNode = ((NodeValue) item).getNode();
            type = realNode.getNodeType();
        }
        if (!Type.subTypeOf(type, primaryType)) {
            return false;
        }
        if (nodeName != null) {
            //TODO : how to improve performance ?
            final QName realName = ((NodeValue) item).getQName();
            if (nodeName.getNamespaceURI() != null) {
                if (!nodeName.getNamespaceURI().equals(realName.getNamespaceURI())) {
                    return false;
                }
            }
            if (nodeName.getLocalPart() != null) {
                return nodeName.getLocalPart().equals(realName.getLocalPart());
            }
        }
        return true;
    }

    /**
     * Check the given type against the primary type
     * declared in this SequenceType.
     *
     * @param type one of the constants defined in {@link Type}
     * @throws XPathException if subtype check fails
     */
    public void checkType(int type) throws XPathException {
        if (type == Type.EMPTY || type == Type.ITEM) {
            return;
        }

        //Although xs:anyURI is not a subtype of xs:string, both types are compatible
        if (type == Type.ANY_URI && primaryType == Type.STRING) {
            return;
        }

        if (!Type.subTypeOf(type, primaryType)) {
            throw new XPathException(
                    "Type error: expected type: "
                            + Type.getTypeName(primaryType)
                            + "; got: "
                            + Type.getTypeName(type));
        }
    }

    /**
     * Check if the given sequence has the cardinality required
     * by this sequence type.
     *
     * @param seq the sequence to check
     * @throws XPathException if cardinality does not match
     */
    public void checkCardinality(Sequence seq) throws XPathException {
        if (!seq.isEmpty() && cardinality == Cardinality.EMPTY) {
            throw new XPathException("Empty sequence expected; got " + seq.getItemCount());
        }
        if (seq.isEmpty() && (cardinality & Cardinality.ZERO) == 0) {
            throw new XPathException("Empty sequence is not allowed here");
        } else if (seq.hasMany() && (cardinality & Cardinality.MANY) == 0) {
            throw new XPathException("Sequence with more than one item is not allowed here");
        }
    }

    public String toString() {
        if (cardinality == Cardinality.EMPTY) {
            return Cardinality.toString(cardinality);
        }
        return Type.getTypeName(primaryType) + Cardinality.toString(cardinality);
    }

}
