/*
 *  eXist SecurityManager Module Extension
 *  Copyright (C) 2010 Adam Retter <adam.rettter@googlemail.com>
 *  www.adamretter.co.uk
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
package org.exist.xquery.functions.securitymanager;

import java.util.Set;
import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


/**
 *
 * @author Adam Retter <adam@googlemail.com>
 */
public class SetAccountMetadataFunction extends BasicFunction {

    private final static QName qnSetAccountMetadata = new QName("set-account-metadata", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            qnSetAccountMetadata,
            "Sets a metadata attribute value for an account",
            new SequenceType[] {
                new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The username of the account to set metadata for."),
                new FunctionParameterSequenceType("attribute", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The metadata attribute namespace as defined by axschema.org"),
                new FunctionParameterSequenceType("value", Type.STRING, Cardinality.EXACTLY_ONE, "The metadata value")
            },
            new SequenceType(Type.EMPTY, Cardinality.ZERO)
        )
    };


    public SetAccountMetadataFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        DBBroker broker = getContext().getBroker();
        Subject currentUser = broker.getSubject();
        if(currentUser.getName().equals(SecurityManager.GUEST_USER)) {
            throw new XPathException("You must be an authenticated user");
        }

        final String username = args[0].getStringValue();

        if(!currentUser.hasDbaRole() && !currentUser.getUsername().equals(username)) {
            throw new XPathException(new PermissionDeniedException("You must have suitable access rights to modify the users metadata."));
        }
        
        if(isCalledAs(qnSetAccountMetadata.getLocalName())) {
            final String metadataAttributeNamespace = args[1].getStringValue();
            final String value = args[2].getStringValue();
            
            setAccountMetadata(broker, currentUser, username, metadataAttributeNamespace, value);
        } else {
            throw new XPathException("Unknown function");
        }
        
        return Sequence.EMPTY_SEQUENCE;
    }

    private void setAccountMetadata(DBBroker broker, Subject currentUser, String username, String metadataAttributeNamespace, String value) throws XPathException {
        final SecurityManager securityManager = broker.getBrokerPool().getSecurityManager();
        final Account account = securityManager.getAccount(currentUser, username);
        
        final AXSchemaType axSchemaType = AXSchemaType.valueOfNamespace(metadataAttributeNamespace);
        if(axSchemaType == null) {
            throw new XPathException("Unknown axschema.org attribute: " + metadataAttributeNamespace);
        }
        
        
        account.setMetadataValue(axSchemaType, value);
        
        try {
            securityManager.updateAccount(account);
        } catch(PermissionDeniedException pde) {
            throw new XPathException(pde.getMessage(), pde);
        } catch(EXistException ee) {
            throw new XPathException(ee.getMessage(), ee);
        }
    }
}