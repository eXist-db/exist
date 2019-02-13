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
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xmldb.api.base.XMLDBException;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Group {

    private String name;

    private List<ActionThread> threads = new ArrayList<ActionThread>();

    private ActionThread setupAction = null;

    private ActionThread tearDownAction = null;

    private Runner runner;

    public Group(Runner runner, Element config) throws EXistException {
        this.runner = runner;

        if (!config.hasAttribute("name"))
            throw new EXistException("group element requires an attribute 'name'");
        name = config.getAttribute("name");

        NodeList nl = config.getElementsByTagNameNS(Namespaces.EXIST_NS, "setup");
        if (nl.getLength() > 0) {
            Element elem = (Element) nl.item(0);
            setupAction = new ActionThread();
            setupAction.setName("setup");
            setupAction.configure(runner, null, elem);
        }

        nl = config.getElementsByTagNameNS(Namespaces.EXIST_NS, "tear-down");
        if (nl.getLength() > 0) {
            Element elem = (Element) nl.item(0);
            tearDownAction = new ActionThread();
            tearDownAction.setName("tear-down");
            tearDownAction.configure(runner, null, elem);
        }

        nl = config.getElementsByTagNameNS(Namespaces.EXIST_NS, "thread");
        for (int i = 0; i < nl.getLength(); i++) {
            Element elem = (Element) nl.item(i);
            ActionThread action = new ActionThread();
            action.configure(runner, null, elem);
            threads.add(action);
        }
    }

    public void run() throws XMLDBException, EXistException {
        runner.getResults().groupStart(this);

        if (setupAction != null) {
            setupAction.run();
        }

        Stack<Thread> stack = new Stack<Thread>();
        for (ActionThread thread : threads) {
            Thread t = new Thread(thread, thread.getName());
            t.start();
            stack.push(t);
        }

        while (!stack.isEmpty()) {
            Thread t = stack.pop();
            try {
                t.join();
            } catch (InterruptedException e) {
            }
        }

        if (tearDownAction != null) {
            tearDownAction.run();
        }

        runner.getResults().groupEnd(this);
    }

    public String getName() {
        return name;
    }
}
