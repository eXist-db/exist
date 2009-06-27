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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.PropertyHelper;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import javax.xml.transform.OutputKeys;
import java.io.*;
import java.util.Properties;

/**
 * an ant task to execute an query using XPath. <p/> The query is either passed
 * as nested text in the element, or via an attribute "query". <p/>
 *
 * @author wolf <p/> modified by:
 * @author peter.klotz@blue-elephant-systems.com
 */
public class XMLDBXPathTask extends AbstractXMLDBTask {
  private String resource = null;

  private String namespace = null;

  private String query = null;

  private String text = null;

  // count mode
  private boolean count = false;

  private File destDir = null;

  private String outputproperty;
  // output encoding
  private String encoding = "UTF-8";

  /*
  * (non-Javadoc)
  *
  * @see org.apache.tools.ant.Task#execute()
  */
  public void execute() throws BuildException {
    if (uri == null)
      throw new BuildException(
        "you have to specify an XMLDB collection URI");
    if (text != null) {
      PropertyHelper helper = PropertyHelper
        .getPropertyHelper(getProject());
      query = helper.replaceProperties(null, text, null);
    }
    if (query == null)
      throw new BuildException("you have to specify a query");

    log("XPath is: " + query, org.apache.tools.ant.Project.MSG_DEBUG);

    registerDatabase();
    try {
      log("Get base collection: " + uri, Project.MSG_DEBUG);
      Collection base = DatabaseManager
        .getCollection(uri, user, password);

      if (base == null){
    	  String msg="Collection " + uri + " could not be found.";
    	  if(failonerror)
    		  throw new BuildException(msg);
    	  else
    		  log(msg,Project.MSG_ERR);
      } else {
	      XPathQueryService service = (XPathQueryService) base.getService(
	        "XPathQueryService", "1.0");
	      // set pretty-printing on
	      service.setProperty(OutputKeys.INDENT, "yes");
	      service.setProperty(OutputKeys.ENCODING, "UTF-8");
	
	      if (namespace != null) {
	        log("Using namespace: " + namespace, Project.MSG_DEBUG);
	        service.setNamespace("ns", namespace);
	      }
	
	      ResourceSet results = null;
	      if (resource != null) {
	        log("Query resource: " + resource, Project.MSG_DEBUG);
	        results = service.queryResource(resource, query);
	      } else {
	        log("Query collection", Project.MSG_DEBUG);
	        results = service.query(query);
	      }
	      log("Found " + results.getSize() + " results", Project.MSG_INFO);
	
	      if (destDir != null && results != null) {
	        log("write results to directory " + destDir.getAbsolutePath(),
	          Project.MSG_INFO);
	        ResourceIterator iter = results.getIterator();
	        XMLResource res = null;
	
	        log(
	          "Writing results to directory "
	            + destDir.getAbsolutePath(), Project.MSG_DEBUG);
	        while (iter.hasMoreResources()) {
	          res = (XMLResource) iter.nextResource();
	          log("Writing resource " + res.getId(), Project.MSG_DEBUG);
	          writeResource(res, destDir);
	        }
	      } else if (outputproperty != null) {
	        if (count) {
	          getProject().setNewProperty(outputproperty,
	            String.valueOf(results.getSize()));
	        } else {
	          ResourceIterator iter = results.getIterator();
	          XMLResource res = null;
	          String result = "";
	          while (iter.hasMoreResources()) {
	            res = (XMLResource) iter.nextResource();
	            result += res.getContent().toString() + "\n";
	          }
	          getProject().setNewProperty(outputproperty, result);
	        }
	      }
      }
    } catch (XMLDBException e) {
  	  String msg="XMLDB exception caught while executing query: " + e.getMessage();
	  if(failonerror)
		  throw new BuildException(msg,e);
	  else
		  log(msg,e,Project.MSG_ERR);
    } catch (IOException e) {
  	  String msg="XMLDB exception caught while writing destination file: " + e.getMessage();
	  if(failonerror)
		  throw new BuildException(msg,e);
	  else
		  log(msg,e,Project.MSG_ERR);
    }
  }

  private void writeResource(XMLResource resource, File dest)
    throws IOException, XMLDBException {
    if (dest != null) {
      Properties outputProperties = new Properties();
      outputProperties.setProperty(OutputKeys.INDENT, "yes");

      SAXSerializer serializer = (SAXSerializer) SerializerPool
        .getInstance().borrowObject(SAXSerializer.class);

      Writer writer = null;
      if (dest.isDirectory()) {
        if (!dest.exists()) {
          dest.mkdirs();
        }
        String fname = resource.getId();
        if (!fname.endsWith(".xml")) {
          fname += ".xml";
        }
        File file = new File(dest, fname);
        writer = new OutputStreamWriter(new FileOutputStream(file), encoding);
      }
      else
      {
        writer = new OutputStreamWriter(new FileOutputStream(dest), encoding);
      }
      serializer.setOutput(writer, outputProperties);
      resource.getContentAsSAX(serializer);
      writer.close();

      SerializerPool.getInstance().returnObject(serializer);
    } else {
    	  String msg="Destination target does not exist.";
    	  if(failonerror)
    		  throw new BuildException(msg);
    	  else
    		  log(msg,Project.MSG_ERR);
    }
  }

  /**
   * @param query
   */
  public void setQuery(String query) {
    this.query = query;
  }

  public void addText(String text) {
    this.text = text;
  }

  public void setResource(String resource) {
    this.resource = resource;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public void setDestDir(File destDir) {
    this.destDir = destDir;
  }

  public void setOutputproperty(String outputproperty) {
    this.outputproperty = outputproperty;
  }

  public void setCount(boolean count) {
    this.count = count;
  }
}
