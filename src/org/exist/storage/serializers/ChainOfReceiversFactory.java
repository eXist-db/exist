/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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
 */
package org.exist.storage.serializers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * List of {@link ChainOfReceivers}.
 */
public class ChainOfReceiversFactory {

    private final static Logger LOG = LogManager.getLogger(ChainOfReceiversFactory.class);

    private ChainOfReceivers first = null;
    private ChainOfReceivers last = null;

    public ChainOfReceiversFactory(List<String> classes) {

        ChainOfReceivers listener;
        for (final String className : classes) {
            try {
                final Class<?> listenerClass = Class.forName(className);
                if (ChainOfReceivers.class.isAssignableFrom(listenerClass)) {
                    listener = (ChainOfReceivers) listenerClass.newInstance();
                    if (first == null) {
                        first = listener;
                        last = listener;
                    } else {
                        last.setNextInChain(listener);
                        last = listener;
                    }
                } else {
                    LOG.error("Failed to instantiate class " + listenerClass.getName() +
                            ": it is not a subclass of ChainOfReceivers");
                }
            } catch (final Exception e) {
                LOG.error("An exception was caught while trying to instantiate a chain of receivers: " + e.getMessage(), e);
            }
        }
    }

    public ChainOfReceivers getFirst() {
        return first;
    }

    public ChainOfReceivers getLast() {
        return last;
    }
}