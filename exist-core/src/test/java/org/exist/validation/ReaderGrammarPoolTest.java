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
package org.exist.validation;

import org.apache.xerces.impl.dtd.XMLDTDLoader;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The view of the shared pool that a reader has does not cache the DTD grammars of the documents that are parsed with
 * it: Xerces builds such a grammar together with the internal subset of the document.
 */
public class ReaderGrammarPoolTest {

    @Test
    void theDtdGrammarOfAParseIsNotCached() throws IOException {
        final GrammarPool shared = new GrammarPool();
        final ReaderGrammarPool view = new ReaderGrammarPool(shared);

        final Grammar dtd = new XMLDTDLoader().loadGrammar(new XMLInputSource("-//EXIST-TEST//DTD A//EN", "file:///dtds/a.dtd", null,
                new StringReader("<!ELEMENT a (#PCDATA)><!ENTITY alpha \"A\">"), null));
        view.cacheGrammars(XMLGrammarDescription.XML_DTD, new Grammar[]{dtd});

        assertEquals(0, shared.retrieveCachedDtdGrammars().length);

        // what is cached through the shared pool itself is offered to a reader that enables DTD reuse, as before
        shared.cacheGrammars(XMLGrammarDescription.XML_DTD, new Grammar[]{dtd});
        view.enableDtdReuse();
        assertEquals(1, view.retrieveInitialGrammarSet(XMLGrammarDescription.XML_DTD).length);
    }
}
