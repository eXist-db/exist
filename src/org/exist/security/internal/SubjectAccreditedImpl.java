/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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
 *  $Id$
 */
package org.exist.security.internal;

import org.exist.security.AbstractSubject;
import org.exist.security.AbstractAccount;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SubjectAccreditedImpl extends AbstractSubject {

	private final Object letterOfCredit;
	
	/**
	 * 
	 * @param account
	 * @param letterOfCredit the object the prove authentication
	 */
	public SubjectAccreditedImpl(AbstractAccount account, Object letterOfCredit) {
		super(account);
		
		this.letterOfCredit = letterOfCredit;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Subject#authenticate(java.lang.Object)
	 */
	@Override
	public boolean authenticate(Object credentials) {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.security.Subject#isAuthenticated()
	 */
	@Override
	public boolean isAuthenticated() {
		return (
			letterOfCredit != null 
			&& account.getId() != account.getRealm().getSecurityManager().getGuestSubject().getId()
		);
	}

	@Override
	public boolean isExternallyAuthenticated() {
		return !(letterOfCredit instanceof SecurityManagerImpl);
	}
}