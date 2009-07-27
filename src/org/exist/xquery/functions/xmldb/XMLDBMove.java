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

import org.apache.log4j.Logger;

import java.net.URISyntaxException;
import org.exist.dom.QName;
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 *
 */
public class XMLDBMove extends XMLDBAbstractCollectionManipulator {
	protected static final Logger logger = Logger.getLogger(XMLDBMove.class);
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
        new QName("move", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
        "Move a collection $source-collection-uri. The collection can be specified either as " +
        "a simple collection path or an XMLDB URI.",
        new SequenceType[]{
            new FunctionParameterSequenceType("source-collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "the source-collection-uri"),
            new FunctionParameterSequenceType("target-collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "the target-collection-uri")},
        new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY, "empty item sequence")),

        new FunctionSignature(
        new QName("move", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
        "Move a resource $resource from the collection specified in $source-collection-uri to collection in $target-collection-uri. " +
        "The collection can be either specified as a simple collection path or " +
        "an XMLDB URI.",
        new SequenceType[]{
            new FunctionParameterSequenceType("source-collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "the source-collection-uri"),
            new FunctionParameterSequenceType("target-collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "the target-collection-uri"),
            new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "the resource")},
        new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY, "empty item sequence"))
    };

    public XMLDBMove(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence)
        throws XPathException {
		logger.info("Entering " + XMLDBModule.PREFIX + ":" + getName().getLocalName());

        XmldbURI destination = new AnyURIValue(args[1].itemAt(0).getStringValue()).toXmldbURI();
        if (getSignature().getArgumentCount() == 3) {
            XmldbURI doc = new AnyURIValue(args[2].itemAt(0).getStringValue()).toXmldbURI();
            try {
                Resource resource = collection.getResource(doc.toString());
                if (resource == null) {
                    logger.error( "Resource " + doc + " not found");
                    logger.info("Exiting " + XMLDBModule.PREFIX + ":" + getName().getLocalName());
                    throw new XPathException(this, "Resource " + doc + " not found");
                }
                CollectionManagementServiceImpl service = (CollectionManagementServiceImpl) collection.getService("CollectionManagementService", "1.0");
                service.moveResource(doc, destination, null);
            } catch (XMLDBException e) {
                logger.error(e.getMessage());
                logger.info("Exiting " + XMLDBModule.PREFIX + ":" + getName().getLocalName());
                throw new XPathException(this, "XMLDB exception caught: " + e.getMessage(), e);
            }
        } else {
            try {
                CollectionManagementServiceImpl service = (CollectionManagementServiceImpl) collection.getService("CollectionManagementService", "1.0");
                service.move(XmldbURI.xmldbUriFor(collection.getName()),
                        destination, null);

            } catch (XMLDBException e) {
                logger.error(e.getMessage());
                logger.info("Exiting " + XMLDBModule.PREFIX + ":" + getName().getLocalName());
                throw new XPathException(this, "Cannot move collection: " + e.getMessage(), e);

            } catch (URISyntaxException e) {
                logger.error(e.getMessage());
                logger.info("Exiting " + XMLDBModule.PREFIX + ":" + getName().getLocalName());
                throw new XPathException(this, "URI exception: " + e.getMessage(), e);
            }
        }
        logger.info("Exiting " + XMLDBModule.PREFIX + ":" + getName().getLocalName());
        return Sequence.EMPTY_SEQUENCE;
    }
}
