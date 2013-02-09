/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.memtree;

import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.TypeInfo;

import org.exist.Namespaces;
import org.exist.dom.ElementAtExist;
import org.exist.dom.NamedNodeMapImpl;
import org.exist.dom.NodeListImpl;
import org.exist.dom.QName;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class ElementImpl extends NodeImpl implements ElementAtExist {

    public ElementImpl( DocumentImpl doc, int nodeNumber ) {
        super( doc, nodeNumber );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#getTagName()
     */
    public String getTagName() {
        return( getNodeName() );
    }

    @Override
    public QName getQName() {
        return( document.nodeName[nodeNumber] );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#hasChildNodes()
     */
    @Override
    public boolean hasChildNodes() {
        return( ( ( nodeNumber + 1 ) < document.size ) && ( document.treeLevel[nodeNumber + 1] > document.treeLevel[nodeNumber] ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getFirstChild()
     */
    @Override
    public Node getFirstChild() {
        final short level    = document.treeLevel[nodeNumber];
        final int   nextNode = nodeNumber + 1;
        if( ( nextNode < document.size ) && ( document.treeLevel[nextNode] > level ) )
            {return( document.getNode( nextNode ) );}
        return null;
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getChildNodes()
     */
    @Override
    public NodeList getChildNodes() {
        final NodeListImpl nl       = new NodeListImpl();
        int          nextNode = document.getFirstChildFor( nodeNumber );
        while( nextNode > nodeNumber ) {
            final Node n = document.getNode( nextNode );
            nl.add( n );
            nextNode = document.next[nextNode];
        }
        return( nl );
    }

    public int getChildCount() {
        return( document.getChildCountFor( nodeNumber ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getNamespaceURI()
     */
    @Override
    public String getNamespaceURI() {
        return( getQName().getNamespaceURI() );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getPrefix()
     */
    @Override
    public String getPrefix() {
        return( getQName().getPrefix() );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getLocalName()
     */
    @Override
    public String getLocalName() {
        return( getQName().getLocalName() );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#hasAttributes()
     */
    @Override
    public boolean hasAttributes() {
        return( ( document.alpha[nodeNumber] > -1 ) || ( document.alphaLen[nodeNumber] > -1 ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#getAttribute(java.lang.String)
     */
    public String getAttribute( String name ) {
        int attr = document.alpha[nodeNumber];
        if( -1 < attr ) {
            while( ( attr < document.nextAttr ) && ( document.attrParent[attr] == nodeNumber ) ) {
                final QName attrQName = document.attrName[attr];
                if( attrQName.getStringValue().equals( name ) ) {
                    return( document.attrValue[attr] );
                }
                ++attr;
            }
        }
        if( name.startsWith( "xmlns:" ) ) {
            int ns = document.alphaLen[nodeNumber];
            if( -1 < ns ) {
                while( ( ns < document.nextNamespace ) && ( document.namespaceParent[ns] == nodeNumber ) ) {
                    final QName nsQName = document.namespaceCode[ns];
                    if( nsQName.getStringValue().equals( name ) ) {
                        return( nsQName.getNamespaceURI() );
                    }
                    ++ns;
                }
            }
        }
        return( null );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#setAttribute(java.lang.String, java.lang.String)
     */
    public void setAttribute( String name, String value ) throws DOMException {
        final int lastNode = document.getLastNode();

        QName qname;
		try {
			qname = QName.parse(document.context, name);
		} catch (final XPathException e) {
			throw new DOMException(DOMException.SYNTAX_ERR, e.getMessage());
		}
        
        document.addAttribute( lastNode, qname, value, AttributeImpl.ATTR_CDATA_TYPE );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#removeAttribute(java.lang.String)
     */
    public void removeAttribute( String arg0 ) throws DOMException {
        // TODO Auto-generated method stub
    }

    public int getAttributesCount() {
        return( document.getAttributesCountFor( nodeNumber ) + document.getNamespacesCountFor( nodeNumber ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getAttributes()
     */
    @Override
    public NamedNodeMap getAttributes() {
        final NamedNodeMapImpl map  = new NamedNodeMapImpl();
        int              attr = document.alpha[nodeNumber];
        if( -1 < attr ) {
            while( ( attr < document.nextAttr ) && ( document.attrParent[attr] == nodeNumber ) ) {
                map.add( new AttributeImpl( document, attr ) );
                ++attr;
            }
        }
        // add namespace declarations attached to this element
        int ns = document.alphaLen[nodeNumber];
        if( ns < 0 ) {
            return( map );
        }
        while( ( ns < document.nextNamespace ) && ( document.namespaceParent[ns] == nodeNumber ) ) {
            final NamespaceNode node = new NamespaceNode( document, ns );
            map.add( node );
            ++ns;
        }
        return( map );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#getAttributeNode(java.lang.String)
     */
    public Attr getAttributeNode( String name ) {
        int attr = document.alpha[nodeNumber];
        if( -1 < attr ) {
            while( ( attr < document.nextAttr ) && ( document.attrParent[attr] == nodeNumber ) ) {
                final QName attrQName = document.attrName[attr];
                if( attrQName.getStringValue().equals( name ) ) {
                    return( new AttributeImpl( document, attr ) );
                }
                ++attr;
            }
        }
        if( name.startsWith( "xmlns:" ) ) {
            int ns = document.alphaLen[nodeNumber];
            if( -1 < ns ) {
                while( ( ns < document.nextNamespace ) && ( document.namespaceParent[ns] == nodeNumber ) ) {
                    final QName nsQName = document.namespaceCode[ns];
                    if( nsQName.getStringValue().equals( name ) ) {
                        return( new NamespaceNode( document, ns ) );
                    }
                    ++ns;
                }
            }
        }
        return( null );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#setAttributeNode(org.w3c.dom.Attr)
     */
    public Attr setAttributeNode( Attr arg0 ) throws DOMException {
        // TODO Auto-generated method stub
        return( null );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#removeAttributeNode(org.w3c.dom.Attr)
     */
    public Attr removeAttributeNode( Attr arg0 ) throws DOMException {
        // TODO Auto-generated method stub
        return( null );
    }

    @Override
    public void selectAttributes( NodeTest test, Sequence result ) throws XPathException {
        int attr = document.alpha[nodeNumber];
        if( -1 < attr ) {
            while( ( attr < document.nextAttr ) && ( document.attrParent[attr] == nodeNumber ) ) {
                final AttributeImpl attrib = new AttributeImpl( document, attr );
                if( test.matches( attrib ) ) {
                    result.add( attrib );
                }
                ++attr;
            }
        }
    }

    @Override
    public void selectDescendantAttributes( NodeTest test, Sequence result ) throws XPathException {
        final int      treeLevel = document.treeLevel[nodeNumber];
        int      nextNode  = nodeNumber;
        NodeImpl n         = document.getNode( nextNode );
        n.selectAttributes( test, result );
        while( ( ++nextNode < document.size ) && ( document.treeLevel[nextNode] > treeLevel ) ) {
            n = document.getNode( nextNode );
            if( n.getNodeType() == Node.ELEMENT_NODE ) {
                n.selectAttributes( test, result );
            }
        }
    }

    @Override
    public void selectChildren( NodeTest test, Sequence result ) throws XPathException {
        int nextNode = document.getFirstChildFor( nodeNumber );
        while( nextNode > nodeNumber ) {
            final NodeImpl n = document.getNode( nextNode );
            if( test.matches( n ) ) {
                result.add( n );
            }
            nextNode = document.next[nextNode];
        }
    }

    public NodeImpl getFirstChild(NodeTest test) throws XPathException {
    	final ValueSequence seq = new ValueSequence();
    	selectChildren(test, seq);
    	return seq.isEmpty() ? null : seq.get(0);
    }
    
    @Override
    public void selectDescendants( boolean includeSelf, NodeTest test, Sequence result ) 
            throws XPathException {
        final int treeLevel = document.treeLevel[nodeNumber];
        int nextNode  = nodeNumber;

        if( includeSelf ) {
            final NodeImpl n = document.getNode( nextNode );
            if( test.matches( n ) ) {
                result.add( n );
            }
        }

        while( ( ++nextNode < document.size ) && ( document.treeLevel[nextNode] > treeLevel ) ) {
            final NodeImpl n = document.getNode( nextNode );
            if( test.matches( n ) ) {
                result.add( n );
            }
        }
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#getElementsByTagName(java.lang.String)
     */
    public NodeList getElementsByTagName( String name ) {
        final NodeListImpl nl       = new NodeListImpl();
        int          nextNode = nodeNumber;
        final int treeLevel = document.treeLevel[nodeNumber];
        while( ( ++nextNode < document.size ) && ( document.treeLevel[nextNode] > treeLevel ) ) {
            if( document.nodeKind[nextNode] == Node.ELEMENT_NODE ) {
                final QName qn = document.nodeName[nextNode];
                if( qn.getStringValue().equals( name ) ) {
                    nl.add( document.getNode( nextNode ) );
                }
            }
        }
        return( nl );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#getAttributeNS(java.lang.String, java.lang.String)
     */
    public String getAttributeNS( String namespaceURI, String localName ) {
        int attr = document.alpha[nodeNumber];
        if( -1 < attr ) {
            QName name;
            while( ( attr < document.nextAttr ) && ( document.attrParent[attr] == nodeNumber ) ) {
                name = document.attrName[attr];
                if( name.getLocalName().equals( localName ) && name.getNamespaceURI().equals( namespaceURI ) ) {
                    return( document.attrValue[attr] );
                }
                ++attr;
            }
        }
        if( Namespaces.XMLNS_NS.equals( namespaceURI ) ) {
            int ns = document.alphaLen[nodeNumber];
            if( -1 < ns ) {
                while( ( ns < document.nextNamespace ) && ( document.namespaceParent[ns] == nodeNumber ) ) {
                    final QName nsQName = document.namespaceCode[ns];
                    if( nsQName.getLocalName().equals( localName ) ) {
                        return( nsQName.getNamespaceURI() );
                    }
                    ++ns;
                }
            }
        }
        return( null );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#setAttributeNS(java.lang.String, java.lang.String, java.lang.String)
     */
    public void setAttributeNS( String arg0, String arg1, String arg2 ) throws DOMException {
        // TODO Auto-generated method stub
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#removeAttributeNS(java.lang.String, java.lang.String)
     */
    public void removeAttributeNS( String arg0, String arg1 ) throws DOMException {
        // TODO Auto-generated method stub
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#getAttributeNodeNS(java.lang.String, java.lang.String)
     */
    public Attr getAttributeNodeNS( String namespaceURI, String localName ) {
        int attr = document.alpha[nodeNumber];
        if( -1 < attr ) {
            QName name;
            while( ( attr < document.nextAttr ) && ( document.attrParent[attr] == nodeNumber ) ) {
                name = document.attrName[attr];
                if( name.getLocalName().equals( localName ) && name.getNamespaceURI().equals( namespaceURI ) ) {
                    return( new AttributeImpl( document, attr ) );
                }
                ++attr;
            }
        }
        if( Namespaces.XMLNS_NS.equals( namespaceURI ) ) {
            int ns = document.alphaLen[nodeNumber];
            if( -1 < ns ) {
                while( ( ns < document.nextNamespace ) && ( document.namespaceParent[ns] == nodeNumber ) ) {
                    final QName nsQName = document.namespaceCode[ns];
                    if( nsQName.getLocalName().equals( localName ) ) {
                        return( new NamespaceNode( document, ns ) );
                    }
                    ++ns;
                }
            }
        }
        return( null );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#setAttributeNodeNS(org.w3c.dom.Attr)
     */
    public Attr setAttributeNodeNS( Attr arg0 ) throws DOMException
    {
        // TODO Auto-generated method stub
        return( null );
    }


    /* (non-Javadoc)
     * @see org.w3c.dom.Element#getElementsByTagNameNS(java.lang.String, java.lang.String)
     */
    public NodeList getElementsByTagNameNS( String namespaceURI, String name ) {
        final QName        qname    = new QName( name, namespaceURI );
        final NodeListImpl nl       = new NodeListImpl();
        int          nextNode = nodeNumber;
        while( ++nextNode < document.size ) {
            if( document.nodeKind[nextNode] == Node.ELEMENT_NODE ) {
                final QName qn = document.nodeName[nextNode];
                if( qname.compareTo( qn ) == 0 ) {
                    nl.add( document.getNode( nextNode ) );
                }
            }
            if( document.next[nextNode] <= nodeNumber ) {
                break;
            }
        }
        return( nl );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#hasAttribute(java.lang.String)
     */
    public boolean hasAttribute( String name ) {
        return( getAttribute( name ) != null );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Element#hasAttributeNS(java.lang.String, java.lang.String)
     */
    public boolean hasAttributeNS( String namespaceURI, String localName ) {
        return( getAttributeNS( namespaceURI, localName ) != null );
    }

    /**
     * The method <code>getNamespaceForPrefix.</code>
     *
     * @param   name  a <code>String</code> value
     *
     * @return  a <code>String</code> value
     */
    public String getNamespaceForPrefix( String name ) {
        int ns = document.alphaLen[nodeNumber];
        if( -1 < ns ) {
            while( ( ns < document.nextNamespace ) && ( document.namespaceParent[ns] == nodeNumber ) ) {
                final QName nsQName = document.namespaceCode[ns];
                if( nsQName.getStringValue().equals( "xmlns:" + name ) ) {
                    return( nsQName.getNamespaceURI() );
                }
                ++ns;
            }
        }
        return( null );
    }

    /**
     * The method <code>getPrefixes.</code>
     *
     * @return  a <code>Set</code> value
     */
    public Set<String> getPrefixes() {
        final HashSet<String> set = new HashSet<String>();
        int             ns  = document.alphaLen[nodeNumber];
        if( -1 < ns ) {
            while( ( ns < document.nextNamespace ) && ( document.namespaceParent[ns] == nodeNumber ) ) {
                final QName nsQName = document.namespaceCode[ns];
                set.add( nsQName.getStringValue() );
                ++ns;
            }
        }
        return( set );
    }

    /**
     * The method <code>declaresNamespacePrefixes.</code>
     *
     * @return  a <code>boolean</code> value
     */
    public boolean declaresNamespacePrefixes() {
        return( document.getNamespacesCountFor( nodeNumber ) > 0 );
    }

    /**
     * The method <code>getNamespaceMap.</code>
     *
     * @return  a <code>Map</code> value
     */
    public Map<String, String> getNamespaceMap() {
    	return getNamespaceMap(new HashMap<String, String>());
    }
    
    public Map<String, String> getNamespaceMap(Map<String, String> map) {
        int ns  = document.alphaLen[nodeNumber];
        if( -1 < ns ) {
            while( ( ns < document.nextNamespace ) && ( document.namespaceParent[ns] == nodeNumber ) ) {
                final QName nsQName = document.namespaceCode[ns];
                map.put( nsQName.getLocalName(), nsQName.getNamespaceURI() );
                ++ns;
            }
        }
        
        int attr = document.alpha[nodeNumber];
        if( -1 < attr ) {
            while( ( attr < document.nextAttr ) && ( document.attrParent[attr] == nodeNumber ) ) {
            	final QName qname = document.attrName[attr];
            	if (qname.getPrefix() != null && !qname.getPrefix().isEmpty())
            		{map.put( qname.getPrefix(), qname.getNamespaceURI() );}
                ++attr;
            }
        }
        
        return map;
    }

    @Override
    public int getItemType() {
        return( Type.ELEMENT );
    }

    @Override
    public String getBaseURI() {
    	final XmldbURI baseURI = calculateBaseURI();
    	if (baseURI != null)
    		{return baseURI.toString();}
    	
    	return "";//UNDERSTAND: is it ok?
    }

    //please, keep in sync with org.exist.dom.ElementImpl
    protected XmldbURI calculateBaseURI() {
    	XmldbURI baseURI = null;
    	
        final String nodeBaseURI = getAttributeNS( Namespaces.XML_NS, "base" );
        if( nodeBaseURI != null ) {
        	baseURI = XmldbURI.create(nodeBaseURI, false);
        	if (baseURI.isAbsolute())
        		{return baseURI;}
        }
        
        int parent = -1;
        int test   = -1;
        test = document.getParentNodeFor( nodeNumber );
        if( document.nodeKind[test] != Node.DOCUMENT_NODE ) {
            parent = test;
        }
        
        if (parent != -1) {
            if( nodeBaseURI == null ) {
                baseURI = document.getNode( parent )
                	.calculateBaseURI();
            } else {
                XmldbURI parentsBaseURI = document.getNode( parent )
                	.calculateBaseURI();

                if (nodeBaseURI.isEmpty())
                	{baseURI = parentsBaseURI;}
                else {
                	baseURI = parentsBaseURI.append(baseURI);
                }
            }
        } else {
        	if (nodeBaseURI == null)
        		{return XmldbURI.create(getDocument().getBaseURI(), false);}
        	else if (nodeNumber == 1) {
        		;
        	} else {
        		final String docBaseURI = getDocument().getBaseURI();
                if (docBaseURI.endsWith("/")) {
                	baseURI = XmldbURI.create(getDocument().getBaseURI(), false);
                	baseURI.append(baseURI);
                } else {
                	baseURI = XmldbURI.create(getDocument().getBaseURI(), false);
                	baseURI = baseURI.removeLastSegment();
                	baseURI.append(baseURI);
                }
        	}
        }
        return baseURI;
    }

    /**
     * ? @see org.w3c.dom.Element#getSchemaTypeInfo()
     *
     * @return  DOCUMENT ME!
     */
    public TypeInfo getSchemaTypeInfo() {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return( null );
    }

    /**
     * ? @see org.w3c.dom.Element#setIdAttribute(java.lang.String, boolean)
     *
     * @param   name  DOCUMENT ME!
     * @param   isId  DOCUMENT ME!
     *
     * @throws  DOMException  DOCUMENT ME!
     */
    public void setIdAttribute( String name, boolean isId ) throws DOMException {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
    }

    /**
     * ? @see org.w3c.dom.Element#setIdAttributeNS(java.lang.String, java.lang.String, boolean)
     *
     * @param   namespaceURI  DOCUMENT ME!
     * @param   localName     DOCUMENT ME!
     * @param   isId          DOCUMENT ME!
     *
     * @throws  DOMException  DOCUMENT ME!
     */
    public void setIdAttributeNS( String namespaceURI, String localName, boolean isId )
            throws DOMException {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
    }

    /**
     * ? @see org.w3c.dom.Element#setIdAttributeNode(org.w3c.dom.Attr, boolean)
     *
     * @param   idAttr  DOCUMENT ME!
     * @param   isId    DOCUMENT ME!
     *
     * @throws  DOMException  DOCUMENT ME!
     */
    public void setIdAttributeNode( Attr idAttr, boolean isId ) throws DOMException {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
    }

    @Override
    public void setTextContent( String textContent ) throws DOMException
    {
        final int nodeNr = document.addNode( Node.TEXT_NODE, (short)( document.getTreeLevel( nodeNumber ) + 1 ), null );
        document.addChars( nodeNr, textContent.toCharArray(), 0, textContent.length() );
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append( "in-memory#" );
        result.append( "element {" );
        result.append( getQName().getStringValue() );
        result.append( "} {" );
        NamedNodeMap theAttrs;
        if( ( theAttrs = getAttributes() ) != null ) {
            for( int i = 0; i < theAttrs.getLength(); i++ ) {
                if( i > 0 ) {
                    result.append( " " );
                }
                final Node natt = theAttrs.item( i );
                if( "org.exist.memtree.AttributeImpl".equals( natt.getClass().getName() ) ) {
                    result.append( ( (AttributeImpl)natt ).toString() );
                } else {
                    result.append( ( (NamespaceNode)natt ).toString() );
                }
            }
        }
        for( int i = 0; i < this.getChildCount(); i++ ) {
            if( i > 0 ) {
                result.append( " " );
            }
            final Node child = getChildNodes().item( i );
            result.append( child.toString() );
        }
        result.append( "} " );
        return( result.toString() );
    }

    @Override
    public String getNodeValue() throws DOMException {
        final StringBuilder result = new StringBuilder();
        for( int i = 0; i < this.getChildCount(); i++ ) {
            final Node child = getChildNodes().item( i );
            if( child instanceof Text ) {
                if( i > 0 ) {
                    result.append( " " );
                }
                result.append( ( (Text)child ).getData() );
            }
        }
        return( result.toString() );
    }
}
