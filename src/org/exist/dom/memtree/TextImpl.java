/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 Wolfgang M. Meier
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

import org.exist.xquery.value.Type;
import org.w3c.dom.DOMException;
import org.w3c.dom.Text;


public class TextImpl extends AbstractCharacterData implements Text {

    public TextImpl(final DocumentImpl doc, final int nodeNumber) {
        super(doc, nodeNumber);
    }

    @Override
    public int getItemType() {
        return Type.TEXT;
    }

    @Override
    public Text splitText(final int offset) throws DOMException {
        return null;
    }

    @Override
    public boolean isElementContentWhitespace() {
        return false;
    }

    @Override
    public String getWholeText() {
        return null;
    }

    @Override
    public Text replaceWholeText(String content) throws DOMException {
        return null;
    }

    @Override
    public String toString() {
        return "in-memory#text {" + getData() + "} ";
    }

}
