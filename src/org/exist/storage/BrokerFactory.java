/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001 Wolfgang M. Meier
 * wolfgang@exist-db.org
 * http://exist.sourceforge.net
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
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.exist.storage;

import org.exist.EXistException;
import org.exist.util.Configuration;

public class BrokerFactory {
	
	public static final String PROPERTY_DATABASE = "database";

	public static DBBroker getInstance(BrokerPool pool, Configuration conf) throws EXistException {
		String dbName = (String) conf.getProperty(PROPERTY_DATABASE);
		if (dbName == null)
			throw new RuntimeException("no database defined");
		if (dbName.equalsIgnoreCase("NATIVE"))
			return new NativeBroker(pool, conf);
        else if (dbName.equalsIgnoreCase("NATIVE_CLUSTER"))
			return new NativeClusterBroker(pool, conf);
		else
			throw new EXistException("no database backend found for " + dbName);
	}
}
