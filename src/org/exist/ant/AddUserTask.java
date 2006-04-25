/**
 * Created by IntelliJ IDEA.
 * User: pak
 * Date: Apr 17, 2005
 * Time: 7:41:35 PM
 * To change this template use File | Settings | File Templates.
 */
package org.exist.ant;

import java.net.URISyntaxException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.exist.security.User;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.base.XMLDBException;

/**
 * an ant task to add a user
 *
 * @author peter.klotz@blue-elephant-systems.com
 */
public class AddUserTask extends UserTask
{
  private String name;
  private String primaryGroup;
  private String home;
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
      User usr = new User(name);
      if (secret != null)
        usr.setPassword(secret);
      if (home != null)
        usr.setHome(XmldbURI.xmldbUriFor(home));
      if (primaryGroup != null)
        usr.addGroup(primaryGroup);
      log("Adding user " + name, Project.MSG_INFO);
      service.addUser(usr);
    } catch (XMLDBException e) {
      throw new BuildException("XMLDB exception caught: " + e.getMessage(), e);
    } catch (URISyntaxException e) {
    throw new BuildException("XMLDB exception caught: " + e.getMessage(), e);
  }
  }

  public void setName(String name)
  {
    this.name = name;
  }

  public void setPrimaryGroup(String primaryGroup)
  {
    this.primaryGroup = primaryGroup;
  }

  public void setHome(String home)
  {
    this.home = home;
  }

  public void setSecret(String secret)
  {
    this.secret = secret;
  }
}
