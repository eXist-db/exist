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
package org.exist.debuggee;

import java.net.InetSocketAddress;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.exist.xquery.Expression;
import org.exist.xquery.XQueryContext;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggeeJointImpl implements DebuggeeJoint {
	
	public DebuggeeJointImpl() {
	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.DebuggeeJoint#expressionEnd(org.exist.xquery.Expression)
	 */
	public void expressionEnd(Expression expr) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.DebuggeeJoint#expressionStart(org.exist.xquery.Expression)
	 */
	public void expressionStart(Expression expr) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.debuggee.DebuggeeJoint#getContext()
	 */
	public XQueryContext getContext() {
		// TODO Auto-generated method stub
		return null;
	}

}
