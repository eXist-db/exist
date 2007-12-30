/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * an ant task to move a collection or resource to a new name
 *
 * @author peter.klotz@blue-elephant-systems.com
 */
public class XMLDBMoveTask extends AbstractXMLDBTask
{

  private String resource = null;
  private String collection = null;
  private String destination = null;
  private String name = null;

  /* (non-Javadoc)
   * @see org.apache.tools.ant.Task#execute()
   */
  public void execute() throws BuildException
  {
    if (uri == null)
      throw new BuildException("You have to specify an XMLDB collection URI");
    if (resource == null && collection == null)
      throw new BuildException("Missing parameter: either resource or collection should be specified");

    registerDatabase();
    try
    {
      log("Get base collection: " + uri, Project.MSG_DEBUG);
      Collection base = DatabaseManager.getCollection(uri, user, password);

      if(base==null){
         throw new BuildException("Collection " + uri + " could not be found.");
      }

      log("Create collection management service for collection " + base.getName(), Project.MSG_DEBUG);
      CollectionManagementServiceImpl service = (CollectionManagementServiceImpl) base.getService("CollectionManagementService", "1.0");
      if (resource != null)
      {
        log("Moving resource: " + resource, Project.MSG_INFO);
        Resource res = base.getResource(resource);
        if (res == null)
          throw new BuildException("Resource " + resource + " not found.");
        service.moveResource(resource, destination, name);
      } else
      {
        log("Moving collection: " + collection, Project.MSG_INFO);
        service.move(collection, destination, name);
      }
    } catch (XMLDBException e)
    {
      throw new BuildException("XMLDB exception during remove: " + e.getMessage(), e);
    }
  }

  /**
   * @param collection
   */
  public void setCollection(String collection)
  {
    this.collection = collection;
  }

  /**
   * @param resource
   */
  public void setResource(String resource)
  {
    this.resource = resource;
  }

  public void setDestination(String destination)
  {
    this.destination = destination;
  }

  public void setName(String name)
  {
    this.name = name;
  }
}
