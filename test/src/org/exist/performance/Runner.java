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
 *  \$Id\$
 */
package org.exist.performance;

import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.performance.actions.Action;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.DatabaseInstanceManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class Runner {

    private Map connections = new HashMap();

    private Map classes = new HashMap();

    private Map groups = new HashMap();

    private TestResultWriter resultWriter;

    private int nextId = 0;

    public Runner(Element root, TestResultWriter reporter) throws EXistException, XMLDBException {
        this.resultWriter = reporter;
        initDb();

        NodeList nl = root.getElementsByTagNameNS(Namespaces.EXIST_NS, "configuration");
        if (nl.getLength() == 0)
            throw new EXistException("no configuration element found");
        if (nl.getLength() > 1)
            throw new EXistException("found more than one configuration element");
        Element config = (Element) nl.item(0);

        nl = config.getElementsByTagNameNS(Namespaces.EXIST_NS, "action");
        for (int i = 0; i < nl.getLength(); i++) {
            Element elem = (Element) nl.item(i);
            try {
                Class clazz = Class.forName(elem.getAttribute("class"));
                classes.put(elem.getAttribute("name"), clazz);
            } catch (ClassNotFoundException e) {
                throw new EXistException("Class not found: " + elem.getAttribute("class"));
            }
        }

        nl = config.getElementsByTagNameNS(Namespaces.EXIST_NS, "connection");
        for (int i = 0; i < nl.getLength(); i++) {
            Element elem = (Element) nl.item(i);
            Connection con = new Connection(elem);
            connections.put(con.getId(), con);
        }

        nl = root.getElementsByTagNameNS(Namespaces.EXIST_NS, "group");
        for (int i = 0; i < nl.getLength(); i++) {
            Element elem = (Element) nl.item(i);
            Group group = new Group(this, elem);
            groups.put(group.getName(), group);
        }
    }

    public void run(String groupToRun) throws XMLDBException, EXistException {
        if (groupToRun == null) {
            for (Iterator iterator = groups.values().iterator(); iterator.hasNext();) {
                Group group = (Group) iterator.next();
                group.run();
            }
        } else {
            Group group = (Group) groups.get(groupToRun);
            if (group == null)
                throw new EXistException("Test group not found: " + groupToRun);
            group.run();
        }
    }

    public Connection getConnection(String connection) {
        return (Connection) connections.get(connection);
    }

    public Class getClassForAction(String action) {
        return (Class) classes.get(action);
    }

    public TestResultWriter getResults() {
        return resultWriter;
    }

    public int getNextId() {
        return ++nextId;
    }

    public void shutdown() {
        resultWriter.close();
        try {
            shutdownDb();
        } catch (XMLDBException e) {
            e.printStackTrace();
        }
    }

    private void initDb() throws EXistException {
        try {
            Class clazz = Class.forName("org.exist.xmldb.DatabaseImpl");
            Database database = (Database)clazz.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
        } catch (Exception e) {
            throw new EXistException(e.getMessage(), e);
        }
    }

    private void shutdownDb() throws XMLDBException {
        for (Iterator iterator = connections.values().iterator(); iterator.hasNext();) {
            Connection connection = (Connection) iterator.next();
            CollectionImpl collection = (CollectionImpl) connection.getCollection("/db");
            if (!collection.isRemoteCollection()) {
                DatabaseInstanceManager mgr = (DatabaseInstanceManager)
                        collection.getService("DatabaseInstanceManager", "1.0");
                mgr.shutdown();
            }
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: org.exist.performance.Runner test-definition.xml [group]");
            return;
        }
        String xmlFile = args[0];
        String group = null;
        if (args.length == 2)
            group = args[1];
        Runner runner = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(xmlFile));

            TestResultWriter writer = new TestResultWriter("out.xml");
            runner = new Runner(doc.getDocumentElement(), writer);
            runner.run(group);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("ERROR: " + e.getMessage());
        } finally {
            if (runner != null)
                runner.shutdown();
        }
    }
}
