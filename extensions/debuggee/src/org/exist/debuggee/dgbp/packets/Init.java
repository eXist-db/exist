/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 *  $Id:$
 */
package org.exist.debuggee.dgbp.packets;

import java.nio.ByteBuffer;

import org.exist.debuggee.dgbp.DGBPPacket;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Init extends DGBPPacket {

	String init_message = "<init " +
		"appid=\"7035\" " +
		"idekey=\"1\" " +
		"session=\"1\" " +
		"thread=\"1\" " +
		"parent=\"1\" " +
		"language=\"XQuery\" " +
		"protocol_version=\"1.0\" " +
		"fileuri=\"file:///home/dmitriy/projects/eXist-svn/trunk/eXist/webapp/admin/admin.xql\"></init>";

	public int getLength() {
		return init_message.length();
	}

	public byte[] toBytes() {
		return init_message.getBytes();
	}
	
	public String toString() {
		return init_message;
	}
}
