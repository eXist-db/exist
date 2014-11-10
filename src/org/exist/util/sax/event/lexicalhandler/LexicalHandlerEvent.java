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

import org.exist.util.sax.event.SAXEvent;
import org.xml.sax.ext.LexicalHandler;

/**
 * LexicalHandlerEvent and it's subclasses provide an
 * Object-Oriented representation of SAX
 * LexicalHandler events
 *
 * These classes can for example be used for recording events
 * in a queue and then applying them at a later
 * date to a ContentHandler
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public interface LexicalHandlerEvent extends SAXEvent<LexicalHandler> {
}
