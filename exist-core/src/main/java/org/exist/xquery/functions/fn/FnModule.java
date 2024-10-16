/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
 *  
 *  $Id$
 */
package org.exist.xquery.functions.fn;

import java.util.*;
import java.util.regex.Pattern;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.util.PatternFactory;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.functions.AccessUtil;

/**
 * Module function definitions for xpath-functions module.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author ljo
 * @author <a href="mailto:adam@evolvedbinary.com>Adam Retter</a>
 */
public class FnModule extends AbstractInternalModule {

    public final static String PREFIX = "";
    public final static String INCLUSION_DATE = "2004-01-29";
    public final static String RELEASED_IN_VERSION = "pre eXist-1.0";

    public final static FunctionDef[] functions = {
        new FunctionDef(FunAbs.signature, FunAbs.class),
        new FunctionDef(FunAvg.signature, FunAvg.class),
        new FunctionDef(FunBaseURI.signatures[0], FunBaseURI.class),
        new FunctionDef(FunBaseURI.signatures[1], FunBaseURI.class),
        new FunctionDef(FunBaseURI.signatures[2], FunBaseURI.class),
        new FunctionDef(FunBoolean.signature, FunBoolean.class),
        new FunctionDef(FunCeiling.signature, FunCeiling.class),
        new FunctionDef(FunCodepointEqual.signature, FunCodepointEqual.class),
        new FunctionDef(FunCodepointsToString.signature, FunCodepointsToString.class),
        new FunctionDef(FunCompare.signatures[0], FunCompare.class),
        new FunctionDef(FunCompare.signatures[1], FunCompare.class),
        new FunctionDef(FunConcat.signature, FunConcat.class),
        new FunctionDef(FunContains.signatures[0], FunContains.class),
        new FunctionDef(FunContains.signatures[1], FunContains.class),
        new FunctionDef(FunCount.signature, FunCount.class),
        new FunctionDef(FunCurrentDateTime.fnCurrentDate, FunCurrentDateTime.class),
        new FunctionDef(FunCurrentDateTime.fnCurrentDateTime, FunCurrentDateTime.class),
        new FunctionDef(FunCurrentDateTime.fnCurrentTime, FunCurrentDateTime.class),
        new FunctionDef(FunData.signatures[0], FunData.class),
        new FunctionDef(FunData.signatures[1], FunData.class),
        new FunctionDef(FunDateTime.signature, FunDateTime.class),
        new FunctionDef(FunDeepEqual.signatures[0], FunDeepEqual.class),
        new FunctionDef(FunDeepEqual.signatures[1], FunDeepEqual.class),
        new FunctionDef(FunDefaultCollation.signature, FunDefaultCollation.class),
        new FunctionDef(FunDistinctValues.signatures[0], FunDistinctValues.class),
        new FunctionDef(FunDistinctValues.signatures[1], FunDistinctValues.class),
        new FunctionDef(FunDoc.signature, FunDoc.class),
        new FunctionDef(FunDocAvailable.signature, FunDocAvailable.class),
        new FunctionDef(FunDocumentURI.signature, FunDocumentURI.class),
        new FunctionDef(FunEmpty.signature, FunEmpty.class),
        new FunctionDef(FunEncodeForURI.signature, FunEncodeForURI.class),
        new FunctionDef(FunEndsWith.signatures[0], FunEndsWith.class),
        new FunctionDef(FunEndsWith.signatures[1], FunEndsWith.class),
        new FunctionDef(FunError.signature[0], FunError.class),
        new FunctionDef(FunError.signature[1], FunError.class),
        new FunctionDef(FunError.signature[2], FunError.class),
        new FunctionDef(FunError.signature[3], FunError.class),
        new FunctionDef(FunEscapeHTMLURI.signature, FunEscapeHTMLURI.class),
        new FunctionDef(FunEscapeURI.signature, FunEscapeURI.class),
        new FunctionDef(FunExactlyOne.signature, FunExactlyOne.class),
        new FunctionDef(FunExists.signature, FunExists.class),
        new FunctionDef(FunFloor.signature, FunFloor.class),
        new FunctionDef(FnFormatDates.FNS_FORMAT_DATETIME_2, FnFormatDates.class),
        new FunctionDef(FnFormatDates.FNS_FORMAT_DATETIME_5, FnFormatDates.class),
        new FunctionDef(FnFormatDates.FNS_FORMAT_DATE_2, FnFormatDates.class),
        new FunctionDef(FnFormatDates.FNS_FORMAT_DATE_5, FnFormatDates.class),
        new FunctionDef(FnFormatDates.FNS_FORMAT_TIME_2, FnFormatDates.class),
        new FunctionDef(FnFormatDates.FNS_FORMAT_TIME_5, FnFormatDates.class),
        new FunctionDef(FnFormatNumbers.signatures[0], FnFormatNumbers.class),
        new FunctionDef(FnFormatNumbers.signatures[1], FnFormatNumbers.class),
        new FunctionDef(FunGenerateId.signatures[0], FunGenerateId.class),
        new FunctionDef(FunGenerateId.signatures[1], FunGenerateId.class),
        new FunctionDef(FunGetDateComponent.fnDayFromDate, FunGetDateComponent.class),
        new FunctionDef(FunGetDateComponent.fnMonthFromDate, FunGetDateComponent.class),
        new FunctionDef(FunGetDateComponent.fnYearFromDate, FunGetDateComponent.class),
        new FunctionDef(FunGetDateComponent.fnTimezoneFromDate, FunGetDateComponent.class),
        new FunctionDef(FunGetDateComponent.fnHoursFromTime, FunGetDateComponent.class),
        new FunctionDef(FunGetDateComponent.fnMinutesFromTime, FunGetDateComponent.class),
        new FunctionDef(FunGetDateComponent.fnSecondsFromTime, FunGetDateComponent.class),
        new FunctionDef(FunGetDateComponent.fnTimezoneFromTime, FunGetDateComponent.class),
        new FunctionDef(FunGetDateComponent.fnDayFromDateTime, FunGetDateComponent.class),
        new FunctionDef(FunGetDateComponent.fnMonthFromDateTime, FunGetDateComponent.class),
        new FunctionDef(FunGetDateComponent.fnYearFromDateTime, FunGetDateComponent.class),
        new FunctionDef(FunGetDateComponent.fnHoursFromDateTime, FunGetDateComponent.class),
        new FunctionDef(FunGetDateComponent.fnMinutesFromDateTime, FunGetDateComponent.class),
        new FunctionDef(FunGetDateComponent.fnSecondsFromDateTime, FunGetDateComponent.class),
        new FunctionDef(FunGetDateComponent.fnTimezoneFromDateTime, FunGetDateComponent.class),
        new FunctionDef(FunGetDurationComponent.fnYearsFromDuration, FunGetDurationComponent.class),
        new FunctionDef(FunGetDurationComponent.fnMonthsFromDuration, FunGetDurationComponent.class),
        new FunctionDef(FunGetDurationComponent.fnDaysFromDuration, FunGetDurationComponent.class),
        new FunctionDef(FunGetDurationComponent.fnHoursFromDuration, FunGetDurationComponent.class),
        new FunctionDef(FunGetDurationComponent.fnMinutesFromDuration, FunGetDurationComponent.class),
        new FunctionDef(FunGetDurationComponent.fnSecondsFromDuration, FunGetDurationComponent.class),
        new FunctionDef(FunAdjustTimezone.fnAdjustDateToTimezone[0], FunAdjustTimezone.class),
        new FunctionDef(FunAdjustTimezone.fnAdjustDateToTimezone[1], FunAdjustTimezone.class),
        new FunctionDef(FunAdjustTimezone.fnAdjustTimeToTimezone[0], FunAdjustTimezone.class),
        new FunctionDef(FunAdjustTimezone.fnAdjustTimeToTimezone[1], FunAdjustTimezone.class),
        new FunctionDef(FunAdjustTimezone.fnAdjustDateTimeToTimezone[0], FunAdjustTimezone.class),
        new FunctionDef(FunAdjustTimezone.fnAdjustDateTimeToTimezone[1], FunAdjustTimezone.class),
        new FunctionDef(FnHasChildren.FNS_HAS_CHILDREN_0, FnHasChildren.class),
        new FunctionDef(FnHasChildren.FNS_HAS_CHILDREN_1, FnHasChildren.class),
        new FunctionDef(FunId.signature[0], FunId.class),
        new FunctionDef(FunId.signature[1], FunId.class),
        new FunctionDef(FunIdRef.signature[0], FunIdRef.class),
        new FunctionDef(FunIdRef.signature[1], FunIdRef.class),
        new FunctionDef(FunImplicitTimezone.signature, FunImplicitTimezone.class),
        new FunctionDef(FunIndexOf.fnIndexOf[0], FunIndexOf.class),
        new FunctionDef(FunIndexOf.fnIndexOf[1], FunIndexOf.class),
        new FunctionDef(FnInnerMost.FNS_INNERMOST, FnInnerMost.class),
        new FunctionDef(FunIRIToURI.signature, FunIRIToURI.class),
        new FunctionDef(FunInScopePrefixes.signature, FunInScopePrefixes.class),
        new FunctionDef(FunInsertBefore.signature, FunInsertBefore.class),
        new FunctionDef(FunLang.signatures[0], FunLang.class),
        new FunctionDef(FunLang.signatures[1], FunLang.class),
        new FunctionDef(FunLast.signature, FunLast.class),
        new FunctionDef(FunLocalName.signatures[0], FunLocalName.class),
        new FunctionDef(FunLocalName.signatures[1], FunLocalName.class),
        new FunctionDef(FunOnFunctions.signatures[0], FunOnFunctions.class),
        new FunctionDef(FunOnFunctions.signatures[1], FunOnFunctions.class),
        new FunctionDef(FunOnFunctions.signatures[2], FunOnFunctions.class),
        new FunctionDef(FunMatches.signatures[0], FunMatches.class),
        new FunctionDef(FunMatches.signatures[1], FunMatches.class),
        new FunctionDef(FunMax.signatures[0], FunMax.class),
        new FunctionDef(FunMax.signatures[1], FunMax.class),
        new FunctionDef(FunMin.signatures[0], FunMin.class),
        new FunctionDef(FunMin.signatures[1], FunMin.class),
        new FunctionDef(FunNodeName.signatures[0], FunNodeName.class),
        new FunctionDef(FunNodeName.signatures[1], FunNodeName.class),
        new FunctionDef(FunName.signatures[0], FunName.class),
        new FunctionDef(FunName.signatures[1], FunName.class),
        new FunctionDef(FunNamespaceURI.signatures[0], FunNamespaceURI.class),
        new FunctionDef(FunNamespaceURI.signatures[1], FunNamespaceURI.class),
        new FunctionDef(FunNamespaceURIForPrefix.signature, FunNamespaceURIForPrefix.class),
        new FunctionDef(FunNilled.signature, FunNilled.class),
        new FunctionDef(FunNormalizeSpace.signatures[0], FunNormalizeSpace.class),
        new FunctionDef(FunNormalizeSpace.signatures[1], FunNormalizeSpace.class),
        new FunctionDef(FunNormalizeUnicode.signatures[0], FunNormalizeUnicode.class),
        new FunctionDef(FunNormalizeUnicode.signatures[1], FunNormalizeUnicode.class),
        new FunctionDef(FunNot.signature, FunNot.class),
        new FunctionDef(FunNumber.signatures[0], FunNumber.class),
        new FunctionDef(FunNumber.signatures[1], FunNumber.class),
        new FunctionDef(FunOneOrMore.signature, FunOneOrMore.class),
        new FunctionDef(FnOuterMost.FNS_OUTERMOST, FnOuterMost.class),
        new FunctionDef(FunPosition.signature, FunPosition.class),
        new FunctionDef(FunQName.signature, FunQName.class),
        new FunctionDef(FunRemove.signature, FunRemove.class),
        new FunctionDef(FunReplace.signatures[0], FunReplace.class),
        new FunctionDef(FunReplace.signatures[1], FunReplace.class),
        new FunctionDef(FunReverse.signature, FunReverse.class),
        new FunctionDef(FunResolveURI.signatures[0], FunResolveURI.class),
        new FunctionDef(FunResolveURI.signatures[1], FunResolveURI.class),
        new FunctionDef(FunRoot.signatures[0], FunRoot.class),
        new FunctionDef(FunRoot.signatures[1], FunRoot.class),
        new FunctionDef(FunRound.signature, FunRound.class),
        new FunctionDef(FunRoundHalfToEven.signatures[0], FunRoundHalfToEven.class),
        new FunctionDef(FunRoundHalfToEven.signatures[1], FunRoundHalfToEven.class),
        new FunctionDef(FunSerialize.signatures[0], FunSerialize.class),
            new FunctionDef(FunSerialize.signatures[1], FunSerialize.class),
        new FunctionDef(FunStartsWith.signatures[0], FunStartsWith.class),
        new FunctionDef(FunStartsWith.signatures[1], FunStartsWith.class),
        new FunctionDef(FunString.signatures[0], FunString.class),
        new FunctionDef(FunString.signatures[1], FunString.class),
        new FunctionDef(FunStringJoin.signatures[0], FunStringJoin.class),
        new FunctionDef(FunStringJoin.signatures[1], FunStringJoin.class),
        new FunctionDef(FunStringToCodepoints.signature, FunStringToCodepoints.class),
        new FunctionDef(FunStrLength.signatures[0], FunStrLength.class),
        new FunctionDef(FunStrLength.signatures[1], FunStrLength.class),
        new FunctionDef(FunSubSequence.signatures[0], FunSubSequence.class),
        new FunctionDef(FunSubSequence.signatures[1], FunSubSequence.class),
        new FunctionDef(FunSubstring.signatures[0], FunSubstring.class),
        new FunctionDef(FunSubstring.signatures[1], FunSubstring.class),
        new FunctionDef(FunSubstringAfter.signatures[0], FunSubstringAfter.class),
        new FunctionDef(FunSubstringAfter.signatures[1], FunSubstringAfter.class),
        new FunctionDef(FunSubstringBefore.signatures[0], FunSubstringBefore.class),
        new FunctionDef(FunSubstringBefore.signatures[1], FunSubstringBefore.class),
        new FunctionDef(FunSum.signatures[0], FunSum.class),
        new FunctionDef(FunSum.signatures[1], FunSum.class),
        new FunctionDef(FunTokenize.FS_TOKENIZE[0], FunTokenize.class),
        new FunctionDef(FunTokenize.FS_TOKENIZE[1], FunTokenize.class),
        new FunctionDef(FunTokenize.FS_TOKENIZE[2], FunTokenize.class),
        new FunctionDef(FunTrace.signature, FunTrace.class),
        new FunctionDef(FunTranslate.signature, FunTranslate.class),
        new FunctionDef(FunTrueOrFalse.fnTrue, FunTrueOrFalse.class),
        new FunctionDef(FunTrueOrFalse.fnFalse, FunTrueOrFalse.class),
        new FunctionDef(FunUpperOrLowerCase.fnLowerCase, FunUpperOrLowerCase.class),
        new FunctionDef(FunUpperOrLowerCase.fnUpperCase, FunUpperOrLowerCase.class),
        new FunctionDef(FunZeroOrOne.signature, FunZeroOrOne.class),
        new FunctionDef(FunUnordered.signature, FunUnordered.class),
        new FunctionDef(ExtCollection.signature, ExtCollection.class),
        new FunctionDef(QNameFunctions.localNameFromQName, QNameFunctions.class),
        new FunctionDef(QNameFunctions.prefixFromQName, QNameFunctions.class),
        new FunctionDef(QNameFunctions.namespaceURIFromQName, QNameFunctions.class),
        new FunctionDef(FunResolveQName.signature, FunResolveQName.class),
        new FunctionDef(FunEquals.signatures[0], FunEquals.class),
        new FunctionDef(FunEquals.signatures[1], FunEquals.class),
        new FunctionDef(FunAnalyzeString.signatures[0], FunAnalyzeString.class),
        new FunctionDef(FunAnalyzeString.signatures[1], FunAnalyzeString.class),
        new FunctionDef(FunHeadTail.signatures[0], FunHeadTail.class),
        new FunctionDef(FunHeadTail.signatures[1], FunHeadTail.class),
        new FunctionDef(FunHigherOrderFun.signatures[0], FunHigherOrderFun.class),
        new FunctionDef(FunHigherOrderFun.signatures[1], FunHigherOrderFun.class),
        new FunctionDef(FunHigherOrderFun.signatures[2], FunHigherOrderFun.class),
        new FunctionDef(FunHigherOrderFun.signatures[3], FunHigherOrderFun.class),
        new FunctionDef(FunHigherOrderFun.signatures[4], FunHigherOrderFun.class),
        new FunctionDef(FunHigherOrderFun.signatures[5], FunHigherOrderFun.class),
        new FunctionDef(FunHigherOrderFun.signatures[6], FunHigherOrderFun.class),
        new FunctionDef(FunHigherOrderFun.signatures[7], FunHigherOrderFun.class),
        new FunctionDef(FunEnvironment.signature[0], FunEnvironment.class),
        new FunctionDef(FunEnvironment.signature[1], FunEnvironment.class),        
        new FunctionDef(ParsingFunctions.signatures[0], ParsingFunctions.class),
        new FunctionDef(ParsingFunctions.signatures[1], ParsingFunctions.class),
        new FunctionDef(JSON.signatures[0], JSON.class),
        new FunctionDef(JSON.signatures[1], JSON.class),
        new FunctionDef(JSON.signatures[2], JSON.class),
        new FunctionDef(JSON.signatures[3], JSON.class),
        new FunctionDef(LoadXQueryModule.LOAD_XQUERY_MODULE_1, LoadXQueryModule.class),
        new FunctionDef(LoadXQueryModule.LOAD_XQUERY_MODULE_2, LoadXQueryModule.class),
        new FunctionDef(FunSort.signatures[0], FunSort.class),
        new FunctionDef(FunSort.signatures[1], FunSort.class),
        new FunctionDef(FunSort.signatures[2], FunSort.class),
        new FunctionDef(FunUnparsedText.FS_UNPARSED_TEXT[0], FunUnparsedText.class),
        new FunctionDef(FunUnparsedText.FS_UNPARSED_TEXT[1], FunUnparsedText.class),
        new FunctionDef(FunUnparsedText.FS_UNPARSED_TEXT_LINES[0], FunUnparsedText.class),
        new FunctionDef(FunUnparsedText.FS_UNPARSED_TEXT_LINES[1], FunUnparsedText.class),
        new FunctionDef(FunUnparsedText.FS_UNPARSED_TEXT_AVAILABLE[0], FunUnparsedText.class),
        new FunctionDef(FunUnparsedText.FS_UNPARSED_TEXT_AVAILABLE[1], FunUnparsedText.class)
    };

    static {
        Arrays.sort(functions, new FunctionComparator());
    }

    public final static ErrorCodes.ErrorCode SENR0001 = new ErrorCodes.ErrorCode("SENR0001", "serialization error in fn:serialize");
    public final static ErrorCodes.ErrorCode SEPM0019 = new ErrorCodes.ErrorCode("SEPM0019", "It is an error if an instance of the data model " +
            "used to specify the settings of serialization parameters specifies the value of the same parameter more than once.");

    private static final Pattern PTN_ENVIRONMENT_VARIABLE_ACCESS = PatternFactory.getInstance().getPattern("environmentVariableAccess\\.([^=\\00])+\\.requires((?:Group)|(?:User))");

    private Map<String, Set<String>> environmentVariableAccessGroups = null;
    private Map<String, Set<String>> environmentVariableAccessUsers = null;

    public FnModule(final Map<String, List<? extends Object>> parameters) {
        super(functions, parameters, true);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getDescription()
     */
    public String getDescription() {
        return "A module with the XQuery/XPath Core Library Functions";
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getNamespaceURI()
     */
    public String getNamespaceURI() {
        return Function.BUILTIN_FUNCTION_NS;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getDefaultPrefix()
     */
    public String getDefaultPrefix() {
        return PREFIX;
    }

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

    /**
     * Get the environment variable names and groups that are allowed to access them.
     *
     * @return a map where the key is the environment variable name, and the value is a set of group names.
     */
    Map<String, Set<String>> getEnvironmentVariableAccessGroups() {
        if (environmentVariableAccessGroups == null) {
            final Tuple2<Map<String, Set<String>>, Map<String, Set<String>>> accessRules = AccessUtil.parseAccessParameters(PTN_ENVIRONMENT_VARIABLE_ACCESS, getParameters());
            this.environmentVariableAccessGroups = accessRules._1;
            this.environmentVariableAccessUsers = accessRules._2;
        }
        return environmentVariableAccessGroups;
    }

    /**
     * Get the environment variable names and users that are allowed to access them.
     *
     * @return a map where the key is the environment variable name, and the value is a set of usernames.
     */
    Map<String, Set<String>> getEnvironmentVariableAccessUsers() {
        if (environmentVariableAccessUsers == null) {
            final Tuple2<Map<String, Set<String>>, Map<String, Set<String>>> accessRules = AccessUtil.parseAccessParameters(PTN_ENVIRONMENT_VARIABLE_ACCESS, getParameters());
            this.environmentVariableAccessGroups = accessRules._1;
            this.environmentVariableAccessUsers = accessRules._2;
        }
        return environmentVariableAccessUsers;
    }
}
