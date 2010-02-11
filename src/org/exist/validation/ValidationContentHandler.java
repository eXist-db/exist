/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */

package org.exist.validation;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *  Simple contenthandler to determine the NamespaceUri of
 * the document root node.
 * 
 * @author Dannes Wessels
 */
public class ValidationContentHandler extends DefaultHandler {

    private boolean isFirstElement = true;
    private String namespaceUri = null;
    
    
    /**
     * @see org.xml.sax.helpers.DefaultHandler#startElement(String,String,String,Attributes)
     */
    @Override
    public void startElement(String uri, String localName, String qName, 
                             Attributes attributes) throws SAXException {
        
        if(isFirstElement){
            namespaceUri=uri;
            isFirstElement=false;
        }
    }
    
    /**
     *  Get namespace of root element. To be used for reporting.
     * 
     * @return Namespace of root element.
     */
    public String getNamespaceUri(){
        return namespaceUri;
    }
}