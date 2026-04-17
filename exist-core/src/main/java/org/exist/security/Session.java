/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.security;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.util.UUIDGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Session {
	
	private final String id;
	private final Subject subject;
	
	private long lastUse;
	
	public Session(Subject subject) {
		this.id = UUIDGenerator.getUUID();
		this.subject = subject;
		used();

		try {
			BrokerPool.getInstance().getSecurityManager().registerSession(this);
		} catch (final EXistException e) {
			//should not happen
		}
	}
	
	private void used() {
		lastUse = System.currentTimeMillis();
	}
	
	public String getId() {
		used();
		return id;
	}
	
	private final Map<String, Object> properties = new HashMap<>();
	
	public void setProperty(String name, Object value) {
		used();
		properties.put(name, value);
	}

	public Object getProperty(String name) {
		used();
		return properties.get(name);
	}

	public void removeProperty(String name) {
		properties.remove(name);
	}

	public Subject getSubject() {
		used();
		return subject;
	}

	public boolean isValid() {
		return System.currentTimeMillis() - lastUse <= 30_000; //30 seconds
	}
}