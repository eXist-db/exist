<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>nn_no</language>
			<calendar.day0>Mån</calendar.day0>
			<calendar.day1>Tys</calendar.day1>
			<calendar.day2>Ons</calendar.day2>
			<calendar.day3>Tor</calendar.day3>
			<calendar.day4>Fre</calendar.day4>
			<calendar.day5>Lau</calendar.day5>
			<calendar.day6>Sun</calendar.day6>
			<calendar.initDay>0</calendar.initDay>
			<calendar.month0>Januar</calendar.month0>
			<calendar.month1>Februar</calendar.month1>
			<calendar.month2>Mars</calendar.month2>
			<calendar.month3>April</calendar.month3>
			<calendar.month4>Mai</calendar.month4>
			<calendar.month5>Juni</calendar.month5>
			<calendar.month6>Juli</calendar.month6>
			<calendar.month7>August</calendar.month7>
			<calendar.month8>September</calendar.month8>
			<calendar.month9>Oktober</calendar.month9>
			<calendar.month10>November</calendar.month10>
			<calendar.month11>Desember</calendar.month11>
			<format.date>dd.MM.yyyy</format.date>
			<format.datetime>dd.MM.yyyy kl. hh.mm.ss</format.datetime>
			<format.decimal>,</format.decimal>
			<status>... Loading ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>