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
package org.exist.xquery.value;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an XQuery SequenceType and provides methods to check
 * sequences and items against this type.
 *
 * @author wolf
 */
public class SequenceType {

    private int primaryType = Type.ITEM;
    private Cardinality cardinality = Cardinality.EXACTLY_ONE;
    private QName nodeName = null;
    private List<SequenceType> choiceAlternatives = null;
    private String[] enumValues = null;

    public SequenceType() {
    }

    /**
     * Construct a new SequenceType using the specified
     * primary type and cardinality constants.
     *
     * @param primaryType one of the constants defined in {@link Type}
     * @param cardinality one of the constants defined in {@link Cardinality}
     */
    public SequenceType(final int primaryType, final Cardinality cardinality) {
        this.primaryType = primaryType;
        this.cardinality = cardinality;
    }

    /**
     * Construct a new SequenceType using the specified
     * primary type and cardinality.
     *
     * @param primaryType one of the constants defined in {@link Type}
     * @param cardinality the cardinality integer value
     *                    
     * @deprecated Use {@link #SequenceType(int, Cardinality)}
     */
    @Deprecated
    public SequenceType(final int primaryType, final int cardinality) {
        this.primaryType = primaryType;
        this.cardinality = Cardinality.fromInt(cardinality);
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
    public Cardinality getCardinality() {
        return cardinality;
    }

    public void setCardinality(Cardinality cardinality) {
        this.cardinality = cardinality;
    }

    public QName getNodeName() {
        return nodeName;
    }

    public void setNodeName(QName qname) {
        this.nodeName = qname;
    }

    public void addChoiceAlternative(final SequenceType alt) {
        if (choiceAlternatives == null) {
            choiceAlternatives = new ArrayList<>();
        }
        choiceAlternatives.add(alt);
    }

    public List<SequenceType> getChoiceAlternatives() {
        return choiceAlternatives;
    }

    public boolean isChoiceType() {
        return choiceAlternatives != null && !choiceAlternatives.isEmpty();
    }

    public void setEnumValues(final String[] values) {
        this.enumValues = values;
        this.primaryType = Type.STRING;
    }

    public String[] getEnumValues() {
        return enumValues;
    }

    public boolean isEnumType() {
        return enumValues != null;
    }

    // Record type support

    /**
     * Represents a field in a record type declaration.
     */
    public static class RecordField {
        private final String name;
        private final boolean optional;
        private final SequenceType fieldType;

        public RecordField(final String name, final boolean optional, final SequenceType fieldType) {
            this.name = name;
            this.optional = optional;
            this.fieldType = fieldType;
        }

        public String getName() { return name; }
        public boolean isOptional() { return optional; }
        public SequenceType getFieldType() { return fieldType; }
    }

    private List<RecordField> recordFields = null;
    private boolean recordExtensible = false;

    public void addRecordField(final RecordField field) {
        if (recordFields == null) {
            recordFields = new ArrayList<>();
        }
        recordFields.add(field);
    }

    public List<RecordField> getRecordFields() {
        return recordFields;
    }

    public void setRecordExtensible(final boolean extensible) {
        this.recordExtensible = extensible;
    }

    public boolean isRecordExtensible() {
        return recordExtensible;
    }

    public boolean isRecordType() {
        return primaryType == Type.RECORD;
    }

    /**
     * Check the specified sequence against this SequenceType.
     *
     * @param seq sequence to check
     * @throws XPathException if check fails for one item in the sequence
     * @return true, if all items of the sequence have the same type as or a subtype of primaryType
     */
    public boolean checkType(final Sequence seq) throws XPathException {
        if (isChoiceType()) {
            Item next;
            for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                next = i.nextItem();
                if (!checkType(next)) {
                    return false;
                }
            }
            return true;
        }
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
    public boolean checkType(final Item item) {
        if (isChoiceType()) {
            for (final SequenceType alt : choiceAlternatives) {
                if (alt.checkType(item)) {
                    return true;
                }
            }
            return false;
        }
        if (isEnumType()) {
            if (!Type.subTypeOf(item.getType(), Type.STRING)) {
                return false;
            }
            try {
                final String val = item.getStringValue();
                for (final String enumVal : enumValues) {
                    if (enumVal.equals(val)) {
                        return true;
                    }
                }
            } catch (final XPathException e) {
                // cannot get string value
            }
            return false;
        }
        if (isRecordType()) {
            return checkRecordType(item);
        }
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

            final NodeValue nvItem = (NodeValue) item;
            QName realName = null;
            if (item.getType() == Type.DOCUMENT) {
                // it's a document... we need to get the document element's name
                final Document doc;
                if (nvItem instanceof Document) {
                    doc = (Document) nvItem;
                } else {
                    doc = nvItem.getOwnerDocument();
                }
                if (doc != null) {
                    final Element elem = doc.getDocumentElement();
                    if (elem != null) {
                        realName = new QName(elem.getLocalName(), elem.getNamespaceURI());
                    }
                }
            } else {
                // get the name of the element/attribute
                realName = nvItem.getQName();
            }

            if (realName == null) {
                return false;
            }

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
     * Check if an item matches this record type declaration.
     * A map matches a record type if:
     * - All required fields are present
     * - Each field value matches the declared type
     * - If not extensible (no *), no extra keys are present
     */
    private boolean checkRecordType(final Item item) {
        if (!Type.subTypeOf(item.getType(), Type.MAP_ITEM)) {
            return false;
        }
        // record(*) matches any map
        if (recordExtensible && (recordFields == null || recordFields.isEmpty())) {
            return true;
        }
        final org.exist.xquery.functions.map.AbstractMapType map =
                (org.exist.xquery.functions.map.AbstractMapType) item;

        // record() with no fields and not extensible: only empty maps match
        if ((recordFields == null || recordFields.isEmpty()) && !recordExtensible) {
            return map.size() == 0;
        }

        // Check required fields are present and types match
        for (final RecordField field : recordFields) {
            final AtomicValue key = new StringValue(null, field.getName());
            final boolean hasKey = map.contains(key);

            if (!hasKey && !field.isOptional()) {
                return false; // required field missing
            }

            if (hasKey && field.getFieldType() != null) {
                try {
                    final Sequence value = map.get(key);
                    if (!field.getFieldType().matchesCardinality(value)) {
                        return false;
                    }
                    if (!value.isEmpty() && !field.getFieldType().checkType(value)) {
                        return false;
                    }
                } catch (final XPathException e) {
                    return false;
                }
            }
        }

        // If not extensible, check for extra keys
        if (!recordExtensible) {
            try {
                final Sequence keys = map.keys();
                for (final SequenceIterator it = keys.iterate(); it.hasNext(); ) {
                    final String keyName = it.nextItem().getStringValue();
                    boolean declared = false;
                    for (final RecordField field : recordFields) {
                        if (field.getName().equals(keyName)) {
                            declared = true;
                            break;
                        }
                    }
                    if (!declared) {
                        return false; // undeclared key in non-extensible record
                    }
                }
            } catch (final XPathException e) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if a sequence's cardinality matches this type's cardinality declaration.
     */
    public boolean matchesCardinality(final Sequence seq) {
        if (cardinality == Cardinality.ZERO_OR_MORE) {
            return true;
        }
        final int count = seq.getItemCount();
        if (count == 0) {
            return cardinality.isSuperCardinalityOrEqualOf(Cardinality.EMPTY_SEQUENCE);
        }
        if (count == 1) {
            return true; // EXACTLY_ONE, ZERO_OR_ONE, ONE_OR_MORE all accept 1
        }
        // count > 1
        return cardinality == Cardinality.ONE_OR_MORE || cardinality == Cardinality.ZERO_OR_MORE;
    }

    /**
     * Check the given type against the primary type
     * declared in this SequenceType.
     *
     * @param type one of the constants defined in {@link Type}
     * @throws XPathException if subtype check fails
     */
    public void checkType(int type) throws XPathException {
        if (type == Type.EMPTY_SEQUENCE || type == Type.ITEM) {
            return;
        }

        //Although xs:anyURI is not a subtype of xs:string, both types are compatible
        if (type == Type.ANY_URI && primaryType == Type.STRING) {
            return;
        }

        if (!Type.subTypeOf(type, primaryType)) {
            throw new XPathException((Expression) null, ErrorCodes.XPTY0004,
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
        if (!seq.isEmpty() && cardinality == Cardinality.EMPTY_SEQUENCE) {
            throw new XPathException((Expression) null, ErrorCodes.XPTY0004, "Empty sequence expected; got " + seq.getItemCount());
        }
        if (seq.isEmpty() && cardinality.atLeastOne()) {
            throw new XPathException((Expression) null, ErrorCodes.XPTY0004, "Empty sequence is not allowed here");
        } else if (seq.hasMany() && cardinality.atMostOne()) {
            throw new XPathException((Expression) null, ErrorCodes.XPTY0004, "Sequence with more than one item is not allowed here");
        }
    }

    @Override
    public String toString() {
        if (cardinality == Cardinality.EMPTY_SEQUENCE) {
            return cardinality.toXQueryCardinalityString();
        }

        if (isChoiceType()) {
            final StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < choiceAlternatives.size(); i++) {
                if (i > 0) {
                    sb.append(" | ");
                }
                sb.append(choiceAlternatives.get(i).toString());
            }
            sb.append(")");
            sb.append(cardinality.toXQueryCardinalityString());
            return sb.toString();
        }

        if (isEnumType()) {
            final StringBuilder sb = new StringBuilder("enum(");
            for (int i = 0; i < enumValues.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("\"").append(enumValues[i]).append("\"");
            }
            sb.append(")");
            sb.append(cardinality.toXQueryCardinalityString());
            return sb.toString();
        }

        final String str;
        if (primaryType == Type.DOCUMENT && nodeName != null) {
            str = "document-node(" + nodeName.getStringValue() + ")";
        } else if (primaryType == Type.ELEMENT && nodeName != null) {
            str = "element(" + nodeName.getStringValue() + ")";
        } else if (primaryType == Type.MAP_ITEM) {
            str = "map(*)";
        } else if (primaryType == Type.ARRAY_ITEM) {
            str = "array(*)";
        } else if (primaryType == Type.FUNCTION) {
            str = "function(*)";
        } else {
            str = Type.getTypeName(primaryType);
        }

        return str + cardinality.toXQueryCardinalityString();
    }

}
