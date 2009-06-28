xquery version "1.0" encoding "UTF-8";

import module namespace const = "http://xproc.net/xproc/const";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace u = "http://xproc.net/xproc/util";

declare variable $local:XPROCXQ_EXAMPLES := "/db/examples";   (: CHANGE ME :)

let $stdin := document{<test>
                            <a>
                                <c/>
                                <b/>
                            </a>
                            <c>
                                <d>test
                                 </d>
                                <b/>
                             </c>
                       </test>}

let $pipeline :=document{
                    <p:pipeline name="pipeline"
                                xmlns:p="http://www.w3.org/ns/xproc"
                                xmlns:c="http://www.w3.org/ns/xproc-step">
						<p:set-attributes match="//c">
						     <p:input port="attributes">
								<p:inline>
									<root rootattr="test">
										<a test="1"/>
										<b b="2"/>
									</root>
								</p:inline>
							 </p:input>
						</p:set-attributes>
	             	</p:pipeline>
                }

return
     xproc:run($pipeline,$stdin)