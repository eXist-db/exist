/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2014 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.util.sax.event.lexicalhandler;

import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class StartDTD implements LexicalHandlerEvent {
    public final String name;
    public final String publicId;
    public final String systemId;

    public StartDTD(final String name, final String publicId, final String systemId) {
        this.name = name;
        this.publicId = publicId;
        this.systemId = systemId;
    }

    @Override
    public void apply(final LexicalHandler handler) throws SAXException {
        handler.startDTD(name, publicId, systemId);
    }
}
