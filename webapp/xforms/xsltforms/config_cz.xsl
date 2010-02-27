<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>cz</language>
			<calendar.day0>Po</calendar.day0>
			<calendar.day1>Út</calendar.day1>
			<calendar.day2>St</calendar.day2>
			<calendar.day3>Čt</calendar.day3>
			<calendar.day4>Pá</calendar.day4>
			<calendar.day5>So</calendar.day5>
			<calendar.day6>Ne</calendar.day6>
			<calendar.initDay>0</calendar.initDay>
			<calendar.month0>leden</calendar.month0>
			<calendar.month1>únor</calendar.month1>
			<calendar.month2>březen</calendar.month2>
			<calendar.month3>duben</calendar.month3>
			<calendar.month4>květen</calendar.month4>
			<calendar.month5>červen</calendar.month5>
			<calendar.month6>červenec</calendar.month6>
			<calendar.month7>srpen</calendar.month7>
			<calendar.month8>září</calendar.month8>
			<calendar.month9>říjen</calendar.month9>
			<calendar.month10>listopad</calendar.month10>
			<calendar.month11>prosinec</calendar.month11>
			<format.date>dd.MM. yyyy</format.date>
			<format.datetime>dd.MM. yyyy, hh.mm:ss</format.datetime>
			<format.decimal>,</format.decimal>
			<status>... Nahrávám ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>