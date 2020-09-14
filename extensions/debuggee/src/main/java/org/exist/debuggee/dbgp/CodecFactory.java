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
package org.exist.debuggee.dbgp;

import java.util.HashMap;
import java.util.Map;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class CodecFactory implements ProtocolCodecFactory {

	private Map<IoSession, ProtocolEncoder> encoders = new HashMap<IoSession, ProtocolEncoder>();
	private Map<IoSession, ProtocolDecoder> decoders = new HashMap<IoSession, ProtocolDecoder>();

	public CodecFactory() {
	}
	
	public ProtocolDecoder getDecoder(IoSession ioSession) throws Exception {
		synchronized (decoders) {
			if (decoders.containsKey(ioSession))
				return decoders.get(ioSession);
			
			ProtocolDecoder decoder = new RequestDecoder();
			decoders.put(ioSession, decoder);
			
			return decoder;
		}
	}

	public ProtocolEncoder getEncoder(IoSession ioSession) throws Exception {
		synchronized (encoders) {
			if (encoders.containsKey(ioSession))
				return encoders.get(ioSession);
			
			ProtocolEncoder encoder = new ResponseEncoder();
			encoders.put(ioSession, encoder);
			
			return encoder;
		}
	}

}
