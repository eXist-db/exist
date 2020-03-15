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
package org.exist.xquery.functions.fn;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author aretter
 */
public class FunNumberTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(true, true, true);
    
    @Test
    public void testFnNumberWithContext() throws XMLDBException {
        final ResourceSet resourceSet = existEmbeddedServer.executeQuery(
            "let $errors := " +
                "<report>" +
                    "<message level=\"Error\" line=\"1191\" column=\"49\" repeat=\"96\"></message>" +
                    "<message level=\"Error\" line=\"161740\" column=\"25\"></message>" +
                    "<message level=\"Error\" line=\"162327\" column=\"92\" repeat=\"87\"></message>" +
                    "<message level=\"Error\" line=\"255090\" column=\"25\">c</message>" +
                    "<message level=\"Error\" line=\"255702\" column=\"414\" repeat=\"9\"></message>" +
                "</report>" +
            "return sum($errors//message/(@repeat/number(),1)[1])"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("194", resourceSet.getResource(0).getContent());
    }
    
    @Test
    public void testFnNumberWithArgument() throws XMLDBException {
        final ResourceSet resourceSet = existEmbeddedServer.executeQuery(
            "let $errors := " +
                "<report>" +
                    "<message level=\"Error\" line=\"1191\" column=\"49\" repeat=\"96\"></message>" +
                    "<message level=\"Error\" line=\"161740\" column=\"25\"></message>" +
                    "<message level=\"Error\" line=\"162327\" column=\"92\" repeat=\"87\"></message>" +
                    "<message level=\"Error\" line=\"255090\" column=\"25\">c</message>" +
                    "<message level=\"Error\" line=\"255702\" column=\"414\" repeat=\"9\"></message>" +
                "</report>" +
            "return sum($errors//message/(number(@repeat),1)[1])"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("NaN", resourceSet.getResource(0).getContent());
    }
}
