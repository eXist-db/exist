/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xmldb.concurrent;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.exist.util.FileUtils;
import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.AttributeUpdateAction;
import org.junit.After;
import org.junit.Before;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertNotNull;

/**
 * @author wolf
 */
public class ConcurrentAttrUpdateTest extends ConcurrentTestBase {

//	private static final String QUERY =
//		"//ELEMENT[@attribute-1]";

    private String[] wordList;
    private Path tempFile;

    @Before
    public void setUp() throws Exception {
        this.wordList = DBUtils.wordList();
        assertNotNull(wordList);
        this.tempFile = DBUtils.generateXMLFile(250, 10, wordList);
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
        return List.of(
                new Runner(new AttributeUpdateAction(XmldbURI.LOCAL_DB + "/C1", "R1.xml", wordList), 20, 0, 0)
                //new Runner(new XQueryAction(getUri + "/C1", "R1.xml", QUERY), 100, 100, 30);
        );
    }
}

