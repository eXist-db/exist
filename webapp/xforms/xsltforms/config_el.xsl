<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>el</language>
			<calendar.day0>Δευ</calendar.day0>
			<calendar.day1>Τρι</calendar.day1>
			<calendar.day2>Τετ</calendar.day2>
			<calendar.day3>Πεμ</calendar.day3>
			<calendar.day4>Παρ</calendar.day4>
			<calendar.day5>Σαβ</calendar.day5>
			<calendar.day6>Κυρ</calendar.day6>
			<calendar.initDay>0</calendar.initDay>
			<calendar.month0>Ιανουάριος</calendar.month0>
			<calendar.month1>Φεβρουάριος</calendar.month1>
			<calendar.month2>Μάρτιος</calendar.month2>
			<calendar.month3>Απρίλιος</calendar.month3>
			<calendar.month4>Μάιος</calendar.month4>
			<calendar.month5>Ιούνιος</calendar.month5>
			<calendar.month6>Ιούλιος</calendar.month6>
			<calendar.month7>Αύγουστος</calendar.month7>
			<calendar.month8>Σεπτέμβριος</calendar.month8>
			<calendar.month9>Οκτώβριος</calendar.month9>
			<calendar.month10>Νοέμβριος</calendar.month10>
			<calendar.month11>Δεκέμβριος</calendar.month11>
			<format.date>dd/MM/yyyy</format.date>
			<format.datetime>dd/MM/yyyy hh:mm:ss</format.datetime>
			<format.decimal>.</format.decimal>
			<status>... Φορτώνονται ...</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>