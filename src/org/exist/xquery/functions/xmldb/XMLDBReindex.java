/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2008-2009 The eXist Project
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

import org.apache.log4j.Logger;

import org.exist.collections.Collection;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 *  Reindex a collection in the database.
 * 
 * @author dizzzz
 * @author ljo
 *
 */
public class XMLDBReindex extends BasicFunction {
	protected static final Logger logger = Logger.getLogger(XMLDBReindex.class);
    public final static FunctionSignature signature = new FunctionSignature(
            new QName("reindex", XMLDBModule.NAMESPACE_URI,
                      XMLDBModule.PREFIX), // yes, only a path not an uri /ljo
            "Reindex collection $collection-path. " +
            XMLDBModule.NEED_PRIV_USER,
            new SequenceType[]{
                new FunctionParameterSequenceType("collection-path", Type.STRING, Cardinality.EXACTLY_ONE, "The collection path")
    },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if successfully reindexed, false() otherwise"));

    /**
     * @param context
     */
    public XMLDBReindex(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence)
        throws XPathException {
        // this is "/db"
        String ROOTCOLLECTION = XmldbURI.ROOT_COLLECTION_URI.toString();

        // Check for DBA user
        if (!context.getUser().hasDbaRole()) {
            logger.error("Permission denied, user '" + context.getUser().getName() + "' must be a DBA to reindex the database");
            return BooleanValue.FALSE;
        }

        // Get collection path
        String collectionArg = args[0].getStringValue();

        // Collection should start with /db
        if (!collectionArg.startsWith(ROOTCOLLECTION)) {
            logger.error("Collection should start with " + ROOTCOLLECTION);
            return BooleanValue.FALSE;            
        }

        // Check if collection does exist
        XmldbURI colName = XmldbURI.create(collectionArg);
        Collection coll = context.getBroker().getCollection(colName);
        if (coll == null) {
            logger.error("Collection " + colName.toString() + " does not exist.");
            return BooleanValue.FALSE;
        }

        // Reindex
        try {
            context.getBroker().reindexCollection(colName);

        } catch (PermissionDeniedException ex) {
            logger.error(ex.getMessage());
            return BooleanValue.FALSE;
        }

        return BooleanValue.TRUE;
    }
}
