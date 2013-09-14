/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.memtree;

import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.TypeInfo;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.QNameable;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;


/**
 * A dynamically constructed namespace node. Used to track namespace declarations in elements. Implements Attr, so it can be treated as a normal
 * attribute.
 *
 * @author  wolf
 */
public class NamespaceNode extends NodeImpl implements Attr, QNameable
{
    /**
     * Creates a new NamespaceNode object.
     *
     * @param  doc
     * @param  nodeNumber
     */
    public NamespaceNode( DocumentImpl doc, int nodeNumber )
    {
        super( doc, nodeNumber );
    }

    /* (non-Javadoc)
     * @see org.exist.memtree.NodeImpl#getNodeType()
     */
    public short getNodeType()
    {
        //TOUNDERSTAND : return value
        //XQuery doesn't support namespace nodes
        //so, mapping as an attribute at *serialization tile*  makes sense
        //however, the Query parser should not accept them in constructors !
        return( NodeImpl.NAMESPACE_NODE);
    }


    /* (non-Javadoc)
     * @see org.exist.memtree.NodeImpl#getType()
     */
    public int getType()
    {
        return( Type.NAMESPACE );
    }


    public String getPrefix()
    {
        return( getQName().getPrefix() );
    }


    public String getNamespaceURI()
    {
        return( Namespaces.XMLNS_NS );
    }


    public boolean getSpecified()
    {
        return( true );
    }


    public QName getQName()
    {
        return( document.namespaceCode[nodeNumber] );
    }


    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getLocalName()
     */
    public String getLocalName()
    {
        return( getQName().getLocalName() );
    }


    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getNodeName()
     */
    public String getNodeName()
    {
        return( getQName().getStringValue() );
    }


    public String getName()
    {
        return( getQName().getStringValue() );
    }


    /* (non-Javadoc)
     * @see org.w3c.dom.Attr#getValue()
     */
    public String getValue()
    {
        return( getQName().getNamespaceURI() );
    }


    /* (non-Javadoc)
     * @see org.w3c.dom.Attr#setValue(java.lang.String)
     */
    public void setValue( String value ) throws DOMException
    {
    }

    @Override
    public Node getFirstChild() {
        return null;
    }

    @Override
    public Node getLastChild() {
        return null;
    }

    public String getNodeValue() throws DOMException
    {
        return( getQName().getNamespaceURI() );
    }


    /* (non-Javadoc)
     * @see org.w3c.dom.Attr#getOwnerElement()
     */
    public Element getOwnerElement()
    {
        return( (Element)document.getNode( document.namespaceParent[nodeNumber] ) );
    }


    /**
     * ? @see org.w3c.dom.Attr#getSchemaTypeInfo()
     *
     * @return  DOCUMENT ME!
     */
    public TypeInfo getSchemaTypeInfo()
    {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return( null );
    }


    /**
     * ? @see org.w3c.dom.Attr#isId()
     *
     * @return  DOCUMENT ME!
     */
    public boolean isId()
    {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return( false );
    }


    public int getItemType()
    {
        return( Type.NAMESPACE );
    }


    //Untested
    public String toString()
    {
        final StringBuilder result = new StringBuilder();
        result.append( "in-memory#" );
        result.append( "namespace {" );
        result.append( getPrefix() );
        result.append( "}" );
        result.append( "{" );
        result.append( getValue() );
        result.append( "} " );
        return( result.toString() );
    }

    @Override
    public void selectAttributes(NodeTest test, Sequence result)
            throws XPathException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void selectChildren(NodeTest test, Sequence result)
            throws XPathException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void selectDescendantAttributes(NodeTest test, Sequence result)
            throws XPathException {
        // TODO Auto-generated method stub
        
    }
}
