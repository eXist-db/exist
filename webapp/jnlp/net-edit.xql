xquery version "1.0";

import module namespace jnlp    = "http://exist-db.org/xquery/jnlp" at "jnlp.xqm";
import module namespace request = "http://exist-db.org/xquery/request";

declare variable $jnlp {

    <jnlp spec="1.0+" href="gate.jnlp">
      <information>
        <title>eXist Gate client</title>
        <vendor>exist-db.org</vendor>
        <homepage href="http://exist-db.org/"/>
        <description>Java applet for automate document managment via REST.</description>
        <description kind="short">eXist Gate client</description>
        <description kind="tooltip">eXist Gate client</description>
        <icon href="/exist/resources/jnlp_logo.jpg" kind="splash"/>
        <icon href="/exist/resources/jnlp_logo.jpg"/>
        <icon href="/exist/resources/jnlp_icon_64x64.gif" width="64" height="64"/>
        <icon href="/exist/resources/jnlp_icon_32x32.gif" width="32" height="32"/>
      </information>
      <security>
        <all-permissions/>
      </security>
      <resources>
        <j2se version="1.6+"/>
        <jar href="exist-gate.jar" main="true"/>
        <jar href="commons-codec-%latest%.jar"/>
        <jar href="commons-httpclient-%latest%.jar"/>
        <jar href="commons-logging-%latest%.jar"/>
      </resources>
      <applet-desc documentBase="{request:get-attribute("codebase")}" 
          name="Gate" main-class="org.exist.gate.GateApplet" width="1" height="1"/>
    </jnlp>

};

jnlp:prepare($jnlp)

