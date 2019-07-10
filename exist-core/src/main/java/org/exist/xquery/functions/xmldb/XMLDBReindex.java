/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2008-2010 The eXist Project
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
 *  $Id$
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
            logger.error("Permission denied, user '" + context.getSubject().getName() + "' must be a DBA to reindex the database");
            return BooleanValue.FALSE;
        }

        // Check if collection does exist
        
        if (collection == null) {
            logger.error("Collection " + args[0].getStringValue() + " does not exist.");
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
