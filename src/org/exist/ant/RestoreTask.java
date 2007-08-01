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
    if (dir == null && dirSet == null)
      throw new BuildException("Missing required argument: either dir or dirset required");

    if (dir != null && !dir.canRead())
      throw new BuildException("Cannot read restore file: " + dir.getAbsolutePath());

    registerDatabase();
    try
    {
      if (dir != null)
      {
        log("Restoring from " + dir.getAbsolutePath(), Project.MSG_INFO);
        File file = new File(dir, "__contents__.xml");
        if (!file.exists())
        {
          throw new BuildException("Did not found file "+file.getAbsolutePath());
        }
        Restore restore = new Restore(user, password, restorePassword, file, uri);
        restore.restore(false, null);
      } else if (dirSet != null)
      {
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
            throw new BuildException("Did not found file "+file.getAbsolutePath());
          }
          log("Restoring from " + file.getAbsolutePath() + " ...\n");
          // TODO subdirectories as sub-collections?
          Restore restore = new Restore(user, password, restorePassword, file, uri);
          restore.restore(false, null);
        }
      }
    } catch (Exception e)
    {
      e.printStackTrace();
      throw new BuildException("Exception during restore: " + e.getMessage(), e);
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
  
  public void setRestorePassword(String pass) {
	  this.restorePassword = pass;
  }
}
