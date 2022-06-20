/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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
package org.exist.xquery.functions.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.dom.QName;
import org.exist.util.PatternFactory;
import org.exist.xquery.*;
import org.exist.xquery.functions.AccessUtil;
import org.exist.xquery.functions.inspect.InspectFunction;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;


/**
 * Module function definitions for util module.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author ljo
 * @author Andrzej Taramina (andrzej@chaeron.com)
 * @author <a href="mailto:adam@evolvedbinary.com>Adam Retter</a>
 */
public class UtilModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/util";

    public final static String PREFIX = "util";
    public final static String INCLUSION_DATE = "2004-09-12";
    public final static String RELEASED_IN_VERSION = "pre eXist-1.0";

    public boolean evalDisabled = false;

    public final static FunctionDef[] functions = {
            new FunctionDef(BuiltinFunctions.signatures[0], BuiltinFunctions.class),
            new FunctionDef(BuiltinFunctions.signatures[1], BuiltinFunctions.class),
            new FunctionDef(BuiltinFunctions.signatures[2], BuiltinFunctions.class),
            new FunctionDef(BuiltinFunctions.signatures[3], BuiltinFunctions.class),
            new FunctionDef(BuiltinFunctions.signatures[4], BuiltinFunctions.class),
            new FunctionDef(InspectFunction.SIGNATURE_DEPRECATED, InspectFunction.class),
            new FunctionDef(ModuleInfo.moduleDescriptionSig, ModuleInfo.class),
            new FunctionDef(ModuleInfo.registeredModuleSig, ModuleInfo.class),
            new FunctionDef(ModuleInfo.registeredModulesSig, ModuleInfo.class),
            new FunctionDef(ModuleInfo.mapModuleSig, ModuleInfo.class),
            new FunctionDef(ModuleInfo.unmapModuleSig, ModuleInfo.class),
            new FunctionDef(ModuleInfo.mappedModuleSig, ModuleInfo.class),
            new FunctionDef(ModuleInfo.mappedModulesSig, ModuleInfo.class),
            new FunctionDef(ModuleInfo.moduleInfoSig, ModuleInfo.class),
            new FunctionDef(ModuleInfo.moduleInfoWithURISig, ModuleInfo.class),
            new FunctionDef(Expand.signatures[0], Expand.class),
            new FunctionDef(Expand.signatures[1], Expand.class),
            new FunctionDef(DescribeFunction.signature, DescribeFunction.class),
            new FunctionDef(FunDoctype.signature, FunDoctype.class),
            new FunctionDef(Eval.FS_EVAL[0], Eval.class),
            new FunctionDef(Eval.FS_EVAL[1], Eval.class),
            new FunctionDef(Eval.FS_EVAL[2], Eval.class),
            new FunctionDef(Eval.FS_EVAL_WITH_CONTEXT[0], Eval.class),
            new FunctionDef(Eval.FS_EVAL_WITH_CONTEXT[1], Eval.class),
            new FunctionDef(Eval.FS_EVAL_INLINE[0], Eval.class),
            new FunctionDef(Eval.FS_EVAL_INLINE[1], Eval.class),
            new FunctionDef(Eval.FS_EVAL_ASYNC, Eval.class),

            new FunctionDef(Eval.FS_EVAL_AND_SERIALIZE[0], Eval.class),
            new FunctionDef(Eval.FS_EVAL_AND_SERIALIZE[1], Eval.class),
            new FunctionDef(Eval.FS_EVAL_AND_SERIALIZE[2], Eval.class),
            new FunctionDef(Compile.signatures[0], Compile.class),
            new FunctionDef(Compile.signatures[1], Compile.class),
            new FunctionDef(Compile.signatures[2], Compile.class),
            new FunctionDef(DocumentNameOrId.docIdSignature, DocumentNameOrId.class),
            new FunctionDef(DocumentNameOrId.docNameSignature, DocumentNameOrId.class),
            new FunctionDef(DocumentNameOrId.absoluteResourceIdSignature, DocumentNameOrId.class),
            new FunctionDef(DocumentNameOrId.resourceByAbsoluteIdSignature, DocumentNameOrId.class),
            new FunctionDef(CollectionName.signature, CollectionName.class),
            new FunctionDef(LogFunction.signatures[0], LogFunction.class),
            new FunctionDef(LogFunction.signatures[1], LogFunction.class),
            new FunctionDef(LogFunction.signatures[2], LogFunction.class),
            new FunctionDef(LogFunction.signatures[3], LogFunction.class),
            new FunctionDef(CatchFunction.signature, CatchFunction.class),
            new FunctionDef(ExclusiveLockFunction.signature, ExclusiveLockFunction.class),
            new FunctionDef(SharedLockFunction.signature, SharedLockFunction.class),
            new FunctionDef(Collations.signature, Collations.class),
            new FunctionDef(SystemProperty.FS_AVAILABLE_SYSTEM_PROPERTIES, SystemProperty.class),
            new FunctionDef(SystemProperty.FS_SYSTEM_PROPERTY, SystemProperty.class),
            new FunctionDef(FunctionFunction.signature, FunctionFunction.class),
            new FunctionDef(CallFunction.signature, CallFunction.class),
            new FunctionDef(NodeId.signature, NodeId.class),
            new FunctionDef(GetNodeById.signature, GetNodeById.class),
            new FunctionDef(IndexKeys.signatures[0], IndexKeys.class),
            new FunctionDef(IndexKeys.signatures[1], IndexKeys.class),
            new FunctionDef(IndexKeys.signatures[2], IndexKeys.class),
            new FunctionDef(IndexKeyOccurrences.signatures[0], IndexKeyOccurrences.class),
            new FunctionDef(IndexKeyOccurrences.signatures[1], IndexKeyOccurrences.class),
            new FunctionDef(IndexKeyDocuments.signatures[0], IndexKeyDocuments.class),
            new FunctionDef(IndexKeyDocuments.signatures[1], IndexKeyDocuments.class),
            new FunctionDef(IndexType.signature, IndexType.class),
            new FunctionDef(QNameIndexLookup.signature, QNameIndexLookup.class),
            new FunctionDef(Serialize.signatures[0], Serialize.class),
            new FunctionDef(Serialize.signatures[1], Serialize.class),
            new FunctionDef(BinaryDoc.FS_BINARY_DOC, BinaryDoc.class),
            new FunctionDef(BinaryDoc.FS_BINARY_DOC_AVAILABLE, BinaryDoc.class),
            new FunctionDef(BinaryDoc.FS_IS_BINARY_DOC, BinaryDoc.class),
            new FunctionDef(BinaryDoc.FS_BINARY_DOC_CONTENT_DIGEST, BinaryDoc.class),
            new FunctionDef(BinaryToString.signatures[0], BinaryToString.class),
            new FunctionDef(BinaryToString.signatures[1], BinaryToString.class),
            new FunctionDef(BinaryToString.signatures[2], BinaryToString.class),
            new FunctionDef(BinaryToString.signatures[3], BinaryToString.class),
            new FunctionDef(Profile.signatures[0], Profile.class),
            new FunctionDef(Profile.signatures[1], Profile.class),
            new FunctionDef(PrologFunctions.signatures[0], PrologFunctions.class),
            new FunctionDef(PrologFunctions.signatures[1], PrologFunctions.class),
            new FunctionDef(PrologFunctions.signatures[2], PrologFunctions.class),
            new FunctionDef(PrologFunctions.signatures[3], PrologFunctions.class),
            new FunctionDef(SystemTime.signatures[0], SystemTime.class),
            new FunctionDef(SystemTime.signatures[1], SystemTime.class),
            new FunctionDef(SystemTime.signatures[2], SystemTime.class),
            new FunctionDef(RandomFunction.signatures[0], RandomFunction.class),
            new FunctionDef(RandomFunction.signatures[1], RandomFunction.class),
            new FunctionDef(RandomFunction.signatures[2], RandomFunction.class),
            new FunctionDef(FunUnEscapeURI.signature, FunUnEscapeURI.class),
            new FunctionDef(UUID.signatures[0], UUID.class),
            new FunctionDef(UUID.signatures[1], UUID.class),
            new FunctionDef(DeepCopyFunction.signature, DeepCopyFunction.class),
            new FunctionDef(GetSequenceType.signature, GetSequenceType.class),
            new FunctionDef(Parse.signatures[0], Parse.class),
            new FunctionDef(Parse.signatures[1], Parse.class),
            new FunctionDef(NodeXPath.signature, NodeXPath.class),
            new FunctionDef(Hash.signatures[0], Hash.class),
            new FunctionDef(Hash.signatures[1], Hash.class),
            new FunctionDef(GetFragmentBetween.signature, GetFragmentBetween.class),
            new FunctionDef(BaseConverter.signatures[0], BaseConverter.class),
            new FunctionDef(BaseConverter.signatures[1], BaseConverter.class),
            new FunctionDef(Wait.signatures[0], Wait.class),
            new FunctionDef(Base64Functions.signatures[0], Base64Functions.class),
            new FunctionDef(Base64Functions.signatures[1], Base64Functions.class),
            new FunctionDef(Base64Functions.signatures[2], Base64Functions.class),
            new FunctionDef(BaseConversionFunctions.FNS_INT_TO_OCTAL, BaseConversionFunctions.class),
            new FunctionDef(BaseConversionFunctions.FNS_OCTAL_TO_INT, BaseConversionFunctions.class),
            new FunctionDef(LineNumber.signature, LineNumber.class)
    };

    static {
        Arrays.sort(functions, new FunctionComparator());
    }

    public final static QName EXCEPTION_QNAME = new QName("exception", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);

    public final static QName EXCEPTION_MESSAGE_QNAME = new QName("exception-message", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);

    public final static QName ERROR_CODE_QNAME = new QName("error-code", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);

    private static final Pattern PTN_SYSTEM_PROPERTY_ACCESS = PatternFactory.getInstance().getPattern("systemPropertyAccess\\.([^=\\00])+\\.requires((?:Group)|(?:User))");

    private Map<String, Set<String>> systemPropertyAccessGroups = null;
    private Map<String, Set<String>> systemPropertyAccessUsers = null;

    public UtilModule(final Map<String, List<? extends Object>> parameters) throws XPathException {
        super(functions, parameters, true);

        final List<String> evalDisabledParamList = (List<String>) getParameter("evalDisabled");
        if (evalDisabledParamList != null && !evalDisabledParamList.isEmpty()) {
            final String strEvalDisabled = evalDisabledParamList.get(0);
            if (strEvalDisabled != null) {
                this.evalDisabled = Boolean.parseBoolean(strEvalDisabled);
            }
        }
    }

    @Override
    public void prepare(final XQueryContext context) throws XPathException {
        declareVariable(EXCEPTION_QNAME, null);
        declareVariable(EXCEPTION_MESSAGE_QNAME, null);
        declareVariable(ERROR_CODE_QNAME, null);
    }

    @Override
    public String getDescription() {
        return "A module for various utility extension functions.";
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

    public boolean isEvalDisabled() {
        return evalDisabled;
    }

    /**
     * Get the system property names and groups that are allowed to access them.
     *
     * @return a map where the key is the system property name, and the value is a set of group names.
     */
    Map<String, Set<String>> getSystemPropertyAccessGroups() {
        if (systemPropertyAccessGroups == null) {
            final Tuple2<Map<String, Set<String>>, Map<String, Set<String>>> accessRules = AccessUtil.parseAccessParameters(PTN_SYSTEM_PROPERTY_ACCESS, getParameters());
            this.systemPropertyAccessGroups = accessRules._1;
            this.systemPropertyAccessUsers = accessRules._2;
        }
        return systemPropertyAccessGroups;
    }

    /**
     * Get the system property names and users that are allowed to access them.
     *
     * @return a map where the key is the system property name, and the value is a set of usernames.
     */
    Map<String, Set<String>> getSystemPropertyAccessUsers() {
        if (systemPropertyAccessUsers == null) {
            final Tuple2<Map<String, Set<String>>, Map<String, Set<String>>> accessRules = AccessUtil.parseAccessParameters(PTN_SYSTEM_PROPERTY_ACCESS, getParameters());
            this.systemPropertyAccessGroups = accessRules._1;
            this.systemPropertyAccessUsers = accessRules._2;
        }
        return systemPropertyAccessUsers;
    }

    @Override
    public void reset(final XQueryContext xqueryContext, final boolean keepGlobals) {
        if (!keepGlobals) {
            mGlobalVariables.clear();
        }
        super.reset(xqueryContext, keepGlobals);
    }

    static FunctionSignature functionSignature(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType... paramTypes) {
        return FunctionDSL.functionSignature(new QName(name, NAMESPACE_URI), description, returnType, paramTypes);
    }

    static FunctionSignature[] functionSignatures(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType[][] variableParamTypes) {
        return FunctionDSL.functionSignatures(new QName(name, NAMESPACE_URI), description, returnType, variableParamTypes);
    }
}
