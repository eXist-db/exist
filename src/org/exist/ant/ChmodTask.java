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
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * an ant task to change permissions on a resource
 *
 * @author peter.klotz@blue-elephant-systems.com
 */
public class ChmodTask extends UserTask
{
  private String resource = null;
  private String mode = null;

  /* (non-Javadoc)
   * @see org.apache.tools.ant.Task#execute()
   */
  public void execute() throws BuildException
  {
    super.execute();

    try
    {
      if (resource != null)
      {
        Resource res = base.getResource(resource);
        service.chmod(res, mode);
      } else
      {
        service.chmod(mode);
      }
    } catch (XMLDBException e)
    {
  	  String msg="XMLDB exception caught: " + e.getMessage();
	  if(failonerror)
		  throw new BuildException(msg,e);
	  else
		  log(msg,e,Project.MSG_ERR);
    }
  }

  public void setResource(String resource)
  {
    this.resource = resource;
  }

  public void setMode(String mode)
  {
    this.mode = mode;
  }
}
