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
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.exist.storage.DBBroker;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.EXistResource;
import org.exist.xquery.Constants;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

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
  private File mimeTypesFile = null;
  private File srcFile = null;
  private String targetFile = null;
  private ArrayList<FileSet> fileSetList = null;
  private boolean createCollection = false;
  private boolean createSubcollections = false;
  private String type = null;
  private String defaultMimeType = null;
  private String forceMimeType = null;
  private MimeTable mtable = null;

  /* (non-Javadoc)
   * @see org.apache.tools.ant.Task#execute()
   */
  public void execute() throws BuildException
  {
    if (uri == null)
      throw new BuildException("you have to specify an XMLDB collection URI");
    if (fileSetList == null && srcFile == null)
      throw new BuildException("no file set specified");

    registerDatabase();
    int p = uri.indexOf(DBBroker.ROOT_COLLECTION);
    if (p == Constants.STRING_NOT_FOUND)
      throw new BuildException("invalid uri: '" + uri + "'");
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
        root = DatabaseManager.getCollection(baseURI + DBBroker.ROOT_COLLECTION, user, password);
        root = mkcol(root, baseURI, DBBroker.ROOT_COLLECTION, path);
      } else
        root = DatabaseManager.getCollection(uri, user, password);

      if(root==null){
    	  String msg="Collection " + uri + " could not be found.";
    	  if(failonerror)
    		  throw new BuildException(msg);
    	  else
    		  log(msg,Project.MSG_ERR);
      } else {
	      Resource res;
	      File file;
	      Collection col = root;
	      String relDir, prevDir = null, resourceType = "XMLResource";
	      if (srcFile != null)
	      {
	        log("Storing " + srcFile.getName());
		
		MimeType mime = getMimeTable().getContentTypeFor(srcFile.getName());
		String baseMimeType;
		if(forceMimeType!=null)
			baseMimeType = forceMimeType;
		else if(mime!=null)
			baseMimeType = mime.getName();
		else
			baseMimeType = defaultMimeType;
		
		if (type != null) {
			if (type.equals("xml"))
				mime = (baseMimeType!=null)?(new MimeType(baseMimeType,MimeType.XML)):MimeType.XML_TYPE;
			else if (type.equals("binary"))
				mime = (baseMimeType!=null)?(new MimeType(baseMimeType,MimeType.BINARY)):MimeType.BINARY_TYPE;
		}
		
	        // single file
	        if (mime == null) {
	      	  String msg="Cannot guess mime-type kind for " + srcFile.getName() + ". Treating it as a binary.";
    		  log(msg,Project.MSG_ERR);
		  mime = (baseMimeType!=null)?(new MimeType(baseMimeType,MimeType.BINARY)):MimeType.BINARY_TYPE;
		}
		resourceType = mime.isXMLType() ? "XMLResource" : "BinaryResource";
		if (targetFile == null)
		    targetFile = srcFile.getName();
		log("Creating resource " + targetFile + " in collection " + col.getName() + " of type " + resourceType + " with mime-type: " + mime.getName(), Project.MSG_DEBUG);
		res = col.createResource(targetFile, resourceType);

        if(srcFile.length() == 0){
             // note: solves bug id 2429889 when this task hits empty files
        }else{
		    res.setContent(srcFile);
            ((EXistResource) res).setMimeType(mime.getName());
		    col.storeResource(res);
        }

	      } else
	      {
		for(FileSet fileSet: fileSetList) {
			log("Storing fileset", Project.MSG_DEBUG);
			// using fileset
			DirectoryScanner scanner = fileSet.getDirectoryScanner(getProject());
			scanner.scan();
			String[] files = scanner.getIncludedFiles();
			log("Found " + files.length + " files.\n");

			File baseDir=scanner.getBasedir();
			for (int i = 0; i < files.length; i++) {
				file = new File(baseDir , files[i]);
				log("Storing " + files[i] + " ...\n");
				//TODO : use dedicated function in XmldbURI
				// check whether the relative file path contains file seps
				p = files[i].lastIndexOf(File.separatorChar);          
				if (p != Constants.STRING_NOT_FOUND) {
					relDir = files[i].substring(0, p);
					// It's necessary to do this translation on Windows, and possibly MacOS:
					relDir = relDir.replace(File.separatorChar, '/');
					if (createSubcollections && (prevDir == null || (!relDir.equals(prevDir)))) {
						//TODO : use dedicated function in XmldbURI
						col = mkcol(root, baseURI, DBBroker.ROOT_COLLECTION + path, relDir);
						prevDir = relDir;
					}
				} else {
					// No file separator found in resource name, reset col to the root collection
					col = root;
				}
				
				MimeType currentMime = getMimeTable().getContentTypeFor(file.getName());
				String currentBaseMimeType;
				if(forceMimeType!=null)
					currentBaseMimeType = forceMimeType;
				else if(currentMime!=null)
					currentBaseMimeType = currentMime.getName();
				else
					currentBaseMimeType = defaultMimeType;
				
				if (type != null) {
					if (type.equals("xml"))
						currentMime = (currentBaseMimeType!=null)?(new MimeType(currentBaseMimeType,MimeType.XML)):MimeType.XML_TYPE;
					else if (type.equals("binary"))
						currentMime = (currentBaseMimeType!=null)?(new MimeType(currentBaseMimeType,MimeType.BINARY)):MimeType.BINARY_TYPE;
				}
				
				if (currentMime == null) {
					String msg="Cannot find mime-type kind for " + file.getName()+". Treating it as a binary.";
					log(msg,Project.MSG_ERR);
					currentMime = (currentBaseMimeType!=null)?(new MimeType(currentBaseMimeType,MimeType.BINARY)):MimeType.BINARY_TYPE;
				}
				resourceType = currentMime.isXMLType() ? "XMLResource" : "BinaryResource";
				log("Creating resource " + file.getName() + " in collection " + col.getName() + " of type " + resourceType + " with mime-type: " + currentMime.getName(), Project.MSG_DEBUG);
				res = col.createResource(file.getName(), resourceType);
				res.setContent(file);
				((EXistResource) res).setMimeType(currentMime.getName());
				col.storeResource(res);
			}
		}
	      }
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

	/**
		This method allows more than one Fileset per store task!
	 */
	public void addFileset(FileSet set) {
		if(fileSetList==null) {
			fileSetList=new ArrayList<FileSet>();
		}
		
		fileSetList.add(set);
	}
	
  public void setSrcFile(File file)
  {
    this.srcFile = file;
  }

    public void setTargetFile(String name) {
        this.targetFile = name;
    }

  public void setCreatecollection(boolean create)
  {
    this.createCollection = create;
  }

  public void setCreatesubcollections(boolean create)
  {
    this.createSubcollections = create;
  }

  public void setMimeTypesFile(File file)
  {
	  this.mimeTypesFile=file;
  }
  
  public void setType(String type)
  {
    this.type = type;
  }
  
  public void setDefaultMimeType(String mimeType)
  {
    this.defaultMimeType = mimeType;
  }
  
  public void setForceMimeType(String mimeType)
  {
    this.forceMimeType = mimeType;
  }
  
  private final Collection mkcol(Collection root, String baseURI, String path, String relPath)
    throws XMLDBException
  {
    CollectionManagementService mgtService;
    Collection current = root, c;
    String token;
    ///TODO : use dedicated function in XmldbURI
    StringTokenizer tok = new StringTokenizer(relPath, "/");
    while (tok.hasMoreTokens())
    {
      token = tok.nextToken();
      if (path != null)
      {
        path = path + "/" + token;
      } else
      {
        path = "/" + token;
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
  
  private final MimeTable getMimeTable()
	throws BuildException
  {
	if (mtable==null) {
		if(mimeTypesFile!=null && mimeTypesFile.exists()) {
			log("Trying to use MIME Types file "+mimeTypesFile.getAbsolutePath(), Project.MSG_DEBUG);
			mtable=MimeTable.getInstance(mimeTypesFile);
		} else {
			log("Using default MIME Types resources", Project.MSG_DEBUG);
			mtable = MimeTable.getInstance();
		}
	}
	
	return mtable;
  }
}
