/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
package org.exist.collections.triggers;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;

/**
 * Defines the base interface for collection triggers. Triggers are registered through the
 * collection configuration file, called "collection.xconf", which should be
 * stored in the corresponding database collection. If a collection configuration file is
 * found in the collection, it will be parsed and any triggers will be created and configured.
 * The {@link org.exist.collections.triggers.XQueryTrigger#configure(DBBroker, Txn, Collection, Map)
 * configure} method is called once on each trigger.
 * 
 * Triggers listen to events. Currently, there are five events to which triggers may be
 * attached:
 * 
 * <table border="0" summary="events triggers can be attached to">
 * 	<tr>
 * 		<td>{@link #STORE_DOCUMENT_EVENT}</td>
 * 		<td>Fired, if a new document is inserted into the collection.</td>
 * 	</tr>
 * 	<tr>
 * 		<td>{@link #UPDATE_DOCUMENT_EVENT}</td>
 * 		<td>Fired, whenever an existing document is updated, i.e. replaced
 * 		with a new version.</td>
 * 	</tr>
 * 	<tr>
 * 		<td>{@link #REMOVE_DOCUMENT_EVENT}</td>
 * 		<td>Fired, whenever a document is removed from the collection.</td>
 * 	</tr>
 * <tr>
 *      <td>{@link #RENAME_COLLECTION_EVENT}</td>
 *      <td>Fired, before a collection is renamed.</td>
 *  </tr>
 *  <tr>
 *      <td>{@link #CREATE_COLLECTION_EVENT}</td>
 *      <td>Fired, before a new collection is created.</td>
 *  </tr>
 *
 * </table>
 * 
 * The document-related events are handled by the sub-interface {@link org.exist.collections.triggers.DocumentTrigger},
 * collection-related events are handled by {@link org.exist.collections.triggers.CollectionTrigger}.
 * 
 * The collection configuration file looks as follows:
 * 
 * <pre>
 * &lt;?xml version="1.0" encoding="ISO-8859-1"?&gt;
 * &lt;exist:collection xmlns:exist="http://exist-db.org/collection-config/1.0"&gt;
 *	&lt;exist:triggers&gt;
 *		&lt;exist:trigger event="store"
 *		class="fully qualified classname of the trigger"&gt;
 *			&lt;exist:parameter name="parameter-name"
 *				value="parameter-value"/&gt;
 *		&lt;/exist:trigger&gt;
 *	&lt;/exist:triggers&gt;
 * &lt;/exist:collection&gt;
 * </pre>
 * 
 * @author wolf
 * @see org.exist.collections.triggers.DocumentTrigger DocumentTrigger
 */
public interface Trigger {
    
    public static final Logger LOG = LogManager.getLogger(Trigger.class);

    public final static int STORE_DOCUMENT_EVENT = 0;
    public final static int CREATE_COLLECTION_EVENT = 1;

    public final static int UPDATE_DOCUMENT_EVENT = 2;
    public final static int UPDATE_COLLECTION_EVENT = 3;

    public final static int RENAME_DOCUMENT_EVENT = 4;
    public final static int RENAME_COLLECTION_EVENT = 5;

    public final static int MOVE_DOCUMENT_EVENT = 6;
    public final static int MOVE_COLLECTION_EVENT = 7;

    public final static int REMOVE_DOCUMENT_EVENT = 8;
    public final static int REMOVE_COLLECTION_EVENT = 9;

    public final static String[] OLD_EVENTS = { "STORE", "CREATE-COLLECTION", "UPDATE", "UPDATE-COLLECTION", "RENAME-DOCUMENT", "RENAME-COLLECTION", "MOVE-DOCUMENT", "MOVE-COLLECTION", "REMOVE", "DELETE-COLLECTION" };

    /**
     * The configure method is called once whenever the collection configuration
     * is loaded. Use it to initialize the trigger, probably by looking at the
     * parameters.
     * 
     * @param broker
     *            the database instance used to load the collection
     *            configuration. The broker object is required for all database
     *            actions. Please note: the broker instance used for
     *            configuration is probably different from the one passed to the
     *            prepare method. Don't store the broker object in your class.
     * @param transaction the tnx transaction
     * @param parent
     *            the collection to which this trigger belongs.
     * @param parameters
     *            a Map containing any key/value parameters defined in the
     *            configuration file.
     * @throws TriggerException if the trigger cannot be initialized.
     */
    public void configure(DBBroker broker, Txn transaction, Collection parent, Map<String, List<? extends Object>> parameters) throws TriggerException;
}
