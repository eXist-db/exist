/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
package org.exist.storage.md;

import java.util.List;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentAtExist;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class MetaData {

    public final static boolean enabled = true;

	protected static MetaData _ = null;
	
	public static MetaData get() {
		return _;
	}
	
	protected final static Logger LOG = Logger.getLogger(MetaData.class);

	public abstract DocumentImpl getDocument(String uuid) throws EXistException, PermissionDeniedException;

	public abstract List<DocumentImpl> matchDocuments(String key, String value) throws EXistException, PermissionDeniedException;

	public abstract Metas addMetas(DocumentAtExist doc);

//	public abstract Metas getMetas(DocumentImpl doc);
	public abstract Metas getMetas(XmldbURI uri);

//	public abstract void delMetas(DocumentImpl doc);
	public abstract void delMetas(XmldbURI uri);

	public abstract void sync();

	public abstract void close();
}
