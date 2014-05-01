/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.indexing;

import org.apache.log4j.Logger;
import org.exist.dom.AttrImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.CharacterDataImpl;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;

/**
 * Default implementation of a StreamListener. By default forwards all events to
 * the next listener in the chain (if there is any). Overwrite methods to handle events
 * (but don't forget to call the super method as well).
 */
public abstract class AbstractStreamListener implements StreamListener {

    protected final static Logger LOG = Logger.getLogger(AbstractStreamListener.class);
    
    private StreamListener next = null;

    @Override
    public void setNextInChain(StreamListener listener) {
        this.next = listener;
    }

    @Override
    public StreamListener getNextInChain() {
        return next;
    }

    @Override
    public void startElement(Txn transaction, ElementImpl element, NodePath path) {
        if (next != null)
            {next.startElement(transaction, element, path);}
    }

    @Override
    public void attribute(Txn transaction, AttrImpl attrib, NodePath path) {
        if (next != null) {
            next.attribute(transaction, attrib, path);
        }
    }

    @Override
    public void endElement(Txn transaction, ElementImpl element, NodePath path) {
        if (next != null) {
            next.endElement(transaction, element, path);
        }
    }

    @Override
    public void characters(Txn transaction, CharacterDataImpl text, NodePath path) {
        if (next != null) {
            next.characters(transaction, text, path);
        }
    }

    @Override
    public abstract IndexWorker getWorker();
}
