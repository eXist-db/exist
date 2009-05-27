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

import java.io.File;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DirSet;
import org.exist.backup.Restore;

/**
 * @author wolf
 */
public class RestoreTask extends AbstractXMLDBTask
{

    private File file = null;
  private File dir = null;
  private DirSet dirSet = null;
  private String restorePassword = null;

  /* (non-Javadoc)
   * @see org.apache.tools.ant.Task#execute()
   */
  public void execute() throws BuildException
  {
    if (uri == null)
      throw new BuildException("You have to specify an XMLDB collection URI");
    if (dir == null && dirSet == null && file == null)
      throw new BuildException("Missing required argument: either dir, dirset or file required");

    if (dir != null && !dir.canRead()) {
  	  String msg="Cannot read restore file: " + dir.getAbsolutePath();
	  if(failonerror)
		  throw new BuildException(msg);
	  else
		  log(msg,Project.MSG_ERR);
    } else {
	    registerDatabase();
	    try
	    {
	      if (dir != null)
	      {
	        log("Restoring from " + dir.getAbsolutePath(), Project.MSG_INFO);
	        File file = new File(dir, "__contents__.xml");
	        if (!file.exists())
	        {
	      	  String msg="Did not found file "+file.getAbsolutePath();
	    	  if(failonerror)
	    		  throw new BuildException(msg);
	    	  else
	    		  log(msg,Project.MSG_ERR);
	        } else {
		        Restore restore = new Restore(user, password, restorePassword, file, uri);
		        restore.restore(false, null);
	        }
	      } else if (dirSet != null) {
	        DirectoryScanner scanner = dirSet.getDirectoryScanner(getProject());
	        scanner.scan();
	        String[] files = scanner.getIncludedFiles();
	        log("Found " + files.length + " files.\n");
	
	        File file = null;
	        for (int i = 0; i < files.length; i++)
	        {
	          dir = new File(scanner.getBasedir() + File.separator + files[i]);
	          file = new File(dir, "__contents__.xml");
	          if (!file.exists())
	          {
	        	  String msg="Did not found file "+file.getAbsolutePath();
	        	  if(failonerror)
	        		  throw new BuildException(msg);
	        	  else
	        		  log(msg,Project.MSG_ERR);
	          } else {
		          log("Restoring from " + file.getAbsolutePath() + " ...\n");
		          // TODO subdirectories as sub-collections?
		          Restore restore = new Restore(user, password, restorePassword, file, uri);
		          restore.restore(false, null);
	          }
	        }
	      } else if (file != null) {
	          log("Restoring from " + file.getAbsolutePath(), Project.MSG_INFO);
	          if (!file.exists()) {
	        	  String msg="File not found: " + file.getAbsolutePath();
	        	  if(failonerror)
	        		  throw new BuildException(msg);
	        	  else
	        		  log(msg,Project.MSG_ERR);
	          } else {
		          Restore restore = new Restore(user, password, restorePassword, file, uri);
		          restore.restore(false, null);
	          }
	      }
	    } catch (Exception e)
	    {
	      e.printStackTrace();
    	  String msg="Exception during restore: " + e.getMessage();
    	  if(failonerror)
    		  throw new BuildException(msg,e);
    	  else
    		  log(msg,e,Project.MSG_ERR);
	    }
    }
  }

  public DirSet createDirSet()
  {
    this.dirSet = new DirSet();
    return dirSet;
  }

  /**
   * @param dir
   */
  public void setDir(File dir)
  {
    this.dir = dir;
  }

    public void setFile(File file) {
        this.file = file;
    }
    
  public void setRestorePassword(String pass) {
	  this.restorePassword = pass;
  }
}
