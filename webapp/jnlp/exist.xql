xquery version "1.0";

import module namespace jnlp    = "http://exist-db.org/xquery/hnlp" at "jnlp.xqm";
import module namespace request = "http://exist-db.org/xquery/request";

declare variable $jnlp {

    <jnlp spec="1.0+" href="exist.jnlp">
      <information>
        <title>eXist XML-DB client</title>
        <vendor>exist-db.org</vendor>
        <homepage href="http://exist-db.org/"/>
        <description>Integrated command-line and gui client, entirely based on the XML:DB API and
          provides commands for most database related tasks, like creating and removing collections,
          user management, batch-loading XML data or querying.</description>
        <description kind="short">eXist XML-DB client</description>
        <description kind="tooltip">eXist XML-DB client</description>
        <icon href="jnlp_logo.jpg" kind="splash"/>
        <icon href="jnlp_logo.jpg"/>
        <icon href="jnlp_icon_64x64.gif" width="64" height="64"/>
        <icon href="jnlp_icon_32x32.gif" width="32" height="32"/>
      </information>
      <security>
        <all-permissions/>
      </security>
      <resources>
        <j2se version="1.6+"/>
        <jar href="exist.jar" main="true"/>
        <jar href="xmldb.jar"/>
        <jar href="xmlrpc-common-%latest%.jar"/>
        <jar href="xmlrpc-client-%latest%.jar"/>
        <jar href="ws-commons-util-%latest%.jar"/>
        <jar href="commons-pool-%latest%.jar"/>
        <jar href="excalibur-cli-%latest%.jar"/>
        <jar href="jEdit-syntax.jar"/>
        <jar href="jline-%latest%.jar"/>
        <jar href="log4j-%latest%.jar"/>
        <jar href="sunxacml-%latest%.jar"/>
      </resources>
      <application-desc main-class="org.exist.client.InteractiveClient">
        <argument>{substring-before(request:get-url(), "/jnlp")}/xmlrpc</argument>
        <argument>--no-embedded-mode</argument>
      </application-desc>
    </jnlp>

};

jnlp:prepare($jnlp)
