/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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
package org.exist.debugger.xquery;

import org.apache.mina.core.session.IoSession;
import org.exist.debuggee.Debuggee;
import org.exist.debuggee.dbgp.packets.Command;
import org.exist.dom.QName;
import org.exist.storage.BrokerPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class StepOver extends BasicFunction {

	public final static FunctionSignature signatures[] = { 
		new FunctionSignature(
			new QName("step-over", Module.NAMESPACE_URI, Module.PREFIX), 
			"", 
			new SequenceType[] { 
				new FunctionParameterSequenceType(
					"session id", 
					Type.STRING, 
					Cardinality.EXACTLY_ONE, 
					""
				) 
			}, 
			new FunctionReturnSequenceType(
				Type.BOOLEAN, 
				Cardinality.EXACTLY_ONE, 
				""
			)
		)
	};

	public StepOver(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

		try {
			Debuggee dbgr = BrokerPool.getInstance().getDebuggee();
			
			IoSession session = (IoSession) dbgr.getSession(args[0].getStringValue());
			if (session == null) return BooleanValue.FALSE;
			
			Command command = new org.exist.debuggee.dbgp.packets.StepOver(session, "");
			command.exec();
			
			//XXX: make sure it executed
			
			return BooleanValue.TRUE;
			
		} catch (Throwable e) {
			throw new XPathException(this, Module.DEBUG001, e);
		}
	}
}