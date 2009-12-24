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
      throw new BuildException("Must specify at least a user name");

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
    	  String msg="user " + name + " not found";
    	  if(failonerror)
    		  throw new BuildException(msg);
    	  else
    		  log(msg,Project.MSG_ERR);
      }
    }
    catch (XMLDBException e)
    {
  	  String msg="XMLDB exception caught: " + e.getMessage();
	  if(failonerror)
		  throw new BuildException(msg,e);
	  else
		  log(msg,e,Project.MSG_ERR);
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
