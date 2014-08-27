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
package org.exist.storage.md;

import java.util.ArrayList;
import java.util.List;

import org.exist.collections.Collection;
import org.exist.dom.DocumentAtExist;
import org.exist.xmldb.XmldbURI;

import com.eaio.uuid.UUID;
import com.sleepycat.persist.EntityCursor;
import com.sleepycat.persist.model.DeleteAction;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.SecondaryKey;

import static com.sleepycat.persist.model.Relationship.ONE_TO_ONE;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
@Entity//(version=1) //http://docs.oracle.com/cd/E17076_02/html/java/com/sleepycat/persist/evolve/Conversion.html
public class MetasImpl implements Metas {
	
	@PrimaryKey private String uuid;
	
	@SecondaryKey(relate=ONE_TO_ONE,
				  onRelatedEntityDelete=DeleteAction.CASCADE) 
	protected String uri;

	@SuppressWarnings("unused")
	private MetasImpl() {}

	protected MetasImpl(DocumentAtExist doc) {
		setURL(doc.getURI().toString());
		
		if (doc.getUUID() == null)
			uuid = (new UUID()).toString();
		else
			uuid = doc.getUUID().toString();
	}
	
	protected MetasImpl(Collection col) {
		setURL(col.getURI().toString());
		uuid = (new UUID()).toString();
	}

	protected MetasImpl(XmldbURI uri) {
		this.uri = uri.toString();
		uuid = (new UUID()).toString();
	}

	protected MetasImpl(String uri, String uuid) {
		this.uri = uri;
		this.uuid = uuid;
	}

	public String getUUID() {
		return uuid;
	}

    public String getURI() {
        return uri;
    }

    public Meta put(String key, Object value) {
		MetaImpl m = (MetaImpl)get(key);
		if (m == null)
			return MetaDataImpl.instance.addMeta(this, key, value);
		
		else {
			m.setValue(value);

			MetaDataImpl.instance.addMeta(m);
		}
		MetaDataImpl.instance.indexMetas(this);
		
		return m;
	}

	public Meta get(String key) {
		return MetaDataImpl.instance.getMeta(this, key);
	}

    public void delete(String key) {
        MetaDataImpl.instance.delMeta(uuid, key);
    }

    protected void setURL(String url) {
		this.uri = url;
	}

	public List<Meta> metas() {
		List<Meta> metas = new ArrayList<Meta>();
		
		EntityCursor<MetaImpl> sub = MetaDataImpl.instance.getMetaKeys(this);
		try {
			
			for (MetaImpl m : sub) {
				metas.add(m);
			}

		} finally {
			sub.close();
		}
		
		return metas;
	}

//	public EntityCursor<MetaImpl> keys() {
//		return MetaDataImpl._.getMetaKeys(this);
//	}

	public void delete() {
	    MetaDataImpl.instance.delMetas(this);
	}

	public void restore(String uuid, String key, String value) {
		MetaDataImpl.instance._addMeta(this, uuid, key, value);
	}
}