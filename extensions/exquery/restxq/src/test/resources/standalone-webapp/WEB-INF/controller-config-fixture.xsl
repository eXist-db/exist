<?xml version="1.0" encoding="UTF-8"?>
<!--

    Copyright © 2001, Adam Retter
    All rights reserved.

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions are met:
        * Redistributions of source code must retain the above copyright
          notice, this list of conditions and the following disclaimer.
        * Redistributions in binary form must reproduce the above copyright
          notice, this list of conditions and the following disclaimer in the
          documentation and/or other materials provided with the distribution.
        * Neither the name of the <organization> nor the
          names of its contributors may be used to endorse or promote products
          derived from this software without specific prior written permission.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
    ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
    WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
    DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
    DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
    (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
    LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
    ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
    (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

-->
<!-- Generates this module's test controller-config.xml, see schema/generate-controller-config-fixture.xsl. -->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xsl xs">

    <xsl:import href="../../../../../../../../schema/generate-controller-config-fixture.xsl"/>

    <xsl:param name="keep-forwards" as="xs:string*" select="('/rest', '/restxq/')"/>

    <!-- This test routes both /rest and /servlet to the same servlet, unlike the template's plain /rest. -->
    <xsl:param name="rest-forward-pattern" as="xs:string?" select="'/(rest|servlet)/'"/>

    <!-- This test serves the default app from the filesystem, not from /db, unlike the template's roots. -->
    <xsl:param name="root-elements" as="element()*">
        <root xmlns="http://exist.sourceforge.net/NS/exist" pattern="/apps" path="xmldb:exist:///db/apps"/>
        <root xmlns="http://exist.sourceforge.net/NS/exist" pattern=".*" path="/"/>
    </xsl:param>

</xsl:stylesheet>
