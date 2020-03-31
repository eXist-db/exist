/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.xquery.functions.util;

import java.net.URISyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.LockedCollection;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 *
 */
public class CollectionSuspendTriggers extends BasicFunction {

    protected static final Logger logger = LogManager.getLogger(CollectionName.class);
    private final static QName QN_COLLECTION_SUSPEND_TRIGGERS = new QName("collection-suspend-triggers", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);
    private final static QName QN_COLLECTION_ENABLE_TRIGGERS = new QName("collection-enable-triggers", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    QN_COLLECTION_SUSPEND_TRIGGERS,
                    "suspends triggers for a collection.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("node-or-path-string", Type.ITEM, Cardinality.ZERO_OR_ONE, "The document node or a path string.")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ONE, "current state of triggers for the collection")

            ),
            new FunctionSignature(
                    QN_COLLECTION_ENABLE_TRIGGERS,
                    "re-enables possibly suspended triggers for a collection.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("node-or-path-string", Type.ITEM, Cardinality.ZERO_OR_ONE, "The document node or a path string.")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ONE, "current state of triggers for the collection")

            )
    };


    public CollectionSuspendTriggers(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {

        if(args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final Item item = args[0].itemAt(0);
        final QName fnName = getSignature().getName();

        boolean skipTriggers = false;
        if(fnName.equals(QN_COLLECTION_SUSPEND_TRIGGERS)) {
            skipTriggers = true;
        }

        if (item.getType() == Type.JAVA_OBJECT) {
            //
        } else if (Type.subTypeOf(item.getType(), Type.STRING)) {
            final String path = item.getStringValue();
            try {
                final XmldbURI uri = XmldbURI.xmldbUriFor(path);
                DBBroker b = context.getBroker();
                org.exist.collections.Collection c = b.getCollection(uri);
                CollectionConfiguration cc = null;
                if (c != null) {
                    c = LockedCollection.unwrapLocked(b.getCollection(uri));
                    cc = c.getConfiguration(b);
                    cc.setSkipTriggers(skipTriggers);
                }
            } catch (PermissionDeniedException | URISyntaxException e) {
                e.printStackTrace();
            }
        } else if (Type.subTypeOf(item.getType(), Type.NODE)) {
            //
        } else {
            throw new XPathException(this, "First argument to util:collection-suspend-triggers should be either " +
                    "a Java object of type org.xmldb.api.base.Collection or a node; got: " +
                    Type.getTypeName(item.getType()));
        }

        return Sequence.EMPTY_SEQUENCE;
    }

}
