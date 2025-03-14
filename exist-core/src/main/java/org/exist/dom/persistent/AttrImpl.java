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
package org.exist.dom.persistent;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.RangeIndexSpec;
import org.exist.storage.Signatures;
import org.exist.util.ByteArrayPool;
import org.exist.util.ByteConversion;
import org.exist.util.UTF8;
import org.exist.util.XMLString;
import org.exist.util.pool.NodePool;
import org.exist.util.serializer.AttrList;
import org.exist.xquery.Expression;
import org.w3c.dom.*;

import javax.xml.XMLConstants;

import static java.nio.charset.StandardCharsets.UTF_8;

public class AttrImpl extends NamedNode<AttrImpl> implements Attr {

    public static final int LENGTH_NS_ID = 2; //sizeof short
    public static final int LENGTH_PREFIX_LENGTH = 2; //sizeof short

    public static final int CDATA = 0;
    public static final int ID = 1;
    public static final int IDREF = 2;
    public static final int IDREFS = 3;

    private static final int DEFAULT_ATTRIBUTE_TYPE = CDATA;

    private int attributeType = DEFAULT_ATTRIBUTE_TYPE;
    private int indexType = RangeIndexSpec.NO_INDEX;
    private XMLString value = null;

    public AttrImpl() {
        this((Expression) null);
    }

    public AttrImpl(final Expression expression) {
        super(expression, Node.ATTRIBUTE_NODE);
    }

    public AttrImpl(final QName name, final SymbolTable symbols) throws DOMException {
        this(null, name, symbols);
    }

    public AttrImpl(final Expression expression, final QName name, final SymbolTable symbols) throws DOMException {
        super(expression, Node.ATTRIBUTE_NODE, name);
        if(symbols != null && symbols.getSymbol(nodeName.getLocalPart()) < 0) {
            throw new DOMException(DOMException.INVALID_ACCESS_ERR,
                "Too many element/attribute names registered in the database. No of distinct names is limited to 16bit. Aborting store.");
        }
    }

    public AttrImpl(final QName name, final String str, final SymbolTable symbols) throws DOMException {
        this(null, name, str, symbols);
    }

    public AttrImpl(final Expression expression, final QName name, final String str, final SymbolTable symbols) throws DOMException {
        this(expression, name, symbols);
        this.value = new XMLString(str.toCharArray());
    }

    public AttrImpl(final AttrImpl other) {
        this(null, other);
    }

    public AttrImpl(final Expression expression, final AttrImpl other) {
        super(expression, other);
        this.attributeType = other.attributeType;
        this.value = new XMLString(other.value);
    }

    @Override
    public void clear() {
        super.clear();
        this.attributeType = DEFAULT_ATTRIBUTE_TYPE;
        this.value.reset();
        this.value = null;
    }

    /**
     * Serializes a (persistent DOM) Attr to a byte array
     *
     * data = signature nodeIdUnitsLength nodeId localNameId namespace? value
     *
     * signature = [byte] 0x80 | localNameType | attrType | hasNamespace?
     *
     * localNameType = noContent OR intContent OR shortContent OR byteContent
     * noContent = 0x0
     * intContent = 0x1
     * shortContent = 0x2
     * byteContent = 0x3
     *
     * attrType = cdata OR id OR idref OR idrefs
     * cdata = 0x0;
     * id = 0x4
     * idref = 0x8
     * idrefs = 0xC
     *
     * hasNamespace = 0x10
     *
     * nodeIdUnitsLength = [short] (2 bytes) The number of units of the attr's NodeId
     * nodeId = {@link org.exist.numbering.DLNBase#serialize(byte[], int)}
     *
     * localNameId = [int] (4 bytes) | [short] (2 bytes) | [byte] 1 byte. The Id of the attr's local name from SymbolTable (symbols.dbx)
     *
     * namespace = namespaceUriId namespacePrefixLength attrNamespacePrefix?
     * namespaceUriId = [short] (2 bytes) The Id of the namespace URI from SymbolTable (symbols.dbx)
     * namespacePrefixLength = [short] (2 bytes)
     * attrNamespacePrefix = eUtf8
     *
     * value = eUtf8
     *
     * eUtf8 = {@link org.exist.util.UTF8#encode(java.lang.String, byte[], int)}
     *
     * @return the returned byte array after use must be returned to the ByteArrayPool
     *     by calling {@link ByteArrayPool#releaseByteArray(byte[])}
     */
    @Override
    public byte[] serialize() {
        if(nodeName.getLocalPart() == null) {
            throw new RuntimeException("Local name is null");
        }
        final short id = ownerDocument.getBrokerPool().getSymbols().getSymbol(this);
        final byte idSizeType = Signatures.getSizeType(id);
        int prefixLen = 0;
        if(nodeName.hasNamespace() && nodeName.getPrefix() != null && nodeName.getPrefix().length() > 0) {
            prefixLen = UTF8.encoded(nodeName.getPrefix());
        }
        final int nodeIdLen = nodeId.size();
        final byte[] data = ByteArrayPool.getByteArray(
            LENGTH_SIGNATURE_LENGTH + NodeId.LENGTH_NODE_ID_UNITS + nodeIdLen +
                Signatures.getLength(idSizeType) +
                (nodeName.hasNamespace() ? LENGTH_NS_ID + LENGTH_PREFIX_LENGTH + prefixLen : 0) +
                value.UTF8Size());
        int pos = 0;
        data[pos] = (byte) (Signatures.Attr << 0x5);
        data[pos] |= idSizeType;
        data[pos] |= (byte) (attributeType << 0x2);
        if(nodeName.hasNamespace()) {
            data[pos] |= 0x10;
        }
        pos += StoredNode.LENGTH_SIGNATURE_LENGTH;
        ByteConversion.shortToByte((short) nodeId.units(), data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        nodeId.serialize(data, pos);
        pos += nodeIdLen;
        Signatures.write(idSizeType, id, data, pos);
        pos += Signatures.getLength(idSizeType);
        if(nodeName.hasNamespace()) {
            final short nsId = ownerDocument.getBrokerPool().getSymbols().getNSSymbol(nodeName.getNamespaceURI());
            ByteConversion.shortToByte(nsId, data, pos);
            pos += LENGTH_NS_ID;
            ByteConversion.shortToByte((short) prefixLen, data, pos);
            pos += LENGTH_PREFIX_LENGTH;
            if(nodeName.getPrefix() != null && nodeName.getPrefix().length() > 0) {
                UTF8.encode(nodeName.getPrefix(), data, pos);
            }
            pos += prefixLen;
        }
        value.UTF8Encode(data, pos);
        return data;
    }

    public static StoredNode deserialize(final byte[] data, final int start, final int len, final DocumentImpl doc, final boolean pooled) {
        int pos = start;
        final byte idSizeType = (byte) (data[pos] & 0x3);
        final boolean hasNamespace = (data[pos] & 0x10) == 0x10;
        final int attrType = (data[pos] & 0x4) >> 0x2;
        pos += StoredNode.LENGTH_SIGNATURE_LENGTH;
        final int dlnLen = ByteConversion.byteToShort(data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        final NodeId dln = doc.getBrokerPool().getNodeFactory().createFromData(dlnLen, data, pos);
        pos += dln.size();
        final short id = (short) Signatures.read(idSizeType, data, pos);
        pos += Signatures.getLength(idSizeType);
        final String name = doc.getBrokerPool().getSymbols().getName(id);
        if(name == null) {
            throw new RuntimeException("no symbol for id " + id);
        }
        short nsId = 0;
        String prefix = null;
        if(hasNamespace) {
            nsId = ByteConversion.byteToShort(data, pos);
            pos += LENGTH_NS_ID;
            int prefixLen = ByteConversion.byteToShort(data, pos);
            pos += LENGTH_PREFIX_LENGTH;
            if(prefixLen > 0) {
                prefix = UTF8.decode(data, pos, prefixLen).toString();
            }
            pos += prefixLen;
        }
        final String namespace = nsId == 0 ? "" : doc.getBrokerPool().getSymbols().getNamespace(nsId);
        final XMLString value = UTF8.decode(data, pos, len - (pos - start));

        //OK : we have the necessary material to build the attribute
        final AttrImpl attr;
        if(pooled) {
            attr = (AttrImpl) NodePool.getInstance().borrowNode(Node.ATTRIBUTE_NODE);
        } else {
            attr = new AttrImpl((Expression) null);
        }
        attr.setNodeName(doc.getBrokerPool().getSymbols().getQName(Node.ATTRIBUTE_NODE, namespace, name, prefix));
        if (attr.value != null) {
            attr.value.reset();
        }
        attr.value = value;
        attr.setNodeId(dln);
        attr.setType(attrType);
        return attr;
    }

    public static void addToList(final DBBroker broker, final byte[] data, final int start, final int len, final AttrList list) {
        int pos = start;
        final byte idSizeType = (byte) (data[pos] & 0x3);
        final boolean hasNamespace = (data[pos] & 0x10) == 0x10;
        final int attrType = (data[pos] & 0x4) >> 0x2;
        pos += StoredNode.LENGTH_SIGNATURE_LENGTH;
        final int dlnLen = ByteConversion.byteToShort(data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        final NodeId dln = broker.getBrokerPool().getNodeFactory().createFromData(dlnLen, data, pos);
        pos += dln.size();
        final short id = (short) Signatures.read(idSizeType, data, pos);
        pos += Signatures.getLength(idSizeType);
        final String name = broker.getBrokerPool().getSymbols().getName(id);
        if(name == null) {
            throw new RuntimeException("no symbol for id " + id);
        }
        short nsId = 0;
        String prefix = null;
        if(hasNamespace) {
            nsId = ByteConversion.byteToShort(data, pos);
            pos += LENGTH_NS_ID;
            int prefixLen = ByteConversion.byteToShort(data, pos);
            pos += LENGTH_PREFIX_LENGTH;
            if(prefixLen > 0) {
                prefix = UTF8.decode(data, pos, prefixLen).toString();
            }
            pos += prefixLen;
        }
        final String namespace = nsId == 0 ? XMLConstants.NULL_NS_URI : broker.getBrokerPool().getSymbols().getNamespace(nsId);
        final String value = new String(data, pos, len - (pos - start), UTF_8);

        list.addAttribute(broker.getBrokerPool().getSymbols().getQName(Node.ATTRIBUTE_NODE, namespace, name, prefix), value, attrType, dln);
    }

    @Override
    public String getName() {
        return getNodeName();
    }

    public int getType() {
        return attributeType;
    }

    public void setType(final int type) {
        //TODO : range check -pb
        this.attributeType = type;
    }

    public static String getAttributeType(final int type) {
        return switch (type) {
            case AttrImpl.ID -> "ID";
            case AttrImpl.IDREF -> "IDREF";
            case AttrImpl.IDREFS -> "IDREFS";
            case AttrImpl.CDATA -> "CDATA";
            default -> null;
        };
    }

    public void setIndexType(final int idxType) {
        this.indexType = idxType;
    }

    public int getIndexType() {
        return indexType;
    }

    @Override
    public String getValue() {
        if(value == null) {
            return "";
        } else {
            return value.toString();
        }
    }

    @Override
    public void setValue(final String value) throws DOMException {
        if (this.value != null) {
            this.value.reset();
            this.value.append(value);
        } else {
            this.value = new XMLString(value.toCharArray());
        }
    }

    @Override
    public String getNodeValue() {
        return getValue();
    }

    @Override
    public void setNodeValue(final String nodeValue) throws DOMException {
       setValue(nodeValue);
    }

    @Override
    public Element getOwnerElement() {
        return (Element)getOwnerDocument().getNode(nodeId.getParentId());
    }

    @Override
    public Node getParentNode() {
        return null;
    }

    @Override
    public StoredNode getParentStoredNode() {
        return (StoredNode)getOwnerDocument().getNode(nodeId.getParentId());
    }

    @Override
    public boolean getSpecified() {
        return true;
    }

    @Override
    public String toString() {
        return nodeName + "=\"" + value + "\"";
    }

    @Override
    public String toString(final boolean top) {
        if(top) {
            return"<exist:attribute " + "xmlns:exist=\"" + Namespaces.EXIST_NS + "\" " +
                    "exist:id=\"" +  getNodeId() +  "\" exist:source=\"" +
                    getOwnerDocument().getFileURI() + "\" " +  getNodeName() + "=\"" + getValue() + "\"/>";
        } else {
            return toString();
        }
    }

    @Override
    public int getChildCount() {
        return 0;
    }

    @Override
    public Node getFirstChild() {
        return null;
    }

    @Override
    public Node getNextSibling() {
        return null;
    }

    @Override
    public TypeInfo getSchemaTypeInfo() {
        return null;
    }

    @Override
    public boolean isId() {
        return this.getType() == ID;
    }

    @Override
    public String getBaseURI() {
        final Element e = getOwnerElement();
        if(e != null) {
            return e.getBaseURI();
        }
        return null;
    }

    @Override
    public short compareDocumentPosition(final Node other) throws DOMException {
        return 0;
    }

    @Override
    public String getTextContent() throws DOMException {
        return getNodeValue();
    }

    @Override
    public void setTextContent(final String textContent) throws DOMException {
        setNodeValue(textContent);
    }

    @Override
    public String lookupPrefix(final String namespaceURI) {
        return null;
    }

    @Override
    public boolean isDefaultNamespace(final String namespaceURI) {
        return false;
    }

    @Override
    public String lookupNamespaceURI(final String prefix) {
        return null;
    }

    @Override
    public boolean isEqualNode(final Node arg) {
        return false;
    }

    @Override
    public Object getFeature(final String feature, final String version) {
        return null;
    }
    @Override
    public Object setUserData(final String key, final Object data, final UserDataHandler handler) {
        return null;
    }

    @Override
    public Object getUserData(final String key) {
        return null;
    }

    @Override
    public boolean equals(final Object obj) {
        if(!super.equals(obj)) {
            return false;
        }

        if(obj instanceof AttrImpl other) {
            return other.getQName().equals(getQName())
                    && other.value.equals(value);
        }

        return false;
    }
}

