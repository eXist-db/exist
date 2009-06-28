<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
		xmlns="http://www.w3.org/1999/xhtml"
                xmlns:fo="http://www.w3.org/1999/XSL/Format"
		xmlns:xlink="http://www.w3.org/1999/xlink"
		xmlns:hip='http://dev.w3.org/cvsweb/2001/palmagent/hiptop'
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
		exclude-result-prefixes="xlink hip xs"
                version="2.0">

<!-- input if you don't want to use hiptop/contacts.xml
<envelope xmlns='http://dev.w3.org/cvsweb/2001/palmagent/hiptop'>
<from>
  <first_name>John</first_name>
  <last_name>Smith</last_name>
  <addresses>
    <address>
      <street>123 Main Street</street>
      <city>Anytown</city>
      <state>SA</state>
      <zip>12345</zip>
    </address>
  </addresses>
</from>
<to>
  <first_name>Jane</first_name>
  <last_name>Doe</last_name>
  <addresses>
    <address>
      <street>456 Random Street
Suite 200</street>
      <city>Othertown</city>
      <state>OA</state>
      <zip>67890</zip>
    </address>
  </addresses>
</to>
</envelope>
-->

<xsl:param name="size" select="'business'"/>

<xsl:param name="from" select="''"/>
<xsl:param name="to" select="''"/>

<xsl:param name="flabel" select="''"/>
<xsl:param name="label" select="''"/>

<xsl:key name="contactlabel" match="hip:contact"
	 use="hip:urls/hip:uri[@label='ID']"/>

<xsl:key name="contactid" match="hip:contact" use="@id"/>

<xsl:variable name="contacts"
	      select="document('contact.xml')"/>

<xsl:variable name="cfrom"
  select="if (/hip:envelope/hip:from)
          then /hip:envelope/hip:from
          else (key('contactlabel',concat('#',$from),$contacts)
	        | key('contactid',concat('_',$from),$contacts))[1]"/>

<xsl:variable name="cto"
  select="if (/hip:envelope/hip:to)
          then /hip:envelope/hip:to
          else (key('contactlabel',concat('#',$to),$contacts)
	        | key('contactid',concat('_',$to),$contacts))[1]"/>

<xsl:template match="/">
  <xsl:if test="count($cto) = 0">
    <xsl:message terminate="yes">
      <xsl:text>Cannot find address for to: </xsl:text>
      <xsl:value-of select="$to"/>
    </xsl:message>
  </xsl:if>

  <xsl:if test="count($cto) &gt; 1">
    <xsl:message terminate="yes">
      <xsl:text>Ambiguous to: </xsl:text>
      <xsl:value-of select="$to"/>
    </xsl:message>
  </xsl:if>

  <xsl:if test="count($cfrom) = 0">
    <xsl:message terminate="yes">
      <xsl:text>Cannot find address for from: </xsl:text>
      <xsl:value-of select="$from"/>
    </xsl:message>
  </xsl:if>

  <xsl:if test="count($cfrom) &gt; 1">
    <xsl:message terminate="yes">
      <xsl:text>Ambiguous from: </xsl:text>
      <xsl:value-of select="$from"/>
    </xsl:message>
  </xsl:if>

  <fo:root>
    <fo:layout-master-set>
      <!-- personal-envelope -->
      <fo:simple-page-master master-name="personal-envelope"
                           page-width="{6+(3 div 8)}in"
                           page-height="{3+(5 div 8)}in"
                           margin-top="{1 div 8}in"
                           margin-bottom="{1 div 8}in"
                           margin-left="{1 div 8}in"
                           margin-right="{1 div 8}in">
	<fo:region-body margin-bottom="0in"
			margin-top="0in"/>
      </fo:simple-page-master>
      <!-- business-envelope -->
      <fo:simple-page-master master-name="business-envelope"
                           page-width="9.5in"
                           page-height="{4+(1 div 8)}in"
                           margin-top="{1 div 8}in"
                           margin-bottom="{1 div 8}in"
                           margin-left="{1 div 8}in"
                           margin-right="{1 div 8}in">
	<fo:region-body margin-bottom="0in"
			margin-top="0in"/>
      </fo:simple-page-master>

      <!-- setup for personal pages -->
      <fo:page-sequence-master master-name="personal">
	<fo:repeatable-page-master-alternatives>
	  <fo:conditional-page-master-reference master-reference="personal-envelope"
						odd-or-even="odd"/>
	  <fo:conditional-page-master-reference master-reference="personal-envelope"
						odd-or-even="even"/>
	</fo:repeatable-page-master-alternatives>
      </fo:page-sequence-master>

      <!-- setup for business pages -->
      <fo:page-sequence-master master-name="business">
	<fo:repeatable-page-master-alternatives>
	  <fo:conditional-page-master-reference master-reference="business-envelope"
						odd-or-even="odd"/>
	  <fo:conditional-page-master-reference master-reference="business-envelope"
						odd-or-even="even"/>
	</fo:repeatable-page-master-alternatives>
      </fo:page-sequence-master>
    </fo:layout-master-set>

    <!-- my printer doesn't like "personal" sized envelopes, so fake it -->
    <fo:page-sequence master-reference="business">
      <fo:flow flow-name="xsl-region-body">
	<fo:block>
	  <xsl:if test="$size = 'personal'">
	    <xsl:attribute name="margin-left" select="'0.5in'"/>
	    <xsl:attribute name="margin-top" select="'0.5in'"/>
	  </xsl:if>
	  <xsl:apply-templates select="$cfrom" mode="address">
	    <xsl:with-param name="label" select="$flabel"/>
	  </xsl:apply-templates>
	</fo:block>

	<fo:block>
	  <xsl:choose>
	    <xsl:when test="$size = 'business'">
	      <xsl:attribute name="margin-left" select="'4in'"/>
	      <xsl:attribute name="margin-top" select="'1in'"/>
	    </xsl:when>
	    <xsl:when test="$size = 'personal'">
	      <xsl:attribute name="margin-left" select="'3in'"/>
	      <xsl:attribute name="margin-top" select="'0.75in'"/>
	    </xsl:when>
	    <xsl:otherwise>
	      <xsl:message terminate="yes">Size?</xsl:message>
	    </xsl:otherwise>
	  </xsl:choose>
	  <xsl:apply-templates select="$cto" mode="address">
	    <xsl:with-param name="label" select="$label"/>
	  </xsl:apply-templates>
	</fo:block>
      </fo:flow>
    </fo:page-sequence>
  </fo:root>
</xsl:template>

<xsl:template match="*" mode="address">
  <xsl:param name="label" select="''"/>

  <xsl:variable name="addr" as="element()">
    <xsl:choose>
      <xsl:when test="$label = ''">
	<xsl:sequence select="hip:addresses/hip:address[1]"/>
      </xsl:when>
      <xsl:when test="hip:addresses/hip:address[@label = $label]">
	<xsl:sequence select="hip:addresses/hip:address[@label = $label]"/>
      </xsl:when>
      <xsl:when test="$label castable as xs:integer">
	<xsl:sequence select="hip:addresses/hip:address[$label cast as xs:integer]"/>
      </xsl:when>
      <xsl:otherwise>
	<xsl:message terminate="yes">
	  <xsl:text>Cannot interpret label: </xsl:text>
	  <xsl:value-of select="$label"/>
	</xsl:message>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:variable>

  <xsl:if test="(hip:first_name and hip:first_name != '')
		or (hip:last_name and hip:last_name != '')">
    <fo:block>
      <xsl:value-of select="hip:first_name"/>
      <xsl:text> </xsl:text>
      <xsl:value-of select="hip:last_name"/>
    </fo:block>
  </xsl:if>

  <xsl:if test="hip:company != ''
		and (hip:category = 'Business' or $addr/@label = 'Work'
	    	    or ((not(hip:first_name) or hip:first_name = '')
		        and (not(hip:last_name) or hip:last_name = '')))">
    <fo:block linefeed-treatment='preserve'>
      <xsl:value-of select="hip:company"/>
    </fo:block>
  </xsl:if>

  <fo:block linefeed-treatment='preserve'>
    <xsl:value-of select="$addr/hip:street"/>
  </fo:block>
  <fo:block>
    <xsl:value-of select="$addr/hip:city"/>
    <xsl:text>, </xsl:text>
    <xsl:value-of select="$addr/hip:state"/>
    <xsl:text>  </xsl:text>
    <xsl:value-of select="$addr/hip:zip"/>
  </fo:block>
  <!--
  <xsl:if test="$toCountry != '' and $toCountry != $fromCountry">
    <fo:block><xsl:value-of select="$fromCountry"/></fo:block>
  </xsl:if>
  -->
</xsl:template>

</xsl:stylesheet>
