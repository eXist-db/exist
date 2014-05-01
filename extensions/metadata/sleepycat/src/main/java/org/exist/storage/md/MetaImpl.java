/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012-2013 The eXist Project
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
package org.exist.storage.md;

import org.exist.dom.DocumentImpl;

import com.eaio.uuid.UUID;
import com.sleepycat.persist.model.DeleteAction;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.SecondaryKey;

import static com.sleepycat.persist.model.Relationship.MANY_TO_ONE;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@Entity//(version=1)
public class MetaImpl implements Meta {
	
	@PrimaryKey private String uuid;
	
	@SecondaryKey(relate=MANY_TO_ONE,
				  onRelatedEntityDelete=DeleteAction.CASCADE)
	private String object;
	
	@SecondaryKey(relate=MANY_TO_ONE,
				  onRelatedEntityDelete=DeleteAction.CASCADE)
	private String key;
	
	@SecondaryKey(relate=MANY_TO_ONE,
			  onRelatedEntityDelete=DeleteAction.CASCADE)
	protected String value;
	
	@SuppressWarnings("unused")
	private MetaImpl() {}

	protected MetaImpl(String objectUUID, String k, Object v) {
		this(objectUUID, (new UUID()).toString(), k, v);
	}
	
	protected MetaImpl(String objectUUID, String uuid, String k, Object v) {
		this.uuid = uuid;
		object = objectUUID;
		
		key = k;
		setValue(v);
	}

	public String getUUID() {
		return uuid;
	}

	public String getKey() {
		return key;
	}

	public Object getValue() {
		try {
			DocumentImpl doc = MetaData._.getDocument(value);
			if (doc != null) return doc;
		} catch (Exception e) {
			//LOG
		}
		return value;
	}
	
	public void setValue(Object value) {
		if (value instanceof DocumentImpl) {
			this.value = MetaData._.getMetas((DocumentImpl) value).getUUID();
			//TODO: set link to master doc?
		} else
			this.value = value.toString(); 
	}

	public String getObject() {
		return object;
	}
	
	public void delete() {
	    MetaDataImpl._.delMeta(object, key);
	}
}