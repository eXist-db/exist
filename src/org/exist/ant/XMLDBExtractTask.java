/*
 *  eXist Open Source Native XML Database
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
 */

package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import javax.xml.transform.OutputKeys;
import java.io.*;
import java.util.Properties;
import org.exist.dom.DocumentImpl;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.ExtendedResource;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;


/**
 * an ant task to extract the content of a collection or resource
 *
 * @author peter.klotz@blue-elephant-systems.com
 * @modified jim.fuller at webcomposite.com to handle binary file extraction
 */
public class XMLDBExtractTask extends AbstractXMLDBTask
{
  private String resource = null;
  private File destFile = null;
  private File destDir = null;
  private String type = "xml";
  private boolean createdirectories = false;
  private boolean subcollections = false;
  
  // output encoding
  private String encoding = "UTF-8";

  /* (non-Javadoc)
  * @see org.apache.tools.ant.Task#execute()
  */
  public void execute() throws BuildException
  {
    if (uri == null)
    {
      if (failonerror)
        throw new BuildException("you have to specify an XMLDB collection URI");
    } else
    {
      registerDatabase();
      try
      {
        Collection base = DatabaseManager.getCollection(uri, user, password);

        if(base==null){
          throw new BuildException("Collection " + uri + " could not be found.");
        }

        if (resource != null)
        {
          log("Extracting resource: " + resource + " to " + destFile.getAbsolutePath(), Project.MSG_INFO);
          Resource res = base.getResource(resource);
          if (res == null)
          {
        	  String msg="Resource " + resource + " not found.";
        	  if(failonerror)
        		  throw new BuildException(msg);
        	  else
        		  log(msg,Project.MSG_ERR);
          } else
          {

           if (res instanceof ExtendedResource) {
                writeBinaryResource((Resource) res, destFile);
            } else
            {
                writeResource((XMLResource)res, destFile);
            }

          }
        } else
        {
          extractResources(base, null);
          if (subcollections)
          {
            extractSubCollections(base, null);
          }
        }
      } catch (XMLDBException e)
      {
    	  String msg="XMLDB exception caught while executing query: " + e.getMessage();
    	  if(failonerror)
    		  throw new BuildException(msg,e);
    	  else
    		  log(msg,e,Project.MSG_ERR);
      } catch (IOException e)
      {
    	  String msg="XMLDB exception caught while writing destination file: " + e.getMessage();
    	  if(failonerror)
    		  throw new BuildException(msg,e);
    	  else
    		  log(msg,e,Project.MSG_ERR);
      }
    }
  }

  private void extractResources(Collection base, String path)
    throws XMLDBException, IOException
  {
    XMLResource res = null;
    String[] resources = base.listResources();
    if (resources != null)
    {
      File dir = destDir;
      log("Extracting to directory " + destDir.getAbsolutePath(), Project.MSG_DEBUG);
      if (path != null)
      {
        dir = new File(destDir, path);
      }
      for (int i = 0; i < resources.length; i++)
      {
        res = (XMLResource) base.getResource(resources[i]);
        log("Extracting resource: " + res.getId(), Project.MSG_DEBUG);
        if (!dir.exists() && createdirectories)
        {
          dir.mkdirs();
        }
        writeResource(res, dir);
      }
    }
  }

  private void extractSubCollections(Collection base, String path) throws XMLDBException, IOException
  {
    String[] childCols = base.listChildCollections();
    if (childCols != null)
    {
      Collection col = null;
      for (int i = 0; i < childCols.length; i++)
      {
        col = base.getChildCollection(childCols[i]);
        if (col != null)
        {
          log("Extracting collection: " + col.getName(), Project.MSG_DEBUG);
          File dir = destDir;
          String subdir;
          if (path != null)
          {
            dir = new File(destDir, path + File.separator + childCols[i]);
            subdir = path + File.separator + childCols[i];
          } else {
            subdir = childCols[i];
          }
          if (!dir.exists() && createdirectories)
          {
            dir.mkdirs();
          }
          extractResources(col, subdir);
          if (subcollections)
          {
            extractSubCollections(col, subdir);
          }
        }
      }
    }
  }

  private void writeResource(XMLResource resource, File dest) throws IOException, XMLDBException
  {
    if (dest != null && !dest.exists())
    {
      Properties outputProperties = new Properties();
      outputProperties.setProperty(OutputKeys.INDENT, "yes");

      SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

      Writer writer = null;
      if (dest.isDirectory())
      {
        String fname = resource.getId();
        if (!fname.endsWith("." + type))
        {
          fname += "." + type;
        }
        File file = new File(dest, fname);
        writer = new OutputStreamWriter(new FileOutputStream(file), encoding);
      }
      else
      {
        writer = new OutputStreamWriter(new FileOutputStream(dest), encoding);
      }
      log("Writing resource " + resource.getId() + " to destination " + dest.getAbsolutePath(), Project.MSG_DEBUG);
      serializer.setOutput(writer, outputProperties);
      resource.getContentAsSAX(serializer);
      SerializerPool.getInstance().returnObject(serializer);
      writer.close();
    } else
    {
  	  String msg="Destination target "+((dest!=null)?(dest.getAbsolutePath()+" "):"")+"does not exist.";
	  if(failonerror)
		  throw new BuildException(msg);
	  else
		  log(msg,Project.MSG_ERR);
    }
  }

  
 private void writeBinaryResource(Resource resource, File dest) throws XMLDBException, FileNotFoundException, UnsupportedEncodingException, IOException  {

    if (dest != null && !dest.exists())
    {
     FileOutputStream os;
     os = new FileOutputStream(destFile);
     ((ExtendedResource) resource).getContentIntoAStream(os);
    } else
    {
  	  String msg="Destination target "+((dest!=null)?(dest.getAbsolutePath()+" "):"")+"does not exist.";
	  if(failonerror)
		  throw new BuildException(msg);
	  else
		  log(msg,Project.MSG_ERR);
    }
}


  /**
   * @param resource
   */
  public void setResource(String resource)
  {
    this.resource = resource;
  }

  public void setDestFile(File destFile)
  {
    this.destFile = destFile;
  }

  public void setDestDir(File destDir)
  {
    this.destDir = destDir;
  }

  public void setType(String type)
  {
    this.type = type;
    if (!"xml".equalsIgnoreCase(type) & !"binary".equalsIgnoreCase(type))
    {
      throw new BuildException("non-xml or non-binary resource types are not supported currently");
    }
  }

  public void setCreatedirectories(boolean createdirectories)
  {
    this.createdirectories = createdirectories;
  }

  public void setSubcollections(boolean subcollections)
  {
    this.subcollections = subcollections;
  }
}
