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
package org.exist.xmldb;

import java.util.Date;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;


/**
 * Extends the {@link org.xmldb.api.modules.CollectionManagementService}
 * interface with extensions specific to eXist, in particular moving and copying
 * collections and resources.
 *
 * @author wolf
 */
public interface EXistCollectionManagementService extends CollectionManagementService {

    /**
     * @deprecated Use XmldbURI version instead
     */
    void move(String collection, String destination, String newName) throws XMLDBException;

    /**
     * @deprecated Use XmldbURI version instead
     */
    void moveResource(String resourcePath, String destinationPath, String newName) throws XMLDBException;

    /**
     * @deprecated Use XmldbURI version instead
     */
    void copyResource(String resourcePath, String destinationPath, String newName) throws XMLDBException;

    /**
     * @deprecated Use XmldbURI version instead
     */
    void copy(String collection, String destination, String newName) throws XMLDBException;

    /**
     * @deprecated Use XmldbURI version instead
     */
    @Deprecated
    Collection createCollection(String collName, Date created) throws XMLDBException;

    void move(XmldbURI collection, XmldbURI destination, XmldbURI newName) throws XMLDBException;

    void moveResource(XmldbURI resourcePath, XmldbURI destinationPath, XmldbURI newName) throws XMLDBException;

    void copyResource(XmldbURI resourcePath, XmldbURI destinationPath, XmldbURI newName) throws XMLDBException;

    void copy(XmldbURI collection, XmldbURI destination, XmldbURI newName) throws XMLDBException;

    Collection createCollection(XmldbURI collName, Date created) throws XMLDBException;

    /**
     * @deprecated Use XmldbURI version instead
     */
    @Deprecated
    @Override
    Collection createCollection(String collName) throws XMLDBException;

    Collection createCollection(XmldbURI collName) throws XMLDBException;

    /**
     * @deprecated Use XmldbURI version instead
     */
    @Override
    @Deprecated
    void removeCollection(String collName) throws XMLDBException;

    void removeCollection(XmldbURI collName) throws XMLDBException;

    void runCommand(String[] params) throws XMLDBException;
}
