<?xml version="1.0" encoding="UTF-8"?>
<!-- Rev. 525
Copyright (C) 2008-2012 agenceXML - Alain COUTHURES
Contact at : info@agencexml.com

Copyright (C) 2006 AJAXForms S.L.
Contact at: info@ajaxforms.com

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
	-->
<xsl:stylesheet xmlns:xhtml="http://www.w3.org/1999/xhtml" xmlns:ajx="http://www.ajaxforms.net/2006/ajx" xmlns:xforms="http://www.w3.org/2002/xforms" xmlns:ev="http://www.w3.org/2001/xml-events" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:msxsl="urn:schemas-microsoft-com:xslt" xmlns:exslt="http://exslt.org/common" xmlns:txs="http://www.agencexml.com/txs" xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:default="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0" exclude-result-prefixes="xhtml xforms ev exslt msxsl"><rdf:RDF>
		<rdf:Description rdf:about="http://www.agencexml.com/xsltforms/xsltforms.xsl">
			<dcterms:title>XSLT 1.0 Stylesheet for XSLTForms</dcterms:title>
			<dcterms:hasVersion>Rev. 525</dcterms:hasVersion>
			<dcterms:creator>Alain Couthures - agenceXML</dcterms:creator>
			<dcterms:conformsTo>XForms 1.1</dcterms:conformsTo>
			<dcterms:created>2012-02-06</dcterms:created>
			<dcterms:description>relevant submission</dcterms:description>
			<dcterms:format>text/xsl</dcterms:format>
		</rdf:Description>
	</rdf:RDF><xsl:output method="html" encoding="utf-8" omit-xml-declaration="no" indent="no" doctype-public="-//W3C//DTD XHTML 1.0 Transitional//EN" doctype-system="http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd"/>
		
		
		<xsl:param xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="baseuri"/>
		<xsl:param xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="pwd"/>
		<xsl:param xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="xsltforms_home"/>
		<xsl:param xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="xsltforms_caller"/>
		<xsl:param xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="xsltforms_config"/>
		<xsl:param xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="xsltforms_debug"/>
		<xsl:param xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="xsltforms_lang"/>
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="configdoc" select="document(concat($xsltforms_home,'config.xsl'))/xsl:stylesheet/xsl:template[@name='config']"/>
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="config0">
			<xsl:choose>
				<xsl:when test="$configdoc/properties"><xsl:copy-of select="$configdoc"/></xsl:when>
				<xsl:otherwise>
					<config xmlns="">
						<options>
						</options>
						<properties>
							<language>navigator</language>
							<calendar.day0>Mon</calendar.day0>
							<calendar.day1>Tue</calendar.day1>
							<calendar.day2>Wed</calendar.day2>
							<calendar.day3>Thu</calendar.day3>
							<calendar.day4>Fri</calendar.day4>
							<calendar.day5>Sat</calendar.day5>
							<calendar.day6>Sun</calendar.day6>
							<calendar.initDay>6</calendar.initDay>
							<calendar.month0>January</calendar.month0>
							<calendar.month1>February</calendar.month1>
							<calendar.month2>March</calendar.month2>
							<calendar.month3>April</calendar.month3>
							<calendar.month4>May</calendar.month4>
							<calendar.month5>June</calendar.month5>
							<calendar.month6>July</calendar.month6>
							<calendar.month7>August</calendar.month7>
							<calendar.month8>September</calendar.month8>
							<calendar.month9>October</calendar.month9>
							<calendar.month10>November</calendar.month10>
							<calendar.month11>December</calendar.month11>
							<format.date>MM/dd/yyyy</format.date>
							<format.datetime>MM/dd/yyyy hh:mm:ss</format.datetime>
							<format.decimal>.</format.decimal>
							<status>... Loading ...</status>
						</properties>
						<extensions/>
					</config>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:variable>
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="piform" select="processing-instruction('xml-form')"/>
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="piforminstanceid">
			<xsl:if test="contains($piform, ' instance=&quot;')">
				<xsl:value-of select="substring-before(substring-after($piform, ' instance=&quot;'), '&quot;')"/>
			</xsl:if>
		</xsl:variable>
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="piforminstance" select="/"/>
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="piformhref"><xsl:if test="contains($piform, ' href=&quot;')"><xsl:value-of select="substring-before(substring-after($piform, ' href=&quot;'), '&quot;')"/></xsl:if></xsl:variable>
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="piformdoc" select="document(concat($pwd, $piformhref))"/>
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="main" select="/"/>
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="profiler">
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms" xmlns:xsltforms="http://www.agencexml.com/xsltforms">
	<head>
		<title>XSLTForms Profiler</title>
		<xforms:model>
			<xforms:instance id="profile" src="javascript:window.opener.XsltForms_globals.xmlrequest('get','xsltforms-profiler')"/>
		</xforms:model>
	</head>
	<body>
		<xforms:group ref=".[not(*)]">
			<p>This profiler is accessible when pressing F1 key then activating the corresponding trigger.</p>
		</xforms:group>
		<xforms:group ref=".[*]">
			<h2>XSLTForms Profiler</h2>
			<p>
				<xforms:output value="xsltforms:date">
					<xforms:label>TimeStamp: </xforms:label>
				</xforms:output>
			</p>
			<h3><xforms:output value="xsltforms:location"/></h3>
			<p>Environment:
				<ul>
					<li>Browser: <xforms:output value="xsltforms:appname"/>&#xA0;<xforms:output value="xsltforms:appcodename"/>&#xA0;<xforms:output value="xsltforms:appversion"/></li>
					<li>User-Agent: <xforms:output value="xsltforms:useragent"/></li>
					<li>Initial XSLT Engine: <xforms:output value="xsltforms:xsltengine"/></li>
					<li>Current XSLT Engine: <xforms:output value="xsltforms:xsltengine2"/></li>
					<li>XSLTForms Version: <xforms:output value="xsltforms:version"/></li>
				</ul>
			</p>
			<table>
				<tr>
					<td>Instances:</td>
					<td>&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;</td>
					<td>Controls:</td>
				</tr>
				<tr valign="top">
					<td>
						<ul>
							<xforms:repeat nodeset="xsltforms:instances/xsltforms:instance">
								<li>"<xforms:output value="@id"/>": <xforms:output value="concat(., ' node', choose(. &gt; 1,'s',''))"/></li>
							</xforms:repeat>
						</ul>
					</td>
					<td>&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;</td>
					<td>
						<ul>
							<xforms:repeat nodeset="xsltforms:controls/xsltforms:control">
								<li>xforms:<xforms:output value="@type"/>: <xforms:output value="concat(., ' item', choose(. &gt; 1,'s',''))"/></li>
							</xforms:repeat>
						</ul>
					</td>
				</tr>
			</table>
			<table>
				<tr>
					<td>HTML Elements Count: <xforms:output value="xsltforms:htmlelements"/><br/>
						HTML Parsing Time: <xforms:output value="xsltforms:htmltime"/>ms<br/>
						HTML Creation Time: <xforms:output value="xsltforms:creatingtime"/>ms
					</td>
					<td>&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;&#xA0;</td>
					<td>XForms Init Time: <xforms:output value="xsltforms:inittime"/>ms<br/>
						XForms Refresh Count: <xforms:output value="xsltforms:refreshcount"/><br/>
						XForms Cumulative Refresh Time: <xforms:output value="xsltforms:refreshtime"/>ms
					</td>
				</tr>
			</table>
			<xforms:group ref=".[xsltforms:xpaths/xsltforms:xpath]">
				<p>XPath Expressions Cumulative Evaluation Time:
					<ul>
						<xforms:repeat nodeset="xsltforms:xpaths/xsltforms:xpath | xsltforms:xpaths/xsltforms:others">
							<li><xforms:output value="choose(local-name()='others', 'Others', concat('&quot;',@expr,'&quot;'))"/>: <xforms:output value="."/>ms</li>
						</xforms:repeat>
						<li>Total: <xforms:output value="xsltforms:xpaths/xsltforms:total"/>ms</li>
					</ul>
				</p>
			</xforms:group>
		</xforms:group>
	</body>
</html>
		</xsl:variable>
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="doc_id" select="'xsltforms-mainform'"/>
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="id_pf" select="'xsltforms-mainform-'"/>
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="jsid_pf" select="'xsltforms_subform.id + &quot;-'"/>
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="vn_pf" select="'xsltforms_'"/>
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="vn_subform" select="concat($vn_pf, 'subform')"/>
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xalan="http://xml.apache.org/xalan" match="/">
			<xsl:choose>
				<xsl:when test="$piform != ''">
					<xsl:choose>
						<xsl:when test="count($piformdoc/*) != 0">
							<xsl:apply-templates select="$piformdoc/*"/>
						</xsl:when>
						<xsl:otherwise>
							<xsl:choose>
								<xsl:when test="function-available('xalan:nodeset')">
									<xsl:apply-templates select="xalan:nodeset($profiler)/*"/>
								</xsl:when>
								<xsl:when test="function-available('exslt:node-set')">
									<xsl:apply-templates select="exslt:node-set($profiler)/*"/>
								</xsl:when>
								<xsl:when test="function-available('msxsl:node-set')">
									<xsl:apply-templates select="msxsl:node-set($profiler)/*"/>
								</xsl:when>
								<xsl:otherwise>
									<xsl:variable name="profiler0" select="$profiler"/>
									<xsl:apply-templates select="$profiler0/*"/>
								</xsl:otherwise>
							</xsl:choose>
						</xsl:otherwise>
					</xsl:choose>
				</xsl:when>
				<xsl:otherwise>
					<xsl:apply-templates/>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xalan="http://xml.apache.org/xalan" match="xhtml:html | html">
			<xsl:choose>
				<xsl:when test="function-available('xalan:nodeset')">
					<xsl:call-template name="html">
						<xsl:with-param name="config" select="xalan:nodeset($config0)/*"/>
					</xsl:call-template>
				</xsl:when>
				<xsl:when test="function-available('exslt:node-set')">
					<xsl:call-template name="html">
						<xsl:with-param name="config" select="exslt:node-set($config0)/*"/>
					</xsl:call-template>
				</xsl:when>
				<xsl:when test="function-available('msxsl:node-set')">
					<xsl:call-template name="html">
						<xsl:with-param name="config" select="msxsl:node-set($config0)/*"/>
					</xsl:call-template>
				</xsl:when>
				<xsl:otherwise>
					<xsl:variable name="config00" select="$config0"/>
					<xsl:call-template name="html">
						<xsl:with-param name="config" select="$config00/*"/>
					</xsl:call-template>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xalan="http://xml.apache.org/xalan" name="html">
			<xsl:param name="config"/>
			<!-- figure out what directory the XSL is loaded from and use it for everything else -->
			<xsl:variable name="pivalue" select="translate(normalize-space(/processing-instruction('xml-stylesheet')[1]), ' ', '')"/>
			<xsl:variable name="hrefquote" select="substring(substring-after($pivalue, 'href='), 1, 1)"/>
			<xsl:variable name="href" select="substring-before(substring-after($pivalue, concat('href=', $hrefquote)), $hrefquote)"/>
			<xsl:variable name="resourcesdir">
				<xsl:choose>
					<xsl:when test="$baseuri != ''">
						<xsl:value-of select="$baseuri"/>
					</xsl:when>
					<xsl:otherwise>
						<xsl:value-of select="substring-before($href, 'xsltforms.xsl')"/>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:variable name="lang">
				<xsl:choose>
					<xsl:when test="$xsltforms_lang != ''"><xsl:value-of select="$xsltforms_lang"/></xsl:when>
					<xsl:when test="$config/properties/language">
						<xsl:value-of select="$config/properties/language"/>
					</xsl:when>
					<xsl:otherwise>
						<xsl:variable name="xsltformspivalue" select="translate(normalize-space(/processing-instruction('xsltforms-options')[1]), ' ', '')"/>
						<xsl:variable name="langquote" select="substring(substring-after($xsltformspivalue, 'lang='), 1, 1)"/>
						<xsl:value-of select="substring-before(substring-after($xsltformspivalue, concat('lang=', $langquote)), $langquote)"/>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<html xmlns="http://www.w3.org/1999/xhtml">
				<xsl:copy-of select="@*"/>
				<xsl:comment>HTML elements and Javascript instructions generated by XSLTForms r525 - Copyright (C) 2008-2012 &lt;agenceXML&gt; - Alain COUTHURES - http://www.agencexml.com</xsl:comment>
				<xsl:variable name="option"> debug="yes" </xsl:variable>
				<xsl:variable name="displaydebug">
					<xsl:choose>
						<xsl:when test="$xsltforms_debug != ''"><xsl:value-of select="$xsltforms_debug"/></xsl:when>
						<xsl:when test="$config/options/debug">true</xsl:when>
						<xsl:when test="contains(concat(' ',translate(normalize-space(/processing-instruction('xsltforms-options')[1]), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'),' '),$option)">true</xsl:when>
						<xsl:otherwise>false</xsl:otherwise>
					</xsl:choose>
				</xsl:variable>
				<xsl:variable name="initdebug">
					<xsl:if test="$displaydebug = 'true'">XsltForms_globals.debugMode = true;XsltForms_globals.debugging();</xsl:if>
				</xsl:variable>
				<head>
					<xsl:copy-of select="xhtml:head/@* | head/@*"/>
					<meta http-equiv="Content-Type" content="text/html;charset=utf-8"/>
					<xsl:copy-of select="xhtml:meta[@http-equiv != 'Content-Type'] | head/meta[@http-equiv != 'Content-Type']"/>
					<script type="text/javascript">
						<xsl:text>var xsltforms_d0 = new Date();
</xsl:text>
					</script>
					<link type="text/css" href="{$resourcesdir}xsltforms.css" rel="stylesheet"/>
					<xsl:apply-templates select="xhtml:head/xhtml:*[local-name() != 'script' and local-name() != 'style' and local-name() != 'link' and local-name() != 'meta'] | xhtml:head/comment() | head/title | head/comment()" mode="nons"/>
					<xsl:apply-templates select="xhtml:head/xhtml:style | xhtml:head/xhtml:link | head/style | head/link">
						<xsl:with-param name="config" select="$config"/>
					</xsl:apply-templates>
					<script src="{$resourcesdir}xsltforms.js" type="text/javascript">/* */</script>
					<xsl:for-each select="$config/jsextensions">
						<script src="{$resourcesdir}{.}" type="text/javascript">/* */</script>
					</xsl:for-each>
					<xsl:apply-templates select="xhtml:head/xhtml:script | head/script"/>
					<xsl:if test="not($config/extensions/beforeInit) and not($config/extensions/onBeginInit) and not($config/extensions/onEndInit) and not($config/extensions/afterInit)">
						<xsl:copy-of select="$config/extensions/*"/>
					</xsl:if>
					<xsl:copy-of select="$config/extensions/beforeInit/*"/>
					<script type="text/javascript">
						<xsl:text>XsltForms_globals.language = "</xsl:text>
						<xsl:choose>
							<xsl:when test="$lang != ''">
								<xsl:value-of select="$lang"/>
							</xsl:when>
							<xsl:otherwise>default</xsl:otherwise>
						</xsl:choose>
						<xsl:text>";
</xsl:text>
						<xsl:text>XsltForms_globals.loadingMsg = "</xsl:text>
						<xsl:value-of select="$config/properties/status"/>
						<xsl:text>";
</xsl:text>
						<xsl:text>XsltForms_globals.valuesSeparator = "</xsl:text>
						<xsl:variable name="vsep" select="$config/properties/valuesseparator"/>
						<xsl:choose>
							<xsl:when test="$vsep != ''">
								<xsl:value-of select="$vsep"/>
							</xsl:when>
							<xsl:otherwise><xsl:text> </xsl:text></xsl:otherwise>
						</xsl:choose>
						<xsl:text>";
</xsl:text>
						<xsl:text>function </xsl:text>
						<xsl:value-of select="$vn_pf"/>
						<xsl:text>initImpl() {
</xsl:text>
						<xsl:text>XsltForms_globals.debugMode = </xsl:text>
						<xsl:value-of select="$displaydebug"/>
						<xsl:text>;
</xsl:text>
						<xsl:variable name="xsltversion">
							<xsl:if test="system-property('xsl:vendor')='Microsoft'">
								<xsl:value-of select="system-property('msxsl:version')"/>
							</xsl:if>
						</xsl:variable>
						<xsl:text>XsltForms_globals.xsltEngine = "</xsl:text>
						<xsl:value-of select="concat(system-property('xsl:vendor'),' ',system-property('xsl:vendor-url'),' ',$xsltversion)"/>
						<xsl:text>";
</xsl:text>
						<xsl:text>XsltForms_browser.jsFileName='xsltforms.js';
</xsl:text>
						<xsl:text>XsltForms_browser.isXhtml = false;
</xsl:text>
						<xsl:text>XsltForms_globals.ltchar = "&lt;"; XsltForms_browser.isEscaped = XsltForms_globals.ltchar.length != 1;
</xsl:text>
						<xsl:text>XsltForms_browser.config = null;
</xsl:text>
						<xsl:text>var xsltforms_subform_eltid = null;
</xsl:text>
						<xsl:text>var xsltforms_parentform = null;
</xsl:text>
						<xsl:text>/* XsltForms_MagicSeparator */ try {
</xsl:text>
						<xsl:text>XsltForms_browser.dialog.show('statusPanel');
</xsl:text>
						<xsl:text>var </xsl:text>
						<xsl:value-of select="$vn_subform"/>
						<xsl:text> = new XsltForms_subform(xsltforms_parentform, "</xsl:text>
						<xsl:value-of select="$doc_id"/>
						<xsl:text>", xsltforms_subform_eltid);
</xsl:text>
						<xsl:text>if (xsltforms_subform.id === "xsltforms-mainform") {
</xsl:text>
						<xsl:text>var d1 = new Date();
</xsl:text>
						<xsl:text>XsltForms_globals.htmltime = d1 - xsltforms_d0;
</xsl:text>
						<xsl:value-of select="$config/extensions/onBeginInit"/>
						<xsl:text>}
</xsl:text>
						<xsl:for-each select="//xforms:model/@schema">
							<xsl:call-template name="loadschemas">
								<xsl:with-param name="schemas" select="normalize-space(.)"/>
							</xsl:call-template>
						</xsl:for-each>
						<xsl:for-each select="//xforms:bind[contains(@type,':')]">
							<xsl:variable name="nstype" select="substring-before(@type,':')"/>
							<xsl:variable name="typename" select="substring-after(@type,':')"/>
							<xsl:if test="not(preceding::xforms:bind[starts-with(@type,$nstype)])">
								<xsl:variable name="nsmodel"><xsl:for-each select="//xforms:model[@schema]"><xsl:value-of select="document(@schema,/)/*[descendant::*[@name = $typename]]/@targetNamespace"/></xsl:for-each></xsl:variable>
								<xsl:variable name="nsuri">
									<xsl:choose>
										<xsl:when test="//namespace::*[name()=$nstype]"><xsl:value-of select="//namespace::*[name()=$nstype][1]"/></xsl:when>
										<xsl:when test="//*[starts-with(name(),concat($nstype,':'))]"><xsl:value-of select="namespace-uri(//*[starts-with(name(),concat($nstype,':'))][1])"/></xsl:when>
										<xsl:when test="//@*[starts-with(name(),concat($nstype,':'))]"><xsl:value-of select="namespace-uri(//@*[starts-with(name(),concat($nstype,':'))][1])"/></xsl:when>
										<xsl:when test="//xsd:schema[descendant::*[@name = $typename]]"><xsl:value-of select="//xsd:schema[descendant::*[@name = $typename]]/@targetNamespace"/></xsl:when>
										<xsl:when test="//@*[name() = concat('xmlns:',$nstype)]"><xsl:value-of select="//@*[name() = concat('xmlns:',$nstype)]"/></xsl:when>
										<xsl:when test="$nsmodel != ''"><xsl:value-of select="$nsmodel"/></xsl:when>
										<xsl:when test="$nstype = 'xs' or $nstype = 'xsd'">http://www.w3.org/2001/XMLSchema</xsl:when>
										<xsl:when test="$nstype = 'xf' or $nstype = 'xform'">http://www.w3.org/2002/xforms</xsl:when>
										<xsl:when test="$nstype = 'xsltforms'">http://www.agencexml.com/xsltforms</xsl:when>
										<xsl:otherwise>unknown (prefix:<xsl:value-of select="$nstype"/>)</xsl:otherwise>
									</xsl:choose>
								</xsl:variable>
								<xsl:text>XsltForms_schema.registerPrefix('</xsl:text><xsl:value-of select="$nstype"/><xsl:text>', '</xsl:text><xsl:value-of select="$nsuri"/><xsl:text>');
</xsl:text>
							</xsl:if>
						</xsl:for-each>
						<xsl:for-each select="//@xsi:type">
							<xsl:variable name="nstype" select="substring-before(.,':')"/>
							<xsl:variable name="typename" select="substring-after(@type,':')"/>
							<xsl:if test="not(preceding::*/@xsi:type[starts-with(.,$nstype)])">
								<xsl:variable name="nsuri">
									<xsl:choose>
										<xsl:when test="//namespace::*[name()=$nstype]"><xsl:value-of select="//namespace::*[name()=$nstype][1]"/></xsl:when>
										<xsl:when test="//*[starts-with(translate(name(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),concat(translate($nstype,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),':'))]"><xsl:value-of select="namespace-uri(//*[starts-with(translate(name(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),concat(translate($nstype,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),':'))][1])"/></xsl:when>
										<xsl:when test="//@*[starts-with(translate(name(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),concat(translate($nstype,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),':'))]"><xsl:value-of select="namespace-uri(//@*[starts-with(translate(name(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),concat(translate($nstype,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),':'))][1])"/></xsl:when>
										<xsl:when test="//xsd:schema[descendant::*[@name = $typename]]"><xsl:value-of select="//xsd:schema[descendant::*[@name = $typename]]/@targetNamespace"/></xsl:when>
										<xsl:when test="//xforms:model[@schema]"><xsl:for-each select="//xforms:model[@schema]"><xsl:value-of select="document(@schema,/)/*[descendant::*[@name = $typename]]/@targetNamespace"/></xsl:for-each></xsl:when>
										<xsl:when test="//@*[name() = concat('xmlns:',$nstype)]"><xsl:value-of select="//@*[name() = concat('xmlns:',$nstype)]"/></xsl:when>
										<xsl:when test="$nstype = 'xs' or $nstype = 'xsd'">http://www.w3.org/2001/XMLSchema</xsl:when>
										<xsl:when test="$nstype = 'xsltforms'">http://www.agencexml.com/xsltforms</xsl:when>
										<xsl:otherwise>unknown</xsl:otherwise>
									</xsl:choose>
								</xsl:variable>
								<xsl:text>XsltForms_schema.registerPrefix('</xsl:text><xsl:value-of select="$nstype"/><xsl:text>', '</xsl:text><xsl:value-of select="$nsuri"/><xsl:text>');
</xsl:text>
							</xsl:if>
						</xsl:for-each>
						<xsl:variable name="xexprs">
							<xexprs xmlns="">
								<xsl:for-each select="//xforms:*/@at | //xforms:*/@calculate | //xforms:*/@constraint | //xforms:*/@context | //xforms:*/@if | //xforms:*/@index | //xforms:*/@iterate | //xforms:*/@nodeset | //xforms:*/@origin | //xforms:*/@readonly | //xforms:*/@ref | //xforms:*/@relevant | //xforms:*/@required | //xforms:*/@target | //xforms:*/@targetref | //xforms:*/@value | //xforms:*/@while | //xforms:script[@type = 'application/xquery']">
									<xsl:sort select="."/>
									<xexpr><xsl:value-of select="."/></xexpr>
								</xsl:for-each>
								<!--
								<xsl:for-each select="//@*[contains(.,'{') and contains(substring-after(.,'{'),'}')]">
									<xsl:sort select="."/>
									<xsl:call-template name="avt2xexpr">
										<xsl:with-param name="a" select="."/>
									</xsl:call-template>
								</xsl:for-each>
								-->
								<xsl:if test="//xforms:bind[not(@nodeset)] | //xforms:group[@model and not(@ref)]"><xexpr>.</xexpr></xsl:if>
							</xexprs>
						</xsl:variable>
						<xsl:choose>
							<xsl:when test="function-available('xalan:nodeset')">
								<xsl:call-template name="xps">
									<xsl:with-param name="ps" select="xalan:nodeset($xexprs)/xexprs"/>
								</xsl:call-template>
							</xsl:when>
							<xsl:when test="function-available('exslt:node-set')">
								<xsl:call-template name="xps">
									<xsl:with-param name="ps" select="exslt:node-set($xexprs)/xexprs"/>
								</xsl:call-template>
							</xsl:when>
							<xsl:when test="function-available('msxsl:node-set')">
								<xsl:call-template name="xps">
									<xsl:with-param name="ps" select="msxsl:node-set($xexprs)/xexprs"/>
								</xsl:call-template>
							</xsl:when>
							<xsl:otherwise>
								<xsl:variable name="xexprs00" select="$xexprs"/>
								<xsl:call-template name="xps">
									<xsl:with-param name="ps" select="$xexprs00/xexprs"/>
								</xsl:call-template>
							</xsl:otherwise>
						</xsl:choose>
						<xsl:apply-templates select="/*" mode="script"/>
						<xsl:for-each select="*[namespace-uri() != 'http://www.w3.org/2002/xforms' and *[@ev:observer]]">
							<xsl:call-template name="listeners"/>
						</xsl:for-each>
						<xsl:text>XsltForms_browser.dialog.show('statusPanel');
</xsl:text>
						<xsl:text>if (xsltforms_subform.id === "xsltforms-mainform") {
</xsl:text>
						<xsl:text>XsltForms_browser.idPf = </xsl:text>
						<xsl:value-of select="$jsid_pf"/>
						<xsl:text>";
</xsl:text>
						<xsl:text>var </xsl:text>
						<xsl:value-of select="$vn_pf"/>
						<xsl:text>model_config = new XsltForms_model(</xsl:text>
						<xsl:value-of select="$vn_subform"/>
						<xsl:text>,</xsl:text>
						<xsl:value-of select="$jsid_pf"/>
						<xsl:text>model-config",null);
</xsl:text>
						<xsl:text>var </xsl:text>
						<xsl:value-of select="$vn_pf"/>
						<xsl:text>instance_config = new XsltForms_instance(</xsl:text>
						<xsl:value-of select="$vn_subform"/>
						<xsl:text>,</xsl:text>
						<xsl:value-of select="$jsid_pf"/>
						<xsl:text>instance-config",</xsl:text>
						<xsl:value-of select="$vn_pf"/>
						<xsl:text>model_config,true,'application/xml',null,'</xsl:text>
						<xsl:choose>
							<xsl:when test="$xsltforms_config != ''">
								<xsl:value-of select="normalize-space($xsltforms_config)"/>
							</xsl:when>
							<xsl:otherwise>
								<xsl:apply-templates select="$config/properties" mode="xml2string">
									<xsl:with-param name="root" select="true()"/>
								</xsl:apply-templates>
							</xsl:otherwise>
						</xsl:choose>
						<xsl:text>');
</xsl:text>
						<xsl:text>XsltForms_browser.config = </xsl:text>
						<xsl:value-of select="$vn_pf"/>
						<xsl:text>instance_config.doc.documentElement;
</xsl:text>
						<xsl:text>var d2 = new Date();
</xsl:text>
						<xsl:text>XsltForms_globals.creatingtime = d2 - d1;
</xsl:text>
						<xsl:text>XsltForms_globals.init();
</xsl:text>
						<xsl:value-of select="$config/extensions/onEndInit"/>
						<xsl:text>var d3 = new Date();
</xsl:text>
						<xsl:text>XsltForms_globals.inittime = d3 - d2;
</xsl:text>
						<xsl:text>} else {
</xsl:text>
						<xsl:text>xsltforms_subform.construct();
</xsl:text>
						<xsl:text>XsltForms_globals.openAction();
</xsl:text>
						<xsl:text>XsltForms_globals.refresh();
</xsl:text>
						<xsl:text>XsltForms_globals.closeAction();
</xsl:text>
						<xsl:text>XsltForms_browser.dialog.hide('statusPanel');
</xsl:text>
						<xsl:text>}
</xsl:text>
						<xsl:for-each select="//xforms:switch/xforms:case">
							<xsl:variable name="noselected" select="count(../xforms:case[@selected='true']) = 0"/>
							<xsl:variable name="otherselected" select="count(preceding-sibling::xforms:case[@selected='true']) != 0"/>
							<xsl:if test="not((not($noselected) and (not(@selected) or @selected != 'true')) or ($noselected and (position() != 1 or @selected)) or $otherselected)">
								<xsl:variable name="rid">
									<xsl:choose>
										<xsl:when test="@id"><xsl:value-of select="@id"/></xsl:when>
										<xsl:otherwise>
											<xsl:value-of select="$jsid_pf"/>
											<xsl:text>case-</xsl:text>
											<xsl:value-of select="count(preceding::xforms:case|ancestor::xforms:case)"/>
										</xsl:otherwise>
									</xsl:choose>
								</xsl:variable>
								<xsl:text>XsltForms_xmlevents.dispatch(document.getElementById('</xsl:text>
								<xsl:value-of select="$rid"/>
								<xsl:text>'), "xforms-select");
</xsl:text>
							</xsl:if>
						</xsl:for-each>
						<xsl:text>} catch (e) {
</xsl:text>
						<xsl:text>XsltForms_browser.dialog.hide('statusPanel');
</xsl:text>
						<xsl:text>if (!XsltForms_globals.debugMode) {
</xsl:text>
						<xsl:text>XsltForms_globals.debugMode = true;
</xsl:text>
						<xsl:text>XsltForms_globals.debugging();
</xsl:text>
						<xsl:text>}
</xsl:text>
						<xsl:text>alert("XSLTForms Exception\n--------------------------\n\nError initializing :\n\n"+(typeof(e.stack)=="undefined"?"":e.stack)+"\n\n"+(e.name?e.name+(e.message?"\n\n"+e.message:""):e));
</xsl:text>
						<xsl:text>} /* XsltForms_MagicSeparator */ };
</xsl:text>
						<xsl:if test="$xsltforms_caller = 'true'">
							<xsl:value-of select="$vn_pf"/><xsl:text>init();if (window.xf_user_init) xf_user_init();</xsl:text>
							<xsl:value-of select="$initdebug"/>
							<xsl:value-of select="xhtml:body/@onload"/>
							<xsl:value-of select="body/@onload"/>
							<xsl:text>;
</xsl:text>
						</xsl:if>
					</script>
					<script type="text/javascript">
						<xsl:text>function </xsl:text>
						<xsl:value-of select="$vn_pf"/>
						<xsl:text>init() {
</xsl:text>
						<xsl:text>try {
</xsl:text>
						<xsl:value-of select="$vn_pf"/>
						<xsl:text>initImpl();
</xsl:text>
						<xsl:text>} catch(e) {
</xsl:text>
						<xsl:text>alert("XSLTForms Exception\n--------------------------\n\nIncorrect Javascript code generation:\n\n"+(typeof(e.stack)=="undefined"?"":e.stack)+"\n\n"+(e.name?e.name+(e.message?"\n\n"+e.message:""):e));
</xsl:text>
						<xsl:text>}
</xsl:text>
						<xsl:text>}
</xsl:text>
					</script>
					<xsl:copy-of select="$config/extensions/afterInit/*"/>
				</head>
				<body>
					<xsl:if test="$xsltforms_caller != 'true'">
						<xsl:attribute name="onload"><xsl:value-of select="$vn_pf"/>init();if (window.xf_user_init) xf_user_init();<xsl:value-of select="$initdebug"/><xsl:value-of select="xhtml:body/@onload"/><xsl:value-of select="body/@onload"/></xsl:attribute>
					</xsl:if>
					<xsl:copy-of select="xhtml:body/@*[name() != 'onload'] | body/@*[name() != 'onload']"/>
					<xsl:comment>XsltForms_MagicSeparator</xsl:comment>
					<xsl:apply-templates select=".//xforms:message|.//ajx:confirm"/>
					<xsl:if test="//xforms:dialog">
						<div id="xforms-dialog-surround"><xsl:text/></div>
					</xsl:if>
					<xsl:apply-templates select="xhtml:body/node() | body/node()"/>
					<div id="xsltforms_console">&#xA0;<xsl:text/></div>
					<div id="statusPanel"><xsl:value-of select="$config/properties/status"/>&#xA0;<xsl:text/></div>
					<xsl:comment>XsltForms_MagicSeparator</xsl:comment>
				</body>
			</html>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="*[@*[contains(.,'{') and contains(substring-after(.,'{'),'}')] and not(@id)]">
			<xsl:copy>
				<xsl:call-template name="genid"/>
				<xsl:apply-templates select="@* | node()"/>
			</xsl:copy>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:dialog" priority="2">
			<div>
				<xsl:call-template name="style">
					<xsl:with-param name="class">xforms-dialog</xsl:with-param>
				</xsl:call-template>
				<xsl:apply-templates>
					<xsl:with-param name="appearance" select="'groupTitle'"/>
				</xsl:apply-templates>
			</div>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:group" priority="2">
			<xsl:call-template name="group"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="processing-instruction()"/>
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="comment()"/>
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:setvalue|xforms:insert|xforms:delete|xforms:action|xforms:load|xforms:toggle|xforms:send|xforms:setfocus" priority="2"/>
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:setindex|xforms:reset|xforms:refresh|xforms:rebuild|xforms:recalculate|xforms:revalidate|xforms:unload" priority="2"/>
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:show|xforms:hide" priority="2"/>
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:*" priority="1">
			<script>
				<xsl:text>XsltForms_browser.dialog.hide('statusPanel');
</xsl:text>
				<xsl:text>if (!XsltForms_globals.debugMode) {
</xsl:text>
				<xsl:text>XsltForms_globals.debugMode = true;
</xsl:text>
				<xsl:text>XsltForms_globals.debugging();
</xsl:text>
				<xsl:text>}
</xsl:text>
				<xsl:text>alert("XSLTForms Exception\n--------------------------\n\nError initializing :\n\nxforms:</xsl:text>
				<xsl:value-of select="local-name()"/>
				<xsl:text> is not supported");
</xsl:text>
			</script>
		</xsl:template>
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="ajx:start|ajx:stop"/>
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.w3.org/1999/xhtml" match="xhtml:br | br"><xsl:element name="br"/></xsl:template>
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.w3.org/1999/xhtml" match="xhtml:script[@type='text/javascript' and @src] | script[@type='text/javascript' and @src]">
			<script type="text/javascript" src="{@src}">/* */</script>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:input" priority="2">
			<xsl:param name="appearance" select="false()"/>
			<xsl:call-template name="field">
				<xsl:with-param name="appearance" select="$appearance"/>
				<xsl:with-param name="body">
					<input type="text">
						<xsl:call-template name="comun"/>
					</input>
				</xsl:with-param>
			</xsl:call-template>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:item" priority="2">
			<xsl:param name="type" select="false()"/> 
			<xsl:choose>
				<xsl:when test="$type">
					<div>
						<xsl:call-template name="genid"/>
						<xsl:call-template name="style">
							<xsl:with-param name="class">xforms-item</xsl:with-param>
						</xsl:call-template>
						<input type="{$type}" value="{xforms:value}">
							<xsl:call-template name="comun"/>
						</input>
						<xsl:apply-templates select="xforms:label">
							<xsl:with-param name="appearance">item</xsl:with-param>
						</xsl:apply-templates>
					</div>
				</xsl:when>
				<xsl:otherwise>
					<option value="{xforms:value}">
						<xsl:call-template name="genid"/>
						<xsl:value-of select="xforms:label"/>
					</option>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:itemset" priority="2">
			<xsl:param name="type" select="false()"/> 
			<xsl:choose>
				<xsl:when test="$type">
					<div>
						<xsl:call-template name="genid"/>
						<xsl:call-template name="style">
							<xsl:with-param name="class">xforms-itemset</xsl:with-param>
						</xsl:call-template>
						<div class="xforms-item">
							<xsl:attribute name="id"><xsl:choose><xsl:when test="@id"><xsl:value-of select="@id"/></xsl:when><xsl:otherwise><xsl:value-of select="$id_pf"/>itemset-item-<xsl:value-of select="count(preceding::xforms:itemset|ancestor::xforms:itemset)"/></xsl:otherwise></xsl:choose>
							</xsl:attribute>
							<input type="{$type}" value="{xforms:value}"/>
							<xsl:apply-templates select="xforms:label">
								<xsl:with-param name="appearance">item</xsl:with-param>
							</xsl:apply-templates>
						</div>
					</div>
				</xsl:when>
				<xsl:otherwise>
					<option class="xforms-disabled"><xsl:call-template name="genid"/>&#xA0;</option>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:label" priority="2">
			<xsl:param name="appearance" select="'undefined'"/>
			<xsl:choose>
				<xsl:when test="$appearance = 'groupTitle'">
					<div>
						<xsl:call-template name="genid"/>
						<xsl:call-template name="comunLabel">
							<xsl:with-param name="class">xforms-group-label</xsl:with-param>
						</xsl:call-template>
						<xsl:choose>
							<xsl:when test="count(./node()) &gt; 0"><xsl:apply-templates/>
							</xsl:when>
							<xsl:otherwise>&#xA0;<xsl:text/></xsl:otherwise>
						</xsl:choose>
					</div>
				</xsl:when>
				<xsl:when test="$appearance = 'treeLabel'">
					<div>
						<xsl:call-template name="genid"/>
						<xsl:call-template name="comunLabel">
							<xsl:with-param name="class">xforms-tree-label</xsl:with-param>
						</xsl:call-template>
						<xsl:choose>
							<xsl:when test="count(./node()) &gt; 0"><xsl:apply-templates/>
							</xsl:when>
							<xsl:otherwise>&#xA0;<xsl:text/></xsl:otherwise>
						</xsl:choose>
					</div>
				</xsl:when>
				<xsl:when test="$appearance = 'itemTreeLabel'">
					<a>
						<xsl:call-template name="genid"/>
						<xsl:call-template name="comunLabel">
							<xsl:with-param name="class">xforms-tree-item-label</xsl:with-param>
						</xsl:call-template>
						<xsl:choose>
							<xsl:when test="count(./node()) &gt; 0"><xsl:apply-templates/>
							</xsl:when>
						<xsl:otherwise>&#xA0;<xsl:text/></xsl:otherwise>
						</xsl:choose>
					</a>
				</xsl:when>
				<xsl:when test="$appearance = 'minimal'">
					<legend>
						<xsl:call-template name="genid"/>
						<xsl:call-template name="comunLabel"/>
						<xsl:choose>
							<xsl:when test="count(./node()) &gt; 0"><xsl:apply-templates/>
							</xsl:when>
							<xsl:otherwise>&#xA0;<xsl:text/></xsl:otherwise>
						</xsl:choose>
					</legend>
				</xsl:when>
				<xsl:when test="$appearance = 'caption'">
					<caption>
						<xsl:call-template name="genid"/>
						<xsl:call-template name="comunLabel"/>
						<xsl:choose>
							<xsl:when test="count(./node()) &gt; 0"><xsl:apply-templates/>
							</xsl:when>
							<xsl:otherwise>&#xA0;<xsl:text/></xsl:otherwise>
						</xsl:choose>
					</caption>
				</xsl:when>
				<xsl:when test="$appearance = 'table'">
					<span scope="col">
						<xsl:call-template name="genid"/>
						<xsl:call-template name="comunLabel"/>
						<xsl:choose>
							<xsl:when test="count(./node()) &gt; 0"><xsl:apply-templates/>
							</xsl:when>
							<xsl:otherwise>&#xA0;<xsl:text/></xsl:otherwise>
						</xsl:choose>
					</span>
				</xsl:when>
				<xsl:when test="$appearance = 'item'">
					<span>
						<xsl:call-template name="genid"/>
						<xsl:call-template name="style">
							<xsl:with-param name="class">xforms-item-label</xsl:with-param>
						</xsl:call-template>
						<xsl:choose>
							<xsl:when test="count(./node()) &gt; 0"><xsl:apply-templates/>
							</xsl:when>
							<xsl:otherwise>&#xA0;<xsl:text/></xsl:otherwise>
						</xsl:choose>
					</span>
				</xsl:when>
				<xsl:when test="$appearance = 'field-minimal' and ../xforms:help[@appearance='minimal' and @href]">
					<a class="xforms-help xforms-minimal-help-link" href="{../xforms:help/@href}">
						<label>
							<xsl:call-template name="genid"/>
							<xsl:call-template name="comunLabel">
								<xsl:with-param name="class">xforms-appearance-minimal</xsl:with-param>
							</xsl:call-template>
							<xsl:choose>
								<xsl:when test="count(./node()) &gt; 0"><xsl:apply-templates/>
								</xsl:when>
								<xsl:otherwise>&#xA0;<xsl:text/></xsl:otherwise>
							</xsl:choose>
						</label>
					</a>
				</xsl:when>
				<xsl:when test="$appearance = 'field-minimal' and not(../xforms:help[@appearance='minimal' and @href])">
					<label>
						<xsl:call-template name="genid"/>
						<xsl:call-template name="comunLabel">
							<xsl:with-param name="class">xforms-appearance-minimal</xsl:with-param>
						</xsl:call-template>
						<xsl:choose>
							<xsl:when test="count(./node()) &gt; 0"><xsl:apply-templates/>
							</xsl:when>
							<xsl:otherwise>&#xA0;<xsl:text/></xsl:otherwise>
						</xsl:choose>
					</label>
				</xsl:when>
				<xsl:when test="$appearance = 'tabs'">
					<xsl:variable name="pid">
						<xsl:choose>
							<xsl:when test="../@id"><xsl:value-of select="../@id"/></xsl:when>
							<xsl:otherwise>
								<xsl:for-each select="parent::*">
									<xsl:variable name="lname" select="local-name()"/>
									<xsl:value-of select="$id_pf"/>
									<xsl:value-of select="$lname"/>
									<xsl:text>-</xsl:text>
									<xsl:value-of select="count(preceding::*[local-name()=$lname]|ancestor::*[local-name()=$lname])"/>
								</xsl:for-each>
							</xsl:otherwise>
						</xsl:choose>
					</xsl:variable>
					<a href="javascript:void(0);" onclick="XsltForms_toggle.toggle('{$pid}');">
						<xsl:call-template name="genid"/>
						<xsl:call-template name="comunLabel"/>
						<xsl:choose>
							<xsl:when test="count(./node()) &gt; 0"><xsl:apply-templates/>
							</xsl:when>
							<xsl:otherwise>&#xA0;<xsl:text/></xsl:otherwise>
						</xsl:choose>
					</a>
				</xsl:when>
				<xsl:when test="$appearance = 'span'">
					<span>
						<xsl:call-template name="genid"/>
						<xsl:call-template name="comunLabel"/>
						<xsl:choose>
							<xsl:when test="count(./node()) &gt; 0"><xsl:apply-templates/>
							</xsl:when>
							<xsl:otherwise>&#xA0;<xsl:text/></xsl:otherwise>
						</xsl:choose>
					</span>
				</xsl:when>
				<xsl:when test="local-name(../..) = 'tabs' or $appearance = 'none' or $appearance = 'groupNone'"/>
				<xsl:when test="../xforms:help[@appearance='minimal' and @href]">
					<a class="xforms-help xforms-minimal-help-link" href="{../xforms:help/@href}">
						<label>
							<xsl:call-template name="genid"/>
							<xsl:call-template name="comunLabel"/>
							<xsl:choose>
								<xsl:when test="count(./node()) &gt; 0"><xsl:apply-templates/>
								</xsl:when>
								<xsl:otherwise>&#xA0;<xsl:text/></xsl:otherwise>
							</xsl:choose>
						</label>
					</a>
				</xsl:when>
				<xsl:otherwise>
					<label>
						<xsl:call-template name="genid"/>
						<xsl:call-template name="comunLabel"/>
						<xsl:choose>
							<xsl:when test="count(./node()) &gt; 0"><xsl:apply-templates/>
							</xsl:when>
						<xsl:otherwise>&#xA0;<xsl:text/></xsl:otherwise>
						</xsl:choose>
					</label>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:message|ajx:confirm" priority="2">
			<span class="xforms-message">
				<xsl:call-template name="genid"/>
				<xsl:apply-templates/>
			</span>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="node()|@*" priority="-2">
			<xsl:param name="appearance" select="@appearance"/>
			<xsl:copy>
				<xsl:apply-templates select="@*"/>
				<xsl:apply-templates select="node()">
					<xsl:with-param name="appearance" select="$appearance"/>
				</xsl:apply-templates>
			</xsl:copy>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="*" mode="nons">
			<xsl:element name="{local-name()}" namespace="http://www.w3.org/1999/xhtml">
				<xsl:apply-templates select="@*" mode="nons"/>
				<xsl:apply-templates select="node()" mode="nons"/>
			</xsl:element>
		</xsl:template>
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="@*" mode="nons">
			<xsl:attribute name="{local-name()}"><xsl:value-of select="."/></xsl:attribute>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:output" priority="2">
			<xsl:param name="appearance" select="false()"/>
				<xsl:call-template name="field">
					<xsl:with-param name="appearance" select="$appearance"/>
					<xsl:with-param name="body">
						<xsl:choose>
							<xsl:when test="starts-with(@mediatype,'image/') and @mediatype != 'image/svg+xml'">
								<img>
									<xsl:call-template name="comun"/>
								</img>
							</xsl:when>
							<xsl:otherwise><span><xsl:call-template name="comun"/>&#xA0;<xsl:text/></span></xsl:otherwise>
						</xsl:choose>
					</xsl:with-param>
				</xsl:call-template>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:repeat" priority="2">
			<xsl:call-template name="group">
				<xsl:with-param name="type" select="'repeat'"/>
			</xsl:call-template>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:secret" priority="2">
			<xsl:param name="appearance" select="false()"/>
			<xsl:call-template name="field">
				<xsl:with-param name="appearance" select="$appearance"/>
				<xsl:with-param name="body">
					<input type="password">
						<xsl:call-template name="comun"/>
					</input>
				</xsl:with-param>
			</xsl:call-template>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:select1|xforms:select" priority="2">
			<xsl:param name="appearance" select="false()"/>
			<xsl:variable name="body">
				<xsl:choose>
					<xsl:when test="@appearance='compact'">
						<select size="4">
							<xsl:call-template name="comun"/>
							<xsl:if test="local-name() = 'select'">
								<xsl:attribute name="multiple">true</xsl:attribute>
							</xsl:if>
							<xsl:for-each select="xforms:item|xforms:itemset|xforms:choices">
								<xsl:choose>
									<xsl:when test="local-name() = 'choices'">
										<optgroup label="{xforms:label/text()}">
											<xsl:apply-templates select="xforms:item|xforms:itemset"/>
										</optgroup>
									</xsl:when>
									<xsl:otherwise>
										<xsl:apply-templates select="."/>
									</xsl:otherwise>
								</xsl:choose>
							</xsl:for-each>
						</select>
					</xsl:when>
					<xsl:when test="@appearance='full'">
						<span>
							<xsl:call-template name="comun"/>
							<xsl:variable name="type">
								<xsl:choose>
									<xsl:when test="local-name() = 'select'">checkbox</xsl:when>
									<xsl:otherwise>radio</xsl:otherwise>
								</xsl:choose>
							</xsl:variable>
							<xsl:for-each select="xforms:item|xforms:itemset|xforms:choices">
								<xsl:choose>
									<xsl:when test="local-name() = 'choices'">
										<optgroup label="{xforms:label/text()}">
											<xsl:apply-templates select="xforms:item|xforms:itemset">
												<xsl:with-param name="type" select="$type"/>
											</xsl:apply-templates>
										</optgroup>
									</xsl:when>
									<xsl:otherwise>
										<xsl:apply-templates select=".">
											<xsl:with-param name="type" select="$type"/>
										</xsl:apply-templates>
									</xsl:otherwise>
								</xsl:choose>
							</xsl:for-each>
						</span>
					</xsl:when>
					<xsl:otherwise>
						<select>
							<xsl:call-template name="comun"/>
							<xsl:if test="local-name() = 'select'">
								<xsl:attribute name="multiple">true</xsl:attribute>
								<xsl:attribute name="size">
									<xsl:value-of select="count(descendant::xforms:item)"/>
								</xsl:attribute>
							</xsl:if>
							<xsl:for-each select="xforms:item|xforms:itemset|xforms:choices">
								<xsl:choose>
									<xsl:when test="local-name() = 'choices'">
										<optgroup label="{xforms:label/text()}">
											<xsl:apply-templates select="xforms:item|xforms:itemset"/>
										</optgroup>
									</xsl:when>
									<xsl:otherwise>
										<xsl:apply-templates select="."/>
									</xsl:otherwise>
								</xsl:choose>
							</xsl:for-each>
						</select>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:call-template name="field">
				<xsl:with-param name="appearance" select="$appearance"/>
				<xsl:with-param name="body" select="$body"/>
			</xsl:call-template>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:switch" priority="2">
			<div>
				<xsl:call-template name="genid"/>
				<xsl:call-template name="style">
					<xsl:with-param name="class">xforms-switch</xsl:with-param>
				</xsl:call-template>
				<xsl:variable name="noselected" select="count(xforms:case[@selected='true']) = 0"/>
				<xsl:for-each select="xforms:case">
					<xsl:variable name="otherselected" select="count(preceding-sibling::xforms:case[@selected='true']) != 0"/>
					<div>
						<xsl:call-template name="genid"/>
						<xsl:call-template name="style">
							<xsl:with-param name="class">xforms-case</xsl:with-param>
						</xsl:call-template>
						<xsl:if test="(not($noselected) and (not(@selected) or @selected != 'true')) or ($noselected and (position() != 1 or @selected)) or $otherselected">
							<xsl:attribute name="style">display:none;</xsl:attribute>
						</xsl:if>
						<xsl:apply-templates/>
					</div>
				</xsl:for-each>
			</div>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xhtml:thead[xforms:repeat] | thead[xforms:repeat] | xhtml:tbody[xforms:repeat] | tbody[xforms:repeat] | xhtml:tfoot[xforms:repeat] | tfoot[xforms:repeat] | xhtml:tr[xforms:repeat] | tr[xforms:repeat]">
			<xsl:apply-templates select="xforms:repeat"/>
		</xsl:template>
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xhtml:table[xforms:repeat] | table[xforms:repeat]">
			<table>
				<xsl:apply-templates select="xforms:repeat"/>
			</table>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="ajx:tabs">
			<div>
				<xsl:call-template name="style">
					<xsl:with-param name="class">ajx-tabs</xsl:with-param>
				</xsl:call-template>
				<xsl:variable name="defselect" select="not(ajx:tab[@selected = 'true'])"/>
				<ul class="ajx-tabs-list">
					<xsl:for-each select="ajx:tab">
						<li>
							<xsl:if test="@selected = 'true' or ($defselect and position() = 1)">
								<xsl:attribute name="class">ajx-tab-selected</xsl:attribute>
							</xsl:if>
							<xsl:apply-templates select="xforms:label">
								<xsl:with-param name="appearance">tabs</xsl:with-param>
							</xsl:apply-templates>
						</li>
					</xsl:for-each>
				</ul>
				<xsl:for-each select="ajx:tab">
					<div>
						<xsl:call-template name="style">
							<xsl:with-param name="class">ajx-tab</xsl:with-param>
						</xsl:call-template>
						<xsl:call-template name="genid"/>
						<xsl:if test="(not(@selected) or @selected != 'true') and not($defselect and position() = 1)">
							<xsl:attribute name="style">display:none;</xsl:attribute>
						</xsl:if>
						<xsl:apply-templates/>
					</div>
				</xsl:for-each>
			</div>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="text()[normalize-space(.)='']" priority="-1"/>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:textarea" priority="2">
			<xsl:param name="appearance" select="false()"/>
			<xsl:call-template name="field">
				<xsl:with-param name="appearance" select="$appearance"/>
				<xsl:with-param name="body">
					<textarea><xsl:copy-of select="@*[local-name() != 'ref']"/><xsl:call-template name="comun"/><xsl:text/>&#xA0;</textarea>
				</xsl:with-param>
			</xsl:call-template>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xhtml:tr[parent::xforms:repeat] | tr[parent::xforms:repeat] | xhtml:td[parent::xforms:repeat] | td[parent::xforms:repeat]">
			<xsl:apply-templates select="node()"/>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:tree">
			<div>
				<xsl:call-template name="style">
					<xsl:with-param name="class">xforms-tree</xsl:with-param>
				</xsl:call-template>
				<xsl:apply-templates select="xforms:label">
				<xsl:with-param name="appearance">treeLabel</xsl:with-param>
				</xsl:apply-templates>
				<ul>
					<li class="xforms-tree-item">
						<span class="xforms-tree-item-button"><xsl:text/></span>
						<xsl:apply-templates select="xforms:item/xforms:label">
							<xsl:with-param name="appearance">itemTreeLabel</xsl:with-param>
						</xsl:apply-templates>
					</li>
				</ul>
			</div>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:trigger|xforms:submit" priority="2">
			<xsl:variable name="innerbody">
				<xsl:choose>
					<xsl:when test="xforms:label">
						<xsl:apply-templates select="xforms:label">
							<xsl:with-param name="appearance" select="'span'"/>
						</xsl:apply-templates>
					</xsl:when>
					<xsl:otherwise>&#xA0;</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:call-template name="field">
				<xsl:with-param name="appearance">none</xsl:with-param>
				<xsl:with-param name="body">
					<xsl:choose>
						<xsl:when test="@appearance = 'minimal'">
							<a href="javascript:void(0);">
								<xsl:copy-of select="$innerbody"/>
							</a>
						</xsl:when>
						<xsl:otherwise>
							<button type="button">
								<xsl:copy-of select="$innerbody"/>
							</button>
						</xsl:otherwise>
					</xsl:choose>
				</xsl:with-param>
			</xsl:call-template>
		</xsl:template>
	
		
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="field">
			<xsl:param name="appearance" select="false()"/>
			<xsl:param name="body"/>
			<span>
				<xsl:call-template name="genid"/>
				<xsl:call-template name="style">
					<xsl:with-param name="class">xforms-control xforms-<xsl:value-of select="local-name()"/><xsl:choose><xsl:when test="local-name()='trigger' or local-name()='submit' or string(xforms:label)=''"> xforms-appearance-minimal</xsl:when><xsl:when test="@appearance"> xforms-appearance-<xsl:value-of select="@appearance"/></xsl:when><xsl:otherwise> xforms-appearance</xsl:otherwise></xsl:choose></xsl:with-param>
				</xsl:call-template>
				<xsl:if test="local-name() != 'trigger' and local-name() != 'submit' and local-name() != 'reset' and local-name() != 'output' and xforms:label/node() and string($appearance) != 'none'">
					<span class="focus">&#xA0;<xsl:text/></span>
				</xsl:if>
				<xsl:if test="local-name() != 'trigger' and local-name() != 'submit' and local-name() != 'reset' and xforms:label/node() and string($appearance) != 'none'">
					<xsl:choose>
						<xsl:when test="$appearance = 'minimal'">
							<xsl:apply-templates select="xforms:label">
								<xsl:with-param name="appearance">field-minimal</xsl:with-param>
							</xsl:apply-templates>
						</xsl:when>
						<xsl:otherwise>
							<xsl:apply-templates select="xforms:label"/>
						</xsl:otherwise>
					</xsl:choose>
				</xsl:if>
				<span class="value">
					<xsl:copy-of select="$body"/>
				</span>
				<xsl:if test="@ajx:aid-button = 'true'">
					<button type="button" class="aid-button">...</button>
				</xsl:if>
				<xsl:if test="local-name() != 'output'">
					<span class="xforms-required-icon">*</span>
				</xsl:if>
				<span class="xforms-alert">
					<span class="xforms-alert-icon">
						<xsl:if test="xforms:alert">
							<xsl:attribute name="onmouseover">XsltForms_browser.show(this, null, true)</xsl:attribute>
							<xsl:attribute name="onmouseout">XsltForms_browser.show(this, null, false)</xsl:attribute>
						</xsl:if>
						<xsl:text>&#xA0;</xsl:text>
					</span>
					<xsl:if test="xforms:alert">
						<xsl:variable name="aid">
							<xsl:choose>
								<xsl:when test="xforms:alert/@id"><xsl:value-of select="xforms:alert/@id"/></xsl:when>
								<xsl:otherwise>
									<xsl:for-each select="xforms:alert[1]">
										<xsl:variable name="lname" select="local-name()"/>
										<xsl:value-of select="$id_pf"/>
										<xsl:value-of select="$lname"/>
										<xsl:text>-</xsl:text>
										<xsl:value-of select="count(preceding::*[local-name()=$lname]|ancestor::*[local-name()=$lname])"/>
									</xsl:for-each>
								</xsl:otherwise>
							</xsl:choose>
						</xsl:variable>
						<span class="xforms-alert-value" id="{$aid}">
							<xsl:apply-templates select="xforms:alert/node()"/>
						</span>
					</xsl:if>
				</span>
				<xsl:if test="xforms:hint">
					<span class="xforms-hint">
						<span class="xforms-hint-icon" onmouseover="XsltForms_browser.show(this, 'hint', true)" onmouseout="XsltForms_browser.show(this, 'hint', false)">&#xA0;<xsl:text/></span>
						<xsl:variable name="hid">
							<xsl:choose>
								<xsl:when test="xforms:hint/@id"><xsl:value-of select="xforms:hint/@id"/></xsl:when>
								<xsl:otherwise>
									<xsl:for-each select="xforms:hint[1]">
										<xsl:variable name="lname" select="local-name()"/>
										<xsl:value-of select="$id_pf"/>
										<xsl:value-of select="$lname"/>
										<xsl:text>-</xsl:text>
										<xsl:value-of select="count(preceding::*[local-name()=$lname]|ancestor::*[local-name()=$lname])"/>
									</xsl:for-each>
								</xsl:otherwise>
							</xsl:choose>
						</xsl:variable>
						<span class="xforms-hint-value" id="{$hid}">
							<xsl:apply-templates select="xforms:hint/node()"/>
						</span>
					</span>
				</xsl:if>
				<xsl:if test="xforms:help[not(@appearance='minimal')]">
					<span class="xforms-help">
						<span class="xforms-help-icon" onmouseover="XsltForms_browser.show(this, 'help', true)" onmouseout="XsltForms_browser.show(this, 'help', false)">&#xA0;<xsl:text/></span>
						<xsl:variable name="hid">
							<xsl:choose>
								<xsl:when test="xforms:help/@id"><xsl:value-of select="xforms:help/@id"/></xsl:when>
								<xsl:otherwise>
									<xsl:for-each select="xforms:help[1]">
										<xsl:variable name="lname" select="local-name()"/>
										<xsl:value-of select="$id_pf"/>
										<xsl:value-of select="$lname"/>
										<xsl:text>-</xsl:text>
										<xsl:value-of select="count(preceding::*[local-name()=$lname]|ancestor::*[local-name()=$lname])"/>
									</xsl:for-each>
								</xsl:otherwise>
							</xsl:choose>
						</xsl:variable>
						<span class="xforms-help-value" id="{$hid}">
							<xsl:apply-templates select="xforms:help/node()"/>
						</span>
					</span>
				</xsl:if>
			</span>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="group">
			<xsl:param name="type" select="'group'"/>
			<xsl:param name="appearance" select="@appearance"/>
			<xsl:choose>
				<xsl:when test="$type = 'repeat'">
					<xsl:variable name="mainelt">
						<xsl:choose>
							<xsl:when test="parent::*[local-name()='table']">tbody</xsl:when>
							<xsl:when test="parent::*[local-name()='thead']">thead</xsl:when>
							<xsl:when test="parent::*[local-name()='tbody']">tbody</xsl:when>
							<xsl:when test="parent::*[local-name()='tfoot']">tfoot</xsl:when>
							<xsl:when test="parent::*[local-name()='tr']">tr</xsl:when>
							<xsl:otherwise>div</xsl:otherwise>
						</xsl:choose>
					</xsl:variable>
					<xsl:element name="{$mainelt}">
						<xsl:call-template name="genid"/>
						<xsl:call-template name="style">
							<xsl:with-param name="class">
								<xsl:choose>
									<xsl:when test="$mainelt != 'div' and parent::*/parent::xforms:repeat">xforms-repeat xforms-repeat-item</xsl:when>
									<xsl:otherwise>xforms-repeat</xsl:otherwise>
								</xsl:choose>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:if test="$mainelt != 'div' and parent::*/parent::xforms:repeat">
							<xsl:attribute name="mixedrepeat">true</xsl:attribute>
						</xsl:if>
						<xsl:if test="parent::*[local-name()='table']">
							<xsl:apply-templates select="preceding-sibling::node()"/>
						</xsl:if>
						<xsl:variable name="itemelt">
							<xsl:choose>
								<xsl:when test="parent::*[local-name()='table' or local-name()='thead' or local-name()='tbody' or local-name()='tfoot']">tr</xsl:when>
								<xsl:when test="parent::*[local-name()='tr']">td</xsl:when>
								<xsl:otherwise>div</xsl:otherwise>
							</xsl:choose>
						</xsl:variable>
						<xsl:choose>
							<xsl:when test="$itemelt = 'tr' and */xforms:repeat">
								<xsl:apply-templates>
									<xsl:with-param name="appearance">
										<xsl:if test="$appearance = 'minimal'">minimal</xsl:if>
									</xsl:with-param>
								</xsl:apply-templates>
							</xsl:when>
							<xsl:otherwise>
								<xsl:element name="{$itemelt}">
									<xsl:attribute name="class">xforms-repeat-item</xsl:attribute>
									<xsl:apply-templates>
										<xsl:with-param name="appearance">
											<xsl:if test="$appearance = 'minimal'">minimal</xsl:if>
										</xsl:with-param>
									</xsl:apply-templates>
								</xsl:element>
							</xsl:otherwise>
						</xsl:choose>
						<xsl:if test="parent::*[local-name()='table']">
							<xsl:apply-templates select="following-sibling::node()"/>
						</xsl:if>
					</xsl:element>
				</xsl:when>
				<xsl:when test="$appearance = 'compact'">
					<span>
						<xsl:call-template name="genid"/>
						<xsl:call-template name="style">
							<xsl:with-param name="class">xforms-<xsl:value-of select="$type"/></xsl:with-param>
						</xsl:call-template>
						<xsl:if test="$type = 'case' and (not(@selected) or @selected != 'true')">
							<xsl:attribute name="style">display:none;</xsl:attribute>
						</xsl:if>
						<xsl:if test="xforms:label">
							<xsl:apply-templates select="xforms:label">
								<xsl:with-param name="appearance" select="'caption'"/>
							</xsl:apply-templates>
						</xsl:if>
						<xsl:for-each select="xforms:*">
							<xsl:choose>
								<xsl:when test="(not(xforms:label) and local-name() != 'label') or local-name() = 'trigger' or local-name() = 'submit'">
									<span class="xforms-label">&#xA0;</span>
								</xsl:when>
								<xsl:otherwise>
									<xsl:apply-templates select="xforms:label">
										<xsl:with-param name="appearance" select="'table'"/>
									</xsl:apply-templates>
								</xsl:otherwise>
							</xsl:choose>
						</xsl:for-each>
						<xsl:if test="$type = 'repeat'">
							<xsl:attribute name="class">xforms-repeat-item</xsl:attribute>
						</xsl:if>
						<xsl:for-each select="xforms:input|xforms:output|xforms:select|xforms:select1|xforms:textarea|xforms:secret|xforms:group|xforms:repeat|xforms:switch|xforms:trigger|xforms:submit">
							<span>
								<xsl:apply-templates select=".">
									<xsl:with-param name="appearance" select="'none'"/>
								</xsl:apply-templates>
							</span>
						</xsl:for-each>
					</span>
				</xsl:when>
				<xsl:when test="$appearance = 'minimal'">
					<fieldset>
						<xsl:call-template name="genid"/>
						<xsl:call-template name="style">
							<xsl:with-param name="class">xforms-<xsl:value-of select="$type"/>
							</xsl:with-param>
						</xsl:call-template>
						<xsl:if test="$type = 'case' and (not(@selected) or @selected != 'true')">
							<xsl:attribute name="style">display:none;</xsl:attribute>
						</xsl:if>
						<xsl:apply-templates>
							<xsl:with-param name="appearance">minimal</xsl:with-param>
						</xsl:apply-templates>
					</fieldset>
				</xsl:when>
				<xsl:otherwise>
					<xsl:variable name="mainelt">
						<xsl:choose>
							<xsl:when test="parent::*[local-name()='table']">tbody</xsl:when>
							<xsl:when test="parent::*[local-name()='thead']">thead</xsl:when>
							<xsl:when test="parent::*[local-name()='tbody']">tbody</xsl:when>
							<xsl:when test="parent::*[local-name()='tfoot']">tfoot</xsl:when>
							<xsl:when test="parent::*[local-name()='tr']">tr</xsl:when>
							<xsl:otherwise>div</xsl:otherwise>
						</xsl:choose>
					</xsl:variable>
					<xsl:element name="{$mainelt}">
						<xsl:call-template name="genid"/>
						<xsl:call-template name="style">
							<xsl:with-param name="class">xforms-<xsl:value-of select="$type"/></xsl:with-param>
						</xsl:call-template>
						<xsl:if test="$type = 'case' and (not(@selected) or @selected != 'true')">
							<xsl:attribute name="style">display:none;</xsl:attribute>
						</xsl:if>
						<xsl:apply-templates select="xforms:label">
							<xsl:with-param name="appearance" select="'groupTitle'"/>
						</xsl:apply-templates>
						<div class="xforms-{$type}-content">
							<xsl:apply-templates>
								<xsl:with-param name="appearance" select="'groupNone'"/>
							</xsl:apply-templates>
						</div>
					</xsl:element>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="comun">
			<xsl:attribute name="class">xforms-value</xsl:attribute>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="comunLabel">
			<xsl:param name="class"/>
			<xsl:call-template name="genid"/>
			<xsl:call-template name="style">
				<xsl:with-param name="class">xforms-label <xsl:value-of select="$class"/></xsl:with-param>
			</xsl:call-template>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="style">
			<xsl:param name="class"/>
			<xsl:if test="@id">
				<xsl:attribute name="id"><xsl:value-of select="@id"/></xsl:attribute>
			</xsl:if>
			<xsl:choose>
				<xsl:when test="contains(concat(' ',$class, ' '), ' xforms-label ') or contains(concat(' ',$class, ' '), ' xforms-item-label ') or contains(concat(' ',$class, ' '), ' xforms-case ') or contains(concat(' ',$class, ' '), ' ajx-tab ') or contains(concat(' ',$class, ' '), ' ajx-tabs ') or contains(concat(' ',$class, ' '), ' xforms-dialog ')">
					<xsl:attribute name="class"><xsl:value-of select="normalize-space(concat(@class, ' ', $class))"/></xsl:attribute>
				</xsl:when>
				<xsl:otherwise>
					<xsl:attribute name="class"><xsl:value-of select="normalize-space(concat(@class, ' xforms-disabled ', $class))"/></xsl:attribute>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="genid">
			<xsl:variable name="lname" select="local-name()"/>
			<xsl:variable name="nsuri" select="namespace-uri()"/>
			<xsl:attribute name="id"><xsl:choose><xsl:when test="@id"><xsl:value-of select="@id"/></xsl:when><xsl:otherwise><xsl:value-of select="$id_pf"/><xsl:value-of select="$lname"/>-<xsl:value-of select="count(preceding::*[local-name()=$lname and namespace-uri()=$nsuri]|ancestor::*[local-name()=$lname and namespace-uri()=$nsuri])"/></xsl:otherwise></xsl:choose>
			</xsl:attribute>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="toScriptParam">
			<xsl:param name="p"/>
			<xsl:param name="default"/>
			<xsl:choose>
				<xsl:when test="$p">"<xsl:call-template name="toXPathExpr"><xsl:with-param name="p" select="$p"/></xsl:call-template>"</xsl:when>
				<xsl:when test="$default != ''"><xsl:value-of select="$default"/></xsl:when>
				<xsl:otherwise>null</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="toScriptBinding">
			<xsl:param name="p"/>
			<xsl:param name="model" select="concat('&quot;',string(@model),'&quot;')"/>
			<xsl:param name="type" select="'null'"/>
			<xsl:param name="mip"/>
			<xsl:variable name="xpath">
				<xsl:choose>
					<xsl:when test="$p"><xsl:value-of select="$p"/></xsl:when>
					<xsl:when test="@value"><xsl:value-of select="@value"/></xsl:when>
					<xsl:when test="@model != '' and $mip = ''">.</xsl:when>
				</xsl:choose>
			</xsl:variable>
			<xsl:variable name="type2">
				<xsl:choose>
					<xsl:when test="$type != 'null'">"<xsl:value-of select="$type"/>"</xsl:when>
					<xsl:when test="$p != '' or (@model != '' and $mip = '')">null</xsl:when>
					<xsl:otherwise>"xsd:string"</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:choose>
				<xsl:when test="@bind">new XsltForms_<xsl:value-of select="$mip"/>binding(null, null, null, "<xsl:call-template name="toXPathExpr"><xsl:with-param name="p" select="@bind"/></xsl:call-template>")</xsl:when>
				<xsl:when test="$xpath != '' and $model != '&quot;&quot;'">new XsltForms_<xsl:value-of select="$mip"/>binding(<xsl:value-of select="$type2"/>, "<xsl:call-template name="toXPathExpr"><xsl:with-param name="p" select="$xpath"/></xsl:call-template>", <xsl:value-of select="$model"/>)</xsl:when>
				<xsl:when test="$xpath != ''">new XsltForms_<xsl:value-of select="$mip"/>binding(<xsl:value-of select="$type2"/>, "<xsl:call-template name="toXPathExpr"><xsl:with-param name="p" select="$xpath"/></xsl:call-template>")</xsl:when>
				<xsl:otherwise>null</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="toXPathExpr">
			<xsl:param name="p"/>
			<xsl:call-template name="escapeJS">
				<xsl:with-param name="text" select="$p"/>
				<xsl:with-param name="trtext" select="translate($p,'&#10;&#13;&#9;&quot;','&#10;&#10;&#10;&#10;')"/>
			</xsl:call-template>
		</xsl:template>
	
		
		<xsl:template xmlns="" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="xps">
			<xsl:param name="ps"/>
			<xsl:for-each select="$ps/*">
				<xsl:if test="position() = 1 or preceding-sibling::*[1] != .">
					<xsl:call-template name="xpath"><xsl:with-param name="xp" select="."/></xsl:call-template>
				</xsl:if>
			</xsl:for-each>
		</xsl:template>
	
		
	  <xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="xpath">
			<xsl:param name="xp"/>
			<xsl:param name="main"/>
			<xsl:variable name="xp2jsres"><xsl:call-template name="xp2js"><xsl:with-param name="xp" select="$xp"/></xsl:call-template></xsl:variable>
			<xsl:variable name="xp2jsres2">
				<xsl:choose>
					<xsl:when test="contains($xp2jsres,'~~~~')">"<xsl:value-of select="substring-after(translate(substring-before(concat($xp2jsres,'~#~#'),'~#~#'),'&quot;',''),'~~~~')"/> in '<xsl:value-of select="$xp"/>'"</xsl:when>
					<xsl:when test="$xp2jsres != ''"><xsl:value-of select="$xp2jsres"/></xsl:when>
					<xsl:otherwise>"Unrecognized expression '<xsl:value-of select="$xp"/>'"</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:variable name="result">XsltForms_xpath.create(<xsl:value-of select="$vn_subform"/>,"<xsl:call-template name="toXPathExpr"><xsl:with-param name="p" select="$xp"/></xsl:call-template>",<xsl:call-template name="unordered"><xsl:with-param name="js" select="$xp2jsres"/></xsl:call-template>,<xsl:value-of select="$xp2jsres2"/><xsl:call-template name="js2ns"><xsl:with-param name="js" select="$xp2jsres"/></xsl:call-template>);</xsl:variable>
			<xsl:value-of select="$result"/><xsl:text>
</xsl:text>
	  </xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="unordered">
			<xsl:param name="js"/>
			<xsl:variable name="pipe">,'|',</xsl:variable>
			<xsl:variable name="ancestor">new XsltForms_stepExpr('ancestor',</xsl:variable>
			<xsl:variable name="ancestororself">new XsltForms_stepExpr('ancestor-or-self',</xsl:variable>
			<xsl:variable name="preceding">new XsltForms_stepExpr('preceding',</xsl:variable>
			<xsl:variable name="precedingsibling">new XsltForms_stepExpr('preceding-sibling',</xsl:variable>
			<xsl:choose>
				<xsl:when test="contains($js,'new XsltForms_binaryExpr(') and contains($js,$pipe)">true</xsl:when>
				<xsl:when test="contains($js,$ancestor) or contains($js,$ancestororself) or contains($js,$preceding) or contains($js,$precedingsibling)">true</xsl:when>
				<xsl:otherwise>false</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="js2ns">
			<xsl:param name="js"/>
			<xsl:if test="contains($js,&quot;,new XsltForms_nodeTestName('&quot;)">
				<xsl:variable name="js2" select="substring-after($js,',new XsltForms_nodeTestName(')"/>
				<xsl:if test="string-length(substring-before($js2,',')) != 2">
					<xsl:text>,</xsl:text>
					<xsl:value-of select="substring-before($js2,',')"/>
					<xsl:text>,'</xsl:text>
					<xsl:variable name="p" select="substring-before(substring($js2,2),&quot;'&quot;)"/>
					<xsl:variable name="pmin" select="translate($p,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')"/>
					<xsl:choose>
						<xsl:when test="($main/descendant::*|$main/descendant::*/@*)/namespace::*[name()=$p]">
							<xsl:value-of select="(($main/descendant::*|$main/descendant::*/@*)/namespace::*[name()=$p])[1]"/>
						</xsl:when>
						<xsl:when test="($main/descendant::*|$main/descendant::*/@*)[starts-with(translate(name(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),concat($pmin,':'))]">
							<xsl:value-of select="namespace-uri(($main/descendant::*|$main/descendant::*/@*)[starts-with(translate(name(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),concat($pmin,':'))][1])"/>
						</xsl:when>
						<xsl:when test="$main/descendant::*/@*[translate(name(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz') = concat('xmlns:',$pmin)]">
							<xsl:value-of select="$main/descendant::*/@*[translate(name(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz') = concat('xmlns:',$pmin)]"/>
						</xsl:when>
						<xsl:when test="($piformdoc/descendant::*|$piformdoc/descendant::*/@*)/namespace::*[name()=$p]">
							<xsl:value-of select="(($piformdoc/descendant::*|$piformdoc/descendant::*/@*)/namespace::*[name()=$p])[1]"/>
						</xsl:when>
						<xsl:when test="($piformdoc/descendant::*|$piformdoc/descendant::*/@*)[starts-with(translate(name(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),concat($pmin,':'))]">
							<xsl:value-of select="namespace-uri(($piformdoc/descendant::*|$piformdoc/descendant::*/@*)[starts-with(translate(name(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),concat($pmin,':'))][1])"/>
						</xsl:when>
						<xsl:when test="$piformdoc/descendant::*/@*[translate(name(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz') = concat('xmlns:',$pmin)]">
							<xsl:value-of select="$piformdoc/descendant::*/@*[translate(name(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz') = concat('xmlns:',$pmin)]"/>
						</xsl:when>
						<xsl:otherwise>notfound</xsl:otherwise>
					</xsl:choose>
					<xsl:text>'</xsl:text>
				</xsl:if>
				<xsl:call-template name="js2ns">
					<xsl:with-param name="js" select="substring-after($js2,')')"/>
				</xsl:call-template>
			</xsl:if>
		</xsl:template>
	
		
		<xsl:variable xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="precedence">./.;0.|.;1.div.mod.*.;2.+.-.;3.&lt;.&gt;.&lt;=.&gt;=.;4.=.!=.;5.and.;6.or.;7.,.;8.</xsl:variable>
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="xp2js">
			<xsl:param name="xp"/>
			<xsl:param name="args"/>
			<xsl:param name="ops"/>
			<xsl:variable name="c" select="substring(normalize-space($xp),1,1)"/>
			<xsl:variable name="d" select="substring-after($xp,$c)"/>
			<xsl:variable name="r">
				<xsl:choose>
					<xsl:when test="contains('./@*',$c)">
						<xsl:variable name="t"><xsl:call-template name="getLocationPath"><xsl:with-param name="s" select="concat($c,$d)"/></xsl:call-template></xsl:variable>
						<xsl:value-of select="substring-before($t,'.')"/>
						<xsl:text>.new XsltForms_locationExpr(</xsl:text>
						<xsl:choose>
							<xsl:when test="$c = '/' and not(starts-with($ops,'3.0./'))">true</xsl:when>
							<xsl:otherwise>false</xsl:otherwise>
						</xsl:choose>
						<xsl:value-of select="substring-after($t,'.')"/><xsl:text>)</xsl:text>
					</xsl:when>
					<xsl:when test="$c = &quot;'&quot;">
						<xsl:variable name="t">'<xsl:value-of select="substring-before($d,&quot;'&quot;)"/>'</xsl:variable>
						<xsl:value-of select="concat(string-length($t),'.new XsltForms_cteExpr(',$t,')')"/>
					</xsl:when>
					<xsl:when test="$c = '&quot;'">
						<xsl:variable name="t">"<xsl:value-of select="substring-before($d,'&quot;')"/>"</xsl:variable>
						<xsl:value-of select="concat(string-length($t),'.new XsltForms_cteExpr(',$t,')')"/>
					</xsl:when>
					<xsl:when test="$c = '('">
						<xsl:text>(</xsl:text>
						<xsl:call-template name="xp2js">
							<xsl:with-param name="xp" select="$d"/>
							<xsl:with-param name="args" select="$args"/>
							<xsl:with-param name="ops" select="concat('5.999.(',$ops)"/>
						</xsl:call-template>
					</xsl:when>
					<xsl:when test="$c = '-'">
						<xsl:choose>
							<xsl:when test="contains('0123456789',substring($d,1,1))">
								<xsl:variable name="t"><xsl:call-template name="getNumber"><xsl:with-param name="s" select="$d"/><xsl:with-param name="r" select="'-'"/></xsl:call-template></xsl:variable>
								<xsl:value-of select="concat(string-length($t),'.new XsltForms_cteExpr(',$t,')')"/>
							</xsl:when>
						</xsl:choose>
					</xsl:when>
					<xsl:when test="contains('0123456789',$c)">
						<xsl:variable name="t"><xsl:call-template name="getNumber"><xsl:with-param name="s" select="concat($c,$d)"/></xsl:call-template></xsl:variable>
						<xsl:value-of select="concat(string-length($t),'.new XsltForms_cteExpr(',$t,')')"/>
					</xsl:when>
					<xsl:when test="contains('_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz',$c)">
						<xsl:variable name="after" select="translate($d,'_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-:','')"/>
						<xsl:choose>
							<xsl:when test="substring($after,1,1)='(' and substring(substring-after($d,'('),1,1) = ')' and not(contains(concat('::',$c,substring-before($d,'('),'('),'::node('))">
								<xsl:variable name="t"><xsl:call-template name="getName"><xsl:with-param name="s" select="concat($c,$d)"/></xsl:call-template></xsl:variable>
								<xsl:value-of select="string-length($t)+2"/>
								<xsl:text>.new XsltForms_functionCallExpr('</xsl:text>
								<xsl:call-template name="fctfullname">
									<xsl:with-param name="fctname" select="$t"/>
								</xsl:call-template>
								<xsl:text>')</xsl:text>
							</xsl:when>
							<xsl:when test="substring($after,1,1)='(' and substring(substring-after($d,'('),1,1) != ')'">
								<xsl:variable name="t"><xsl:call-template name="getName"><xsl:with-param name="s" select="concat($c,$d)"/></xsl:call-template></xsl:variable>
								<xsl:text>(</xsl:text>
								<xsl:call-template name="xp2js">
									<xsl:with-param name="xp" select="substring($d,string-length($t)+1)"/>
									<xsl:with-param name="args" select="$args"/>
									<xsl:with-param name="ops" select="concat(string-length($t)+4,'.999.',$t,$ops)"/>
								</xsl:call-template>
							</xsl:when>
							<xsl:otherwise>
								<xsl:variable name="t"><xsl:call-template name="getLocationPath"><xsl:with-param name="s" select="concat($c,$d)"/></xsl:call-template></xsl:variable>
								<xsl:value-of select="substring-before($t,'.')"/>
								<xsl:text>.new XsltForms_locationExpr(false</xsl:text>
								<xsl:value-of select="substring-after($t,'.')"/><xsl:text>)</xsl:text>
							</xsl:otherwise>
						</xsl:choose>
					</xsl:when>
					<xsl:otherwise>~~~~Unexpected char at '<xsl:value-of select="concat($c,$d)"/>'~#~#</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:choose>
				<xsl:when test="contains($r,'~~~~')"><xsl:value-of select="$r"/></xsl:when>
				<xsl:when test="substring($r,1,1) = '('"><xsl:value-of select="substring($r,2)"/></xsl:when>
				<xsl:otherwise>
					<xsl:variable name="rlen" select="number(substring-before($r,'.'))"/>
					<xsl:variable name="rval" select="substring-after($r,'.')"/>
					<xsl:variable name="e">
						<xsl:call-template name="closepar">
							<xsl:with-param name="s" select="substring($d,$rlen)"/>
							<xsl:with-param name="args" select="concat(string-length($rval),'.',$rval,$args)"/>
							<xsl:with-param name="ops" select="$ops"/>
						</xsl:call-template>
					</xsl:variable>
					<xsl:variable name="flen" select="substring-before($e,'.')"/>
					<xsl:variable name="f" select="substring(substring-after($e,'.'),1,number($flen))"/>
					<xsl:variable name="e2" select="substring($e,string-length($flen)+2+number($flen))"/>
					<xsl:variable name="args2len" select="substring-before($e2,'.')"/>
					<xsl:variable name="args2" select="substring(substring-after($e2,'.'),1,number($args2len))"/>
					<xsl:variable name="ops2" select="substring-after(substring($e2,string-length($args2len)+2+number($args2len)),'.')"/>
					<xsl:choose>
						<xsl:when test="normalize-space($f)=''">
							<xsl:variable name="stacks">
								<xsl:call-template name="calc">
									<xsl:with-param name="args" select="$args2"/>
									<xsl:with-param name="ops" select="$ops2"/>
									<xsl:with-param name="opprec" select="9999999"/>
								</xsl:call-template>
							</xsl:variable>
							<xsl:variable name="reslen" select="substring-before(substring-after($stacks,'.'),'.')"/>
							<xsl:value-of select="substring(substring-after(substring-after($stacks,'.'),'.'),1,number($reslen))"/>
						</xsl:when>
						<xsl:otherwise>
							<xsl:variable name="o" select="substring(normalize-space($f),1,1)"/>
							<xsl:choose>
								<xsl:when test="$o = ']'">
									<xsl:variable name="stacks">
										<xsl:call-template name="calc">
											<xsl:with-param name="args" select="$args2"/>
											<xsl:with-param name="ops" select="$ops2"/>
											<xsl:with-param name="opprec" select="9999999"/>
										</xsl:call-template>
									</xsl:variable>
									<xsl:variable name="reslen" select="substring-before(substring-after($stacks,'.'),'.')"/>
									<xsl:value-of select="concat(string-length(substring-after($f,$o)),'.',substring(substring-after(substring-after($stacks,'.'),'.'),1,number($reslen)))"/>
								</xsl:when>
								<xsl:otherwise>
									<xsl:variable name="p" select="concat($o,substring-after($f,$o))"/>
									<xsl:variable name="op">
										<xsl:choose>
											<xsl:when test="starts-with($p,'div') or starts-with($p,'and') or starts-with($p,'mod')"><xsl:value-of select="substring($p,1,3)"/></xsl:when>
											<xsl:when test="starts-with($p,'or') or starts-with($p,'!=') or starts-with($p,'&lt;=') or starts-with($p,'&gt;=')"><xsl:value-of select="substring($p,1,2)"/></xsl:when>
											<xsl:when test="contains('+-*=|,&lt;&gt;/',$o)"><xsl:value-of select="$o"/></xsl:when>
											<xsl:otherwise>null</xsl:otherwise>
										</xsl:choose>
									</xsl:variable>
									<xsl:choose>
										<xsl:when test="$op!='null'">
											<xsl:variable name="opprec" select="number(substring-before(substring-after(substring-after($precedence,concat('.',$op,'.')),';'),'.'))"/>
											<xsl:variable name="stacks">
												<xsl:call-template name="calc">
													<xsl:with-param name="args" select="$args2"/>
													<xsl:with-param name="ops" select="$ops2"/>
													<xsl:with-param name="opprec" select="$opprec"/>
												</xsl:call-template>
											</xsl:variable>
											<xsl:variable name="args3len" select="substring-before($stacks,'.')"/>
											<xsl:variable name="args3" select="substring(substring-after($stacks,'.'),1,number($args3len))"/>
											<xsl:variable name="nextstack" select="substring($stacks,string-length($args3len)+2+number($args3len))"/>
											<xsl:variable name="ops3len" select="substring-before($nextstack,'.')"/>
											<xsl:variable name="ops3" select="substring(substring-after($nextstack,'.'),1,number($ops3len))"/>
											<xsl:variable name="xp3">
												<xsl:choose>
													<xsl:when test="$op = '/'"><xsl:value-of select="$p"/></xsl:when>
													<xsl:otherwise><xsl:value-of select="substring($p,string-length($op)+1)"/></xsl:otherwise>
												</xsl:choose>
											</xsl:variable>
											<xsl:call-template name="xp2js">
												<xsl:with-param name="xp" select="$xp3"/>
												<xsl:with-param name="args" select="$args3"/>
												<xsl:with-param name="ops" select="concat(string-length($opprec)+1+string-length($op),'.',$opprec,'.',$op,$ops3)"/>
											</xsl:call-template>
										</xsl:when>
										<xsl:otherwise>~~~~Unknown operator at '<xsl:value-of select="$f"/>'~#~#</xsl:otherwise>
									</xsl:choose>
								</xsl:otherwise>
							</xsl:choose>
						</xsl:otherwise>
					</xsl:choose>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="avt2xexpr">
			<xsl:param name="a"/>
			<xsl:variable name="avt" select="substring-before(substring-after($a,'{'),'}')"/>
			<xsl:if test="$avt != ''">
				<xsl:value-of select="string-length($avt)"/>.<xsl:value-of select="$avt"/>
				<xsl:call-template name="avt2xexpr">
					<xsl:with-param name="a" select="substring-after($a,'}')"/>
				</xsl:call-template>
			</xsl:if>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="closepar">
			<xsl:param name="s"/>
			<xsl:param name="args"/>
			<xsl:param name="ops"/>
			<xsl:variable name="c" select="substring(normalize-space($s),1,1)"/>
			<xsl:choose>
				<xsl:when test="$c = ')'">
					<xsl:variable name="stacks">
						<xsl:call-template name="calc">
							<xsl:with-param name="args" select="$args"/>
							<xsl:with-param name="ops" select="$ops"/>
							<xsl:with-param name="opprec" select="998"/>
						</xsl:call-template>
					</xsl:variable>
					<xsl:variable name="args3len" select="substring-before($stacks,'.')"/>
					<xsl:variable name="args3" select="substring(substring-after($stacks,'.'),1,number($args3len))"/>
					<xsl:variable name="nextstack" select="substring($stacks,string-length($args3len)+2+number($args3len))"/>
					<xsl:variable name="ops3len" select="substring-before($nextstack,'.')"/>
					<xsl:variable name="ops3" select="substring(substring-after($nextstack,'.'),1,number($ops3len))"/>
					<xsl:choose>
						<xsl:when test="starts-with($ops3,'5.999.(')">
							<xsl:call-template name="closepar">
								<xsl:with-param name="s" select="substring-after($s,$c)"/>
								<xsl:with-param name="args" select="$args3"/>
								<xsl:with-param name="ops" select="substring($ops3,8)"/>
							</xsl:call-template>
						</xsl:when>
						<xsl:otherwise>
							<xsl:variable name="arg1len" select="substring-before($args3,'.')"/>
							<xsl:variable name="arg1val" select="substring(substring-after($args3,'.'),1,number($arg1len))"/>
							<xsl:variable name="oplen" select="substring-before($ops3,'.')"/>
							<xsl:variable name="opval" select="substring(substring-after($ops3,'.'),1,number($oplen))"/>
							<xsl:variable name="newarg1">
								<xsl:text>new XsltForms_functionCallExpr('</xsl:text>
								<xsl:call-template name="fctfullname">
									<xsl:with-param name="fctname" select="substring-after($opval,'.')"/>
								</xsl:call-template>
								<xsl:text>',</xsl:text>
								<xsl:value-of select="$arg1val"/>
								<xsl:text>)</xsl:text>
							</xsl:variable>
							<xsl:call-template name="closepar">
								<xsl:with-param name="s" select="substring-after($s,$c)"/>
								<xsl:with-param name="args" select="concat(string-length($newarg1),'.',$newarg1,substring($args3,string-length($arg1len)+2+number($arg1len)))"/>
								<xsl:with-param name="ops" select="substring($ops3,string-length($oplen)+2+number($oplen))"/>
							</xsl:call-template>
						</xsl:otherwise>
					</xsl:choose>
				</xsl:when>
				<xsl:otherwise><xsl:value-of select="concat(string-length($s),'.',$s,string-length($args),'.',$args,string-length($ops),'.',$ops)"/></xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="calc">
			<xsl:param name="args"/>
			<xsl:param name="ops"/>
			<xsl:param name="opprec"/>
			<xsl:choose>
				<xsl:when test="$ops='' or number(substring-before(substring-after($ops,'.'),'.')) &gt; number($opprec)">
					<xsl:value-of select="concat(string-length($args),'.',$args,string-length($ops),'.',$ops)"/>
				</xsl:when>
				<xsl:otherwise>
					<xsl:variable name="op" select="substring-after(substring(substring-after($ops,'.'),1,substring-before($ops,'.')),'.')"/>
					<xsl:variable name="arg2len" select="substring-before($args,'.')"/>
					<xsl:variable name="arg2val" select="substring(substring-after($args,'.'),1,number($arg2len))"/>
					<xsl:variable name="args3" select="substring($args,string-length($arg2len)+2+number($arg2len))"/>
					<xsl:variable name="arg1len" select="substring-before($args3,'.')"/>
					<xsl:variable name="arg1val" select="substring(substring-after($args3,'.'),1,number($arg1len))"/>
					<xsl:variable name="arg">
						<xsl:choose>
							<xsl:when test="$op = ','">
								<xsl:value-of select="$arg1val"/>
								<xsl:text>,</xsl:text>
								<xsl:value-of select="$arg2val"/>
							</xsl:when>
							<xsl:when test="$op = '/'">
								<xsl:text>new XsltForms_pathExpr(</xsl:text>
								<xsl:value-of select="$arg1val"/>
								<xsl:text>,</xsl:text>
								<xsl:value-of select="$arg2val"/>
								<xsl:text>)</xsl:text>
							</xsl:when>
							<xsl:when test="$op = '|'">
								<xsl:text>new XsltForms_unionExpr(</xsl:text>
								<xsl:value-of select="$arg1val"/>
								<xsl:text>,</xsl:text>
								<xsl:value-of select="$arg2val"/>
								<xsl:text>)</xsl:text>
							</xsl:when>
							<xsl:otherwise>
								<xsl:text>new XsltForms_binaryExpr(</xsl:text>
								<xsl:value-of select="$arg1val"/>
								<xsl:text>,'</xsl:text>
								<xsl:value-of select="$op"/>
								<xsl:text>',</xsl:text>
								<xsl:value-of select="$arg2val"/>
								<xsl:text>)</xsl:text>
							</xsl:otherwise>
						</xsl:choose>
					</xsl:variable>
					<xsl:variable name="args2" select="concat(string-length($arg),'.',$arg,substring($args3,string-length($arg1len)+2+number($arg1len)))"/>
					<xsl:call-template name="calc">
						<xsl:with-param name="args" select="$args2"/>
						<xsl:with-param name="ops" select="substring(substring-after($ops,'.'),number(substring-before($ops,'.'))+1)"/>
						<xsl:with-param name="opprec" select="$opprec"/>
					</xsl:call-template>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="getNumber">
			<xsl:param name="s"/>
			<xsl:param name="r"/>
			<xsl:choose>
				<xsl:when test="$s = ''"><xsl:value-of select="$r"/></xsl:when>
				<xsl:otherwise>
					<xsl:variable name="c" select="substring($s,1,1)"/>
					<xsl:choose>
						<xsl:when test="contains('0123456789',$c) or ($c='.' and not(contains($r,$c)))">
							<xsl:call-template name="getNumber">
								<xsl:with-param name="s" select="substring($s,2)"/>
								<xsl:with-param name="r" select="concat($r,$c)"/>
							</xsl:call-template>
						</xsl:when>
						<xsl:otherwise><xsl:value-of select="$r"/></xsl:otherwise>
					</xsl:choose>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
	 	<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="getName">
			<xsl:param name="s"/>
			<xsl:variable name="o" select="translate(substring($s,1,100),'_.-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz:','')"/>
			<xsl:variable name="r">
				<xsl:choose>
					<xsl:when test="$o = ''"><xsl:value-of select="substring($s,1,100)"/></xsl:when>
					<xsl:otherwise><xsl:value-of select="substring-before($s, substring($o,1,1))"/></xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:choose>
				<xsl:when test="contains($r,':') and contains(substring-after($r,':'),':')">
					<xsl:value-of select="concat(substring-before($r,':'),':',substring-before(substring-after($r,':'),':'))"/>
				</xsl:when>
				<xsl:otherwise><xsl:value-of select="$r"/></xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="getLocationPath">
			<xsl:param name="s"/>
			<xsl:param name="r"/>
			<xsl:param name="l" select="0"/>
			<xsl:choose>
				<xsl:when test="$s = ''"><xsl:value-of select="concat($l,'.',$r)"/></xsl:when>
				<xsl:otherwise>
					<xsl:variable name="axis">
						<xsl:if test="contains($s, '::') and contains('.ancestor-or-self.ancestor.attribute.child.descendant-or-self.descendant.following-sibling.following.namespace.parent.preceding-sibling.preceding.self.',concat('.',substring-before($s,'::'),'.')) and not(contains(substring-before($s,'::'),'.'))">
							<xsl:value-of select="substring-before($s,'::')"/>
						</xsl:if>
					</xsl:variable>
					<xsl:variable name="s2">
						<xsl:choose>
							<xsl:when test="$axis != ''">
								<xsl:value-of select="substring-after($s,'::')"/>
							</xsl:when>
							<xsl:otherwise>
								<xsl:value-of select="$s"/>
							</xsl:otherwise>
						</xsl:choose>
					</xsl:variable>
					<xsl:variable name="axis2">
						<xsl:choose>
							<xsl:when test="$axis != ''">
								<xsl:value-of select="$axis"/>
							</xsl:when>
							<xsl:otherwise>child</xsl:otherwise>
						</xsl:choose>
					</xsl:variable>
					<xsl:variable name="axislength">
						<xsl:choose>
							<xsl:when test="$axis != ''">
								<xsl:value-of select="string-length($axis)+2"/>
							</xsl:when>
							<xsl:otherwise>0</xsl:otherwise>
						</xsl:choose>
					</xsl:variable>
					<xsl:variable name="c" select="substring($s2,1,1)"/>
					<xsl:variable name="i">
						<xsl:choose>
							<xsl:when test="starts-with($s2,'//')">2.,new XsltForms_stepExpr('descendant-or-self',new XsltForms_nodeTestAny()</xsl:when>
							<xsl:when test="starts-with($s2,'../')">3.,new XsltForms_stepExpr('parent',new XsltForms_nodeTestAny()</xsl:when>
							<xsl:when test="starts-with($s2,'..')">2.,new XsltForms_stepExpr('parent',new XsltForms_nodeTestAny()</xsl:when>
							<xsl:when test="starts-with($s2,'text()')">6.,new XsltForms_stepExpr('child',new XsltForms_nodeTestType(3)</xsl:when>
							<xsl:when test="$c = '*' and substring($s2,2,1) != ':'"><xsl:value-of select="$axislength + 1"/>.,new XsltForms_stepExpr('<xsl:value-of select="$axis2"/>',new XsltForms_nodeTestType(XsltForms_nodeType.ELEMENT)</xsl:when>
							<xsl:when test="$c = '/'">1.</xsl:when>
							<xsl:when test="starts-with($s2,'@*')">2.,new XsltForms_stepExpr('attribute',new XsltForms_nodeTestAny()</xsl:when>
							<xsl:when test="$c = '@'">
								<xsl:variable name="t"><xsl:call-template name="getName"><xsl:with-param name="s" select="substring($s2,2)"/></xsl:call-template></xsl:variable>
								<xsl:choose>
									<xsl:when test="$t != ''">
										<xsl:variable name="pt"><xsl:if test="not(contains($t,':'))">:</xsl:if><xsl:value-of select="$t"/></xsl:variable>
										<xsl:value-of select="string-length($t)+1"/>.,new XsltForms_stepExpr('attribute',new XsltForms_nodeTestName(<xsl:choose><xsl:when test="starts-with($pt,':')">null</xsl:when><xsl:otherwise>'<xsl:value-of select="substring-before($pt,':')"/>'</xsl:otherwise></xsl:choose>,'<xsl:value-of select="substring-after($pt,':')"/><xsl:text>')</xsl:text>
									</xsl:when>
									<xsl:otherwise>
										<xsl:variable name="msg">"~~~~Name expected at '<xsl:value-of select="substring($s,2)"/>'~#~#"</xsl:variable>
										<xsl:value-of select="string-length($msg)"/>.<xsl:value-of select="$msg"/>
									</xsl:otherwise>
								</xsl:choose>
							</xsl:when>
							<xsl:when test="$c = '.'">1.,new XsltForms_stepExpr('self',new XsltForms_nodeTestAny()</xsl:when>
							<xsl:when test="starts-with($s2,'node()')"><xsl:value-of select="$axislength + 6"/>.,new XsltForms_stepExpr('<xsl:value-of select="$axis2"/>',new XsltForms_nodeTestAny()</xsl:when>
							<xsl:when test="contains('_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz',$c)">
								<xsl:variable name="t"><xsl:call-template name="getName"><xsl:with-param name="s" select="$s2"/></xsl:call-template></xsl:variable>
								<xsl:variable name="pt"><xsl:if test="not(contains($t,':'))">:</xsl:if><xsl:value-of select="$t"/></xsl:variable>
								<xsl:value-of select="$axislength + string-length($t)"/>.,new XsltForms_stepExpr('<xsl:value-of select="$axis2"/>',new XsltForms_nodeTestName('<xsl:value-of select="substring-before($pt,':')"/>','<xsl:value-of select="substring-after($pt,':')"/><xsl:text>')</xsl:text>
							</xsl:when>
							<xsl:when test="starts-with($s2,'*:') and contains('_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz',substring($s2,3,1))">
								<xsl:variable name="t"><xsl:call-template name="getName"><xsl:with-param name="s" select="substring($s2,3)"/></xsl:call-template></xsl:variable>
								<xsl:choose>
									<xsl:when test="not(contains($t,':'))">
										<xsl:value-of select="$axislength + 2 + string-length($t)"/>.,new XsltForms_stepExpr('<xsl:value-of select="$axis2"/>',new XsltForms_nodeTestName('*','<xsl:value-of select="$t"/><xsl:text>')</xsl:text>
									</xsl:when>
									<xsl:otherwise>
										<xsl:variable name="msg">"~~~~Two prefixes at '<xsl:value-of select="$s"/>'~#~#"</xsl:variable>
										<xsl:value-of select="string-length($msg)"/>.<xsl:value-of select="$msg"/>
									</xsl:otherwise>
								</xsl:choose>
							</xsl:when>
							<xsl:otherwise>0</xsl:otherwise>
						</xsl:choose>
					</xsl:variable>
					<xsl:choose>
						<xsl:when test="$i = '0'"><xsl:value-of select="concat($l,'.',$r)"/></xsl:when>
						<xsl:otherwise>
							<xsl:variable name="s3" select="substring($s,number(substring-before($i,'.'))+1)"/>
							<xsl:variable name="p">
								<xsl:choose>
									<xsl:when test="substring($s3,1,1) = '['">
										<xsl:variable name="t"><xsl:call-template name="getPredicates"><xsl:with-param name="s" select="substring($s3,2)"/></xsl:call-template></xsl:variable>
										<xsl:value-of select="concat(substring-before($t,'.'),'.',substring-after($t,'.'),')')"/>
									</xsl:when>
									<xsl:when test="substring-after($i,'.') = ''">0.</xsl:when>
									<xsl:otherwise>0.)</xsl:otherwise>
								</xsl:choose>
							</xsl:variable>
							<xsl:call-template name="getLocationPath">
								<xsl:with-param name="s" select="substring($s3,number(substring-before($p,'.'))+1)"/>
								<xsl:with-param name="r" select="concat($r,substring-after($i,'.'),substring-after($p,'.'))"/>
								<xsl:with-param name="l" select="$l+number(substring-before($i,'.'))+number(substring-before($p,'.'))"/>
							</xsl:call-template>
						</xsl:otherwise>
					</xsl:choose>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="getPredicates">
			<xsl:param name="s"/>
			<xsl:param name="r"/>
			<xsl:param name="l" select="0"/>
			<xsl:variable name="p">
				<xsl:variable name="t"><xsl:call-template name="xp2js"><xsl:with-param name="xp" select="$s"/></xsl:call-template></xsl:variable>
				<xsl:choose>
					<xsl:when test="contains($t,'~~~~')">
						<xsl:variable name="msg">"~~~~<xsl:value-of select="substring-after(translate(substring-before(concat($t,'~#~#'),'~#~#'),'&quot;',''),'~~~~')"/> in '<xsl:value-of select="$s"/>'~#~#"</xsl:variable>
						<xsl:value-of select="string-length($s)-number(substring-before($t,'.'))+1"/>.<xsl:value-of select="$msg"/>
					</xsl:when>
					<xsl:when test="$t != ''">
						<xsl:value-of select="string-length($s)-number(substring-before($t,'.'))+1"/>.,new XsltForms_predicateExpr(<xsl:value-of select="substring-after($t,'.')"/><xsl:text>)</xsl:text>
					</xsl:when>
					<xsl:otherwise>
						<xsl:variable name="msg">"~~~~Unrecognized expression '<xsl:value-of select="$s"/>'~#~#"</xsl:variable>
						<xsl:value-of select="string-length($s)-number(substring-before($t,'.'))+1"/>.<xsl:value-of select="$msg"/>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:choose>
				<xsl:when test="substring($s,number(substring-before($p,'.')),1)='['">
					<xsl:call-template name="getPredicates">
						<xsl:with-param name="s" select="substring($s,number(substring-before($p,'.'))+1)"/>
						<xsl:with-param name="r" select="concat($r,substring-after($p,'.'))"/>
						<xsl:with-param name="l" select="$l+number(substring-before($p,'.'))"/>
					</xsl:call-template>
				</xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="concat(string($l+number(substring-before($p,'.'))),'.',$r,substring-after($p,'.'))"/>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
	 	<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="fctfullname">
			<xsl:param name="fctname"/>
			<xsl:choose>
				<xsl:when test="contains($fctname,':')">
					<xsl:variable name="ns" select="substring-before($fctname,':')"/>
					<xsl:choose>
						<xsl:when test="($main/descendant::*|$main/descendant::*/@*)/namespace::*[name()=$ns]"><xsl:value-of select="($main/descendant::*|$main/descendant::*/@*)/namespace::*[name()=$ns][1]"/></xsl:when>
						<xsl:when test="($main/descendant::*|$main/descendant::*/@*)[starts-with(name(),concat($ns,':'))]"><xsl:value-of select="namespace-uri(($main/descendant::*|$main/descendant::*/@*)[starts-with(name(),concat($ns,':'))][1])"/></xsl:when>
						<xsl:when test="($piformdoc/descendant::*|$piformdoc/descendant::*/@*)/namespace::*[name()=$ns]"><xsl:value-of select="($piformdoc/descendant::*|$piformdoc/descendant::*/@*)/namespace::*[name()=$ns][1]"/></xsl:when>
						<xsl:when test="($piformdoc/descendant::*|$piformdoc/descendant::*/@*)[starts-with(name(),concat($ns,':'))]"><xsl:value-of select="namespace-uri(($piformdoc/descendant::*|$piformdoc/descendant::*/@*)[starts-with(name(),concat($ns,':'))][1])"/></xsl:when>
						<xsl:when test="$ns = 'xf' or $ns = 'xform' or $ns = 'xforms'">http://www.w3.org/2002/xforms</xsl:when>
						<xsl:when test="$ns = 'math'">http://exslt.org/math</xsl:when>
						<xsl:otherwise>http://www.w3.org/2005/xpath-functions</xsl:otherwise>
					</xsl:choose>
					<xsl:text> </xsl:text>
					<xsl:value-of select="substring-after($fctname,':')"/>
				</xsl:when>
				<xsl:otherwise>
					<xsl:choose>
						<xsl:when test="contains(' boolean-from-string is-card-number count-non-empty index power random if choose property digest hmac local-date local-dateTime now days-from-date days-to-date seconds-from-dateTime seconds-to-dateTime adjust-dateTime-to-timezone seconds months instance current context event nodeindex is-valid serialize transform ', concat(' ', $fctname, ' '))">http://www.w3.org/2002/xforms <xsl:value-of select="$fctname"/></xsl:when>
						<xsl:otherwise>http://www.w3.org/2005/xpath-functions <xsl:value-of select="$fctname"/></xsl:otherwise>
					</xsl:choose>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="escapeJS">
			<xsl:param name="text"/>
			<xsl:param name="trtext"/>
			<xsl:choose>
				<xsl:when test="contains($trtext, '&#10;')">
					<xsl:value-of select="substring-before($trtext, '&#10;')"/>
					<xsl:variable name="c" select="substring($text, string-length(substring-before($trtext, '&#10;'))+1, 1)"/>
					<xsl:choose>
						<xsl:when test="$c = '&#10;'">\n</xsl:when>
						<xsl:when test="$c = '&#13;'">\r</xsl:when>
						<xsl:when test="$c = '&#9;'">\t</xsl:when>
						<xsl:when test="$c = '&quot;'">\"</xsl:when>
						<xsl:when test="$c = '\'">\\</xsl:when>
					</xsl:choose>
					<xsl:call-template name="escapeJS">
						<xsl:with-param name="text" select="substring-after($text, $c)"/>
						<xsl:with-param name="trtext" select="substring-after($trtext, '&#10;')"/>
					</xsl:call-template>
				</xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$text"/>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
	
	
		
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xhtml:link[@type='text/css' and @rel='stylesheet'] | link[@type='text/css' and @rel='stylesheet']">
			<xsl:param name="config"/>
			<xsl:choose>
				<xsl:when test="$config/options/nocss">
					<xsl:copy-of select="."/>
				</xsl:when>
				<xsl:when test="translate(normalize-space(/processing-instruction('css-conversion')[1]), 'YESNO ', 'yesno')='no'">
					<xsl:copy-of select="."/>
				</xsl:when>
				<xsl:otherwise>
					<style type="text/css">
						<xsl:call-template name="cssconv">
							<xsl:with-param name="input" select="normalize-space(document(@href,/)/*)"/>
						</xsl:call-template>
					</style>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xhtml:style | style">
			<xsl:param name="config"/>
			<xsl:variable name="option"> css="no" </xsl:variable>
			<xsl:choose>
				<xsl:when test="$config/options/nocss">
					<xsl:copy-of select="."/>
				</xsl:when>
				<xsl:when test="translate(normalize-space(/processing-instruction('css-conversion')[1]), 'YESNO ', 'yesno')='no'">
					<xsl:copy-of select="."/>
				</xsl:when>
				<xsl:when test="contains(concat(' ',translate(normalize-space(/processing-instruction('xsltforms-options')[1]), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'),' '),$option)">
					<xsl:copy-of select="."/>
				</xsl:when>
				<xsl:otherwise>
					<style type="text/css">
						<xsl:copy-of select="@*"/>
						<xsl:call-template name="cssconv">
							<xsl:with-param name="input" select="normalize-space(.)"/>
						</xsl:call-template>
					</style>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="addsel">
			<xsl:param name="sels"/>
			<xsl:param name="xformscontext"/>
			<xsl:param name="xhtmlcontext"/>
			<xsl:variable name="sel">
				<xsl:choose>
					<xsl:when test="contains($sels,' ') and contains($sels,',')"><xsl:value-of select="substring-before(translate($sels,',',' '),' ')"/></xsl:when>
					<xsl:when test="contains($sels,' ')"><xsl:value-of select="substring-before($sels,' ')"/></xsl:when>
					<xsl:when test="contains($sels,',')"><xsl:value-of select="substring-before($sels,',')"/></xsl:when>
					<xsl:otherwise><xsl:value-of select="$sels"/></xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:choose>
				<xsl:when test="contains($sel,'|') and contains($xformscontext, concat('|',substring-before($sel,'|'),'|')) and not(starts-with(substring-after($sel,'|'),'*'))">
					<xsl:text>.xforms-</xsl:text>
					<xsl:value-of select="substring-after($sel,'|')"/>
				</xsl:when>
				<xsl:when test="contains($sel,'|') and contains($xhtmlcontext, concat('|',substring-before($sel,'|'),'|'))">
					<xsl:value-of select="substring-after($sel,'|')"/>
				</xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$sel"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:if test="contains($sels,',') or contains($sels,' ')">
				<xsl:variable name="sep" select="substring(substring-after($sels,substring-before(translate($sels,',',' '),' ')),1,1)"/>
				<xsl:value-of select="$sep"/><xsl:text> </xsl:text>
				<xsl:call-template name="addsel">
					<xsl:with-param name="sels" select="substring-after($sels,$sep)"/>
					<xsl:with-param name="xformscontext" select="$xformscontext"/>
					<xsl:with-param name="xhtmlcontext" select="$xhtmlcontext"/>
				</xsl:call-template>
			</xsl:if>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="cssconv">
			<xsl:param name="input"/>
			<xsl:param name="xformscontext" select="'|'"/>
			<xsl:param name="xhtmlcontext" select="'|'"/>
			<xsl:choose>
				<xsl:when test="$input = ''"/>
				<xsl:when test="starts-with($input, ' ')">
					<xsl:text> </xsl:text>
					<xsl:call-template name="cssconv">
						<xsl:with-param name="input" select="substring($input,2)"/>
						<xsl:with-param name="xformscontext" select="$xformscontext"/>
						<xsl:with-param name="xhtmlcontext" select="$xhtmlcontext"/>
					</xsl:call-template>
				</xsl:when>
				<xsl:when test="translate(substring($input, 1, 11),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz') = '@namespace '">
					<xsl:variable name="prefix" select="substring-before(substring-after($input,' '),' ')"/>
					<xsl:variable name="url" select="translate(substring-before(translate(substring-after(substring-after($input,' '),' '),';',' '),' '),'&quot;','')"/>
					<xsl:variable name="url2" select="translate($url,'&quot;',&quot;&quot;)"/>
					<xsl:variable name="xformsprefix">
						<xsl:if test="$url2 = 'url(http://www.w3.org/2002/xforms)' or $url2 = 'http://www.w3.org/2002/xforms'">
							<xsl:value-of select="concat($prefix,'|')"/>
						</xsl:if>
					</xsl:variable>
					<xsl:variable name="xhtmlprefix">
						<xsl:if test="$url2 = 'url(http://www.w3.org/1999/xhtml)' or $url2 = 'http://www.w3.org/1999/xhtml'">
							<xsl:value-of select="concat($prefix,'|')"/>
						</xsl:if>
					</xsl:variable>
					<xsl:value-of select="concat(substring-before($input,';'),';')"/>
					<xsl:call-template name="cssconv">
						<xsl:with-param name="input" select="substring-after($input,';')"/>
						<xsl:with-param name="xformscontext" select="concat($xformscontext,$xformsprefix)"/>
						<xsl:with-param name="xhtmlcontext" select="concat($xhtmlcontext,$xhtmlprefix)"/>
					</xsl:call-template>
				</xsl:when>
				<xsl:when test="translate(substring($input, 1, 8),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz') = '@import '">
					<xsl:variable name="url" select="substring-before(substring-after($input,' '),' ')"/>
					<xsl:variable name="url2">
						<xsl:choose>
							<xsl:when test="starts-with($url,'url(')"><xsl:value-of select="substring-before(substring-after($url,'url('),')')"/></xsl:when>
							<xsl:otherwise><xsl:value-of select="substring-before(substring-after($url,'&quot;'),'&quot;')"/></xsl:otherwise>
						</xsl:choose>
					</xsl:variable>
					<xsl:call-template name="cssconv">
						<xsl:with-param name="input" select="normalize-space(document($url2,/)/*)"/>
						<xsl:with-param name="xformscontext" select="$xformscontext"/>
						<xsl:with-param name="xhtmlcontext"><xsl:value-of select="$xhtmlcontext"/></xsl:with-param>
					</xsl:call-template>
					<xsl:call-template name="cssconv">
						<xsl:with-param name="input"><xsl:value-of select="substring-after($input,';')"/></xsl:with-param>
						<xsl:with-param name="xformscontext"><xsl:value-of select="$xformscontext"/></xsl:with-param>
						<xsl:with-param name="xhtmlcontext"><xsl:value-of select="$xhtmlcontext"/></xsl:with-param>
					</xsl:call-template>
				</xsl:when>
				<xsl:when test="starts-with($input, '@')">
					<xsl:value-of select="concat(substring-before($input,';'),';')"/>
					<xsl:call-template name="cssconv">
						<xsl:with-param name="input" select="substring-after($input,';')"/>
						<xsl:with-param name="xformscontext" select="$xformscontext"/>
						<xsl:with-param name="xhtmlcontext" select="$xhtmlcontext"/>
					</xsl:call-template>
				</xsl:when>
				<xsl:when test="starts-with($input, '/*')">
					<xsl:call-template name="cssconv">
						<xsl:with-param name="input" select="substring-after(substring($input,3),'*/')"/>
						<xsl:with-param name="xformscontext" select="$xformscontext"/>
						<xsl:with-param name="xhtmlcontext" select="$xhtmlcontext"/>
					</xsl:call-template>
				</xsl:when>
				<xsl:otherwise>
					<xsl:call-template name="addsel">
						<xsl:with-param name="sels" select="normalize-space(substring-before($input,'{'))"/>
						<xsl:with-param name="xformscontext" select="$xformscontext"/>
						<xsl:with-param name="xhtmlcontext" select="$xhtmlcontext"/>
					</xsl:call-template>
					<xsl:value-of select="concat(' {',substring-before(substring-after($input,'{'),'}'),'}')"/>
					<xsl:text>
</xsl:text>
					<xsl:call-template name="cssconv">
						<xsl:with-param name="input" select="substring-after($input,'}')"/>
						<xsl:with-param name="xformscontext" select="$xformscontext"/>
						<xsl:with-param name="xhtmlcontext" select="$xhtmlcontext"/>
					</xsl:call-template>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
	
	
		
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xsd:schema" mode="schema" priority="1">
			<xsl:param name="filename"/>
			<xsl:param name="namespaces" select="'{}'"/>
			<xsl:text>var schema = new XsltForms_schema(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,"</xsl:text>
			<xsl:value-of select="@targetNamespace"/>
			<xsl:text>", "</xsl:text>
			<xsl:value-of select="$filename"/>
			<xsl:text>", </xsl:text>
			<xsl:value-of select="$namespaces"/>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="xsd:simpleType" mode="schema"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xsd:simpleType" mode="schema" priority="1">
			<xsl:apply-templates mode="schema"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsltforms="http://www.agencexml.com/xsltforms" match="xsd:restriction" mode="schema" priority="1">
			<xsl:text>new XsltForms_atomicType().setSchema(schema)</xsl:text>
			<xsl:if test="local-name(../..) = 'schema'">
				<xsl:text>.setName("</xsl:text>
				<xsl:value-of select="../@name"/>
				<xsl:text>")</xsl:text>
			</xsl:if>
			<xsl:if test="@base">
				<xsl:text>.put("base", "</xsl:text>
				<xsl:value-of select="@base"/>
				<xsl:text>")</xsl:text>
			</xsl:if>
			<xsl:if test="xsd:simpleType">
				<xsl:text>.put("base", </xsl:text>
				<xsl:apply-templates select="xsd:simpleType" mode="schema"/>
				<xsl:text>)</xsl:text>
			</xsl:if>
			<xsl:for-each select="xsd:length|xsd:minLength|xsd:maxLength|xsd:enumeration|xsd:whiteSpace|xsd:maxInclusive|xsd:minInclusive|xsd:maxExclusive|xsd:minExclusive|xsd:totalDigits|xsd:fractionDigits|xsd:maxScale|xsd:minScale">
				<xsl:text>.put("</xsl:text>
				<xsl:value-of select="local-name()"/>
				<xsl:text>", "</xsl:text>
				<xsl:value-of select="@value"/>
				<xsl:text>")</xsl:text>
			</xsl:for-each>
			<xsl:for-each select="xsd:pattern">
				<xsl:text>.put("</xsl:text>
				<xsl:value-of select="local-name()"/>
				<xsl:text>", /^(</xsl:text>
				<xsl:value-of select="@value"/>
				<xsl:text>)$/)</xsl:text>
			</xsl:for-each>
			<xsl:if test="@xsltforms:rte">
				<xsl:text>.put("rte", "</xsl:text>
				<xsl:value-of select="@xsltforms:rte"/>
				<xsl:text>")</xsl:text>
			</xsl:if>
			<xsl:if test="../xsd:annotation/xsd:appinfo">
				<xsl:text>.put("appinfo", "</xsl:text>
				<xsl:call-template name="toXPathExpr"><xsl:with-param name="p" select="../xsd:annotation/xsd:appinfo"/></xsl:call-template>
				<xsl:text>")</xsl:text>
			</xsl:if>
			<xsl:if test="local-name(../..) = 'schema'">
				<xsl:text>;
</xsl:text>
			</xsl:if>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xsd:list" mode="schema" priority="1">
			<xsl:text>new XsltForms_listType(</xsl:text>
			<xsl:if test="@itemType">
				<xsl:text>"</xsl:text>
				<xsl:value-of select="@itemType"/>
				<xsl:text>"</xsl:text>
			</xsl:if>
			<xsl:text>).setSchema(schema)</xsl:text>
			<xsl:if test="local-name(../..) = 'schema'">
				<xsl:text>.setName("</xsl:text>
				<xsl:value-of select="../@name"/>
				<xsl:text>")</xsl:text>
			</xsl:if>
			<xsl:if test="xsd:simpleType">
				<xsl:text>.setItemType(</xsl:text>
				<xsl:apply-templates select="xsd:simpleType" mode="schema"/>
				<xsl:text>)</xsl:text>
			</xsl:if>
			<xsl:if test="local-name(../..) = 'schema'">
				<xsl:text>;
</xsl:text>
			</xsl:if>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xsd:union" mode="schema" priority="1">
			<xsl:text>new XsltForms_unionType(</xsl:text>
			<xsl:if test="@memberTypes">
				<xsl:text>"</xsl:text>
				<xsl:value-of select="@memberTypes"/>
				<xsl:text>"</xsl:text>
			</xsl:if>
			<xsl:text>).setSchema(schema)</xsl:text>
			<xsl:if test="local-name(../..) = 'schema'">
				<xsl:text>.setName("</xsl:text>
				<xsl:value-of select="../@name"/>
				<xsl:text>")</xsl:text>
			</xsl:if>
			<xsl:text>.addTypes()</xsl:text>
			<xsl:for-each select="xsd:simpleType">
				<xsl:text>.addType(</xsl:text>
				<xsl:apply-templates select="."/>
				<xsl:text>)</xsl:text>
			</xsl:for-each>
			<xsl:if test="local-name(../..) = 'schema'">
				<xsl:text>;
</xsl:text>
			</xsl:if>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="node()" mode="schema" priority="0"/>
	
		
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="loadschemas">
			<xsl:param name="schemas"/>
			<xsl:variable name="schema">
				<xsl:choose>
					<xsl:when test="contains($schemas,' ')">
						<xsl:value-of select="substring-before($schemas,' ')"/>
					</xsl:when>
					<xsl:otherwise>
						<xsl:value-of select="$schemas"/>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:apply-templates select="document($schema,/)/*" mode="schema">
				<xsl:with-param name="filename" select="$schema"/>
			</xsl:apply-templates>
			<xsl:if test="contains($schemas,' ')">
				<xsl:call-template name="loadschemas">
					<xsl:with-param name="schemas" select="substring-after($schemas,' ')"/>
				</xsl:call-template>
			</xsl:if>
		</xsl:template>
	
	
	
		
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:action" mode="script" priority="1">
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:variable name="idaction" select="count(preceding::xforms:action|ancestor::xforms:action)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>action_</xsl:text>
			<xsl:value-of select="$idaction"/>
			<xsl:text> = new XsltForms_action(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
			<xsl:text>)</xsl:text>
			<xsl:for-each select="xforms:setvalue|xforms:insert|xforms:delete|xforms:action|xforms:toggle|xforms:send|xforms:setfocus|xforms:setindex|xforms:load|xforms:message|xforms:dispatch|xforms:reset|xforms:show|xforms:hide|xforms:script|xforms:unload">
				<xsl:text>.add(</xsl:text>
				<xsl:value-of select="$vn_pf"/>
				<xsl:variable name="lname" select="local-name()"/>
				<xsl:variable name="nsuri" select="namespace-uri()"/>
				<xsl:value-of select="$lname"/>
				<xsl:text>_</xsl:text>
				<xsl:value-of select="count(preceding::*[local-name()=$lname and namespace-uri()=$nsuri]|ancestor::*[local-name()=$lname and namespace-uri()=$nsuri])"/>
				<xsl:text>)</xsl:text>
			</xsl:for-each>
			<xsl:text>;
</xsl:text>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:bind" mode="script" priority="1">
			<xsl:variable name="idbind" select="count(preceding::xforms:bind|ancestor::xforms:bind)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>bind_</xsl:text>
			<xsl:value-of select="$idbind"/>
			<xsl:text> = new XsltForms_bind(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:text>bind-</xsl:text>
					<xsl:value-of select="$idbind"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",</xsl:text>
			<xsl:for-each select="parent::*">
				<xsl:variable name="lname" select="local-name()"/>
				<xsl:value-of select="$vn_pf"/>
				<xsl:value-of select="$lname"/>
				<xsl:text>_</xsl:text>
				<xsl:value-of select="count(preceding::*[local-name()=$lname]|ancestor::*[local-name()=$lname])"/>
			</xsl:for-each>
			<xsl:text>,"</xsl:text>
			<xsl:variable name="nodeset">
				<xsl:choose>
					<xsl:when test="@nodeset"><xsl:value-of select="@nodeset"/></xsl:when>
					<xsl:otherwise>.</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:call-template name="toXPathExpr"><xsl:with-param name="p" select="$nodeset"/></xsl:call-template>
			<xsl:text>",</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@type"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@readonly"/><xsl:with-param name="type" select="'xsd:boolean'"/><xsl:with-param name="mip" select="'mip'"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@required"/><xsl:with-param name="type" select="'xsd:boolean'"/><xsl:with-param name="mip" select="'mip'"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@relevant"/><xsl:with-param name="type" select="'xsd:boolean'"/><xsl:with-param name="mip" select="'mip'"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@calculate"/><xsl:with-param name="type" select="'xsd:string'"/><xsl:with-param name="mip" select="'mip'"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@constraint"/><xsl:with-param name="type" select="'xsd:boolean'"/><xsl:with-param name="mip" select="'mip'"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:case" mode="script" priority="1">
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:delete" mode="script" priority="1">
			<xsl:variable name="iddelete" select="count(preceding::xforms:delete|ancestor::xforms:delete)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>delete_</xsl:text>
			<xsl:value-of select="$iddelete"/>
			<xsl:text> = new XsltForms_delete(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,"</xsl:text>
			<xsl:call-template name="toXPathExpr"><xsl:with-param name="p" select="@nodeset"/></xsl:call-template>
			<xsl:text>",</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@model"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@bind"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@at"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@context"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:dispatch" mode="script" priority="1">
			<xsl:variable name="iddispatch" select="count(preceding::xforms:dispatch|ancestor::xforms:dispatch)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>dispatch_</xsl:text>
			<xsl:value-of select="$iddispatch"/>
			<xsl:text> = new XsltForms_dispatch(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="xforms:name/@value">
					<xsl:for-each select="xforms:name[1]">
						<xsl:call-template name="toScriptBinding"/>
					</xsl:for-each>
				</xsl:when>
				<xsl:when test="xforms:name/text()">
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="normalize-space(xforms:name/text())"/></xsl:call-template>
				</xsl:when>
				<xsl:otherwise>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@name"/></xsl:call-template>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="xforms:targetid/@value">
					<xsl:for-each select="xforms:targetid[1]">
						<xsl:call-template name="toScriptBinding"/>
					</xsl:for-each>
				</xsl:when>
				<xsl:when test="xforms:targetid/text()">
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="normalize-space(xforms:targetid/text())"/></xsl:call-template>
				</xsl:when>
				<xsl:otherwise>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@targetid"/></xsl:call-template>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
			<xsl:if test="@delay | xforms:delay">
				<xsl:text>,</xsl:text>
				<xsl:choose>
					<xsl:when test="xforms:delay/@value">
						<xsl:for-each select="xforms:delay[1]">
							<xsl:call-template name="toScriptBinding"/>
						</xsl:for-each>
					</xsl:when>
					<xsl:when test="xforms:delay/text()">
						<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="normalize-space(xforms:delay/text())"/></xsl:call-template>
					</xsl:when>
					<xsl:otherwise>
						<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@delay"/></xsl:call-template>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:if>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="*" mode="script" priority="0">
			<xsl:apply-templates select="*" mode="script"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:group" mode="script" priority="1">
			<xsl:variable name="idgroup" select="count(preceding::xforms:group|ancestor::xforms:group)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>group_</xsl:text>
			<xsl:value-of select="$idgroup"/>
			<xsl:text> = new XsltForms_group(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:text>group-</xsl:text>
					<xsl:value-of select="$idgroup"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@ref"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:input | xforms:secret | xforms:textarea" mode="script" priority="1">
			<xsl:variable name="lname" select="local-name()"/>
			<xsl:variable name="idinput" select="count(preceding::xforms:*[local-name()=$lname]|ancestor::xforms:*[local-name()=$lname])"/>
			<xsl:text>new XsltForms_input(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:value-of select="$lname"/>
					<xsl:text>-</xsl:text>
					<xsl:value-of select="$idinput"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",</xsl:text>
			<xsl:choose>
				<xsl:when test="xforms:label/node()">2</xsl:when>
				<xsl:otherwise>0</xsl:otherwise>
			</xsl:choose>
			<xsl:text>,"</xsl:text>
			<xsl:choose>
				<xsl:when test="$lname = 'input'">text</xsl:when>
				<xsl:when test="$lname = 'secret'">password</xsl:when>
				<xsl:when test="$lname = 'textarea'">textarea</xsl:when>
			</xsl:choose>
			<xsl:text>",</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@ref"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@inputmode"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@incremental"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@delay"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@mediatype"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@ajx:aid-button"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:insert" mode="script" priority="1">
			<xsl:variable name="idinsert" select="count(preceding::xforms:insert|ancestor::xforms:insert)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>insert_</xsl:text>
			<xsl:value-of select="$idinsert"/>
			<xsl:text> = new XsltForms_insert(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,"</xsl:text>
			<xsl:call-template name="toXPathExpr"><xsl:with-param name="p" select="@nodeset"/></xsl:call-template>
			<xsl:text>",</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@model"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@bind"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@at"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@position"/><xsl:with-param name="default">"after"</xsl:with-param></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@origin"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@context"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:instance" mode="script" priority="1">
			<xsl:variable name="idinstance" select="count(preceding::xforms:instance|ancestor::xforms:instance)"/>
			<xsl:text>XsltForms_instance.create(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:when test="$idinstance = 0 and parent::xforms:model/@id = ''">"<xsl:value-of select="$id_pf"/>instance-default</xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:text>instance-</xsl:text>
					<xsl:value-of select="$idinstance"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",</xsl:text>
			<xsl:for-each select="parent::*">
				<xsl:variable name="lname" select="local-name()"/>
				<xsl:value-of select="$vn_pf"/>
				<xsl:value-of select="$lname"/>
				<xsl:text>_</xsl:text>
				<xsl:value-of select="count(preceding::*[local-name()=$lname]|ancestor::*[local-name()=$lname])"/>
			</xsl:for-each>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@readonly = 'true'">true</xsl:when>
				<xsl:otherwise>false</xsl:otherwise>
			</xsl:choose>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@mediatype">
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@mediatype"/></xsl:call-template>
				</xsl:when>
				<xsl:otherwise>"application/xml"</xsl:otherwise>
			</xsl:choose>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@src">
				<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@src"/></xsl:call-template>
				<xsl:text>,null);
</xsl:text>
				</xsl:when>
				<xsl:when test="@resource and not(*)">
				<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@resource"/></xsl:call-template>
				<xsl:text>,null);
</xsl:text>
				</xsl:when>
				<xsl:otherwise>
					<xsl:text>null,'</xsl:text>
					<xsl:choose>
						<xsl:when test="@mediatype and @mediatype != 'application/xml'">
							<xsl:call-template name="escapeJS">
								<xsl:with-param name="text" select="."/>
								<xsl:with-param name="trtext" select="translate(.,'&#10;&#13;&#9;\','&#10;&#10;&#10;&#10;')"/>
							</xsl:call-template>
						</xsl:when>
						<xsl:when test="$piform != '' and @id = $piforminstanceid">
							<xsl:apply-templates select="$piforminstance/*" mode="xml2string">
								<xsl:with-param name="root" select="true()"/>
							</xsl:apply-templates>
						</xsl:when>
						<xsl:otherwise>
							<xsl:apply-templates select="node()" mode="xml2string">
								<xsl:with-param name="root" select="true()"/>
							</xsl:apply-templates>
						</xsl:otherwise>
					</xsl:choose>
					<xsl:text>');
</xsl:text>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:item | xforms:itemset[ancestor::xforms:*[1][@appearance='full']]" mode="script" priority="1">
			<xsl:variable name="lname" select="local-name()"/>
			<xsl:variable name="iditem" select="count(preceding::xforms:*[local-name()=$lname]|ancestor::xforms:*[local-name()=$lname])"/>
			<xsl:if test="local-name() = 'itemset'">
				<xsl:text>var </xsl:text>
				<xsl:value-of select="$vn_pf"/>
				<xsl:value-of select="$lname"/>
				<xsl:text>_</xsl:text>
				<xsl:value-of select="$iditem"/>
				<xsl:text> = new XsltForms_repeat(</xsl:text>
				<xsl:value-of select="$vn_subform"/>
				<xsl:text>,</xsl:text>
				<xsl:choose>
					<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
					<xsl:otherwise>
						<xsl:value-of select="$jsid_pf"/>
						<xsl:value-of select="$lname"/>
						<xsl:text>-</xsl:text>
						<xsl:value-of select="$iditem"/>
					</xsl:otherwise>
				</xsl:choose>
				<xsl:text>",</xsl:text>
				<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@nodeset"/></xsl:call-template>
				<xsl:text>);
</xsl:text>
			</xsl:if>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:value-of select="$lname"/>
			<xsl:text>_</xsl:text>
			<xsl:value-of select="$iditem"/>
			<xsl:text> = new XsltForms_item(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:value-of select="$lname"/>
					<xsl:text>-</xsl:text>
					<xsl:if test="local-name() = 'itemset'">
						<xsl:text>item-</xsl:text>
					</xsl:if>
					<xsl:value-of select="$iditem"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="xforms:label/@ref"/><xsl:with-param name="model" select="concat('&quot;',string(xforms:label/@model),'&quot;')"/></xsl:call-template>
			<xsl:if test="xforms:value">
				<xsl:text>,</xsl:text>
				<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="xforms:value/@ref"/><xsl:with-param name="model" select="concat('&quot;',string(xforms:value/@model),'&quot;')"/></xsl:call-template>
			</xsl:if>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:itemset[ancestor::xforms:*[1][string(@appearance)!='full']]" mode="script" priority="1">
			<xsl:variable name="iditemset" select="count(preceding::xforms:itemset|ancestor::xforms:itemset)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>itemset_</xsl:text>
			<xsl:value-of select="$iditemset"/>
			<xsl:text> = new XsltForms_itemset(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:text>itemset-</xsl:text>
					<xsl:value-of select="$iditemset"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@nodeset"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="xforms:label/@ref"/><xsl:with-param name="model" select="concat('&quot;',string(xforms:label/@model),'&quot;')"/></xsl:call-template>
			<xsl:if test="xforms:value">
				<xsl:text>,</xsl:text>
				<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="xforms:value/@ref"/><xsl:with-param name="model" select="concat('&quot;',string(xforms:value/@model),'&quot;')"/></xsl:call-template>
			</xsl:if>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:load" mode="script" priority="1">
			<xsl:variable name="idload" select="count(preceding::xforms:load|ancestor::xforms:load)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>load_</xsl:text>
			<xsl:value-of select="$idload"/>
			<xsl:text> = new XsltForms_load(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@ref"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="xforms:resource/@value">
					<xsl:for-each select="xforms:resource[1]">
						<xsl:call-template name="toScriptBinding"/>
					</xsl:for-each>
				</xsl:when>
				<xsl:when test="xforms:resource/text()">
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="normalize-space(xforms:resource/text())"/></xsl:call-template>
				</xsl:when>
				<xsl:otherwise>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@resource"/></xsl:call-template>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@show"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@targetid"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@instance"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
	</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:message" mode="script" priority="1">
			<xsl:variable name="idmessage" select="count(preceding::xforms:message|ancestor::xforms:message)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>message_</xsl:text>
			<xsl:value-of select="$idmessage"/>
			<xsl:text> = new XsltForms_message(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:text>message-</xsl:text>
					<xsl:value-of select="$idmessage"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@ref"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@level"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:model" mode="script" priority="1">
			<xsl:variable name="idmodel" select="count(preceding::xforms:model|ancestor::xforms:model)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>model_</xsl:text>
			<xsl:value-of select="$idmodel"/>
			<xsl:text> = XsltForms_model.create(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:variable name="rid">
				<xsl:choose>
					<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
					<xsl:when test="$idmodel = 0">"<xsl:value-of select="$id_pf"/>model-default</xsl:when>
					<xsl:otherwise>
						<xsl:value-of select="$jsid_pf"/>
						<xsl:text>model-</xsl:text>
						<xsl:value-of select="$idmodel"/>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:value-of select="$rid"/>
			<xsl:text>",</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@schema"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="xsd:schema" mode="schema"/>
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"><xsl:with-param name="current" select="."/></xsl:call-template>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:output" mode="script" priority="1">
			<xsl:variable name="idoutput" select="count(preceding::xforms:output|ancestor::xforms:output)"/>
			<xsl:text>new XsltForms_output(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:text>output-</xsl:text>
					<xsl:value-of select="$idoutput"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",</xsl:text>
			<xsl:choose>
				<xsl:when test="xforms:label/node()">1</xsl:when>
				<xsl:otherwise>0</xsl:otherwise>
			</xsl:choose>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@ref"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@mediatype"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:repeat" mode="script" priority="1">
			<xsl:variable name="idrepeat" select="count(preceding::xforms:repeat|ancestor::xforms:repeat)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>repeat_</xsl:text>
			<xsl:value-of select="$idrepeat"/>
			<xsl:text> = new XsltForms_repeat(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:text>repeat-</xsl:text>
					<xsl:value-of select="$idrepeat"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@nodeset"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:reset" mode="script" priority="1">
			<xsl:choose>
				<xsl:when test="parent::xforms:action">
					<xsl:variable name="idreset" select="count(preceding::xforms:reset|ancestor::xforms:reset)"/>
					<xsl:text>var </xsl:text>
					<xsl:value-of select="$vn_pf"/>
					<xsl:text>reset_</xsl:text>
					<xsl:value-of select="$idreset"/>
					<xsl:text> = new XsltForms_dispatch(</xsl:text>
					<xsl:value-of select="$vn_subform"/>
					<xsl:text>,"xforms-reset",</xsl:text>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@model"/></xsl:call-template>
					<xsl:text>,</xsl:text>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
					<xsl:text>,</xsl:text>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
					<xsl:text>,</xsl:text>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
					<xsl:text>);
</xsl:text>
				</xsl:when>
				<xsl:otherwise>
					<xsl:variable name="parentid">
						<xsl:for-each select="parent::*">
							<xsl:choose>
								<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
								<xsl:otherwise>
									<xsl:variable name="lname" select="local-name()"/>
									<xsl:value-of select="$jsid_pf"/>
									<xsl:value-of select="$lname"/>
									<xsl:text>-</xsl:text>
									<xsl:value-of select="count(preceding::*[local-name()=$lname]|ancestor::*[local-name()=$lname])"/>
								</xsl:otherwise>
							</xsl:choose>
						</xsl:for-each>
					</xsl:variable>
					<xsl:text>new XsltForms_listener(document.getElementById(</xsl:text>
					<xsl:value-of select="$parentid"/>
					<xsl:text>"),</xsl:text>
					<xsl:choose>
						<xsl:when test="@ev:target">
							<xsl:text>document.getElementById("</xsl:text>
							<xsl:value-of select="@ev:target"/>
							<xsl:text>"),</xsl:text>
						</xsl:when>
						<xsl:otherwise><xsl:text>null,</xsl:text></xsl:otherwise>
					</xsl:choose>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@ev:event"/></xsl:call-template>
					<xsl:text>,null,function(evt) {XsltForms_browser.run(new XsltForms_dispatch(</xsl:text>
					<xsl:value-of select="$vn_subform"/>
					<xsl:text>,"xforms-reset",</xsl:text>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@model"/></xsl:call-template>
					<xsl:text>,</xsl:text>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
					<xsl:text>,</xsl:text>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
					<xsl:text>,</xsl:text>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
					<xsl:text>),</xsl:text>
					<xsl:value-of select="$parentid"/>
					<xsl:text>",evt,false,true)});
</xsl:text>
					<xsl:apply-templates select="*" mode="script"/>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:select1" mode="script" priority="1">
			<xsl:variable name="idselect1" select="count(preceding::xforms:select1|ancestor::xforms:select1)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>select1_</xsl:text>
			<xsl:value-of select="$idselect1"/>
			<xsl:text> = new XsltForms_select(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:text>select1-</xsl:text>
					<xsl:value-of select="$idselect1"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",false,</xsl:text>
			<xsl:choose><xsl:when test="@appearance='full'"><xsl:text>true</xsl:text></xsl:when><xsl:otherwise><xsl:text>false</xsl:text></xsl:otherwise></xsl:choose>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@ref"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@incremental"/><xsl:with-param name="default">true</xsl:with-param></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:select" mode="script" priority="1">
			<xsl:variable name="idselect" select="count(preceding::xforms:select|ancestor::xforms:select)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>select_</xsl:text>
			<xsl:value-of select="$idselect"/>
			<xsl:text> = new XsltForms_select(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:text>select-</xsl:text>
					<xsl:value-of select="$idselect"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",true,</xsl:text>
			<xsl:choose><xsl:when test="@appearance='full'"><xsl:text>true</xsl:text></xsl:when><xsl:otherwise><xsl:text>false</xsl:text></xsl:otherwise></xsl:choose>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@ref"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@incremental"/><xsl:with-param name="default">true</xsl:with-param></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:send" mode="script" priority="1">
			<xsl:variable name="idsend" select="count(preceding::xforms:send|ancestor::xforms:send)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>send_</xsl:text>
			<xsl:value-of select="$idsend"/>
			<xsl:text> = new XsltForms_dispatch(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,"xforms-submit",</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@submission"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:setfocus" mode="script" priority="1">
			<xsl:variable name="idsetfocus" select="count(preceding::xforms:setfocus|ancestor::xforms:setfocus)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>setfocus_</xsl:text>
			<xsl:value-of select="$idsetfocus"/>
			<xsl:text> = new XsltForms_dispatch(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,"xforms-focus",</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@control"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:setindex" mode="script" priority="1">
			<xsl:variable name="idsetindex" select="count(preceding::xforms:setindex|ancestor::xforms:setindex)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>setindex_</xsl:text>
			<xsl:value-of select="$idsetindex"/>
			<xsl:text> = new XsltForms_setindex(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@repeat"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@index"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:setvalue" mode="script" priority="1">
			<xsl:variable name="idsetvalue" select="count(preceding::xforms:setvalue|ancestor::xforms:setvalue)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>setvalue_</xsl:text>
			<xsl:value-of select="$idsetvalue"/>
			<xsl:text> = new XsltForms_setvalue(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@ref"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@value"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="normalize-space(text())"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@context"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:show | xforms:hide" mode="script" priority="1">
			<xsl:variable name="lname" select="local-name()"/>
			<xsl:variable name="iddispatch" select="count(preceding::xforms:*[local-name()=$lname]|ancestor::xforms:*[local-name()=$lname])"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:value-of select="local-name()"/>
			<xsl:text>_</xsl:text>
			<xsl:value-of select="$iddispatch"/>
			<xsl:text> = new XsltForms_dispatch(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,"xforms-dialog-</xsl:text>
			<xsl:choose>
				<xsl:when test="local-name() = 'show'">open</xsl:when>
				<xsl:otherwise>close</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@dialog"/></xsl:call-template>
			<xsl:text>,null,null);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:submission" mode="script" priority="1">
			<xsl:variable name="idsubmission" select="count(preceding::xforms:submission|ancestor::xforms:submission)"/>
			<xsl:text>new XsltForms_submission(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:text>submission-</xsl:text>
					<xsl:value-of select="$idsubmission"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",</xsl:text>
			<xsl:for-each select="parent::*">
				<xsl:variable name="lname" select="local-name()"/>
				<xsl:value-of select="$vn_pf"/>
				<xsl:value-of select="$lname"/>
				<xsl:text>_</xsl:text>
				<xsl:value-of select="count(preceding::*[local-name()=$lname]|ancestor::*[local-name()=$lname])"/>
			</xsl:for-each>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@ref"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@bind"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="xforms:resource/@value">
					<xsl:variable name="idmodel">
						<xsl:for-each select="ancestor::xforms:model">
							<xsl:choose>
								<xsl:when test="@id">"<xsl:value-of select="@id"/>"</xsl:when>
								<xsl:otherwise>
									<xsl:variable name="nbmodel" select="count(preceding::xforms:model|ancestor::xforms:model)"/>
									<xsl:value-of select="$jsid_pf"/>
									<xsl:text>model-</xsl:text>
									<xsl:choose>
										<xsl:when test="$nbmodel = 0">default</xsl:when>
										<xsl:otherwise><xsl:value-of select="$nbmodel"/></xsl:otherwise>
									</xsl:choose>
									<xsl:text>"</xsl:text>
								</xsl:otherwise>
							</xsl:choose>
						</xsl:for-each>
					</xsl:variable>
					<xsl:for-each select="xforms:resource[1]">
						<xsl:call-template name="toScriptBinding"><xsl:with-param name="model" select="$idmodel"/></xsl:call-template>
					</xsl:for-each>
				</xsl:when>
				<xsl:otherwise>
					<xsl:variable name="resource">
						<xsl:choose>
							<xsl:when test="xforms:resource"><xsl:value-of select="xforms:resource"/></xsl:when>
							<xsl:when test="@resource"><xsl:value-of select="@resource"/></xsl:when>
							<xsl:otherwise><xsl:value-of select="@action"/></xsl:otherwise>
						</xsl:choose>
					</xsl:variable>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="$resource"/></xsl:call-template>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="xforms:method/@value">
					<xsl:variable name="idmodel">
						<xsl:for-each select="ancestor::xforms:model">
							<xsl:choose>
								<xsl:when test="@id">"<xsl:value-of select="@id"/>"</xsl:when>
								<xsl:otherwise>
									<xsl:variable name="nbmodel" select="count(preceding::xforms:model|ancestor::xforms:model)"/>
									<xsl:value-of select="$jsid_pf"/>
									<xsl:text>model-</xsl:text>
									<xsl:choose>
										<xsl:when test="$nbmodel = 0">default</xsl:when>
										<xsl:otherwise><xsl:value-of select="$nbmodel"/></xsl:otherwise>
									</xsl:choose>
									<xsl:text>"</xsl:text>
								</xsl:otherwise>
							</xsl:choose>
						</xsl:for-each>
					</xsl:variable>
					<xsl:for-each select="xforms:method[1]">
						<xsl:call-template name="toScriptBinding"><xsl:with-param name="model" select="$idmodel"/></xsl:call-template>
					</xsl:for-each>
				</xsl:when>
				<xsl:otherwise>
					<xsl:variable name="method">
						<xsl:choose>
							<xsl:when test="xforms:method"><xsl:value-of select="xforms:method"/></xsl:when>
							<xsl:when test="@method"><xsl:value-of select="@method"/></xsl:when>
							<xsl:otherwise>post</xsl:otherwise>
						</xsl:choose>
					</xsl:variable>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="$method"/></xsl:call-template>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@version"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@indent"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@mediatype"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@encoding"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@omit-xml-declaration"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@cdata-section-elements"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@replace"/><xsl:with-param name="default">"all"</xsl:with-param></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@targetref"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@instance"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@separator"/><xsl:with-param name="default">"&amp;"</xsl:with-param></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@includenamespaceprefixes"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@validate = 'false'">false</xsl:when>
				<xsl:when test="@validate">true</xsl:when>
				<xsl:when test="@serialization='none'">false</xsl:when>
				<xsl:otherwise>true</xsl:otherwise>
			</xsl:choose>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@relevant = 'false'">false</xsl:when>
				<xsl:when test="@relevant">true</xsl:when>
				<xsl:when test="@serialization='none'">false</xsl:when>
				<xsl:otherwise>true</xsl:otherwise>
			</xsl:choose>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@ajx:synchronized"/><xsl:with-param name="default">true</xsl:with-param></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@show"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@serialization"/></xsl:call-template>
			<xsl:text>)</xsl:text>
			<xsl:for-each select="xforms:header">
				<xsl:text>.header(</xsl:text>
				<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@nodeset"/></xsl:call-template>
				<xsl:text>,</xsl:text>
				<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@combine"/><xsl:with-param name="default">"append"</xsl:with-param></xsl:call-template>
				<xsl:text>,</xsl:text>
				<xsl:choose>
					<xsl:when test="xforms:name/@value">
						<xsl:text>new XsltForms_binding("xsd:string", "</xsl:text>
						<xsl:call-template name="toXPathExpr"><xsl:with-param name="p" select="xforms:name/@value"/></xsl:call-template>
						<xsl:text>")</xsl:text>
					</xsl:when>
					<xsl:otherwise>
						<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="xforms:name"/></xsl:call-template>
					</xsl:otherwise>
				</xsl:choose>
				<xsl:text>,[</xsl:text>
				<xsl:for-each select="xforms:value">
					<xsl:choose>
						<xsl:when test="@value">
							<xsl:text>new XsltForms_binding("xsd:string", "</xsl:text>
							<xsl:call-template name="toXPathExpr"><xsl:with-param name="p" select="@value"/></xsl:call-template>
							<xsl:text>")</xsl:text>
						</xsl:when>
						<xsl:otherwise>
							<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="."/></xsl:call-template>
						</xsl:otherwise>
					</xsl:choose>
					<xsl:if test="position() != last()">
						<xsl:text>,</xsl:text>
					</xsl:if>
				</xsl:for-each>
				<xsl:text>])</xsl:text>
			</xsl:for-each>
			<xsl:text>;
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:submit" mode="script" priority="1">
			<xsl:variable name="idsubmit" select="count(preceding::xforms:submit|ancestor::xforms:submit)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>submit_</xsl:text>
			<xsl:value-of select="$idsubmit"/>
			<xsl:text> = new XsltForms_trigger(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:text>submit-</xsl:text>
					<xsl:value-of select="$idsubmit"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@ref"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:text>new XsltForms_listener(document.getElementById(</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:text>submit-</xsl:text>
					<xsl:value-of select="$idsubmit"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>"),null,"DOMActivate",null,function(evt) {XsltForms_browser.run(new XsltForms_dispatch(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,"xforms-submit",</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@submission"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
			<xsl:text>),</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:text>submit-</xsl:text>
					<xsl:value-of select="$idsubmit"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",evt,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@ajx:synchronized"/><xsl:with-param name="default">true</xsl:with-param></xsl:call-template>
			<xsl:text>,true)});
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:switch" mode="script" priority="1">
			<xsl:variable name="idswitch" select="count(preceding::xforms:switch|ancestor::xforms:switch)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>switch_</xsl:text>
			<xsl:value-of select="$idswitch"/>
			<xsl:text> = new XsltForms_group(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="$jsid_pf"/>
					<xsl:text>switch-</xsl:text>
					<xsl:value-of select="$idswitch"/>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>",</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@ref"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:toggle" mode="script" priority="1">
			<xsl:variable name="idtoggle" select="count(preceding::xforms:toggle|ancestor::xforms:toggle)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>toggle_</xsl:text>
			<xsl:value-of select="$idtoggle"/>
			<xsl:text> = new XsltForms_toggle(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:choose>
				<xsl:when test="xforms:case/@value">
					<xsl:for-each select="xforms:case[1]">
						<xsl:call-template name="toScriptBinding"/>
					</xsl:for-each>
				</xsl:when>
				<xsl:when test="xforms:case/text()">
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="normalize-space(xforms:case/text())"/></xsl:call-template>
				</xsl:when>
				<xsl:otherwise>
					<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@case"/></xsl:call-template>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:trigger" mode="script" priority="1">
			<xsl:variable name="idtrigger" select="count(preceding::xforms:trigger|ancestor::xforms:trigger)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>trigger_</xsl:text>
			<xsl:value-of select="$idtrigger"/>
			<xsl:text> = new XsltForms_trigger(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:variable name="rid">
				<xsl:choose>
					<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
					<xsl:otherwise>
						<xsl:value-of select="$jsid_pf"/>
						<xsl:text>trigger-</xsl:text>
						<xsl:value-of select="$idtrigger"/>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:value-of select="$rid"/>
			<xsl:text>",</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@ref"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
			<xsl:call-template name="listeners"/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:unload" mode="script" priority="1">
			<xsl:variable name="idunload" select="count(preceding::xforms:unload|ancestor::xforms:unload)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>unload_</xsl:text>
			<xsl:value-of select="$idunload"/>
			<xsl:text> = new XsltForms_unload(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@targetid"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
	</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:script" mode="script" priority="1">
			<xsl:variable name="idscript" select="count(preceding::xforms:script|ancestor::xforms:script)"/>
			<xsl:text>var </xsl:text>
			<xsl:value-of select="$vn_pf"/>
			<xsl:text>script_</xsl:text>
			<xsl:value-of select="$idscript"/>
			<xsl:text> = new XsltForms_script(</xsl:text>
			<xsl:value-of select="$vn_subform"/>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="@ref"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:variable name="type">
				<xsl:choose>
					<xsl:when test="@type = 'application/xquery'">application/xquery</xsl:when>
					<xsl:otherwise>text/javascript</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="$type"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptBinding"><xsl:with-param name="p" select="."/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@show"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@if"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@while"/></xsl:call-template>
			<xsl:text>,</xsl:text>
			<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@iterate"/></xsl:call-template>
			<xsl:text>);
</xsl:text>
			<xsl:apply-templates select="*" mode="script"/>
	</xsl:template>
	
		
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="listeners">
			<xsl:param name="current"/>
			<xsl:variable name="lname" select="local-name()"/>
			<xsl:variable name="idlist" select="count(preceding::xforms:*[local-name()=$lname]|ancestor::xforms:*[local-name()=$lname])"/>
			<xsl:variable name="rid">
				<xsl:choose>
					<xsl:when test="@id">"<xsl:value-of select="@id"/></xsl:when>
					<xsl:when test="$lname = 'model' and $idlist = 0">"xsltforms-mainform-model-default</xsl:when>
					<xsl:otherwise>
						<xsl:value-of select="$jsid_pf"/>
						<xsl:value-of select="$lname"/>
						<xsl:text>-</xsl:text>
						<xsl:value-of select="$idlist"/>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:variable>
			<xsl:for-each select="xforms:setvalue|xforms:insert|xforms:load|xforms:delete|xforms:action|xforms:toggle|xforms:send|xforms:setfocus|xforms:dispatch|xforms:message|xforms:show|xforms:hide|xforms:script|xforms:unload">
				<xsl:text>new XsltForms_listener(document.getElementById(</xsl:text>
				<xsl:choose>
					<xsl:when test="@ev:observer">"<xsl:value-of select="@ev:observer"/></xsl:when>
					<xsl:otherwise><xsl:value-of select="$rid"/></xsl:otherwise>
				</xsl:choose>
				<xsl:text>"),</xsl:text>
				<xsl:choose>
					<xsl:when test="@ev:target">
						<xsl:text>document.getElementById("</xsl:text>
						<xsl:value-of select="@ev:target"/>
						<xsl:text>"),</xsl:text>
					</xsl:when>
					<xsl:otherwise><xsl:text>null,</xsl:text></xsl:otherwise>
				</xsl:choose>
				<xsl:call-template name="toScriptParam"><xsl:with-param name="p" select="@ev:event | @ev:actiontype"/></xsl:call-template>
				<xsl:text>,</xsl:text>
				<xsl:choose>
					<xsl:when test="@ev:phase">"<xsl:value-of select="@ev:phase"/>"</xsl:when>
					<xsl:otherwise>null</xsl:otherwise>
				</xsl:choose>
				<xsl:text>,function(evt) {XsltForms_browser.run(</xsl:text>
				<xsl:value-of select="$vn_pf"/>
				<xsl:variable name="lname2" select="local-name()"/>
				<xsl:variable name="nsuri" select="namespace-uri()"/>
				<xsl:value-of select="$lname2"/>
				<xsl:text>_</xsl:text>
				<xsl:value-of select="count(preceding::*[local-name()=$lname2 and namespace-uri()=$nsuri]|ancestor::*[local-name()=$lname2 and namespace-uri()=$nsuri])"/>
				<xsl:text>,XsltForms_browser.getId(evt.currentTarget ? evt.currentTarget : evt.target),evt,</xsl:text>
				<xsl:choose>
					<xsl:when test="@mode = 'synchronous'">true</xsl:when>
					<xsl:otherwise>false</xsl:otherwise>
				</xsl:choose>
				<xsl:text>,</xsl:text>
				<xsl:choose>
					<xsl:when test="@ev:propagate = 'stop'">false</xsl:when>
					<xsl:otherwise>true</xsl:otherwise>
				</xsl:choose>
				<xsl:text>);});
</xsl:text>
		</xsl:for-each>
	</xsl:template>
	
	
	
		
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="@*" mode="island" priority="0">
			<xsl:param name="nodeid"/>
			<node xmlns="" id="xf-node-{$nodeid}-{translate(name(),':','|')}" type="attribute" name="{local-name()}" prefix="{substring-before(name(),':')}" ns="{namespace-uri()}"><xsl:value-of select="."/></node>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="*" mode="island" priority="0">
			<xsl:param name="prefix"/>
			<xsl:variable name="nodeid" select="count(preceding::*|ancestor::*)"/>
			<node xmlns="" id="xf-node-{$prefix}-{$nodeid}" type="element" name="{local-name()}" prefix="{substring-before(name(),':')}" ns="{namespace-uri()}">
				<xsl:apply-templates select="@*" mode="island"><xsl:with-param name="nodeid" select="concat($prefix,'-',$nodeid)"/></xsl:apply-templates>
				<xsl:apply-templates select="* | text()" mode="island"><xsl:with-param name="prefix" select="$prefix"/></xsl:apply-templates>
			</node>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:instance" mode="island" priority="1">
			<xsl:variable name="instid" select="count(preceding::xforms:instance|ancestor::xforms:instance)"/>
			<instance xmlns="" id="xf-instance-{$instid}">
				<xsl:choose>
					<xsl:when test="@src">
						<xsl:apply-templates select="document(@src,/)/*" mode="island"><xsl:with-param name="prefix" select="$instid"/></xsl:apply-templates>
					</xsl:when>
					<xsl:when test="*">
						<xsl:apply-templates select="*" mode="island"><xsl:with-param name="prefix" select="$instid"/></xsl:apply-templates>
					</xsl:when>
					<xsl:when test="@resource">
						<xsl:apply-templates select="document(@resource,/)/*" mode="island"><xsl:with-param name="prefix" select="$instid"/></xsl:apply-templates>
					</xsl:when>
				</xsl:choose>
			</instance>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="xforms:model" mode="island" priority="1">
			<model xmlns="" id="{concat('xf-model-',count(preceding::xforms:model|ancestor::xforms:model))}">
				<xsl:apply-templates select="xforms:instance" mode="island"/>
			</model>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="text()" mode="island" priority="0">
			<xsl:value-of select="."/>
		</xsl:template>
	
	
		
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="processing-instruction()" mode="xml2string">
			<xsl:text>&lt;?</xsl:text> 
			<xsl:value-of select="name()"/> 
			<xsl:text> </xsl:text> 
			<xsl:value-of select="."/> 
			<xsl:text>?&gt;</xsl:text>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="*" mode="xml2string">
			<xsl:param name="root"/>
			<xsl:text>&lt;</xsl:text>
			<xsl:value-of select="name()"/>
			<xsl:variable name="parent" select=".."/>
			<xsl:variable name="element" select="."/>
			<xsl:choose>
				<xsl:when test="namespace::*">
					<xsl:for-each select="(namespace::* | @*/namespace::*)[name()!='xml']">
						<xsl:variable name="prefix" select="name()"/>
						<xsl:variable name="uri" select="."/>
						<xsl:if test="(not($parent/namespace::*[name()=$prefix and . = $uri]) or $root) and (($element|$element//*|$element//@*)[namespace-uri()=$uri])"> xmlns<xsl:if test="$prefix">:<xsl:value-of select="$prefix"/></xsl:if>="<xsl:value-of select="$uri"/>"</xsl:if>
					</xsl:for-each>
				</xsl:when>
				<xsl:otherwise>
					<xsl:variable name="prefixes"><xsl:for-each select="(. | @*)[name() != local-name() and not(starts-with(name(), 'xml:'))]"><xsl:sort select="substring-before(name(),':')"/><xsl:value-of select="substring-before(name(),':')"/>:<xsl:value-of select="namespace-uri()"/>|</xsl:for-each></xsl:variable>
					<xsl:call-template name="nmdecls"><xsl:with-param name="pfs" select="$prefixes"/></xsl:call-template>
					<xsl:if test="name() = local-name() and ($root or namespace-uri() != namespace-uri($parent))"> xmlns="<xsl:value-of select="namespace-uri()"/>"</xsl:if>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:apply-templates select="@*" mode="xml2string"/>
			<xsl:choose>
				<xsl:when test="node()">&gt;<xsl:apply-templates select="node()" mode="xml2string"><xsl:with-param name="root" select="false()"/></xsl:apply-templates>&lt;/<xsl:value-of select="name()"/>&gt;</xsl:when>
				<xsl:otherwise>/&gt;</xsl:otherwise>
			</xsl:choose>
			<xsl:text/>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="text()" mode="xml2string">
			<xsl:if test="normalize-space(.)!=''"><xsl:call-template name="escapeEntities"><xsl:with-param name="text" select="."/></xsl:call-template></xsl:if>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" match="@*" mode="xml2string">
			<xsl:text> </xsl:text>
			<xsl:value-of select="name()"/>
			<xsl:text>="</xsl:text>
			<xsl:call-template name="escapeEntities"><xsl:with-param name="text" select="."/></xsl:call-template>
			<xsl:text>"</xsl:text>
		</xsl:template>
	
		
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="escapeEntities">
			<xsl:param name="text"/>
			<xsl:param name="done"/>
			<xsl:param name="entities">&amp;.&amp;amp;.'.&amp;apos;.&lt;.&amp;lt;.&gt;.&amp;gt;.".&amp;quot;.
.&amp;#xA;.\.\\.</xsl:param>
			<xsl:param name="entity" select="substring-before($entities,'.')"/>
			<xsl:choose>
				<xsl:when test="contains($text, $entity)">
					<xsl:call-template name="escapeEntities">
						<xsl:with-param name="text" select="substring-after($text,$entity)"/>
						<xsl:with-param name="done" select="concat($done, substring-before($text,$entity), substring-before(substring-after($entities,'.'), '.'))"/>
						<xsl:with-param name="entities" select="$entities"/>
						<xsl:with-param name="entity" select="$entity"/>
					</xsl:call-template>
				</xsl:when>
				<xsl:when test="substring-after(substring-after($entities,'.'),'.') != ''">
					<xsl:call-template name="escapeEntities">
						<xsl:with-param name="text" select="concat($done, $text)"/>
						<xsl:with-param name="entities" select="substring-after(substring-after($entities,'.'),'.')"/>
					</xsl:call-template>
				</xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="concat($done, $text)"/>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:template>
	
		
		<xsl:template xmlns:xsl="http://www.w3.org/1999/XSL/Transform" name="nmdecls">
			<xsl:param name="prev"/>
			<xsl:param name="pfs"/>
			<xsl:if test="contains($pfs, '|')">
				<xsl:variable name="prev2" select="substring-before($pfs,':')"/>
				<xsl:if test="$prev2 != $prev"> xmlns:<xsl:value-of select="$prev2"/>="<xsl:value-of select="substring-before(substring-after($pfs,':'),'|')"/>"</xsl:if>
				<xsl:call-template name="nmdecls">
					<xsl:with-param name="prev" select="$prev2"/>
					<xsl:with-param name="pfs" select="substring-after($pfs, '|')"/>
				</xsl:call-template>
			</xsl:if>
		</xsl:template>
	
	
	
	</xsl:stylesheet>
