/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
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

/**
 * an ant task to extract the content of a collection or resource
 *
 * @author peter.klotz@blue-elephant-systems.com
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
          XMLResource res = (XMLResource) base.getResource(resource);
          if (res == null)
          {
            if (failonerror)
              throw new BuildException("Resource " + resource + " not found.");
          } else
          {
            writeResource(res, destFile);
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
        if (failonerror)
          throw new BuildException("XMLDB exception caught while executing query: " + e.getMessage(), e);
      } catch (IOException e)
      {
        if (failonerror)
          throw new BuildException("XMLDB exception caught while writing destination file: " + e.getMessage(), e);
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
    if (dest != null || !dest.exists())
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
      throw new BuildException("Destionation target does not exist.");
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
    if (!"xml".equalsIgnoreCase(type))
    {
      throw new BuildException("non-xml resource types are not supported currently");
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
