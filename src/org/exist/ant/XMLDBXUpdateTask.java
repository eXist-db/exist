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
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * an ant task to update a collection or resource using XUpdate
 *
 * @author peter.klotz@blue-elephant-systems.com
 */
public class XMLDBXUpdateTask extends AbstractXMLDBTask
{

  private String resource = null;
  private String commands = null;

  /* (non-Javadoc)
   * @see org.apache.tools.ant.Task#execute()
   */
  public void execute() throws BuildException
  {
    if (uri == null)
      throw new BuildException("You have to specify an XMLDB collection URI");

    log("XUpdate command is: " + commands, Project.MSG_DEBUG);
    registerDatabase();
    try
    {
      log("Get base collection: " + uri, Project.MSG_DEBUG);
      Collection base = DatabaseManager.getCollection(uri, user, password);

      if(base==null){
         throw new BuildException("Collection " + uri + " could not be found.");
      }

      XUpdateQueryService service = (XUpdateQueryService) base.getService("XUpdateQueryService", "1.0");
      if (resource != null)
      {
        log("Updating resource: " + resource, Project.MSG_INFO);
        Resource res = base.getResource(resource);
        if (res == null)
          throw new BuildException("Resource " + resource + " not found.");
        service.updateResource(resource, commands);
      } else
      {
        log("Updating collection: " + base.getName(), Project.MSG_INFO);
        service.update(commands);
      }
    } catch (XMLDBException e)
    {
      throw new BuildException("XMLDB exception during remove: " + e.getMessage(), e);
    }
  }


  /**
   * @param resource
   */
  public void setResource(String resource)
  {
    this.resource = resource;
  }

  public void setCommands(String commands)
  {
    this.commands = commands;
  }
}
