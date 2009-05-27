/**
 * Created by IntelliJ IDEA.
 * User: pak
 * Date: Apr 17, 2005
 * Time: 7:48:40 PM
 * To change this template use File | Settings | File Templates.
 */
package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * abstract base class for all user-related tasks
 *
 * @author peter.klotz@blue-elephant-systems.com
 */
public abstract class UserTask extends AbstractXMLDBTask
{
  protected UserManagementService service = null;
  protected Collection base = null;

  /* (non-Javadoc)
   * @see org.apache.tools.ant.Task#execute()
   */
  public void execute() throws BuildException
  {
    if (uri == null)
      throw new BuildException("you have to specify an XMLDB collection URI");

    registerDatabase();
    try
    {
      log("Get base collection: " + uri, Project.MSG_DEBUG);
      base = DatabaseManager.getCollection(uri, user, password);
        
      if(base==null){
    	  String msg="Collection " + uri + " could not be found.";
    	  if(failonerror)
    		  throw new BuildException(msg);
    	  else
    		  log(msg,Project.MSG_ERR);
      } else {
    	  service = (UserManagementService) base.getService("UserManagementService", "1.0");
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
}
