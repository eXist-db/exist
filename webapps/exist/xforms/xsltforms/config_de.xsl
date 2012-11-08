<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>de</language>
			<calendar.day0>Mo</calendar.day0>
			<calendar.day1>Di</calendar.day1>
			<calendar.day2>Mi</calendar.day2>
			<calendar.day3>Do</calendar.day3>
			<calendar.day4>Fr</calendar.day4>
			<calendar.day5>Sa</calendar.day5>
			<calendar.day6>So</calendar.day6>
			<calendar.initDay>0</calendar.initDay>
			<calendar.month0>Januar</calendar.month0>
			<calendar.month1>Februar</calendar.month1>
			<calendar.month2>März</calendar.month2>
			<calendar.month3>April</calendar.month3>
			<calendar.month4>Mai</calendar.month4>
			<calendar.month5>Juni</calendar.month5>
			<calendar.month6>Juli</calendar.month6>
			<calendar.month7>August</calendar.month7>
			<calendar.month8>September</calendar.month8>
			<calendar.month9>Oktober</calendar.month9>
			<calendar.month10>November</calendar.month10>
			<calendar.month11>Dezember</calendar.month11>
			<format.date>dd.MM.yyyy</format.date>
			<format.datetime>dd.MM.yyyy hh:mm:ss</format.datetime>
			<format.decimal>,</format.decimal>
			<status>... Lade ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>