/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
package org.exist.dom.memtree;

import org.w3c.dom.CDATASection;
import org.w3c.dom.DOMException;
import org.w3c.dom.Text;

import org.exist.xquery.value.Type;


/**
 * Represents a CDATA section.
 *
 * @author wolf
 */
public class CDATASectionImpl extends AbstractCharacterData implements CDATASection {

    public CDATASectionImpl(final DocumentImpl doc, final int nodeNumber) {
        super(doc, nodeNumber);
    }

    @Override
    public int getItemType() {
        return Type.CDATA_SECTION;
    }

    @Override
    public Text splitText(final int offset) throws DOMException {
        return null;
    }

    @Override
    public String getNodeValue() {
        return getData();
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
    public Text replaceWholeText(final String content) throws DOMException {
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        if(isPersistentSet()) {
            result.append("persistent ");
        }
        result.append("in-memory#");
        result.append("CDATA {");
        result.append(getData());
        result.append("}");
        return result.toString();
    }
}
