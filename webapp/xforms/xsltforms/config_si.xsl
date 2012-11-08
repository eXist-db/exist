<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>si</language>
			<calendar.day0>Pon</calendar.day0>
			<calendar.day1>Tor</calendar.day1>
			<calendar.day2>Sre</calendar.day2>
			<calendar.day3>Čet</calendar.day3>
			<calendar.day4>Pet</calendar.day4>
			<calendar.day5>Sob</calendar.day5>
			<calendar.day6>Ned</calendar.day6>
			<calendar.initDay>0</calendar.initDay>
			<calendar.month0>Januar</calendar.month0>
			<calendar.month1>Februar</calendar.month1>
			<calendar.month2>Marec</calendar.month2>
			<calendar.month3>April</calendar.month3>
			<calendar.month4>Maj</calendar.month4>
			<calendar.month5>Junij</calendar.month5>
			<calendar.month6>Julij</calendar.month6>
			<calendar.month7>Avgust</calendar.month7>
			<calendar.month8>September</calendar.month8>
			<calendar.month9>Oktober</calendar.month9>
			<calendar.month10>November</calendar.month10>
			<calendar.month11>December</calendar.month11>
			<format.date>dd/MM/yyyy</format.date>
			<format.datetime>dd/MM/yyyy hh:mm:ss</format.datetime>
			<format.decimal>,</format.decimal>
			<status>... Nalagam ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>