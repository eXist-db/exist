/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.storage.btree;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 *
 */
public class TreeMetrics {

    private int leafPages = 0;
    private int innerPages = 0;
    private int dataPages = 0;
    private String btreeName;

    public TreeMetrics(String name) {
        this.btreeName = name;
    }

    public void addPage(int status) {
        if (status == BTree.BRANCH)
            {addInnerPage();}
        else
            {addLeafPage();}
    }

    public void addLeafPage() {
        ++leafPages;
    }

    public void addInnerPage() {
        ++innerPages;
    }

    public void addDataPage() {
        ++dataPages;
    }

    public void print(PrintWriter writer) {
        writer.println("BTree tree metrics for " + btreeName);
        writer.println("# inner pages: " + innerPages);
        writer.println("# leaf pages: " + leafPages);
        writer.println("# data pages: " + dataPages);
    }

    public void toLogger() {
        final StringWriter sw = new StringWriter();
        final PrintWriter writer = new PrintWriter(sw);
        print(writer);
        if (BTree.LOG.isDebugEnabled())
            {BTree.LOG.debug(sw.toString());}
    }
}
