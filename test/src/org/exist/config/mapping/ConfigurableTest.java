/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2010 The eXist Project
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id: ConfigurableTest.java 13769 2011-02-12 17:47:00Z shabanovd $
 */
package org.exist.config.mapping;

import static org.junit.Assert.*;

import java.io.InputStream;

import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.util.io.FastByteArrayInputStream;
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
		InputStream is = new FastByteArrayInputStream(config1.getBytes(UTF_8));
        
        Configuration config = Configurator.parse(is);
        
        ConfigurableObject object = new ConfigurableObject(config);
        
        assertNotNull(object.subclasses);
        
        assertEquals("A", object.subclasses.name);
        assertEquals("1", object.subclasses.version);

        assertEquals(1, object.subclasses.subconfs.size());
        //XXX: assertEquals(2, object.subclasses.subconfs.size());

        assertEquals("1", object.subclasses.subconfs.get(0).getKey());
        assertEquals("secret1", object.subclasses.subconfs.get(0).getSecret());

        //XXX: assertEquals("2", object.subclasses.subconfs.get(1).getKey());
        //XXX: assertEquals("secret2", object.subclasses.subconfs.get(1).getSecret());
	}
}
