/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import java.io.File;
import java.util.StringTokenizer;

/**
 * An Ant task to store a set of files into eXist.
 * <p/>
 * The task expects a nested fileset element. The files
 * selected by the fileset will be stored into the database.
 * <p/>
 * New collections can be created as needed. It is also possible
 * to specify that files relative to the base
 * directory should be stored into subcollections of the root
 * collection, where the relative path of the directory corresponds
 * to the relative path of the subcollections.
 *
 * @author wolf
 *         <p/>
 *         slightly modified by:
 * @author peter.klotz@blue-elephant-systems.com
 */
public class XMLDBStoreTask extends AbstractXMLDBTask
{
  private File srcFile = null;
  private FileSet fileSet = null;
  private boolean createCollection = false;
  private boolean createSubcollections = false;
  private String type = "xml";

  /* (non-Javadoc)
   * @see org.apache.tools.ant.Task#execute()
   */
  public void execute() throws BuildException
  {
    if (uri == null)
      throw new BuildException("you have to specify an XMLDB collection URI");
    if (fileSet == null && srcFile == null)
      throw new BuildException("no file set specified");

    registerDatabase();
    int p = uri.indexOf("/db");
    if (p < 0)
      throw new BuildException("invalid uri: " + uri);
    try
    {
      String baseURI = uri.substring(0, p);
      String path;
      if (p == uri.length() - 3)
        path = "";
      else
        path = uri.substring(p + 3);

      Collection root = null;
      if (createCollection)
      {
        root = DatabaseManager.getCollection(baseURI + "/db");
        root = mkcol(root, baseURI, "/db", path);
      } else
        root = DatabaseManager.getCollection(uri, user, password);
      if (root == null)
        throw new BuildException("collection " + uri + " not found");

      Resource res;
      File file;
      Collection col = root;
      String relDir, prevDir = null, resourceType = "XMLResource";
      if (srcFile != null)
      {
        log("Storing single file " + srcFile.getAbsolutePath(), Project.MSG_DEBUG);
        // single file
        resourceType = type.equals("binary") ? "BinaryResource" : "XMLResource";
        log("Creating resource of type " + resourceType, Project.MSG_DEBUG);
        res = col.createResource(srcFile.getName(), resourceType);
        res.setContent(srcFile);
        col.storeResource(res);
      } else
      {
        log("Storing fileset", Project.MSG_DEBUG);
        // using fileset
        DirectoryScanner scanner = fileSet.getDirectoryScanner(getProject());
        scanner.scan();
        String[] files = scanner.getIncludedFiles();
        log("Found " + files.length + " files.\n");

        for (int i = 0; i < files.length; i++)
        {
          file = new File(scanner.getBasedir() + File.separator + files[i]);
          log("Storing " + files[i] + " ...\n");
          // check whether the relative file path contains file seps
          p = files[i].lastIndexOf(File.separatorChar);
          if (p > -1)
          {
            relDir = files[i].substring(0, p);
            // It's necessary to do this translation on Windows, and possibly MacOS:
            relDir = relDir.replace(File.separatorChar, '/');
            if (createSubcollections && (prevDir == null || (!relDir.equals(prevDir))))
            {
              col = mkcol(root, baseURI, "/db" + path, relDir);
              prevDir = relDir;
            }
          }
          resourceType = type.equals("binary") ? "BinaryResource" : "XMLResource";
          log("Creating resource of type " + resourceType, Project.MSG_DEBUG);
          res = col.createResource(file.getName(), resourceType);
          res.setContent(file);
          col.storeResource(res);
        }
      }
    } catch (XMLDBException e)
    {
      throw new BuildException("XMLDB exception caught: " + e.getMessage(), e);
    }
  }

  public void setSrcFile(File file)
  {
    this.srcFile = file;
  }

  public FileSet createFileSet()
  {
    this.fileSet = new FileSet();
    return fileSet;
  }

  public void setCreatecollection(boolean create)
  {
    this.createCollection = create;
  }

  public void setCreatesubcollections(boolean create)
  {
    this.createSubcollections = create;
  }

  public void setType(String type)
  {
    this.type = type;
  }

  private final Collection mkcol(Collection root, String baseURI, String path, String relPath)
    throws XMLDBException
  {
    CollectionManagementService mgtService;
    Collection current = root, c;
    String token;
    StringTokenizer tok = new StringTokenizer(relPath, "/");
    while (tok.hasMoreTokens())
    {
      token = tok.nextToken();
      if (path != null)
      {
        path = path + '/' + token;
      } else
      {
        path = '/' + token;
      }
      log("Get collection " + baseURI + path, Project.MSG_DEBUG);
      c = DatabaseManager.getCollection(baseURI + path, user, password);
      if (c == null)
      {
        log("Create collection management service for collection " + current.getName(), Project.MSG_DEBUG);
        mgtService = (CollectionManagementService) current.getService("CollectionManagementService", "1.0");
        log("Create child collection " + token, Project.MSG_DEBUG);
        current = mgtService.createCollection(token);
        log("Created collection " + current.getName() + '.', Project.MSG_DEBUG);
      } else
        current = c;
    }
    return current;
  }
}
