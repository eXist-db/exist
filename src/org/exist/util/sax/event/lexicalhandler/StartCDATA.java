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
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class StartCDATA implements LexicalHandlerEvent {

    public final static StartCDATA INSTANCE = new StartCDATA();

    /**
     * Constructor is private because this class
     * carries no state and so is more efficient for
     * re-use as a Singleton {@see INSTANCE}
     */
    private StartCDATA() {}

    @Override
    public void apply(final LexicalHandler handler) throws SAXException {
        handler.startCDATA();
    }
}
