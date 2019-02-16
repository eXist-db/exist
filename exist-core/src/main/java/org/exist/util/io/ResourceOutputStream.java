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
package org.exist.util.io;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ResourceOutputStream extends FileOutputStream {

	private Resource resource;
	
	public ResourceOutputStream(Resource file) throws FileNotFoundException {
		super(file.getFile().toFile());
		
		resource = file;
	}
	
	public ResourceOutputStream(Resource file, boolean append) throws FileNotFoundException {
		super(file.getFile().toFile(), append);
		
		resource = file;
	}
	
	public void close() throws IOException {
		super.close();

		resource.freeFile();

		//XXX: xml upload back to db
		
		//XXX: locking?
	}
}
