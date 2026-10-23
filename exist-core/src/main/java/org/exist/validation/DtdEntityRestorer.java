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

import org.apache.xerces.impl.XMLEntityManager;
import org.apache.xerces.impl.dtd.DTDGrammar;
import org.apache.xerces.impl.dtd.XMLEntityDecl;
import org.apache.xerces.util.URI;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Objects;

/**
 * Makes the general entities of a cached DTD available to the document that uses it.
 *
 * When Xerces uses a cached DTD grammar it does not scan the DTD again, so the entity manager that
 * expands the references in the content is never told about the entities that the DTD declares.
 * This registers them, once the DOCTYPE has been processed, with the same rules that Xerces uses to
 * select the cached grammar: the public id and the expanded system id of the DOCTYPE. As
 * declarations that were seen first win, an entity that the internal subset declares is kept.
 *
 * This works around the Xerces defect described in
 * <a href="https://issues.apache.org/jira/browse/XERCESJ-1205">XERCESJ-1205</a>, which is not
 * fixed in any Xerces release.
 */
public class DtdEntityRestorer {

    private final GrammarPool grammarPool;
    private final XMLEntityManager entityManager;

    public DtdEntityRestorer(final GrammarPool grammarPool, final XMLEntityManager entityManager) {
        this.grammarPool = grammarPool;
        this.entityManager = entityManager;
    }

    /**
     * Register the entities of the cached DTD that this DOCTYPE refers to, if there is one.
     *
     * @param publicId the public id of the DOCTYPE, or null
     * @param systemId the system id of the DOCTYPE as written in the document
     * @param baseSystemId the system id of the document, used to expand a relative system id
     *
     * @throws IOException if an entity cannot be registered
     */
    public void restore(@Nullable final String publicId, @Nullable final String systemId,
            @Nullable final String baseSystemId) throws IOException {
        if (systemId == null) {
            return;
        }

        final String expandedSystemId;
        try {
            expandedSystemId = XMLEntityManager.expandSystemId(systemId, baseSystemId, false);
        } catch (final URI.MalformedURIException e) {
            return;
        }

        final DTDGrammar dtd = findGrammar(publicId, expandedSystemId);
        if (dtd == null) {
            return;
        }

        // a cached grammar only has internal general entities, see GrammarPool#isReusableDtd
        final XMLEntityDecl decl = new XMLEntityDecl();
        for (int i = 0; dtd.getEntityDecl(i, decl); i++) {
            if (!decl.isPE && decl.systemId == null && decl.notation == null) {
                entityManager.addInternalEntity(decl.name, decl.value);
            }
        }
    }

    @Nullable
    private DTDGrammar findGrammar(@Nullable final String publicId, final String expandedSystemId) {
        for (final Grammar grammar : grammarPool.retrieveCachedDtdGrammars()) {
            final XMLGrammarDescription desc = grammar.getGrammarDescription();
            if (grammar instanceof DTDGrammar dtd
                    && Objects.equals(publicId, desc.getPublicId())
                    && expandedSystemId.equals(desc.getExpandedSystemId())) {
                return dtd;
            }
        }
        return null;
    }
}
