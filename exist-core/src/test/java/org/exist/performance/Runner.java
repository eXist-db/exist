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
package org.exist.performance;

import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.performance.actions.Action;
import org.exist.xmldb.EXistCollection;
import org.exist.xmldb.DatabaseInstanceManager;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Runner {

    private Map<String, Connection> connections = new HashMap<String, Connection>();
    private Connection firstConnection = null;
    
    private Map<String, Class<Action>> classes = new HashMap<String, Class<Action>>();

    private Map<String, Group> groups = new HashMap<String, Group>();

    private TestResultWriter resultWriter;

    private int nextId = 0;

    @SuppressWarnings("unchecked")
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
                Class<Action> clazz = (Class<Action>) Class.forName(elem.getAttribute("class"));
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
            if (firstConnection == null)
                firstConnection = con;
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
            for (Group group : groups.values()) {
                group.run();
            }
        } else {
            Group group = groups.get(groupToRun);
            if (group == null)
                throw new EXistException("Test group not found: " + groupToRun);
            group.run();
        }
    }

    public Connection getConnection(String connection) {
        return connections.get(connection);
    }

    public Connection getConnection() {
        return firstConnection;
    }
    
    public Class<Action> getClassForAction(String action) {
        return classes.get(action);
    }

    public TestResultWriter getResults() {
        return resultWriter;
    }

    public int getNextId() {
        return ++nextId;
    }

    public void shutdown() {
        try {
            if (resultWriter != null) {
                resultWriter.close();
            }
        } catch(final IOException e) {
            e.printStackTrace();
        }

        try {
            shutdownDb();
        } catch (final XMLDBException e) {
            e.printStackTrace();
        }
    }

    private void initDb() throws EXistException {
        try {
            Class<?> clazz = Class.forName("org.exist.xmldb.DatabaseImpl");
            Database database = (Database)clazz.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
        } catch (Exception e) {
            throw new EXistException(e.getMessage(), e);
        }
    }

    private void shutdownDb() throws XMLDBException {
        for (Connection connection : connections.values()) {
            EXistCollection collection = (EXistCollection) connection.getCollection("/db");
            if (!collection.isRemoteCollection()) {
                DatabaseInstanceManager mgr = (DatabaseInstanceManager)
                        collection.getService("DatabaseInstanceManager", "1.0");
                mgr.shutdown();
            }
        }
    }
}
