/*
*  eXist Open Source Native XML Database
*  Copyright (C) 2001-04 Wolfgang M. Meier (wolfgang@exist-db.org) 
*  and others (see http://exist-db.org)
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
package org.exist.xmldb.concurrent;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.exist.util.FileUtils;
import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.XQueryAction;
import org.junit.After;
import org.junit.Before;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 */
public class ConcurrentQueryTest extends ConcurrentTestBase {

    private static final String QUERY0 = "/ROOT-ELEMENT/ELEMENT/ELEMENT-1/ELEMENT-2[@attribute-3]";
    private static final String QUERY1 = "distinct-values(//ELEMENT/@attribute-2)";
    private static final String QUERY2 = "/ROOT-ELEMENT//ELEMENT-1[@attribute-3]";

	private Path tempFile;

    @Before
    public void setUp() throws Exception {
        final String[] wordList = DBUtils.wordList();
        tempFile = DBUtils.generateXMLFile(500, 7, wordList);
        DBUtils.addXMLResource(getTestCollection(), "R1.xml", tempFile);
    }

    @After
    public void tearDown() throws XMLDBException {
        FileUtils.deleteQuietly(tempFile);
    }

    @Override
    public String getTestCollectionName() {
        return "C1";
	}

    @Override
    public List<Runner> getRunners() {
        return Arrays.asList(
                new Runner(new XQueryAction(XmldbURI.LOCAL_DB + "/C1", "R1.xml", QUERY0),50, 100, 0),
                new Runner(new XQueryAction(XmldbURI.LOCAL_DB + "/C1", "R1.xml", QUERY1), 50, 50, 0),
                new Runner(new XQueryAction(XmldbURI.LOCAL_DB + "/C1", "R1.xml", QUERY2), 50, 0, 0)
        );
    }
}
