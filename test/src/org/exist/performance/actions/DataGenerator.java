/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
 *  http://exist-db.org
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
package org.exist.performance.actions;

import org.exist.performance.AbstractAction;
import org.exist.performance.Runner;
import org.exist.performance.Connection;
import org.exist.EXistException;
import org.exist.xquery.value.IntegerValue;
import org.exist.xmldb.XQueryService;
import org.exist.util.serializer.DOMSerializer;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.Resource;

import javax.xml.transform.TransformerException;
import java.io.StringWriter;
import java.io.File;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.util.Properties;

public class DataGenerator extends AbstractAction {

    private final static String IMPORT =
            "import module namespace pt='http://exist-db.org/xquery/test/performance' " +
            "at 'java:org.exist.performance.xquery.PerfTestModule';\n" +
            "declare variable $filename external;\n" +
            "declare variable $count external;\n";

    private String xqueryContent;

    private String prefix = "";

    private File directory;

    private int count = 1;

    public void configure(Runner runner, Action parent, Element config) throws EXistException {
        super.configure(runner, parent, config);

        if (config.hasAttribute("count")) {
            try {
                count = Integer.parseInt(config.getAttribute("count"));
            } catch (NumberFormatException e) {
                throw new EXistException("invalid value for attribute 'count': " + config.getAttribute("count"), e);
            }
        }
        
        if (!config.hasAttribute("todir"))
            throw new EXistException("generate requires an attribute 'todir'");
        directory = new File(config.getAttribute("todir"));
        if (!(directory.exists() && directory.isDirectory()))
            throw new EXistException(directory.getAbsolutePath() + " does not exist or is not a directory");

        if (config.hasAttribute("prefix"))
            prefix = config.getAttribute("prefix");
        
        NodeList children = config.getChildNodes();
        Element root = null;
        for (int i = 0;  i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                root = (Element) node;
                break;
            }
        }
        if (root == null)
            throw new EXistException("no content element found for generate");
        StringWriter writer = new StringWriter();
        DOMSerializer serializer = new DOMSerializer(writer, new Properties());
        try {
            serializer.serialize(root);
        } catch (TransformerException e) {
            throw new EXistException("exception while serializing generate content: " + e.getMessage(), e);
        }
        xqueryContent = writer.toString();
    }


    public void execute(Connection connection) throws XMLDBException, EXistException {
        Collection collection = connection.getCollection("/db");
        XQueryService service = (XQueryService) collection.getService("XQueryService", "1.0");
        service.declareVariable("filename", "");
        service.declareVariable("count", "0");
        String query = IMPORT + xqueryContent;
        System.out.println("query: " + query);
        CompiledExpression compiled = service.compile(query);
        try {
            for (int i = 0; i < count; i++) {
                File nextFile = new File(directory, prefix + i + ".xml");
                
                service.declareVariable("filename", nextFile.getName());
                service.declareVariable("count", new IntegerValue(i));
                ResourceSet results = service.execute(compiled);

                Writer out = new OutputStreamWriter(new FileOutputStream(nextFile), "UTF-8");
                for (ResourceIterator iter = results.getIterator(); iter.hasMoreResources(); ) {
                    Resource r = iter.nextResource();
                    out.write(r.getContent().toString());
                }
                out.close();
            }
        } catch (IOException e) {
            throw new EXistException("exception while storing generated data: " + e.getMessage(), e);
        }
    }
}
