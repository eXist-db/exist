/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  dizzzz@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.xmldb;

import org.exist.collections.Collection;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 *  Reindex a collection in the database.
 * 
 * @author dizzzz
 */
public class XMLDBReindex extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("reindex", XMLDBModule.NAMESPACE_URI,
            XMLDBModule.PREFIX),
            "Reindex collection $a. DBA only",
            new SequenceType[]{
        new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
    },
            new SequenceType(Type.BOOLEAN, Cardinality.EMPTY));

    /**
     * @param context
     */
    public XMLDBReindex(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        // this is "/db"
        String ROOTCOLLECTION = XmldbURI.ROOT_COLLECTION_URI.toString();

        // Check for DBA user
        if (!context.getUser().hasDbaRole()) {
            throw new XPathException("Permission denied, user '" + context.getUser().getName() + "' must be a DBA to shutdown the database");
        }

        // Get collection path
        String collectionArg = args[0].getStringValue();

        // Collection should start with /db
        if (!collectionArg.startsWith(ROOTCOLLECTION)) {
            throw new XPathException(getASTNode(),
                    "Collection should start with " + ROOTCOLLECTION + "");
        }

        // Check if collection does exist
        XmldbURI colName = XmldbURI.create(collectionArg);
        Collection coll = context.getBroker().getCollection(colName);
        if (coll == null) {
            throw new XPathException(getASTNode(),
                    "Collection " + colName.toString() + " does not exist.");
        }

        // Reindex
        try {
            context.getBroker().reindexCollection(colName);

        } catch (PermissionDeniedException ex) {
            throw new XPathException(getASTNode(), ex.getMessage());
        }

        return Sequence.EMPTY_SEQUENCE;
    }
}
