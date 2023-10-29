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

import org.exist.config.ConfigurationException;
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
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class AccountStatusFunction extends BasicFunction {

    private final static QName qnIsAccountEnabled = new QName("is-account-enabled", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnSetAccountEnabled = new QName("set-account-enabled", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    
    public final static FunctionSignature FNS_IS_ACCOUNT_ENABLED = new FunctionSignature(
        qnIsAccountEnabled,
        "Determines whether a user account is enabled. You must be a DBA, or you must be enquiring about your own user account.",
        new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The username of the account to check the status for.")
        },
        new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the account is enabled, false otherwise.")
    );
    
    public final static FunctionSignature FNS_SET_ACCOUNT_ENABLED = new FunctionSignature(
        qnSetAccountEnabled,
        "Enabled or disables a users account. You must be a DBA to enable or disable an account.",
        new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The username of the account to enable or disable."),
            new FunctionParameterSequenceType("enabled", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true to enable the account, false to disable the account.")
        },
        new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
    );
    
    public AccountStatusFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }
    
    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final DBBroker broker = getContext().getBroker();
        final Subject currentUser = broker.getCurrentSubject();
        final SecurityManager securityManager = broker.getBrokerPool().getSecurityManager();
        
        final String username = args[0].getStringValue();
        
        if(isCalledAs(qnIsAccountEnabled.getLocalPart())) {
            if(!currentUser.hasDbaRole() && !currentUser.getName().equals(username)) {
                throw new XPathException(this, "You must be a DBA or be enquiring about your own account!");
            }
            final Account account = securityManager.getAccount(username);

            return (account==null) ? BooleanValue.FALSE
                                   : new BooleanValue(this, account.isEnabled());

        } else if(isCalledAs(qnSetAccountEnabled.getLocalPart())) {
            if(!currentUser.hasDbaRole()) {
                throw new XPathException(this, "You must be a DBA to change the status of an account!");
            }
            
            final boolean enable = args[1].effectiveBooleanValue();
            
            final Account account = securityManager.getAccount(username);
            account.setEnabled(enable);
            
            try {
                account.save(broker);
                return Sequence.EMPTY_SEQUENCE;
            } catch(final ConfigurationException | PermissionDeniedException ce) {
                throw new XPathException(this, ce.getMessage(), ce);
            }
        } else {
            throw new XPathException(this, "Unknown function");
        }
    }
    
}
