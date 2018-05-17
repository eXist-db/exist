/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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

import java.net.URISyntaxException;
import org.exist.xmldb.EXistCollectionManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.xmldb.XMLDBModule.functionSignatures;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 *
 */
public class XMLDBCopy extends XMLDBAbstractCollectionManipulator {
    private static final Logger logger = LogManager.getLogger(XMLDBCopy.class);

    private static final String FS_COPY_NAME = "copy";
    private static final FunctionParameterSequenceType FS_PARAM_SOURCE_COLLECTION_URI = param("source-collection-uri", Type.STRING, "The source URI");
    private static final FunctionParameterSequenceType FS_PARAM_TARGET_COLLECTION_URI = param("target-collection-uri", Type.STRING, "The target URI");
    private static final FunctionParameterSequenceType FS_PARAM_PRESERVE = param("preserve", Type.BOOLEAN, "Cause the copy process to preserve the following attributes of each source in the copy: modification time, file mode, user ID, and group ID, as allowed by permissions. Access Control Lists (ACLs) will also be preserved");
    private static final FunctionParameterSequenceType FS_PARAM_RESOURCE = param("resource", Type.STRING,"The resource to copy");

    static final FunctionSignature[] FS_COPY_COLLECTION = functionSignatures(
            FS_COPY_NAME,
            "Copy the collection $source-collection-uri to the collection $target-collection-uri.",
            returnsNothing(),
            arities(
                    arity(
                            FS_PARAM_SOURCE_COLLECTION_URI,
                            FS_PARAM_TARGET_COLLECTION_URI
                    ),
                    arity(
                            FS_PARAM_SOURCE_COLLECTION_URI,
                            FS_PARAM_TARGET_COLLECTION_URI,
                            FS_PARAM_PRESERVE
                    )
            )
    );

    static final FunctionSignature[] FS_COPY_RESOURCE = functionSignatures(
            FS_COPY_NAME,
            "Copy the resource $resource in $source-collection-uri to collection $target-collection-uri.",
            returnsNothing(),
            arities(
                    arity(
                            FS_PARAM_SOURCE_COLLECTION_URI,
                            FS_PARAM_TARGET_COLLECTION_URI,
                            FS_PARAM_RESOURCE
                    ),
                    arity(
                            FS_PARAM_SOURCE_COLLECTION_URI,
                            FS_PARAM_TARGET_COLLECTION_URI,
                            FS_PARAM_RESOURCE,
                            FS_PARAM_PRESERVE
                    )
            )
    );

    public XMLDBCopy(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence)
	throws XPathException {

        final XmldbURI destination = new AnyURIValue(args[1].itemAt(0).getStringValue()).toXmldbURI();
        if (getSignature().getArgumentCount() == 3) {
            final XmldbURI doc = new AnyURIValue(args[2].itemAt(0).getStringValue()).toXmldbURI();
            try {
                final Resource resource = collection.getResource(doc.toString());
                if (resource == null) {
		    logger.error("Resource " + doc + " not found");
                    throw new XPathException(this, "Resource " + doc + " not found");
                }
                final EXistCollectionManagementService service = (EXistCollectionManagementService) collection.getService("CollectionManagementService", "1.0");
                service.copyResource(doc, destination, null);
            } catch (final XMLDBException e) {
		logger.error("XMLDB exception caught: ", e);
                throw new XPathException(this, "XMLDB exception caught: " + e.getMessage(), e);
            }
            
        } else {
            try {
                final EXistCollectionManagementService service = (EXistCollectionManagementService) collection.getService("CollectionManagementService", "1.0");
                // DWES to check not sure about XmldbURI.xmldbUriFor() here.
                service.copy(XmldbURI.xmldbUriFor(collection.getName()), destination, null);

            } catch (final XMLDBException e) {
		logger.error("Cannot copy collection: ", e);
                throw new XPathException(this, "Cannot copy collection: " + e.getMessage(), e);

            } catch (final URISyntaxException e) {
		logger.error("URI exception: ", e);
		throw new XPathException(this, "URI exception: " + e.getMessage(), e);
            }
        }

        return Sequence.EMPTY_SEQUENCE;
    }
}
