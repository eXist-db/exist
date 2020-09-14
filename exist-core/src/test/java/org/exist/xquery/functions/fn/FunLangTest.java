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

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import org.junit.runner.RunWith;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author ljo
 */
@RunWith(ParallelRunner.class)
public class FunLangTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(true, true, true);

    @Test
    public void testFnLangWithContext() throws XMLDBException {
        final ResourceSet resourceSet = existEmbeddedServer.executeQuery(
            "let $doc-frag := " +
	    "<desclist xml:lang=\"en\">" +
	       "<desc xml:lang=\"en-US\" n=\"1\">" +
	          "<line>The first line of the description.</line>" +
	       "</desc>" +
	       "<desc xml:lang=\"fr\" n=\"2\">"+
	          "<line>La premi&#232;re ligne de la déscription.</line>" +
	       "</desc>" +
	    "</desclist>" + 
            "return $doc-frag//desc[lang(\"en-US\")]"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("<desc xml:lang=\"en-US\" n=\"1\">\n    <line>The first line of the description.</line>\n</desc>", resourceSet.getResource(0).getContent());
    }

        @Test
    public void testFnLangWithArgument() throws XMLDBException {
		final ResourceSet resourceSet = existEmbeddedServer.executeQuery(
            "let $doc-frag := " +
	    "<desclist xml:lang=\"en\">" +
	       "<desc xml:lang=\"en-US\" n=\"1\">" +
	          "<line>The first line of the description.</line>" +
	       "</desc>" +
	       "<desc xml:lang=\"fr\" n=\"2\">"+
	          "<line>La premi&#232;re ligne de la déscription.</line>" +
	       "</desc>" +
	    "</desclist>" + 
            "return lang(\"en-US\", $doc-frag//desc[@n eq \"2\"])"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("false", resourceSet.getResource(0).getContent());
    }
    
    @Test
    public void testFnLangWithAttributeArgument() throws XMLDBException {
		final ResourceSet resourceSet = existEmbeddedServer.executeQuery(
            "let $doc-frag := " +
	    "<desclist xml:lang=\"en\">" +
	       "<desc xml:lang=\"en-US\" n=\"1\">" +
	          "<line>The first line of the description.</line>" +
	       "</desc>" +
	       "<desc xml:lang=\"fr\" n=\"2\">"+
	          "<line>La premi&#232;re ligne de la déscription.</line>" +
	       "</desc>" +
	    "</desclist>" + 
            "return lang(\"en-US\", $doc-frag//desc/@n[. eq \"1\"])"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("true", resourceSet.getResource(0).getContent());
    }
}
