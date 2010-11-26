/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.xslt;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stax.StAXSource;

import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class StAXSequenceIterator implements SequenceIterator {
	
	private StAXSource source;
	private XMLStreamReader reader;

	public StAXSequenceIterator(StAXSource source) {
		this.source = source;
		
		reader = source.getXMLStreamReader();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.SequenceIterator#hasNext()
	 */
	@Override
	public boolean hasNext() {
		try {
			return reader.hasNext();
		} catch (XMLStreamException e) {
			return false; //TODO: report error
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.SequenceIterator#nextItem()
	 */
	@Override
	public Item nextItem() {
		// TODO Auto-generated method stub
		return null;
	}

}
