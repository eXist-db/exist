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
package org.exist.dom.memtree;

import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;


public class TextImpl extends NodeImpl implements Text
{
    public TextImpl( DocumentImpl doc, int nodeNumber )
    {
        super( doc, nodeNumber );
    }

//    public boolean hasChildNodes() {
//        return false;
//    }

    @Override
    public String getStringValue()
    {
        //Quick and (not so ?) dirty...
        return( getData() );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Text#splitText(int)
     */
    @Override
    public Text splitText( int arg0 ) throws DOMException
    {
        // _TODO_ Auto-generated method stub
        return( null );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.CharacterData#getData()
     */
    @Override
    public String getData() throws DOMException
    {
        return( new String( document.characters, document.alpha[nodeNumber], document.alphaLen[nodeNumber] ) );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.CharacterData#setData(java.lang.String)
     */
    @Override
    public void setData( String arg0 ) throws DOMException
    {
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.CharacterData#getLength()
     */
    @Override
    public int getLength()
    {
        return( document.alphaLen[nodeNumber] );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getNodeValue()
     */
    @Override
    public String getNodeValue() throws DOMException
    {
        return( getData() );
    }

    @Override
    public String getLocalName()
    {
        return( "" );
    }

    @Override
    public String getNamespaceURI()
    {
        return( "" );
    }

    @Override
    public Node getFirstChild()
    {
        return( null );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.CharacterData#substringData(int, int)
     */
    @Override
    public String substringData( int arg0, int arg1 ) throws DOMException
    {
        // _TODO_ Auto-generated method stub
        return( null );
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.CharacterData#appendData(java.lang.String)
     */
    @Override
    public void appendData( String arg0 ) throws DOMException
    {
        // _TODO_ Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.w3c.dom.CharacterData#insertData(int, java.lang.String)
     */
    @Override
    public void insertData( int arg0, String arg1 ) throws DOMException
    {
        // _TODO_ Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.w3c.dom.CharacterData#deleteData(int, int)
     */
    @Override
    public void deleteData( int arg0, int arg1 ) throws DOMException
    {
        // _TODO_ Auto-generated method stub
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.CharacterData#replaceData(int, int, java.lang.String)
     */
    @Override
    public void replaceData( int arg0, int arg1, String arg2 ) throws DOMException
    {
        // _TODO_ Auto-generated method stub
    }

    /**
     * ? @see org.w3c.dom.Text#isElementContentWhitespace()
     *
     * @return  DOCUMENT ME!
     */
    @Override
    public boolean isElementContentWhitespace()
    {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return( false );
    }

    /**
     * ? @see org.w3c.dom.Text#getWholeText()
     *
     * @return  DOCUMENT ME!
     */
    @Override
    public String getWholeText()
    {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return( null );
    }

    /**
     * ? @see org.w3c.dom.Text#replaceWholeText(java.lang.String)
     *
     * @param   content  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  DOMException  DOCUMENT ME!
     */
    @Override
    public Text replaceWholeText( String content ) throws DOMException
    {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return( null );
    }

    @Override
    public int getItemType()
    {
        return( Type.TEXT );
    }

    @Override
    public String toString()
    {
        final StringBuilder result = new StringBuilder();
        result.append( "in-memory#" );
        result.append( "text {" );
        result.append( getData() );
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
