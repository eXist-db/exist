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
package org.exist.xquery.functions.xmldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xmldb.IndexQueryService;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 *  Reindex a collection in the database.
 * 
 * @author dizzzz
 * @author ljo
 *
 */
public class XMLDBReindex extends XMLDBAbstractCollectionManipulator {
	protected static final Logger logger = LogManager.getLogger(XMLDBReindex.class);

    public final static FunctionSignature FNS_REINDEX_COLLECTION = new FunctionSignature(
            new QName("reindex", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Reindex collection $collection-uri. " +
            XMLDBModule.COLLECTION_URI + " " +
            XMLDBModule.NEED_PRIV_USER,
            new SequenceType[] {
                new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if successfully reindexed, false() otherwise")
    );

    public final static FunctionSignature FNS_REINDEX_DOCUMENT = new FunctionSignature(
            new QName("reindex", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Reindex document $doc-uri from $collection-uri. " +
                    XMLDBModule.COLLECTION_URI + " " +
                    XMLDBModule.ANY_URI + " " +
                    XMLDBModule.NEED_PRIV_USER,
            new SequenceType[] {
                    new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI"),
                    new FunctionParameterSequenceType("doc-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The document URI")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if successfully reindexed, false() otherwise")
    );

    public XMLDBReindex(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature, false);
    }

    @Override
    public Sequence evalWithCollection(final Collection collection, final Sequence[] args, final Sequence contextSequence)
        throws XPathException {
        // Check for DBA user
        if (!context.getSubject().hasDbaRole()) {
            logger.error("Permission denied, user '{}' must be a DBA to reindex the database", context.getSubject().getName());
            return BooleanValue.FALSE;
        }

        // Check if collection does exist
        
        if (collection == null) {
            logger.error("Collection {} does not exist.", args[0].getStringValue());
            return BooleanValue.FALSE;
        }

        try {
            final IndexQueryService iqs = (IndexQueryService) collection.getService("IndexQueryService", "1.0");
            if(args.length == 2) {
                //reindex document
                iqs.reindexDocument(args[1].getStringValue());
            } else {
                //reindex collection
                iqs.reindexCollection();
            }
        } catch (final XMLDBException xe) {
            logger.error("Unable to reindex collection", xe);
            return BooleanValue.FALSE;
        }

        return BooleanValue.TRUE;
    }
}
