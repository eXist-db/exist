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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.md;

import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ExtractorIndex extends AbstractIndex {
	
	protected static final String ID = ExtractorIndex.class.getName();;
	
	public ExtractorIndex() {
		setName("meta data extractor");
	}
	
	@Override
    public String getIndexId() {
		return ID;
	}

	@Override
	public void open() throws DatabaseConfigurationException {
	}

	@Override
	public void close() throws DBException {
	}

	@Override
	public void sync() throws DBException {
	}

	@Override
	public void remove() throws DBException {
	}

	@Override
	public IndexWorker getWorker(DBBroker broker) {
		return new ExtractorWorker(this, broker);
	}

	@Override
	public boolean checkIndex(DBBroker broker) {
		return true;
	}

}
