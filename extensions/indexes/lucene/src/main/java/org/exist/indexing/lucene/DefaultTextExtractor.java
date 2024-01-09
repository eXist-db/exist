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
package org.exist.indexing.lucene;

import org.exist.dom.QName;
import org.exist.util.XMLString;

public class DefaultTextExtractor extends AbstractTextExtractor {

    private int stack = 0;
    private boolean addSpaceBeforeNext = false;
    
    public int startElement(QName name) {
        if (isInlineNode(name)) {
            // discard not yet applied whitespaces
            addSpaceBeforeNext = false;
        }
        if (isIgnoredNode(name)) {
            stack++;
        } else if (!isInlineNode(name) && !buffer.isEmpty() && buffer.charAt(buffer.length() - 1) != ' ') {
        	// separate the current element's text from preceding text
            buffer.append(' ');
            return 1;
        }
        return 0;
    }

    private boolean isIgnoredNode(final QName name) {
        return (config.isIgnoredNode(name) || (idxConfig != null && idxConfig.isIgnoredNode(name)));
    }

	private boolean isInlineNode(final QName name) {
		return (config.isInlineNode(name) || (idxConfig != null && idxConfig.isInlineNode(name)));
	}

    public int endElement(final QName name) {
        if (isIgnoredNode(name)) {
            stack--;
        } else if (!isInlineNode(name)) {
        	// add space before following text
        	addSpaceBeforeNext = true;
        }
        return 0;
    }

    public int beforeCharacters() {
    	if (addSpaceBeforeNext && !buffer.isEmpty() && buffer.charAt(buffer.length() - 1) != ' ') {
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
