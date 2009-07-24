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
import org.xmldb.api.base.XMLDBException;

/**
 * an ant task to list the sub-collections or resources in a collection
 *
 * @author peter.klotz@blue-elephant-systems.com
 */
public class XMLDBListTask extends AbstractXMLDBTask
{

  private boolean collections = false;
  private boolean resources = false;
  private String separator = ",";
  private String outputproperty;

  /* (non-Javadoc)
   * @see org.apache.tools.ant.Task#execute()
   */
  public void execute() throws BuildException
  {
    if (uri == null)
      throw new BuildException("You have to specify an XMLDB collection URI");
    if (collections == false && resources == false)
      throw new BuildException("You have at least one of collections or resources or both");

    registerDatabase();
    try
    {
      log("Get base collection: " + uri, Project.MSG_DEBUG);
      Collection base = DatabaseManager.getCollection(uri, user, password);

      if(base==null){
    	  String msg="Collection " + uri + " could not be found.";
    	  if(failonerror)
    		  throw new BuildException(msg);
    	  else
    		  log(msg,Project.MSG_ERR);
      }
        
      StringBuilder buf = new StringBuilder();
      if (collections)
      {
        String[] cols = base.listChildCollections();
        if (cols != null)
        {
          log("Listing child collections", Project.MSG_DEBUG);
          for (int i = 0; i < cols.length; i++)
          {
            buf.append(cols[i]);
            if (i < cols.length - 1)
            {
              buf.append(separator);
            }
          }
        }
      }
      if (resources)
      {
        log("Listing resources", Project.MSG_DEBUG);
        String[] res = base.listResources();
        if (res != null)
        {
          if (buf.length() > 0)
          {
            buf.append(separator);
          }
          for (int i = 0; i < res.length; i++)
          {
            buf.append(res[i]);
            if (i < res.length - 1)
            {
              buf.append(separator);
            }
          }
        }
      }

      if (buf.length() > 0)
      {
        log("Set property " + outputproperty, Project.MSG_INFO);
        getProject().setNewProperty(outputproperty, buf.toString());
      }
    } catch (XMLDBException e)
    {
  	  String msg="XMLDB exception during list: " + e.getMessage();
	  if(failonerror)
		  throw new BuildException(msg,e);
	  else
		  log(msg,e,Project.MSG_ERR);
    }
  }

  public void setCollections(boolean collections)
  {
    this.collections = collections;
  }

  public void setResources(boolean resources)
  {
    this.resources = resources;
  }

  public void setSeparator(String separator)
  {
    this.separator = separator;
  }

  public void setOutputproperty(String outputproperty)
  {
    this.outputproperty = outputproperty;
  }
}
