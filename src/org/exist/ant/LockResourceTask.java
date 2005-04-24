/**
 * Created by IntelliJ IDEA.
 * User: pak
 * Date: Apr 17, 2005
 * Time: 7:41:35 PM
 * To change this template use File | Settings | File Templates.
 */
package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.exist.security.User;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * an ant task to lock a resource for a user
 *
 * @author peter.klotz@blue-elephant-systems.com
 */
public class LockResourceTask extends UserTask
{
  private String name = null;
  private String resource = null;

  /* (non-Javadoc)
   * @see org.apache.tools.ant.Task#execute()
   */
  public void execute() throws BuildException
  {
    super.execute();
    if (resource == null || name == null)
      throw new BuildException("Must specify user and resource name");

    try
    {
      Resource res = base.getResource(resource);
      if (res == null)
        throw new BuildException("Resource " + resource + " not found");
      User usr = service.getUser(name);
      if (usr == null)
        throw new BuildException("User " + name + " not found");
      service.lockResource(res, usr);
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
}
