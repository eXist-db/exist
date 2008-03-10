package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.exist.security.User;
import org.xmldb.api.base.XMLDBException;

/**
 * an ant task to set the password of a user
 *
 * @author peter.klotz@blue-elephant-systems.com
 */
public class UserPasswordTask extends UserTask
{
  private String name;
  private String secret;

  /* (non-Javadoc)
   * @see org.apache.tools.ant.Task#execute()
   */
  public void execute() throws BuildException
  {
    super.execute();
    if (name == null)
      throw new BuildException("Must specify at leat a user name");

    try
    {
      log("Looking up user " + name, Project.MSG_INFO);
      User usr = service.getUser(name);
      if (usr != null)
      {
        log("Setting password for user " + name, Project.MSG_INFO);
        if (secret != null)
          usr.setPassword(secret);
      }
      else
      {
        throw new BuildException("user " + name + " not found");
      }
    }
    catch (XMLDBException e)
    {
      throw new BuildException("XMLDB exception caught: " + e.getMessage(), e);
    }
  }

  public void setName(String name)
  {
    this.name = name;
  }

  public void setSecret(String secret)
  {
    this.secret = secret;
  }
}
