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
package org.exist.config.mapping;

import static org.junit.Assert.*;

import java.io.InputStream;

import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ConfigurableTest {

	String config1 = "" +
			"<instance xmlns='http://exist-db.org/Configuration'>" +
				"<mappedConfig name='A' version='1'>" +
					"<subconfig key='1' secret='secret1'/>"+
					//XXX: "<subconfig key='2' secret='secret2'/>"+
				"</mappedConfig> " +
			"</instance>";
	
	@Test
	public void simple() throws Exception {
		InputStream is = new UnsynchronizedByteArrayInputStream(config1.getBytes(UTF_8));
        
        Configuration config = Configurator.parse(is);
        
        ConfigurableObject object = new ConfigurableObject(config);
        
        assertNotNull(object.subclasses);
        
        assertEquals("A", object.subclasses.name);
        assertEquals("1", object.subclasses.version);

        assertEquals(1, object.subclasses.subconfs.size());
        //XXX: assertEquals(2, object.subclasses.subconfs.size());

        assertEquals("1", object.subclasses.subconfs.getFirst().getKey());
        assertEquals("secret1", object.subclasses.subconfs.getFirst().getSecret());

        //XXX: assertEquals("2", object.subclasses.subconfs.get(1).getKey());
        //XXX: assertEquals("secret2", object.subclasses.subconfs.get(1).getSecret());
	}
}
