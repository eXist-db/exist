/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011-2012 The eXist Project
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
package org.exist.collections.triggers;

import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

/**
 *
 * @author aretter
 */
public class LexicalHandlerWrapper implements LexicalHandler {

    private final LexicalHandler output;
    
    LexicalHandlerWrapper(LexicalHandler output, DocumentTrigger trigger) {
        this.output = output;
        trigger.setLexicalOutputHandler(output);
    }

    @Override
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        output.startDTD(name, publicId, systemId);
    }

    @Override
    public void endDTD() throws SAXException {
        output.endDTD();
    }

    @Override
    public void startEntity(String name) throws SAXException {
        output.startEntity(name);
    }

    @Override
    public void endEntity(String name) throws SAXException {
        output.endEntity(name);
    }

    @Override
    public void startCDATA() throws SAXException {
        output.startCDATA();
    }

    @Override
    public void endCDATA() throws SAXException {
        output.endCDATA();
    }

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        output.comment(ch, start, length);
    }   
}