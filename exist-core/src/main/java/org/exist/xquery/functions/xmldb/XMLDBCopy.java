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

import org.exist.storage.DBBroker;
import org.exist.xmldb.EXistCollectionManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.xmldb.XMLDBModule.functionSignature;
import static org.exist.xquery.functions.xmldb.XMLDBModule.functionSignatures;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class XMLDBCopy extends XMLDBAbstractCollectionManipulator {
    private static final Logger logger = LogManager.getLogger(XMLDBCopy.class);

    private static final String FS_COPY_COLLECTION_NAME = "copy-collection";
    private static final String FS_COPY_RESOURCE_NAME = "copy-resource";
    private static final FunctionParameterSequenceType FS_PARAM_SOURCE_COLLECTION_URI = param("source-collection-uri", Type.STRING, "The source URI");
    private static final FunctionParameterSequenceType FS_PARAM_TARGET_COLLECTION_URI = param("target-collection-uri", Type.STRING, "The target URI");
    private static final FunctionParameterSequenceType FS_PARAM_PRESERVE = param("preserve", Type.BOOLEAN, "Cause the copy process to preserve the following attributes of each source in the copy: modification time, file mode, user ID, and group ID, as allowed by permissions. Access Control Lists (ACLs) will also be preserved");
    private static final FunctionParameterSequenceType FS_PARAM_SOURCE_RESOURCE_NAME = param("source-resource-name", Type.STRING, "The name of the resource to copy");
    private static final FunctionParameterSequenceType FS_PARAM_TARGET_RESOURCE_NAME = optParam("target-resource-name", Type.STRING, "The name of the resource for the target");

    static final FunctionSignature[] FS_COPY_COLLECTION = functionSignatures(
            FS_COPY_COLLECTION_NAME,
            "Copy the collection $source-collection-uri to the collection $target-collection-uri.",
            returns(Type.STRING, "The path to the newly copied collection"),
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
            FS_COPY_RESOURCE_NAME,
            "Copy the resource $source-collection-uri/$source-resource-name to collection $target-collection-uri/$target-resource-name. If the $target-resource-name is omitted, the $source-resource-name will be used.",
            returns(Type.STRING, "The path to the newly copied resource"),
            arities(
                    arity(
                            FS_PARAM_SOURCE_COLLECTION_URI,
                            FS_PARAM_SOURCE_RESOURCE_NAME,
                            FS_PARAM_TARGET_COLLECTION_URI,
                            FS_PARAM_TARGET_RESOURCE_NAME
                    ),
                    arity(
                            FS_PARAM_SOURCE_COLLECTION_URI,
                            FS_PARAM_SOURCE_RESOURCE_NAME,
                            FS_PARAM_TARGET_COLLECTION_URI,
                            FS_PARAM_TARGET_RESOURCE_NAME,
                            FS_PARAM_PRESERVE
                    )
            )
    );

    public XMLDBCopy(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence evalWithCollection(final Collection collection, final Sequence[] args,
            final Sequence contextSequence) throws XPathException {

        if (isCalledAs(FS_COPY_RESOURCE_NAME)) {
            final XmldbURI destination = new AnyURIValue(args[2].itemAt(0).getStringValue()).toXmldbURI();
            final XmldbURI doc = new AnyURIValue(args[1].itemAt(0).getStringValue()).toXmldbURI();
            try {
                final Resource resource = collection.getResource(doc.toString());
                if (resource == null) {
                    logger.error("Resource " + doc + " not found");
                    throw new XPathException(this, "Resource " + doc + " not found");
                }
                final EXistCollectionManagementService service = (EXistCollectionManagementService) collection.getService("CollectionManagementService", "1.0");
                final DBBroker.PreserveType preserve;
                if (getArgumentCount() == 5) {
                    final boolean preserveArg = args[4].itemAt(0).toJavaObject(boolean.class);
                    if (preserveArg) {
                        preserve = DBBroker.PreserveType.PRESERVE;
                    } else {
                        preserve = DBBroker.PreserveType.DEFAULT;
                    }
                } else {
                    preserve = DBBroker.PreserveType.DEFAULT;
                }

                final XmldbURI newName;
                if (getArgumentCount() >= 4) {
                    if (!args[3].isEmpty()) {
                        newName = XmldbURI.create(args[3].itemAt(0).getStringValue());
                    } else {
                        newName = doc.lastSegment();
                    }
                } else {
                    newName = null;
                }

                service.copyResource(doc, destination, newName, preserve.name());

                if (isCalledAs(FS_COPY_RESOURCE_NAME)) {
                    return new StringValue(destination.append(newName).getRawCollectionPath());
                } else {
                    return Sequence.EMPTY_SEQUENCE;
                }

            } catch (final XMLDBException e) {
                logger.error("XMLDB exception caught: ", e);
                throw new XPathException(this, "XMLDB exception caught: " + e.getMessage(), e);
            }

        } else {
            final XmldbURI destination = new AnyURIValue(args[1].itemAt(0).getStringValue()).toXmldbURI();
            try {
                final EXistCollectionManagementService service = (EXistCollectionManagementService) collection.getService("CollectionManagementService", "1.0");

                final DBBroker.PreserveType preserve;
                if (getArgumentCount() == 3) {
                    final boolean preserveArg = args[2].itemAt(0).toJavaObject(boolean.class);
                    if (preserveArg) {
                        preserve = DBBroker.PreserveType.PRESERVE;
                    } else {
                        preserve = DBBroker.PreserveType.DEFAULT;
                    }
                } else {
                    preserve = DBBroker.PreserveType.DEFAULT;
                }

                service.copy(XmldbURI.xmldbUriFor(collection.getName()), destination, null, preserve.name());

                if (isCalledAs(FS_COPY_COLLECTION_NAME)) {
                    final XmldbURI targetName = XmldbURI.xmldbUriFor(collection.getName()).lastSegment();
                    return new StringValue(destination.append(targetName).getRawCollectionPath());
                } else {
                    return Sequence.EMPTY_SEQUENCE;
                }

            } catch (final XMLDBException e) {
                logger.error("Cannot copy collection: ", e);
                throw new XPathException(this, "Cannot copy collection: " + e.getMessage(), e);

            } catch (final URISyntaxException e) {
                logger.error("URI exception: ", e);
                throw new XPathException(this, "URI exception: " + e.getMessage(), e);
            }
        }

    }
}
