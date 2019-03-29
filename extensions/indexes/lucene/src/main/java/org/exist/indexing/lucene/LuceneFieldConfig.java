/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.indexing.lucene;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.w3c.dom.Element;

import java.util.Map;

public class LuceneFieldConfig extends AbstractFieldConfig {

    public final static String ATTR_FIELD_NAME = "name";

    protected String fieldName;

    public LuceneFieldConfig(Element configElement, Map<String, String> namespaces) throws DatabaseConfigurationException {
        super(configElement, namespaces);

        fieldName = configElement.getAttribute(ATTR_FIELD_NAME);
        if (fieldName == null || fieldName.length() == 0) {
            throw new DatabaseConfigurationException("Invalid config: attribute 'name' must be given");
        }
    }

    @Override
    void build(DBBroker broker, DocumentImpl document, NodeId nodeId, Document luceneDoc) {
        try {
            doBuild(broker, document, nodeId, luceneDoc);
        } catch (XPathException e) {
            LOG.warn("XPath error while evaluating expression for field named '" + fieldName + "': " + expression +
                    ": " + e.getMessage(), e);
        } catch (PermissionDeniedException e) {
            LOG.warn("Permission denied while evaluating expression for field named '" + fieldName + "': " + expression, e);
        }
    }

    @Override
    void processResult(Sequence result, Document luceneDoc) throws XPathException {
        for (SequenceIterator i = result.unorderedIterator(); i.hasNext(); ) {
            final String text = i.nextItem().getStringValue();
            luceneDoc.add(new TextField(fieldName, text, Field.Store.YES));
        }
    }
}
