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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import java.util.StringTokenizer;

/**
 * an ant task to create a empty collection
 *
 * @author peter.klotz@blue-elephant-systems.com
 */
public class XMLDBCreateTask extends AbstractXMLDBTask
{
  private String collection = null;

  /* (non-Javadoc)
   * @see org.apache.tools.ant.Task#execute()
   */
  public void execute() throws BuildException
  {
    if (uri == null)
      throw new BuildException("you have to specify an XMLDB collection URI");

    registerDatabase();
    try
    {
      log("Get base collection: " + uri, Project.MSG_DEBUG);
      Collection base = DatabaseManager.getCollection(uri, user, password);
      if (base == null)
        throw new BuildException("collection " + uri + " not found");

      Collection root = null;
      if (collection != null)
      {
        log("Creating collection " + collection + " in base collection " + uri, Project.MSG_DEBUG);
        root = mkcol(base, uri, null, collection);
      } else
      {
        root = base;
      }
      log("Created collection " + root.getName(), Project.MSG_INFO);
    } catch (XMLDBException e)
    {
      throw new BuildException("XMLDB exception caught: " + e.getMessage(), e);
    }
  }


  /**
   * @param collection
   */
  public void setCollection(String collection)
  {
    this.collection = collection;
  }

  private final Collection mkcol(Collection root, String baseURI, String path, String relPath)
    throws XMLDBException
  {
    CollectionManagementService mgtService;
    Collection current = root, c;
    String token;
    StringTokenizer tok = new StringTokenizer(relPath, "/");
    log("BASEURI=" + baseURI, Project.MSG_DEBUG);
    log("RELPATH=" + relPath, Project.MSG_DEBUG);
    log("PATH=" + path, Project.MSG_DEBUG);
    //TODO : use dedicated function in XmldbURI
    while (tok.hasMoreTokens())
    {
      token = tok.nextToken();
      if (path != null)
      {
        path = path + "/" + token;
      } else
      {
        path = "/" + token;
      }
      log("Get collection " + baseURI + path, Project.MSG_DEBUG);
      c = DatabaseManager.getCollection(baseURI + path, user, password);
      if (c == null)
      {
        log("Create collection management service for collection " + current.getName(), Project.MSG_DEBUG);
        mgtService = (CollectionManagementService) current.getService("CollectionManagementService", "1.0");
        log("Create child collection " + token);
        current = mgtService.createCollection(token);
        log("Created collection " + current.getName() + '.');
      } else
        current = c;
    }
    return current;
  }
}
