/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery.functions;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class ModuleImpl extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://www.w3.org/2003/05/xpath-functions";
	
	public final static String PREFIX = "";
	
	public final static FunctionDef[] functions = {
		new FunctionDef(FunAbs.signature, FunAbs.class),
		new FunctionDef(FunAvg.signature, FunAvg.class),
		new FunctionDef(FunBaseURI.signature, FunBaseURI.class),
		new FunctionDef(FunBoolean.signature, FunBoolean.class),
		new FunctionDef(FunCeiling.signature, FunCeiling.class),
		new FunctionDef(FunConcat.signature, FunConcat.class),
		new FunctionDef(FunContains.signatures[0], FunContains.class),
		new FunctionDef(FunContains.signatures[1], FunContains.class),
		new FunctionDef(FunCount.signature, FunCount.class),
		new FunctionDef(FunCurrentDate.signature, FunCurrentDate.class),
		new FunctionDef(FunCurrentDateTime.signature, FunCurrentDateTime.class),
		new FunctionDef(FunCurrentTime.signature, FunCurrentTime.class),
		new FunctionDef(FunData.signature, FunData.class),
		new FunctionDef(FunDeepEqual.signature, FunDeepEqual.class),
		new FunctionDef(FunDistinctValues.signature, FunDistinctValues.class),
		new FunctionDef(FunDoc.signature, FunDoc.class),
		new FunctionDef(FunDocumentURI.signature, FunDocumentURI.class),
		new FunctionDef(FunEmpty.signature, FunEmpty.class),
		new FunctionDef(FunEndsWith.signature, FunEndsWith.class),
        new FunctionDef(FunError.signature, FunError.class),
		new FunctionDef(FunExactlyOne.signature, FunExactlyOne.class),
		new FunctionDef(FunExists.signature, FunExists.class),
		new FunctionDef(FunFloor.signature, FunFloor.class),
		new FunctionDef(FunGetDateComponent.fnGetDayFromDate, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnGetMonthFromDate, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnGetYearFromDate, FunGetDateComponent.class),
		new FunctionDef(FunGetDateComponent.fnGetTimezoneFromDate, FunGetDateComponent.class),
		new FunctionDef(FunGetTimeComponent.fnHoursFromTime, FunGetTimeComponent.class),
		new FunctionDef(FunGetTimeComponent.fnMinutesFromTime, FunGetTimeComponent.class),
		new FunctionDef(FunGetTimeComponent.fnSecondsFromTime, FunGetTimeComponent.class),
		new FunctionDef(FunGetTimeComponent.fnTimezoneFromTime, FunGetTimeComponent.class),
		new FunctionDef(FunGetDateTimeComponent.fnGetDayFromDateTime, FunGetDateTimeComponent.class),
		new FunctionDef(FunGetDateTimeComponent.fnGetMonthFromDateTime, FunGetDateTimeComponent.class),
		new FunctionDef(FunGetDateTimeComponent.fnGetYearFromDateTime, FunGetDateTimeComponent.class),
		new FunctionDef(FunGetDateTimeComponent.fnHoursFromDateTime, FunGetDateTimeComponent.class),
		new FunctionDef(FunGetDateTimeComponent.fnMinutesFromDateTime, FunGetDateTimeComponent.class),
		new FunctionDef(FunGetDateTimeComponent.fnSecondsFromDateTime, FunGetDateTimeComponent.class),
		new FunctionDef(FunGetDateTimeComponent.fnTimezoneFromDateTime, FunGetDateTimeComponent.class),
		new FunctionDef(FunGetDaysFromDayTimeDuration.signature, FunGetDaysFromDayTimeDuration.class),
		new FunctionDef(FunGetHoursFromDayTimeDuration.signature, FunGetHoursFromDayTimeDuration.class),
		new FunctionDef(FunGetMinutesFromDayTimeDuration.signature, FunGetMinutesFromDayTimeDuration.class),
		new FunctionDef(FunGetSecondsFromDayTimeDuration.signature, FunGetSecondsFromDayTimeDuration.class),
		new FunctionDef(FunAdjustDateToTimezone.fnAdjustDateToTimezone[0], FunAdjustDateToTimezone.class),
		new FunctionDef(FunAdjustDateToTimezone.fnAdjustDateToTimezone[1], FunAdjustDateToTimezone.class),
		new FunctionDef(FunAdjustTimeToTimezone.fnAdjustTimeToTimezone[0], FunAdjustTimeToTimezone.class),
		new FunctionDef(FunAdjustTimeToTimezone.fnAdjustTimeToTimezone[1], FunAdjustTimeToTimezone.class),
		new FunctionDef(FunAdjustDateTimeToTimezone.fnAdjustDateTimeToTimezone[0], FunAdjustDateTimeToTimezone.class),
		new FunctionDef(FunAdjustDateTimeToTimezone.fnAdjustDateTimeToTimezone[1], FunAdjustDateTimeToTimezone.class),
		new FunctionDef(FunId.signature, FunId.class),
		new FunctionDef(FunItemAt.signature, FunItemAt.class),
		new FunctionDef(FunLang.signature, FunLang.class),
		new FunctionDef(FunLast.signature, FunLast.class),
		new FunctionDef(FunLocalName.signatures[0], FunLocalName.class),
		new FunctionDef(FunLocalName.signatures[1], FunLocalName.class),
		new FunctionDef(FunMatches.signatures[0], FunMatches.class),
		new FunctionDef(FunMatches.signatures[1], FunMatches.class),
		new FunctionDef(FunMax.signatures[0], FunMax.class),
		new FunctionDef(FunMax.signatures[1], FunMax.class),
		new FunctionDef(FunMin.signatures[0], FunMin.class),
		new FunctionDef(FunMin.signatures[1], FunMin.class),
		new FunctionDef(FunNodeName.signature, FunNodeName.class),
		new FunctionDef(FunName.signatures[0], FunName.class),
		new FunctionDef(FunName.signatures[1], FunName.class),
		new FunctionDef(FunNamespaceURI.signatures[0], FunNamespaceURI.class),
		new FunctionDef(FunNamespaceURI.signatures[1], FunNamespaceURI.class),
		new FunctionDef(FunNormalizeSpace.signatures[0], FunNormalizeSpace.class),
		new FunctionDef(FunNormalizeSpace.signatures[1], FunNormalizeSpace.class),
		new FunctionDef(FunNot.signature, FunNot.class),
		new FunctionDef(FunNumber.signatures[0], FunNumber.class),
		new FunctionDef(FunNumber.signatures[1], FunNumber.class),
		new FunctionDef(FunOneOrMore.signature, FunOneOrMore.class),
		new FunctionDef(FunPosition.signature, FunPosition.class),
		new FunctionDef(FunReplace.signatures[0], FunReplace.class),
		new FunctionDef(FunReplace.signatures[1], FunReplace.class),
		new FunctionDef(FunRoot.signatures[0], FunRoot.class),
		new FunctionDef(FunRoot.signatures[1], FunRoot.class),
		new FunctionDef(FunRound.signature, FunRound.class),
		new FunctionDef(FunStartsWith.signature, FunStartsWith.class),
		new FunctionDef(FunString.signatures[0], FunString.class),
		new FunctionDef(FunString.signatures[1], FunString.class),
		new FunctionDef(FunStringJoin.signature, FunStringJoin.class),
		new FunctionDef(FunStringPad.signature, FunStringPad.class),
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
		new FunctionDef(FunTokenize.signatures[0], FunTokenize.class),
		new FunctionDef(FunTokenize.signatures[1], FunTokenize.class),
		new FunctionDef(FunTranslate.signature, FunTranslate.class),
		new FunctionDef(FunTrueOrFalse.fnTrue, FunTrueOrFalse.class),
		new FunctionDef(FunTrueOrFalse.fnFalse, FunTrueOrFalse.class),
		new FunctionDef(FunUpperOrLowerCase.fnLowerCase, FunUpperOrLowerCase.class),
		new FunctionDef(FunUpperOrLowerCase.fnUpperCase, FunUpperOrLowerCase.class),
		new FunctionDef(FunZeroOrOne.signature, FunZeroOrOne.class),
		new FunctionDef(FunUnordered.signature, FunUnordered.class),
		new FunctionDef(ExtCollection.signature, ExtCollection.class),
		new FunctionDef(ExtXCollection.signature, ExtXCollection.class),
		new FunctionDef(ExtDoctype.signature, ExtDoctype.class),
		new FunctionDef(ExtDocument.signature, ExtDocument.class),
		new FunctionDef(ExtRegexp.signature, ExtRegexp.class),
		new FunctionDef(ExtRegexpOr.signature, ExtRegexpOr.class)
	};
	
	public ModuleImpl() {
		super(functions);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() {
		return "XQuery/XPath Core Library Functions";
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDefaultPrefix()
	 */
	public String getDefaultPrefix() {
		return PREFIX;
	}
	
}
