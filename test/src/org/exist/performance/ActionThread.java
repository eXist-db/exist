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

import org.w3c.dom.Element;
import org.exist.EXistException;
import org.exist.performance.actions.Action;
import org.xmldb.api.base.XMLDBException;

public class ActionThread extends ActionSequence implements Runnable {

    private String name;

    private Connection connection;

    public void configure(Runner runner, Action parent, Element config) throws EXistException {
        name = config.getAttribute("name");
        String con = config.getAttribute("connection");
        if (con.length() == 0)
            throw new EXistException("thread needs a connection");
        connection = runner.getConnection(con);
        if (connection == null)
            throw new EXistException("unknown connection " + con +
                    " referenced by thread " + name);
        super.configure(runner, null, config);
    }

    public void run() {
        runner.getResults().threadStarted(this);
        try {
            execute(this.connection);
        } catch (XMLDBException e) {
            e.printStackTrace();
        } catch (EXistException e) {
            e.printStackTrace();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public String getId() {
        return name;
    }
}
