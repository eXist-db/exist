/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.xquery.functions.xmldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xmldb.EXistResource;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import java.util.Calendar;
import java.util.Date;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class XMLDBTouch extends XMLDBAbstractCollectionManipulator {

    private static final Logger logger = LogManager.getLogger(XMLDBTouch.class);

    private final static QName qnTouch = new QName("touch", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX);

    private final static SequenceType PARAM_COLLECTION_URI = new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI");
    private final static SequenceType PARAM_RESOURCE = new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the resource in the collection");

    private final static SequenceType RETURN_VAL = new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the modification time was updated, false otherwise.");

    public final static FunctionSignature FNS_TOUCH_DOCUMENT_NOW = new FunctionSignature(
        qnTouch,
        "Sets the modification time of a resource to the current system time. If not resource does not exist it is not created.",
        new SequenceType[] {
            PARAM_COLLECTION_URI,
            PARAM_RESOURCE
        },
        RETURN_VAL
    );

    public final static FunctionSignature FNS_TOUCH_DOCUMENT = new FunctionSignature(
        qnTouch,
        "Sets the modification time of a resource.  If not resource does not exist it is not created.",
        new SequenceType[] {
            PARAM_COLLECTION_URI,
            PARAM_RESOURCE,
            new FunctionParameterSequenceType("modification-time", Type.DATE_TIME, Cardinality.EXACTLY_ONE, "The modification time to set on the resource")
        },
        RETURN_VAL
    );

    public XMLDBTouch(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }


    @Override
    protected Sequence evalWithCollection(final Collection collection, final Sequence[] args, final Sequence contextSequence) throws XPathException {

        try {
            final EXistResource resource = (EXistResource)collection.getResource(args[1].getStringValue());
            if(resource == null) {
                return BooleanValue.FALSE;
            }

            final Date newModificationTime;
            if (getSignature().getArgumentCount() == 2) {
                newModificationTime = Calendar.getInstance().getTime();
            } else if (getSignature().getArgumentCount() == 3) {
                newModificationTime = args[2].toJavaObject(Date.class);
            } else {
                throw new XPathException(this, "Unrecognised function signature: " + getSignature());
            }

            resource.setLastModificationTime(newModificationTime);

            return BooleanValue.TRUE;

        } catch(final XMLDBException e) {
            logger.error("Failed to set modification time of: " + args[0].getStringValue() + "/" + args[1].getStringValue());
            throw new XPathException(this, "Failed to set modification time: " + e.getMessage(), e);
        }
    }
}
