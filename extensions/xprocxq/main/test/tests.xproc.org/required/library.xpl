<p:library xmlns:p="http://www.w3.org/ns/xproc"
	   xmlns:c="http://www.w3.org/ns/xproc-step"
	   xmlns:cl="http://xmlcalabash.com/ns/library"
	   xmlns:err="http://www.w3.org/ns/xproc-error">

<p:declare-step name="make-sequence" type="cl:make-sequence">
  <p:input port="source" sequence="true"/>
  <p:output port="result" sequence="true"/>
  <p:option name="count" required="true"/>

  <p:choose>
    <p:when test="$count &lt;= 0">
      <p:identity>
	<p:input port="source">
	  <p:empty/>
	</p:input>
      </p:identity>
    </p:when>
    <p:when test="$count = 1">
      <p:identity/>
    </p:when>
    <p:when test="$count = 2">
      <p:identity>
	<p:input port="source">
	  <p:pipe step="make-sequence" port="source"/>
	  <p:pipe step="make-sequence" port="source"/>
	</p:input>
      </p:identity>
    </p:when>
    <p:otherwise>
      <p:identity name="make-one"/>
      <cl:make-sequence name="make-n-minus-one">
	<p:with-option name="count" select="$count - 1">
	  <p:empty/>
	</p:with-option>
      </cl:make-sequence>
      <p:identity>
	<p:input port="source">
	  <p:pipe step="make-one" port="result"/>
	  <p:pipe step="make-n-minus-one" port="result"/>
	</p:input>
      </p:identity>
    </p:otherwise>
  </p:choose>
</p:declare-step>

<p:declare-step name="fibonacci" type="cl:fibonacci">
  <p:input port="source" sequence="true"/>
  <p:output port="result" sequence="true"/>
  <p:option name="number" required="true"/>

  <p:choose>
    <p:xpath-context>
      <p:empty/>
    </p:xpath-context>
    <p:when test="$number = 0">
      <p:split-sequence test="position() = 1"/>
    </p:when>
    <p:when test="$number = 1">
      <p:split-sequence test="position() = 1"/>
    </p:when>
    <p:otherwise>
      <cl:fibonacci name="make-n-minus-two">
	<p:with-option name="number" select="$number - 2">
	  <p:empty/>
	</p:with-option>
      </cl:fibonacci>
      <cl:fibonacci name="make-n-minus-one">
	<p:with-option name="number" select="$number - 1">
	  <p:empty/>
	</p:with-option>
      </cl:fibonacci>
      <p:identity>
	<p:input port="source">
	  <p:pipe step="make-n-minus-one" port="result"/>
	  <p:pipe step="make-n-minus-two" port="result"/>
	</p:input>
      </p:identity>
    </p:otherwise>
  </p:choose>
</p:declare-step>

</p:library>
