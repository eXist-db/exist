/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xquery.functions.securitymanager;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.security.*;
import org.exist.security.SecurityManager;
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
public class SetPrincipalMetadataFunction extends BasicFunction {

    private final static QName qnSetAccountMetadata = new QName("set-account-metadata", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnSetGroupMetadata = new QName("set-group-metadata", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);

    
    public final static FunctionSignature FNS_SET_ACCOUNT_METADATA = new FunctionSignature(
        qnSetAccountMetadata,
        "Sets a metadata attribute value for an account",
        new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The username of the account to set metadata for."),
            new FunctionParameterSequenceType("attribute", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The metadata attribute key."),
            new FunctionParameterSequenceType("value", Type.STRING, Cardinality.EXACTLY_ONE, "The metadata value,")
        },
        new SequenceType(Type.EMPTY, Cardinality.ZERO)
    );
    
    public final static FunctionSignature FNS_SET_GROUP_METADATA = new FunctionSignature(
        qnSetGroupMetadata,
        "Sets a metadata attribute value for a group",
        new SequenceType[] {
            new FunctionParameterSequenceType("group-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the group to set metadata for."),
            new FunctionParameterSequenceType("attribute", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The metadata attribute key."),
            new FunctionParameterSequenceType("value", Type.STRING, Cardinality.EXACTLY_ONE, "The metadata value,")
        },
        new SequenceType(Type.EMPTY, Cardinality.ZERO)
    );

    public SetPrincipalMetadataFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        final DBBroker broker = getContext().getBroker();
        final Subject currentUser = broker.getCurrentSubject();
        if(currentUser.getName().equals(SecurityManager.GUEST_USER)) {
            throw new XPathException("You must be an authenticated user");
        }

        final SecurityManager securityManager = broker.getBrokerPool().getSecurityManager();
        
        final String strPrincipal = args[0].getStringValue();
        final String metadataAttributeNamespace = args[1].getStringValue();
        final String value = args[2].getStringValue();
            
        final Principal principal;
        if(isCalledAs(qnSetAccountMetadata.getLocalPart())) {
            if(!currentUser.hasDbaRole() && !currentUser.getUsername().equals(strPrincipal)) {
                throw new XPathException(this, new PermissionDeniedException("You must have suitable access rights to modify the users metadata."));
            }
            principal = securityManager.getAccount(strPrincipal);
        } else if(isCalledAs(qnSetGroupMetadata.getLocalPart())) {
            
            //check for a valid group metadata key
            boolean valid = false;
            for(final SchemaType groupMetadataKey : GetPrincipalMetadataFunction.GROUP_METADATA_KEYS) {
                if(groupMetadataKey.getNamespace().equals(metadataAttributeNamespace)) {
                    valid = true;
                    break;
                }
            }
            
            if(!valid) {
                throw new XPathException("The metadata attribute key '" + metadataAttributeNamespace + "' is not valid on a group.");
            }
            
            final Group group = securityManager.getGroup(strPrincipal);
            if(!currentUser.hasDbaRole() && !group.isManager(currentUser)) {
                throw new XPathException(this, new PermissionDeniedException("You must have suitable access rights to modify the groups metadata."));
            }
            principal = group;
        } else {
            throw new XPathException(this, "Unknown function");
        }

        setAccountMetadata(securityManager, principal, metadataAttributeNamespace, value);
        
        return Sequence.EMPTY_SEQUENCE;
    }

    private void setAccountMetadata(final SecurityManager securityManager, final Principal principal, final String metadataAttributeNamespace, final String value) throws XPathException {
        SchemaType schemaType = AXSchemaType.valueOfNamespace(metadataAttributeNamespace);
        if(schemaType == null) {
            schemaType = EXistSchemaType.valueOfNamespace(metadataAttributeNamespace);
        }
        
        if(schemaType == null) {
            throw new XPathException("Unknown metadata attribute key: " + metadataAttributeNamespace);
        }
        
        principal.setMetadataValue(schemaType, value);
        
        try {
            if(principal instanceof Account) {
                securityManager.updateAccount((Account)principal);
            } else if(principal instanceof Group) {
                securityManager.updateGroup((Group)principal);
            }
        } catch(final PermissionDeniedException pde) {
            throw new XPathException(this, pde);
        } catch(final EXistException ee) {
            throw new XPathException(this, ee);
        }
    }
}