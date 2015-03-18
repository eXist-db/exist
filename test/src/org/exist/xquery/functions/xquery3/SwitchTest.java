/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2011 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id: SwitchTest.java 13849 2011-02-26 16:29:17Z dizzzz $
 */
package org.exist.xquery.functions.xquery3;

import org.xmldb.api.base.ResourceSet;

import org.exist.test.EmbeddedExistTester;
import org.junit.AfterClass;
import org.junit.BeforeClass;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author ljo
 */
public class SwitchTest extends EmbeddedExistTester {

    public SwitchTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @After
    public void tearDown() {
    }

    // *******************************************
    @Test
    public void oneCaseCaseMatch() {
        String query = "xquery version '3.0';"
                + "let $animal := 'Cat' return "
                + "switch ($animal)"
                + "case 'Cow' return 'Moo'"
                + "case 'Cat' return 'Meow'"
                + "case 'Duck' return 'Quack'"
                + "default return 'Odd noise!'";
        try {
            ResourceSet results = executeQuery(query);
            String r = (String) results.getResource(0).getContent();
            assertEquals("Meow", r);

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

    }

    @Test
    public void twoCaseDefault() {
        String query = "xquery version '3.0';"
                + "let $animal := 'Cat' return "
                + "switch ($animal)"
                + "case 'Cow' case 'Calf' return 'Moo'"
                + "default return 'No Bull?'";
        try {
            ResourceSet results = executeQuery(query);
            String r = (String) results.getResource(0).getContent();
            assertEquals("No Bull?", r);

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

    }

    @Test
    public void twoCaseCaseMatch() {
        String query = "xquery version '3.0';"
                + "let $animal := 'Calf' return "
                + "switch ($animal)"
                + "case 'Cow' case 'Calf' return 'Moo'"
                + "case 'Cat' return 'Meow'"
                + "case 'Duck' return 'Quack'"
                + "default return 'Odd noise!'";
        try {
            ResourceSet results = executeQuery(query);
            String r = (String) results.getResource(0).getContent();
            assertEquals("Moo", r);

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

    }
}
