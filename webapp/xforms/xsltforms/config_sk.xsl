<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>sk</language>
			<calendar.day0>pondelok</calendar.day0>
			<calendar.day1>utorok</calendar.day1>
			<calendar.day2>streda</calendar.day2>
			<calendar.day3>štvrtok</calendar.day3>
			<calendar.day4>piatok</calendar.day4>
			<calendar.day5>sobota</calendar.day5>
			<calendar.day6>nedeľa</calendar.day6>
			<calendar.initDay>0</calendar.initDay>
			<calendar.month0>Január</calendar.month0>
			<calendar.month1>Február</calendar.month1>
			<calendar.month2>Marec</calendar.month2>
			<calendar.month3>Apríl</calendar.month3>
			<calendar.month4>Máj</calendar.month4>
			<calendar.month5>Jún</calendar.month5>
			<calendar.month6>Júl</calendar.month6>
			<calendar.month7>August</calendar.month7>
			<calendar.month8>September</calendar.month8>
			<calendar.month9>Október</calendar.month9>
			<calendar.month10>November</calendar.month10>
			<calendar.month11>December</calendar.month11>
			<format.date>dd/MM/yyyy</format.date>
			<format.datetime>dd/MM/yyyy hh:mm:ss</format.datetime>
			<format.decimal>,</format.decimal>
			<status>... Načítavam ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>