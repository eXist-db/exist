/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.indexing.lucene;

import org.exist.dom.QName;
import org.exist.util.XMLString;

public class DefaultTextExtractor extends AbstractTextExtractor {

    private int stack = 0;
    private boolean addSpaceBeforeNext = false;
    
    public int startElement(QName name) {
        if(isInlineNode(name)) {
            // discard not yet applied whitespaces
            addSpaceBeforeNext = false;
        }
        if (config.isIgnoredNode(name) || (idxConfig != null && idxConfig.isIgnoredNode(name)))
            stack++;
        else if (!isInlineNode(name) && buffer.length() > 0 && buffer.charAt(buffer.length() - 1) != ' ') {
        	// separate the current element's text from preceding text
            buffer.append(' ');
            return 1;
        }
        return 0;
    }

	private boolean isInlineNode(QName name) {
		return (config.isInlineNode(name) || (idxConfig != null && idxConfig.isInlineNode(name)));
	}

    public int endElement(QName name) {
        if (config.isIgnoredNode(name) || (idxConfig != null && idxConfig.isIgnoredNode(name)))
            stack--;
        else if (!isInlineNode(name)) {
        	// add space before following text
        	addSpaceBeforeNext = true;
        }
        return 0;
    }

    public int beforeCharacters() {
    	if (addSpaceBeforeNext && buffer.length() > 0 && buffer.charAt(buffer.length() - 1) != ' ') {
    		// separate the previous element's text from following text
    		buffer.append(' ');
    		addSpaceBeforeNext = false;
    		return 1;
    	}
    	return 0;
    }
    
    public int characters(XMLString text) {
        if (stack == 0) {
            buffer.append(text);
            return text.length();
        }
        return 0;
    }
}
