<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>nl</language>
			<calendar.day0>Ma</calendar.day0>
			<calendar.day1>Di</calendar.day1>
			<calendar.day2>Wo</calendar.day2>
			<calendar.day3>Do</calendar.day3>
			<calendar.day4>Vr</calendar.day4>
			<calendar.day5>Za</calendar.day5>
			<calendar.day6>Zo</calendar.day6>
			<calendar.initDay>6</calendar.initDay>
			<calendar.month0>januari</calendar.month0>
			<calendar.month1>februari</calendar.month1>
			<calendar.month2>maart</calendar.month2>
			<calendar.month3>april</calendar.month3>
			<calendar.month4>mei</calendar.month4>
			<calendar.month5>juni</calendar.month5>
			<calendar.month6>juli</calendar.month6>
			<calendar.month7>augustus</calendar.month7>
			<calendar.month8>september</calendar.month8>
			<calendar.month9>oktober</calendar.month9>
			<calendar.month10>november</calendar.month10>
			<calendar.month11>december</calendar.month11>
			<format.date>dd-MM-yyyy</format.date>
			<format.datetime>dd-MM-yyyy hh:mm:ss</format.datetime>
			<format.decimal>,</format.decimal>
			<status>... Laden ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>