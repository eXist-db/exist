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

import java.util.Set;
import org.exist.dom.QName;
import org.exist.security.AXSchemaType;
import org.exist.security.EXistSchemaType;
import org.exist.security.Principal;
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
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class GetPrincipalMetadataFunction extends BasicFunction {

    public final static SchemaType[] GROUP_METADATA_KEYS = {
        AXSchemaType.LANGUAGE,
        AXSchemaType.EMAIL,
        EXistSchemaType.DESCRIPTION
    };
    
    private final static QName qnGetAccountMetadataKeys = new QName("get-account-metadata-keys", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnGetAccountMetadata = new QName("get-account-metadata", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);

    private final static QName qnGetGroupMetadataKeys = new QName("get-group-metadata-keys", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnGetGroupMetadata = new QName("get-group-metadata", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);

    
    public final static FunctionSignature FNS_GET_ALL_ACCOUNT_METADATA_KEYS = new FunctionSignature(
        qnGetAccountMetadataKeys,
        "Gets a sequence of the metadata attribute keys that may be used for an account.",
        null,
        new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ZERO_OR_MORE, "The fully qualified metadata attribute key names")
    );
    
    public final static FunctionSignature FNS_GET_ACCOUNT_METADATA_KEYS = new FunctionSignature(
        qnGetAccountMetadataKeys,
        "Gets a sequence of the metadata attribute keys present for an account",
        new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The username of the account to retrieve metadata from.")
        },
        new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ZERO_OR_MORE, "The fully qualified metadata attribute key names")
    );
    
    public final static FunctionSignature FNS_GET_ACCOUNT_METADATA = new FunctionSignature(
        qnGetAccountMetadata,
        "Gets a metadata attribute value for an account",
        new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The username of the account to retrieve metadata from."),
            new FunctionParameterSequenceType("attribute", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The fully qualified metadata attribute key name")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "The metadata value")
    );

    public final static FunctionSignature FNS_GET_ALL_GROUP_METADATA_KEYS = new FunctionSignature(
        qnGetGroupMetadataKeys,
        "Gets a sequence of the metadata attribute keys that may be used for a group.",
        null,
        new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ZERO_OR_MORE, "The fully qualified metadata attribute key names")
    );
    public final static FunctionSignature FNS_GET_GROUP_METADATA_KEYS = new FunctionSignature(
        qnGetGroupMetadataKeys,
        "Gets a sequence of the metadata attribute keys present for a group",
        new SequenceType[] {
            new FunctionParameterSequenceType("group-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the group to retrieve metadata from.")
        },
        new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ZERO_OR_MORE, "The fully qualified metadata attribute key names")
    );
    public final static FunctionSignature FNS_GET_GROUP_METADATA = new FunctionSignature(
        qnGetGroupMetadata,
        "Gets a metadata attribute value for a group",
        new SequenceType[] {
            new FunctionParameterSequenceType("group-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the group to retrieve metadata from."),
            new FunctionParameterSequenceType("attribute", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The fully qualified metadata attribute key name")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "The metadata value")
    );


    public GetPrincipalMetadataFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        Sequence result = Sequence.EMPTY_SEQUENCE;
        
        final DBBroker broker = getContext().getBroker();
        final Subject currentUser = broker.getCurrentSubject();

        if(args.length == 0) {
            if(isCalledAs(qnGetAccountMetadataKeys.getLocalPart())) {
                result = getAllAccountMetadataKeys();
            } else if(isCalledAs(qnGetGroupMetadataKeys.getLocalPart())) {
                result = getAllGroupMetadataKeys();
            } else {
                throw new XPathException("Unknown function");
            }
        } else {
            final SecurityManager securityManager = broker.getBrokerPool().getSecurityManager();
            final String strPrincipal = args[0].getStringValue();
            final Principal principal;
            if(isCalledAs(qnGetAccountMetadataKeys.getLocalPart()) || isCalledAs(qnGetAccountMetadata.getLocalPart())) {
                if(!currentUser.hasDbaRole() && !currentUser.getUsername().equals(strPrincipal)) {
                    throw new XPathException("You must be a DBA to retrieve metadata about other users, otherwise you may only retrieve metadata about yourself.");
                }
                principal = securityManager.getAccount(strPrincipal);
            } else if(isCalledAs(qnGetGroupMetadataKeys.getLocalPart()) || isCalledAs(qnGetGroupMetadata.getLocalPart())) {
                if(!currentUser.hasDbaRole() && !currentUser.hasGroup(strPrincipal)) {
                    throw new XPathException("You must be a DBA to retrieve metadata about other groups, otherwise you may only retrieve metadata about groups you are a member of.");
                }
                principal = securityManager.getGroup(strPrincipal);
            } else {
                throw new XPathException("Unknown function");
            }

            if(principal == null) {
                result = Sequence.EMPTY_SEQUENCE;
            } else {
                if (isCalledAs(qnGetAccountMetadataKeys.getLocalPart()) || isCalledAs(qnGetGroupMetadataKeys.getLocalPart())) {
                    result = getPrincipalMetadataKeys(principal);
                } else if (isCalledAs(qnGetAccountMetadata.getLocalPart()) || isCalledAs(qnGetGroupMetadata.getLocalPart())) {
                    final String metadataAttributeNamespace = args[1].getStringValue();
                    result = getPrincipalMetadata(principal, metadataAttributeNamespace);
                } else {
                    throw new XPathException("Unknown function");
                }
            }
        }
        
        return result;
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
    
    private Sequence getAllGroupMetadataKeys() throws XPathException {
        final ValueSequence result = new ValueSequence();   
        for(final SchemaType GROUP_METADATA_KEY : GROUP_METADATA_KEYS) {
            result.add(new AnyURIValue(GROUP_METADATA_KEY.getNamespace()));
        }
        return result;
    }
    
    private Sequence getPrincipalMetadata(final Principal principal, final String metadataAttributeNamespace) {
        
        final AXSchemaType axSchemaType = AXSchemaType.valueOfNamespace(metadataAttributeNamespace);
        String metadataValue = null;
        if(axSchemaType != null) {
            metadataValue = principal.getMetadataValue(axSchemaType);
        } else {
            final EXistSchemaType exSchemaType = EXistSchemaType.valueOfNamespace(metadataAttributeNamespace);
            if(exSchemaType != null) {
                metadataValue = principal.getMetadataValue(exSchemaType);
            }
        }

        if(metadataValue == null || metadataValue.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        } else {
            return new StringValue(metadataValue);
        }
    }

    private Sequence getPrincipalMetadataKeys(final Principal principal) throws XPathException {
        
        final Set<SchemaType> metadataKeys = principal.getMetadataKeys();
        final Sequence seq = new ValueSequence(metadataKeys.size());
        for(final SchemaType schemaType : metadataKeys) {
            seq.add(new AnyURIValue(schemaType.getNamespace()));
        }
        return seq;
    }
}