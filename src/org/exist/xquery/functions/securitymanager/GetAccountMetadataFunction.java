/*
 *  eXist SecurityManager Module Extension
 *  Copyright (C) 2010 Adam Retter <adam@existsolutions.com>
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
import org.exist.dom.QName;
import org.exist.security.AXSchemaType;
import org.exist.security.Account;
import org.exist.security.EXistSchemaType;
import org.exist.security.SchemaType;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;


/**
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class GetAccountMetadataFunction extends BasicFunction {

    private final static QName qnGetAccountMetadataKeys = new QName("get-account-metadata-keys", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnGetAccountMetadata = new QName("get-account-metadata", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            qnGetAccountMetadataKeys,
            "Gets a sequence of the metadata attribute keys that may be used for an account.",
            null,
            new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ZERO_OR_MORE, "The fully qualified metadata attribute key names")
        ),
        new FunctionSignature(
            qnGetAccountMetadataKeys,
            "Gets a sequence of the metadata attribute keys present for an account",
            new SequenceType[] {
                new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The username of the account to retrieve metadata from.")
            },
            new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ZERO_OR_MORE, "The fully qualified metadata attribute key names")
        ),
        new FunctionSignature(
            qnGetAccountMetadata,
            "Gets a metadata attribute value for an account",
            new SequenceType[] {
                new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The username of the account to retrieve metadata from."),
                new FunctionParameterSequenceType("attribute", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The fully qualified metadata attribute key name")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "The metadata value")
        )
    };


    public GetAccountMetadataFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        final DBBroker broker = getContext().getBroker();
        final Subject currentUser = broker.getSubject();
        if(currentUser.getName().equals(SecurityManager.GUEST_USER)) {
            throw new XPathException("You must be an authenticated user");
        }

        if(isCalledAs(qnGetAccountMetadataKeys.getLocalName()) && args.length == 0) {
            return getAllAccountMetadataKeys();
        } else {
            final String username = args[0].getStringValue();
            
            if(!currentUser.hasDbaRole() && !currentUser.getUsername().equals(username)) {
                throw new XPathException("You must be a DBA to retrieve metadata about other users. Otherwise you may only retrieve metadata about yourself.");
            }
            
            if(isCalledAs(qnGetAccountMetadataKeys.getLocalName())) {
                return getAccountMetadataKeys(broker, username);
            } else if(isCalledAs(qnGetAccountMetadata.getLocalName())) {
                final String metadataAttributeNamespace = args[1].getStringValue();
                return getAccountMetadata(broker, currentUser, username, metadataAttributeNamespace);
            } else {
                throw new XPathException("Unknown function");
            }
        }
    }

    private Sequence getAllAccountMetadataKeys() throws XPathException {
        final ValueSequence result = new ValueSequence();
        for(final AXSchemaType axSchemaType : AXSchemaType.values()) {
            result.add(new AnyURIValue(axSchemaType.getNamespace()));
        }
        for(final EXistSchemaType exSchemaType : EXistSchemaType.values()) {
            result.add(new AnyURIValue(exSchemaType.getNamespace()));
        }
        return result;
    }
    
    private Sequence getAccountMetadata(final DBBroker broker, final Subject currentUser, final String username, final String metadataAttributeNamespace) {
        final SecurityManager securityManager = broker.getBrokerPool().getSecurityManager();
        final Account account = securityManager.getAccount(username);
        final AXSchemaType axSchemaType = AXSchemaType.valueOfNamespace(metadataAttributeNamespace);
        String metadataValue = null;
        if(axSchemaType != null) {
            metadataValue = account.getMetadataValue(axSchemaType);
        }

        if(metadataValue == null || metadataValue.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        } else {
            return new StringValue(metadataValue);
        }
    }

    private Sequence getAccountMetadataKeys(final DBBroker broker, final String username) throws XPathException {
        final SecurityManager securityManager = broker.getBrokerPool().getSecurityManager();
        final Account account = securityManager.getAccount(username);
        
        final Set<SchemaType> metadataKeys = account.getMetadataKeys();
        final Sequence seq = new ValueSequence(metadataKeys.size());
        for(final SchemaType schemaType : metadataKeys) {
            seq.add(new AnyURIValue(schemaType.getNamespace()));
        }

        return seq;
    }
}