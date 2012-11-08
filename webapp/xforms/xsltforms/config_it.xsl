<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:template name="config">
		<options>
		</options>
		<properties> <!--  accessible at run time -->
			<language>it</language>
			<calendar.day0>Lun</calendar.day0>
			<calendar.day1>Mar</calendar.day1>
			<calendar.day2>Mer</calendar.day2>
			<calendar.day3>Gio</calendar.day3>
			<calendar.day4>Ven</calendar.day4>
			<calendar.day5>Sab</calendar.day5>
			<calendar.day6>Dom</calendar.day6>
			<calendar.initDay>0</calendar.initDay>
			<calendar.month0>Gennaio</calendar.month0>
			<calendar.month1>Febbraio</calendar.month1>
			<calendar.month2>Marzo</calendar.month2>
			<calendar.month3>Aprile</calendar.month3>
			<calendar.month4>Maggio</calendar.month4>
			<calendar.month5>Giugno</calendar.month5>
			<calendar.month6>Luglio</calendar.month6>
			<calendar.month7>Agosto</calendar.month7>
			<calendar.month8>Settembre</calendar.month8>
			<calendar.month9>Ottobre</calendar.month9>
			<calendar.month10>Novembre</calendar.month10>
			<calendar.month11>Dicembre</calendar.month11>
			<format.date>dd/MM/yyyy</format.date>
			<format.datetime>dd/MM/yyyy hh:mm:ss</format.datetime>
			<format.decimal>,</format.decimal>
			<status>Caricamento in corso</status>
		</properties>
		<extensions/> <!-- HTML elements to be added just after xsltforms.js and xsltforms.css loading -->
	</xsl:template>
</xsl:stylesheet>