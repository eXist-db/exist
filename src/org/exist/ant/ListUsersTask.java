/**
 * Created by IntelliJ IDEA.
 * User: pak
 * Date: Apr 17, 2005
 * Time: 7:41:35 PM
 * To change this template use File | Settings | File Templates.
 */
package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.exist.security.User;
import org.xmldb.api.base.XMLDBException;

/**
 * an ant task to list users
 *
 * @author peter.klotz@blue-elephant-systems.com
 */
public class ListUsersTask extends UserTask
{
  private String outputproperty = null;
  private String separator = ",";

  /* (non-Javadoc)
   * @see org.apache.tools.ant.Task#execute()
   */
  public void execute() throws BuildException
  {
    super.execute();
    try
    {
      log("Listing all users", Project.MSG_DEBUG);
      User[] users = service.getUsers();
      if (users != null)
      {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < users.length; i++)
        {
          buf.append(users[i].getName());
          if (i < users.length - 1)
          {
            buf.append(separator);
          }
        }
        if (buf.length() > 0)
        {
          log("Setting output property " + outputproperty + " to " + buf.toString(), Project.MSG_DEBUG);
          getProject().setNewProperty(outputproperty, buf.toString());
        }
      }
    } catch (XMLDBException e)
    {
      throw new BuildException("XMLDB exception caught: " + e.getMessage(), e);
    }
  }

  public void setOutputproperty(String outputproperty)
  {
    this.outputproperty = outputproperty;
  }

  public void setSeparator(String separator)
  {
    this.separator = separator;
  }
}
