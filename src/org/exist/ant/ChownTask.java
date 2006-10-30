/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.exist.security.User;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * an ant task to change permissions on a resource
 *
 * @author peter.klotz@blue-elephant-systems.com
 */
public class ChownTask extends UserTask
{
  private String name = null;
  private String group = null;
  private String resource = null;

  /* (non-Javadoc)
   * @see org.apache.tools.ant.Task#execute()
   */
  public void execute() throws BuildException
  {
    super.execute();
    if (name == null || group == null)
      throw new BuildException("Must specify user and group");

    try
    {
      User usr = service.getUser(name);
      if (resource != null)
      {
        Resource res = base.getResource(resource);
        service.chown(res, usr, group);
      } else
      {
        service.chown(usr, group);
      }
    } catch (XMLDBException e)
    {
      throw new BuildException("XMLDB exception caught: " + e.getMessage(), e);
    }
  }

  public void setName(String user)
  {
    this.name = user;
  }

  public void setResource(String resource)
  {
    this.resource = resource;
  }

  public void setGroup(String group)
  {
    this.group = group;
  }
}
