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
package org.exist.xquery.functions.securitymanager;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.security.Account;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class UMaskFunction extends BasicFunction {
    
    private final static QName qnGetUMask = new QName("get-umask", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnSetUMask = new QName("set-umask", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);

    
    public final static FunctionSignature FNS_GET_UMASK = new FunctionSignature(
        qnGetUMask,
        "Gets the umask of a Users Account.",
        new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The username of the account to retrieve the umask for.")
        },
        new FunctionReturnSequenceType(Type.INT, Cardinality.ZERO_OR_MORE, "The umask of the users account expressed as an integer")
    );
    
    public final static FunctionSignature FNS_SET_UMASK = new FunctionSignature(
        qnSetUMask,
        "Sets the umask of a Users Account.",
        new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The username of the account to set the umask for."),
            new FunctionParameterSequenceType("umask", Type.INT, Cardinality.EXACTLY_ONE, "The umask to set as an integer.")
        },
        new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
    );
    
    public UMaskFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final DBBroker broker = getContext().getBroker();
        final Subject currentUser = broker.getCurrentSubject();
        if(currentUser.getName().equals(SecurityManager.GUEST_USER)) {
            throw new XPathException(this, "You must be an authenticated user");
        }
        
        final String username = args[0].getStringValue();
        
        if(isCalledAs(qnGetUMask.getLocalPart())) {
            return getUMask(broker, username);
        } else if(isCalledAs(qnSetUMask.getLocalPart())) {
            final int umask = ((IntegerValue)args[1].itemAt(0)).getInt();
            setUMask(broker, currentUser, username, umask);
            return Sequence.EMPTY_SEQUENCE;
        } else {
            throw new XPathException(this, "Unknown function");
        }
    }
    
    private IntegerValue getUMask(final DBBroker broker, final String username) {
       final SecurityManager securityManager = broker.getBrokerPool().getSecurityManager();
       final Account account = securityManager.getAccount(username);
       return new IntegerValue(this, account.getUserMask());
    }
    
    private void setUMask(final DBBroker broker, final Subject currentUser, final String username, final int umask) throws XPathException {
        if(!currentUser.hasDbaRole() && !currentUser.getUsername().equals(username)) {
            throw new XPathException(this, new PermissionDeniedException("You must have suitable access rights to set the users umask."));
        }
        
        final SecurityManager securityManager = broker.getBrokerPool().getSecurityManager();
        final Account account = securityManager.getAccount(username);
        
        account.setUserMask(umask);
        
        try {
            securityManager.updateAccount(account);
        } catch(final PermissionDeniedException | EXistException pde) {
            throw new XPathException(this, pde);
        }
    }
}
