/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
package org.exist.ant;

import java.io.File;

import org.apache.tools.ant.BuildException;
import org.exist.backup.Restore;

/**
 * @author wolf
 */
public class RestoreTask extends AbstractXMLDBTask {

	private String file = null;
	
	/* (non-Javadoc)
	 * @see org.apache.tools.ant.Task#execute()
	 */
	public void execute() throws BuildException {
		if (uri == null)
			throw new BuildException("You have to specify an XMLDB collection URI");
		if(file == null)
			throw new BuildException("Missing required argument: file");
		File f = new File(file);
		if(!f.canRead())
			throw new BuildException("Cannot read restore file: " + file);
		registerDatabase();
		try {
			log("Restoring from " + f.getAbsolutePath());
			Restore restore = new Restore(user, password, f, uri);
			restore.restore(false, null);
		} catch (Exception e) {
			e.printStackTrace();
			throw new BuildException("Exception during restore: " + e.getMessage(), e);
		}
	}

	/**
	 * @param file
	 */
	public void setFile(String file) {
		this.file = file;
	}

	public void setBase(String base)  {
		this.uri = base;
	}
}
