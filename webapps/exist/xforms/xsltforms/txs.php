<?php
// Copyright (C) 2008-2010 Alain COUTHURES <agenceXML>
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

//require("class.phpmailer.php");

$namedentities["AElig"] = 198;     $namedentities["Aacute"] = 193;     $namedentities["Acirc"] = 194;     $namedentities["Agrave"] = 192;     $namedentities["Alpha"] = 913;
$namedentities["Aring"] = 197;     $namedentities["Atilde"] = 195;       $namedentities["Auml"] = 196;     $namedentities["Beta"] = 914;          $namedentities["Ccedil"] = 199;
$namedentities["Chi"] = 935;        $namedentities["Dagger"] = 8225;   $namedentities["Delta"] = 916;     $namedentities["ETH"] = 208;          $namedentities["Eacute"] = 201;
$namedentities["Ecirc"] = 202;     $namedentities["Egrave"] = 200;      $namedentities["Epsilon"] = 917; $namedentities["Eta"] = 919;            $namedentities["Euml"] = 203;
$namedentities["Gamma"] = 915; $namedentities["Iacute"] = 205;      $namedentities["Icirc"] = 206;      $namedentities["Igrave"] = 204;      $namedentities["Iota"] = 921;
$namedentities["Iuml"] = 207;      $namedentities["Kappa"] = 922;       $namedentities["Lambda"] = 923; $namedentities["Mu"] = 924;            $namedentities["Ntilde"] = 209;
$namedentities["Nu"] = 925;         $namedentities["OElig"] = 338;        $namedentities["Oacute"] = 211;  $namedentities["Ocirc"] = 212;       $namedentities["Ograve"] = 210;
$namedentities["Omega"] = 937; $namedentities["Omicron"] = 927;   $namedentities["Oslash"] = 216;   $namedentities["Otilde"] = 213;      $namedentities["Ouml"] = 214;
$namedentities["Phi"] = 934;        $namedentities["Pi"] = 928;              $namedentities["Prime"] = 8243;   $namedentities["Psi"] = 936;           $namedentities["Rho"] = 929;
$namedentities["Scaron"] = 352;  $namedentities["Sigma"] = 931;       $namedentities["THORN"] = 222;  $namedentities["Tau"] = 932;         $namedentities["Theta"] = 920;
$namedentities["Uacute"] = 218;  $namedentities["Ucirc"] = 219;        $namedentities["Ugrave"] = 217;   $namedentities["Upsilon"] = 933;   $namedentities["Uuml"] = 220;
$namedentities["Xi"] = 926;           $namedentities["Yacute"] = 221;     $namedentities["Yuml"] = 376;      $namedentities["Zeta"] = 918;        $namedentities["aacute"] = 225;
$namedentities["acirc"] = 226;      $namedentities["acute"] = 180;       $namedentities["aelig"] = 230;      $namedentities["agrave"] = 224;     $namedentities["alpha"] = 945;
$namedentities["and"] = 8743;      $namedentities["ang"] = 8736;        $namedentities["aring"] = 229;      $namedentities["asymp"] = 8776;   $namedentities["atilde"] = 227;
$namedentities["auml"] = 228;      $namedentities["bdquo"] = 8222;    $namedentities["beta"] = 946;       $namedentities["brvbar"] = 166;      $namedentities["bull"] = 8226;
$namedentities["cap"] = 8745;     $namedentities["ccedil"] = 231;       $namedentities["cedil"] = 184;      $namedentities["cent"] = 162;          $namedentities["chi"] = 967;
$namedentities["circ"] = 710;       $namedentities["clubs"] = 9827;      $namedentities["cong"] = 8773;   $namedentities["copy"] = 169;          $namedentities["crarr"] = 8629;
$namedentities["cup"] = 8746;     $namedentities["curren"] = 164;      $namedentities["dagger"] = 8224; $namedentities["darr"] = 8595;        $namedentities["deg"] = 176;
$namedentities["delta"] = 948;     $namedentities["diams"] = 9830;    $namedentities["divide"] = 247;    $namedentities["eacute"] = 233;      $namedentities["ecirc"] = 234;
$namedentities["egrave"] = 232;  $namedentities["empty"] = 8709;    $namedentities["emsp"] = 8195;  $namedentities["ensp"] = 8194;       $namedentities["epsilon"] = 949;
$namedentities["equiv"] = 8801;  $namedentities["eta"] = 951;           $namedentities["eth"] = 240;        $namedentities["euml"] = 235;         $namedentities["euro"] = 8364;
$namedentities["exists"] = 8707; $namedentities["fnof"] = 402;          $namedentities["forall"] = 8704;   $namedentities["frac12"] = 189;      $namedentities["frac14"] = 188;
$namedentities["frac34"] = 190;  $namedentities["gamma"] = 947;     $namedentities["ge"] = 8805;       $namedentities["harr"] = 8596;        $namedentities["hearts"] = 9829;
$namedentities["hellip"] = 8230;  $namedentities["iacute"] = 237;      $namedentities["icirc"] = 238;      $namedentities["iexcl"] = 161;         $namedentities["igrave"] = 236;
$namedentities["infin"] = 8734;    $namedentities["int"] = 8747;          $namedentities["iota"] = 953;       $namedentities["iquest"] = 191;      $namedentities["isin"] = 8712;
$namedentities["iuml"] = 239;      $namedentities["kappa"] = 954;       $namedentities["lambda"] = 923; $namedentities["laquo"] = 171;        $namedentities["larr"] = 8592;
$namedentities["lceil"] = 8968;    $namedentities["ldquo"] = 8220;     $namedentities["le"] = 8804;        $namedentities["lfloor"] = 8970;      $namedentities["lowast"] = 8727;
$namedentities["loz"] = 9674;      $namedentities["lrm"] = 8206;         $namedentities["lsaquo"] = 8249;$namedentities["lsquo"] = 8216;     $namedentities["macr"] = 175;
$namedentities["mdash"] = 8212;$namedentities["micro"] = 181;       $namedentities["middot"] = 183;  $namedentities["minus"] = 8722;    $namedentities["mu"] = 956;
$namedentities["nabla"] = 8711;  $namedentities["nbsp"] = 160;        $namedentities["ndash"] = 8211; $namedentities["ne"] = 8800;          $namedentities["ni"] = 8715;
$namedentities["not"] = 172;        $namedentities["notin"] = 8713;     $namedentities["nsub"] = 8836;   $namedentities["ntilde"] = 241;       $namedentities["nu"] = 925;
$namedentities["oacute"] = 243;  $namedentities["ocirc"] = 244;       $namedentities["oelig"] = 339;      $namedentities["ograve"] = 242;     $namedentities["oline"] = 8254;
$namedentities["omega"] = 969;  $namedentities["omicron"] = 959;  $namedentities["oplus"] = 8853;   $namedentities["or"] = 8744;          $namedentities["ordf"] = 170;
$namedentities["ordm"] = 186;     $namedentities["oslash"] = 248;    $namedentities["otilde"] = 245;     $namedentities["otimes"] = 8855;  $namedentities["ouml"] = 246;
$namedentities["para"] = 182;      $namedentities["part"] = 8706;      $namedentities["permil"] = 8240;  $namedentities["perp"] = 8869;      $namedentities["phi"] = 966;
$namedentities["pi"] = 960;          $namedentities["piv"] = 982;          $namedentities["plusmn"] = 177;   $namedentities["pound"] = 163;      $namedentities["prime"] = 8242;
$namedentities["prod"] = 8719;   $namedentities["prop"] = 8733;      $namedentities["psi"] = 968;         $namedentities["radic"] = 8730;      $namedentities["raquo"] = 187;
$namedentities["rarr"] = 8594;     $namedentities["rceil"] = 8969;     $namedentities["rdquo"] = 8221;   $namedentities["reg"] = 174;          $namedentities["rfloor"] = 8971;
$namedentities["rho"] = 961;       $namedentities["rlm"] = 8207;        $namedentities["rsaquo"] = 8250; $namedentities["rsquo"] = 8217;    $namedentities["sbquo"] = 8218;
$namedentities["scaron"] = 353; $namedentities["sdot"] = 8901;      $namedentities["sect"] = 167;       $namedentities["shy"] = 173;          $namedentities["sigma"] = 963;
$namedentities["sigmaf"] = 962; $namedentities["sim"] = 8764;       $namedentities["spades"] = 9824; $namedentities["sub"] = 8834;       $namedentities["sube"] = 8838;
$namedentities["sum"] = 8721;   $namedentities["sup"] = 8835;       $namedentities["sup1"] = 185;      $namedentities["sup3"] = 179;       $namedentities["supe"] = 8839;
$namedentities["szlig"] = 223;    $namedentities["tau"] = 964;          $namedentities["there4"] = 8756;  $namedentities["theta"] = 952;      $namedentities["thetasym"] = 977;
$namedentities["thinsp"] = 8201;$namedentities["thorn"] = 254;      $namedentities["tilde"] = 732;       $namedentities["times"] = 215;      $namedentities["trade"] = 8482;
$namedentities["uacute"] = 250; $namedentities["uarr"] = 8593;      $namedentities["ucirc"] = 251;      $namedentities["ugrave"] = 249;    $namedentities["uml"] = 168;
$namedentities["up2"] = 178;      $namedentities["upsih"] = 978;      $namedentities["upsilon"] = 965;  $namedentities["uuml"] = 252;        $namedentities["xi"] = 958;
$namedentities["yacute"] = 253; $namedentities["yen"] = 165;         $namedentities["yuml"] = 255;      $namedentities["zeta"] = 950;         $namedentities["zwj"] = 8205;
$namedentities["zwnj"] = 8204;
$emptytags = array("area", "base", "basefont", "br", "col", "frame", "hr", "img", "input", "isindex", "link", "meta", "param");
$autoclosetags["basefont"] = array("basefont");
$autoclosetags["colgroup"] = array("colgroup");
$autoclosetags["dd"] = array("colgroup");
$autoclosetags["dt"] = array("dt");
$autoclosetags["li"] = array("li");
$autoclosetags["p"] = array("p");
$autoclosetags["thead"] = array("tbody", "tfoot");
$autoclosetags["tbody"] = array("thead", "tfoot");
$autoclosetags["tfoot"] = array("thead", "tbody");
$autoclosetags["th"] = array("td");
$autoclosetags["td"] = array("th", "td");
$autoclosetags["tr"] = array("tr");
$autoclosekeys = array("basefont", "colgroup", "dd", "dt", "li", "p", "thead", "tbody", "tfoot", "th", "td", "tr");
define("states_text", 0);define("states_tag", 1);define("states_endtag", 2);define("states_attrtext", 3);define("states_script", 4);define("states_endscript", 5);
define("states_specialtag", 6);define("states_comment", 7);define("states_skipcdata", 8);define("states_entity", 9);define("states_namedentity", 10);
define("states_numericentity", 11);define("states_hexaentity", 12);define("states_tillgt", 13);define("states_tillquote", 14);define("states_tillinst", 15);define("states_andgt", 16);
function html2xml($s) {
	global $namedentities, $emptytags, $autoclosetags, $autoclosekeys;
	$r2 = "";
	$r = "";
	$limit = strlen($s);
	$state = states_text;
	$prevstate = $state;
	$opentags = array();
	$name = "";
	$tagname = "";
	$attrname = "";
	$attrs = "";
	$attrnames = array();
	$entvalue = 0;
	$attrdelim = "\"";
	$attrvalue = "";
	$cs = "";
	$prec = " ";
	$preprec = " ";
	$c = " ";
	if(substr($s, 0, 3) == "\xef\xbb\xbf") {
		$encoding = "utf-8";
		$start = 3;
	} else {
		$encoding = "iso-8859-1";
		$start = 0;
	}
	for($i=$start; $i<$limit && (($r2=="" && $r=="") || count($opentags)!=0); $i++) {
		if(strlen($r) > 10240) {
			$r2 .= $r;
			$r = "";
		}
		$c = $s{$i};
		switch($state) {
			case states_text:
				if($c == "<") {
					$name = "";
					$tagname = "";
					$attrname = "";
					$attrs = "";
					$attrnames = array();
					$state = states_tag;
					break;
				}
				if(!ctype_space($c) && count($opentags)==0) {
					$r .= "<html>";
					array_push($opentags, "html");
				}
				if(ctype_space($c) && count($opentags)==0) {
					break;
				}
				if($c == "&") {
					$name = "";
					$entvalue = 0;
					$prevstate = $state;
					$state = states_entity;
					break;
				}
				$r .= $c;
				break;
			case states_tag:
				if($c=="?" && $tagname=="") {
					$state = states_tillinst;
					break;
				}
				if($c=="!" && $tagname=="") {
					$state = states_specialtag;
					$prec = " ";
					break;
				}
				if($c=="/" && $name=="" && $tagname=="") {
					$state = states_endtag;
					$name = "";
					break;
				}
				if(ctype_space($c)) {
					if($name=="") {
						break;
					}
					if($tagname=="" && $name!="_") {
						$tagname = $name;
						$name = "";
						break;
					}
					if($attrname=="" && $name!="_") {
						$attrname = strtolower($name);
						$name = "";
						break;
					}
					break;
				}
				if($c=="=") {
					if($attrname=="" && $name!="_") {
						$attrname = strtolower($name);
						$name = "";
					}
					$state = states_tillquote;
					break;
				}
				if($c=="/" && ($tagname!="" || $name!="")) {
					if($tagname=="") {
						$tagname = $name;
					}
					$tagname = strtolower($tagname);
					if($tagname!="html" && count($opentags)==0) {
						$r .= "<html>";
						array_push($opentags, "html");
					}
					if(in_array($tagname, $autoclosekeys) && count($opentags) > 0) {
						$prevtag = $opentags[count($opentags)-1];
						if(in_array($prevtag, $autoclosetags[$tagname])) {
							array_pop($opentags);
							$r .= "</".$prevtag.">";
						}
					}
					$r .= "<".$tagname.$attrs."/>";
					$state = states_tillgt;
					break;
				}
				if($c==">") {
					if($tagname=="" && $name!="") {
						$tagname = $name;
					}
					if($tagname!="") {
						$tagname = strtolower($tagname);
						if($tagname!="html" && count($opentags)==0) {
							$r .= "<html>";
							array_push($opentags, "html");
						}
						if(in_array($tagname, $autoclosekeys) && count($opentags) > 0) {
							$prevtag = $opentags[count($opentags)-1];
							if(in_array($prevtag, $autoclosetags[$tagname])) {
								array_pop($opentags);
								$r .= "</".$prevtag.">";
							}
						}
						if(in_array($tagname, $emptytags)) {
							$r .= "<".$tagname.$attrs."/>";
						} else {
							array_push($opentags, $tagname);
							$r .= "<".$tagname.$attrs.">";
							if($tagname=="script") {
								$r .= "<![CDATA[";
								array_pop($opentags);
								$state = states_script;
								break;
							}
						}
						$state = states_text;
						break;
					}
				}
				if($attrname!="") {
					$attrs .= " ".$attrname."=\"".$attrname."\"";
					$attrname = "";
				}
				$name .= (ctype_alnum($c) && $name!="") || ctype_alpha($c) ? $c : ($name=="" ? "_" : ($c=="-" ? "-" : ($name != "_" ? "_" : "")));
				break;
			case states_endtag:
				if($c==">") {
					$name = strtolower($name);
					if(in_array($name, $opentags)) {
						while(($prevtag = array_pop($opentags)) != $name) {
							$r .= "</".$prevtag.">";
						}
						$r .= "</".$name.">";
					} else {
						if($name!="html" && count($opentags)==0) {
							$r .= "<html>";
							array_push($opentags, "html");
						}
					}
					$state = states_text;
					break;
				}
				if(ctype_space($c)) {
					break;
				}
				$name .= ctype_alnum($c) ? $c : ($name != "_" ? "_" : "");
				break;
			case states_attrtext:
				if($c==$attrdelim || (ctype_space($c) && $attrdelim==" ")) {
					if(!in_array($attrname, $attrnames)) {
						array_push($attrnames, $attrname);
						$attrs .= " ".$attrname."=\"".$attrvalue."\"";
					}
					$attrname = "";
					$state = states_tag;
					break;
				}
				if($attrdelim==" " && ($c=="/" || $c==">")) {
					$tagname = strtolower($tagname);
					if($tagname!="html" && count($opentags)==0) {
						$r .= "<html>";
						array_push($opentags, "html");
					}
					if(in_array($tagname, $autoclosekeys) && count($opentags) > 0) {
						$prevtag = $opentags[count($opentags)-1];
						if(in_array($prevtag, $autoclosetags[$tagname])) {
							array_pop($opentags);
							$r .= "</".$prevtag.">";
						}
					}
					if(!in_array($attrname, $attrnames)) {
						array_push($attrnames, $attrname);
						$attrs .= " ".$attrname."=\"".$attrvalue."\"";
					}
					$attrname = "";
					if($c=="/") {
						$r .= "<".$tagname.$attrs."/>";
						$state = states_tillgt;
						break;
					}
					if($c==">") {
						if(in_array($tagname, $emptytags)) {
							$r .= "<".$tagname.$attrs."/>";
							$state = states_text;
							break;
						} else {
							array_push($opentags, $tagname);
							$r .= "<".$tagname.$attrs.">";
							if($tagname=="script") {
								$r .= "<![CDATA[";
								array_pop($opentags);
								$prec = " ";
								$preprec = " ";
								$state = states_script;
								break;
							}
							$state = states_text;
							break;
						}
					}
				}
				if($c=="&") {
					$name = "";
					$entvalue = 0;
					$prevstate = $state;
					$state = states_entity;
					break;
				}
				$attrvalue .= $c=="\"" ? "&quot;" : ($c == "'" ? "&apos;" : ($c == "<" ? "&lt;" : ($c == ">" ? "&gt;" : $c)));
				break;
			case states_script:
				if($c=="/" && $prec=="<") {
					$state = states_endscript;
					$name = "";
					break;
				}
				if($c=="[" && $prec=="!" && $preprec=="<") {
					$state = states_skipcdata;
					$name = "<![";
					break;
				}
				if($c==">" && $prec=="]" && $preprec=="]") {
					$c = $r{strlen($r)-3};
					$r = substr($r, 0, strlen($r)-4);
				}
				$r .= $c;
				$preprec = $prec;
				$prec = $c;
				break;
			case states_endscript:
				if($c==">" && strtolower($name)=="script") {
					$r = substr($r, 0, strlen($r)-1);
					$r .= "]]></script>";
					$state = states_text;
					break;
				}
				$name .= $c;
				if(substr("script", 0, strlen($name)) != strtolower($name)) {
					$r .= $name;
					$state = states_script;
				}
				break;
			case states_specialtag:
				if($c!="-") {
					$state = states_tillgt;
					break;
				}
				if($prec=="-") {
					$state = states_comment;
					$preprec = " ";
					break;
				}
				$prec = $c;
				break;
			case states_comment:
				if($c==">" && $prec=="-" && $preprec=="-") {
					$state = states_text;
					break;
				}
				$preprec = $prec;
				$prec = $c;
				break;
			case states_skipcdata:
				if($name=="<![CDATA[") {
					$state = states_script;
					break;
				}
				$name .= $c;
				if(substr("<![CDATA[", 0, strlen($name)) != $name) {
					$r .= $name;
					$state = states_script;
				}
				break;
			case states_entity:
				if($c=="#") {
					$state = states_numericentity;
					break;
				}
				$name .= $c;
				$state = states_namedentity;
				break;
			case states_numericentity:
				if($c=="x" || $c=="X") {
					$state = states_hexaentity;
					break;
				}
				if($c==";") {
					$ent = "&#".$entvalue.";";
					if($prevstate==states_text) {
						$r .= $ent;
					} else {
						$attrvalue .= $ent;
					}
					$state = $prevstate;
					break;
				}
				$entvalue = $entvalue * 10 + ord($c) - ord("0");
				break;
			case states_hexaentity:
				if($c==";") {
					$ent = "&#".$entvalue.";";
					if($prevstate==states_text) {
						$r .= $ent;
					} else {
						$attrvalue .= $ent;
					}
					$state = $prevstate;
					break;
				}
				$entvalue = $entvalue * 16 + (ctype_digit($c) ? ord($c)-ord("0") : ord(strtoupper($c)) - ord("A"));
				break;
			case states_namedentity:
				if($c==";") {
					$name = strtolower($name);
					if($name=="amp" || $name=="lt" || $name=="gt" || $name=="quot" || $name=="apos") {
						$ent = "&".$name.";";
						$name = "";
						if($prevstate==states_text) {
							$r .= $ent;
						} else {
							$attrvalue .= $ent;
						}
						$state = $prevstate;
						break;
					}
					if(in_array($name, array_keys($namedentities))) {
						$entvalue = $namedentities[$name];
					}
					$ent = "&#".$entvalue.";";
					$name = "";
					if($prevstate==states_text) {
						$r .= $ent;
					} else {
						$attrvalue .= $ent;
					}
					$state = $prevstate;
					break;
				}
				if(!ctype_alnum($c) || strlen($name) > 6) {
					$ent = "&amp;".$name;
					$name = "";
					if($prevstate==states_text) {
						$r .= $ent;
					} else {
						$attrvalue .= $ent;
					}
					$state = $prevstate;
					$i--;
					break;
				}
				$name .= $c;
				break;
			case states_tillinst:
				if($c=="?") {
					$state = states_andgt;
				}
				break;
			case states_andgt:
				if($c==">") {
					$state = states_text;
					break;
				}
				$state = states_tillinst;
				break;
			case states_tillgt:
				if($c==">") {
					$state = states_text;
				}
				break;
			case states_tillquote:
				if(ctype_space($c)) {
					break;
				}
				if($c=="\"" || $c=="'") {
					$attrdelim = $c;
					$attrvalue = "";
					$state = states_attrtext;
					break;
				}
				if($c=="/" || $c==">") {
					$attrs .= " ".$attrname."=\"".$attrname."\"";
				}
				if($c=="/") {
					$r .= "<".strtolower($tagname).$attrs."/>";
					$state = states_tillgt;
					break;
				}
				if($c==">") {
					$tagname = $tagname.ToLower();
					if($tagname!="html" && count($opentags)==0) {
						$r .= "<html>";
						array_push($opentags, "html");
					}
					if(in_array($tagname, $autoclosekeys) && count($opentags) > 0) {
						$prevtag = $opentags[count($opentags)-1];
						if(in_array($prevtag, $autoclosetags[$tagname])) {
							array_pop($opentags);
							$r .= "</".$prevtag.">";
						}
					}
					if(in_array($tagname, $emptytags)) {
						$r .= "<".$tagname.$attrs."/>";
						$state = states_text;
						break;
					} else {
						array_push($opentags, $tagname);
						$r .= "<".$tagname.$attrs.">";
						if($tagname=="script") {
							$r .= "<![CDATA[";
							array_pop($opentags);
							$state = states_script;
							break;
						}
					}
				}
				$attrdelim = " ";
				$attrvalue = $c;
				$state = states_attrtext;
				break;
		}
	}
	while(count($opentags) != 0) {
		$r .= "</".array_pop($opentags).">";
	}
	$r2 .= $r;
	return "<?xml version=\"1.0\" encoding=\"".$encoding."\"?>\n".$r2;
}
function Load($n) {
	switch($n->getAttribute("format")) {
		case "xhtml":
		case "xhtm":
		case "txs":
		case "xsl":
		case "xforms":
		case "dc":
		case "docbook":
		case "xml":
			if(file_exists($n->getAttribute("filename"))) {
				$xLoad = new DOMDocument();
				$xLoad->load($n->getAttribute("filename"));
				$root = $n->ownerDocument->importNode($xLoad->documentElement, true);
				$n->parentNode->replaceChild($root, $n);
				for($i = 0; $i < $xLoad->childNodes->length; $i++) {
					$cur = $xLoad->childNodes->item($i);
					if($cur->nodeType == XML_PI_NODE) {
						$root->parentNode->insertBefore($root->ownerDocument->importNode($cur, true), $root);
					}
				}
			}
			else {
				$nofile = $n->ownerDocument->createElementNS("http://www.agencexml.com/txs", "txs:nofile");
				$n->parentNode->replaceChild($nofile, $n);
			}
			break;
		case "htm":
		case "html":
			if(file_exists($n->getAttribute("filename"))) {
				$xLoad = new DOMDocument();
				$xLoad->loadXML(html2xml(file_get_contents($n->getAttribute("filename"))));
				$n->parentNode->replaceChild($n->ownerDocument->importNode($xLoad->documentElement, true), $n);
			}
			else {
				$nofile = $n->ownerDocument->createElementNS("http://www.agencexml.com/txs", "txs:nofile");
				$n->parentNode->replaceChild($nofile, $n);
			}
			break;
		case "text":
		case "txt":
		case "js":
		case "css":
			if(file_exists($n->getAttribute("filename"))) {
				$n->parentNode->replaceChild($n->ownerDocument->createTextNode(file_get_contents($n->getAttribute("filename"))), $n);
			}
			else {
				$nofile = $n->ownerDocument->createElementNS("http://www.agencexml.com/txs", "txs:nofile");
				$n->parentNode->replaceChild($nofile, $n);
			}
			break;
	}
}
function Save($n) {
	switch($n->getAttribute("format")) {
		case "xml":
			$xRes = new DOMDocument();
			for($i = 0; $i < $n->childNodes->length; $i++) {
				if($n->childNodes->item($i)->nodeName == "txs:comment" && $n->childNodes->item($i)->namespaceURI == "http://www.agencexml.com/txs") {
					$xRes->appendChild($xRes->createComment($n->childNodes->item($i)->nodeValue));
				} else {
					$xRes->appendChild($xRes->importNode($n->childNodes->item($i), true));
				}
			}
			$xRes->encoding = "ISO-8859-1";
			$txsnodes = $xRes->getElementsByTagNameNS("http://www.agencexml.com/txs", "stylesheet");
			foreach($txsnodes as $txsnode) {
				$replnode = $xRes->createElementNS("http://www.w3.org/1999/XSL/Transform", "xsl:stylesheet");
				for($j = 0; $j < $txsnode->attributes->length; $j++) {
					$replnode->setAttributeNodeNS($txsnode->attributes->item($j));
				}
				for($j = 0; $j < $txsnode->childNodes->length; $j++) {
					$replnode->appendChild($xRes->importNode($txsnode->childNodes->item($j), true));
				}
				$txsnode->parentNode->replaceChild($replnode, $txsnode);
			}
			$txsnodes = $xRes->getElementsByTagNameNS("http://www.agencexml.com/txs", "output");
			foreach($txsnodes as $txsnode) {
				$replnode = $xRes->createElementNS("http://www.w3.org/1999/XSL/Transform", "xsl:output");
				for($j = 0; $j < $txsnode->attributes->length; $j++) {
					$replnode->setAttributeNodeNS($txsnode->attributes->item($j));
				}
				for($j = 0; $j < $txsnode->childNodes->length; $j++) {
					$replnode->appendChild($xRes->importNode($txsnode->childNodes->item($j), true));
				}
				$txsnode->parentNode->replaceChild($replnode, $txsnode);
			}
			$txsnodes = $xRes->getElementsByTagNameNS("http://www.agencexml.com/txs", "comment");
			foreach($txsnodes as $txsnode) {
				$txsnode->parentNode->replaceChild($xRes->createComment($txsnode->nodeValue), $txsnode);
			}
			//$xRes->save($n->getAttribute("filename"));
			$fp = fopen($n->getAttribute("filename"), "wb");
			fwrite($fp, $xRes->saveXML());
			fclose($fp);
			$empty = $n->ownerDocument->createTextNode("");
			$n->parentNode->replaceChild($empty, $n);
			break;
		case "text":
			$fp = fopen($n->getAttribute("filename"), "wb");
			fwrite($fp, $n->nodeValue);
			fclose($fp);
			$empty = $n->ownerDocument->createTextNode("");
			$n->parentNode->replaceChild($empty, $n);
			break;
/*
		case "email":
			$smtpserver = $n->getAttribute("smtpserver");
			$smtplogin = $n->getAttribute("login");
			$smtppassword = $n->getAttribute("password");
			for($i = 0; $i < $n->childNodes->length; $i++) {
				$email = $n->childNodes->item($i);
				$mail = new PHPMailer();
				$mail->IsSMTP();
				$mail->SMTPAuth = $smtplogin != "";
				$mail->Host = $smtpserver;
				$mail->Username = $smtplogin;
				$mail->Password = $smtppassword;
				$mail->From = $email->getAttribute("from");
				$mail->Sender = $email->getAttribute("from");
				$mail->FromName = $email->getAttribute("displayfrom");
				if($email->getAttribute("mailer") != "") {
					$mail->Version = $email->getAttribute("mailer");
				}
				if($email->getAttribute("readreceipt") == "true") {
					$mail->ConfirmReadingTo = $email->getAttribute("from");
				}
				$mail->AddAddress($email->getAttribute("to"),$email->getAttribute("displayto"));
				switch($email->getAttribute("priority")) {
					case "high":
						$mail->Priority = 1;
						break;
					case "low":
						$mail->Priority = 5;
						break;
					default:
						$mail->Priority = 3;
						break;
				}
				$mail->Subject = $email->getAttribute("subject");
				for($j = 0; $j < $email->childNodes->length; $j++) {
					$emailview = $email->childNodes->item($j);
					if($emailview->nodeName == "txs:emailview") {
						$body = "body";
						for($k = 0; $k < $emailview->childNodes->length; $k++) {
							$emailviewchild = $emailview->childNodes->item($k);
							switch($emailviewchild->nodeName) {
								case "txs:emailbody":
									switch($emailview->getAttribute("format")) {
										case "text/html":
											for($l = 0;$l < $emailviewchild->childNodes->length; $l++) {
												$cur = $emailviewchild->childNodes->item($l);
												if($cur->nodeType == XML_ELEMENT_NODE) {
													$mail->IsHTML(true);
													$xBody = new DOMDocument();
													$xBody->loadXML("<dummy/>");
													$xBody->documentElement->parentNode->replaceChild($xBody->importNode($cur, true), $xBody->documentElement);
													$mail->Body = str_replace("<?xml version=\"1.0\"?>", "", str_replace("<default:", "<", str_replace("</default:", "</", str_replace(" xmlns:default=", " xmlns=", $xBody->saveXML()))));
													break;
												}
											}
											break;
										case "text/plain":
											$mail->AltBody = $emailviewchild->textContent;
											break;
									}
									break;
									case "txs:emailresource":
										$mail->AddEmbeddedImage($emailviewchild->getAttribute("filename"),$emailviewchild->getAttribute("id"),"","base64",$emailviewchild->getAttribute("type"));
										break;
							}
						}
					}
				}
				$mail->Send();
			}
			$empty = $n->ownerDocument->createTextNode("");
			$n->parentNode->replaceChild($empty, $n);
			break;
*/
	}
}
function Transform($n) {
	$xml = new DOMDocument();
	$xml->loadXML("<dummy/>");
	$xpath = new DOMXPath($n->ownerDocument);
	$xpath->registerNameSpace("txs", "http://www.agencexml.com/txs");
	$xpath->registerNameSpace("xsl", "http://www.w3.org/1999/XSL/Transform");
	if($xpath->query("txs:input", $n)->length == 0) {
		$xml->documentElement->parentNode->replaceChild($xml->importNode($n->firstChild, true), $xml->documentElement);
	}
	else {
		$root = $xml->importNode($xpath->query("txs:input/*", $n)->item(0), true);
		$xml->documentElement->parentNode->replaceChild($root, $xml->documentElement);
		$rootNodes = $xpath->query("txs:input/node()", $n);
		for($i = 0; $i < $rootNodes->length; $i++) {
			$cur = $rootNodes->item($i);
			if($cur->nodeType == XML_PI_NODE) {
				$root->parentNode->insertBefore($root->ownerDocument->importNode($cur, true), $root);
			}
		}
	}
	$xsl = new DOMDocument();
	$stsh = $n->getAttribute("stylesheet");
	if($stsh != "") {
		if(substr($stsh, 0, 1) == "#") {
			$stns = $xpath->query("//xsl:stylesheet[@txs:name='".substr($stsh, 1)."']");
			$xsl->loadXML("<dummy/>");
			$xsl->documentElement->parentNode->replaceChild($xsl->importNode($stns->item(0), true), $xsl->documentElement);
		}
		else {
		$xsl->load($stsh);
		}
	}
	else {
		$xsl->loadXML("<dummy/>");
		$xsl->documentElement->parentNode->replaceChild($xsl->importNode($xpath->query("txs:stylesheet/*", $n)->item(0), true), $xsl->documentElement);
	}
	$xslp = new xsltProcessor();
	$xslp->importStyleSheet($xsl);
	$prms = $xpath->query("txs:with-param", $n);
	foreach($prms as $prm) {
		$xslp->setParameter("", $prm->getAttribute("name"), $prm->getAttribute("value"));
	}
	$result = $xslp->transformToXml($xml);
	if(substr($result, 0, 1) == "<") {
		$xRes = new DOMDocument();
		$xRes->loadXML($result);
		$n->parentNode->replaceChild($n->ownerDocument->importNode($xRes->documentElement, true), $n);
	}
	else {
		$n->parentNode->replaceChild($n->ownerDocument->createCDATASection($result), $n);
	}
}
function Httprequest($n) {
	$url = $n->getAttribute("url");
	$curl = curl_init($url);
	curl_setopt($curl, CURLOPT_RETURNTRANSFER, 1);
	curl_setopt($curl, CURLOPT_TIMEOUT, 2);
	$content = curl_exec($curl);
	curl_close($curl);
	if($content == FALSE) {
		$content = "<html/>";
	}
	switch($n->getAttribute("content")) {
		case "xmlstring":
			break;
		case "textstring":
			break;
		case "html":
			$xRes = new DOMDocument();
			$xRes->loadXML(html2xml($content));
			$n->parentNode->replaceChild($n->ownerDocument->importNode($xRes->documentElement, true), $n);
			break;
	}
}
function Call($n) {
	$xPar = new DOMDocument();
	$xPar->loadXML("<txs:args xmlns:txs=\"http://www.agencexml.com/txs\" filename=\"".$n->getAttribute("filename")."\"/>");
	for($i = 0; $i < $n->childNodes->length; $i++) {
		$xPar->documentElement->appendChild($xPar->importNode($n->childNodes->item($i), true));
	}
	$res = Execute($n->getAttribute("filename"), $xPar->saveXML());
	$xRes = new DOMDocument();
	$xRes->loadXML($res);
	$n->parentNode->replaceChild($n->ownerDocument->importNode($xRes->documentElement, true), $n);
}
function FileExists($n) {
	$n->parentNode->replaceChild($n->ownerDocument->createTextNode(is_file($n->getAttribute("filename"))?"true":"false"), $n);
}
function FolderExists($n) {
	$n->parentNode->replaceChild($n->ownerDocument->createTextNode(is_dir($n->getAttribute("filename"))?"true":"false"), $n);
}
function FromModel($n, $n0) {
	$model = new DOMDocument();
	$modelname = $n->getAttribute("name");
	if(substr($modelname, 0, 1) == "#") {
		$xpathn0 = new DOMXPath($n0->ownerDocument);
		$xpathn0->registerNameSpace("txs", "http://www.agencexml.com/txs");
		$modelnode = $xpathn0->query("ancestor::txs:collection/txs:model[@name='".substr($modelname, 1)."']", $n0)->item(0);
		$model->loadXML("<dummy/>");
		$model->documentElement->parentNode->replaceChild($model->importNode($modelnode, true), $model->documentElement);
	}
	else {
		$model->load($modelname);
	}
	$xpathm = new DOMXPath($model);
	$xpathm->registerNameSpace("txs", "http://www.agencexml.com/txs");
	$xpathm->registerNameSpace("xhtml", "http://www.w3.org/1999/xhtml");
	$fmodels = $xpathm->query("//txs:from-model");
	while($fmodels->length != 0) {
		FromModel($fmodels->item(0), $n0);
		$fmodels = $xpathm->query("//txs:from-model");
	}
	$xpathn = new DOMXPath($n->ownerDocument);
	$xpathn->registerNameSpace("txs", "http://www.agencexml.com/txs");
	$replaces = $xpathn->query("txs:replace", $n);
	foreach($replaces as $replace) {
		$replacen = $xpathn->query("*", $replace)->item(0);
		$repl = $xpathm->query($replace->getAttribute("select"))->item(0);
		$repl->parentNode->replaceChild($model->importNode($replacen, true), $repl);
	}
	$appends = $xpathn->query("txs:append", $n);
	foreach($appends as $append) {
		$appendn = $xpathn->query("*", $append)->item(0);
		$appd = $xpathm->query($append->getAttribute("select"))->item(0);
		$appd->appendChild($model->importNode($appendn, true));
	}
	$n->parentNode->replaceChild($n->ownerDocument->importNode($xpathm->query("/txs:model/*")->item(0), true), $n);
}
function Script($n) {
	if($n->hasChildNodes()) {
		$i = 0;
		while($i < $n->childNodes->length) {
			$cur = $n->childNodes->item($i);
			if($cur->nodeType == XML_DOCUMENT_NODE || $cur->nodeType == XML_ELEMENT_NODE) {
				$curname = $cur->nodeName;
				if(substr($curname, 0, 4) == "txs:") {
					$xpath = new DOMXPath($n->ownerDocument);
					$xpath->registerNameSpace("txs", "http://www.agencexml.com/txs");
					$xpath->registerNameSpace("xsl", "http://www.w3.org/1999/XSL/Transform");
					if($cur->getAttribute("node") != '') {
						if($xpath->query($cur->getAttribute("node"), $cur)->length == 0 ) {
							$curname = "dummy";
						}
					}
					else {
						if($xpath->query("ancestor::xsl:stylesheet", $cur)->length != 0 || $xpath->query("ancestor::txs:model", $cur)->length != 0) {
							$curname = "dummy";
						}
					}
				}
				switch($curname) {
					case "txs:load":
						Load($cur);
						break;
					case "txs:save":
						Script($cur);
						Save($cur);
						break;
					case "txs:transform":
						Script($cur);
						Transform($cur);
						continue(2);
					case "txs:httprequest":
						Httprequest($cur);
						break;
					case "txs:call":
						Call($cur);
						continue(2);
					case "txs:fileexists":
						FileExists($cur);
						break;
					case "txs:folderexists":
						FolderExists($cur);
						break;
					case "txs:from-model":
						Script($cur);
						FromModel($cur, $cur);
						break;
					default:
						Script($cur);
				}
			}
			$i++;
		}
	}
}
function Execute($filename, $xparams, $querystring) {
	$xDoc = new DOMDocument();
	$xDoc->load($filename);
	$result = $xDoc->createElementNS("http://www.agencexml.com/txs", "txs:result");
	$transf = $xDoc->createElementNS("http://www.agencexml.com/txs", "txs:transform");
	$transf->setAttribute("stylesheet", "#main");
	$param = $xDoc->createElementNS("http://www.agencexml.com/txs", "txs:with-param");
	$param->setAttribute("name", "now");
	$m = microtime();
	$m = substr($m, 0, strpos($m, " "));
	$m = substr($m, strpos($m, "."));
	$today = getdate();
	$param->setAttribute("value", sprintf("%04d-%02d-%02dT%02d:%02d:%02d%sZ",$today["year"],$today["mon"],$today["mday"],$today["hours"],$today["minutes"],$today["seconds"],$m));
	$param2 = $xDoc->createElementNS("http://www.agencexml.com/txs", "txs:with-param");
	$param2->setAttribute("name", "querystring");
	$param2->setAttribute("value", $querystring);
	$input = $xDoc->createElementNS("http://www.agencexml.com/txs", "txs:input");
	$xPar = new DOMDocument();
	$xPar->loadXML($xparams);
	$input->appendChild($xDoc->importNode($xPar->documentElement,true));
	$transf->appendChild($param);
	$transf->appendChild($param2);
	$transf->appendChild($input);
	$result->appendChild($transf);
	$xDoc->documentElement->appendChild($result);
	Script($result);
	$xRes = new DOMDocument();
	$xRes->loadXML("<dummy/>");
	$xRes->documentElement->parentNode->replaceChild($xRes->importNode($result->firstChild, true), $xRes->documentElement);
	return $xRes->saveXML();
}
function replaceHtmlComment($capture) {
	$escs = preg_replace('/--+/sU', "- ", $capture[2]);
	return "\n<!--\n".$escs."\n-->\n";
}
function replaceJsComment($capture) {
	$escs = preg_replace('/\*\//sU', " * /", $capture[2]);
	return "\n/*\n".$escs."\n*/\n";
}
if(array_key_exists("exec", $_GET) && $_GET["exec"] != "") {
	$execfile = $_GET["exec"];
} else {
	$execfile = "echo.txs";
}
$querystring = "";
$reqparams = file_get_contents("php://input");
if(substr($reqparams,0,9) == "postdata=") {
	$reqparams = urldecode(substr($reqparams,strpos($reqparams,"=")+1));
}
if($reqparams == "") {
	$reqparams = "<req:params xmlns:req=\"http://www.agencexml.com/requests\">";
	foreach($_REQUEST as $param => $value) {
	  $param = str_replace("amp;", "", $param);
		if( preg_match("/^[a-z][0-9a-z]*$/",$param) && strpos(@$value, "<txs:") == FALSE ) {
			$reqparams .= "<req:".$param.">".@$value."</req:".$param.">";
		}
	}
	$reqparams .= "</req:params>";
} else {
	foreach($_GET as $param => $value) {
	  $param = str_replace("amp;", "", $param);
	  $querystring .= $param."=".@$value."&";
	}
	if( $querystring != "" ) {
		$querystring = substr($querystring, 0, strlen($querystring)-1);
	}
}
$response = Execute($execfile, $reqparams, $querystring);
$s = substr($response, strpos($response, "<txs:return ")+12);
$ns = " ".substr($s, 0, strpos($s, " contenttype=\""));
$s = substr($s, strpos($s, " contenttype=\"")+14);
$ctype = substr($s, 0, strpos($s, "\""));
$content = substr($s, strpos($s, ">")+1);
$s = substr($s, strpos($s, "\"")+1);
$attfn = "";
if( strpos($s, "filename=\"") ) {
	$s = substr($s, strpos($s, "filename=\"")+10);
	$attfn = substr($s, 0, strpos($s, "\""));
	$s = substr($s, strpos($s, "\"")+1);
			header('Content-Disposition: attachment; filename="'.$attfn.'"');
}
$s = substr($s, strpos($s, "\"")+1);
$format = substr($s, 0, strpos($s, "\""));
$content = substr($content, 0, strpos($content, "</txs:return>"));
$content = preg_replace("/<txs:stylesheet(.*)>/sU", "<xsl:stylesheet$1>", $content);
$content = preg_replace("/<\/txs:stylesheet>/sU", "</xsl:stylesheet>", $content);
$content = preg_replace("/<txs:output(.*)\/>/U", "<xsl:output$1/>", $content);
//echo "<pre>".$content."</pre>";
$content = preg_replace("/<txs:processing-instruction\s+name=\"(.*)\">(.*)<\/txs:processing-instruction>/U", "<?\$1 \$2?>\n", $content);
switch($ctype){
	case "text/javascript" :
	case "text/css" :
		$content = preg_replace_callback('/<txs:comment( [^>]*)?>(.*)<\/txs:comment>/sU', 'replaceJsComment', $content);
		break;
	case "application/xml" :
	case "application/xhtml+xml" :
	case "text/html" :
		$content = preg_replace_callback('/<txs:comment( [^>]*)?>(.*)<\/txs:comment>/sU', 'replaceHtmlComment', $content);
		break;
}
if($format != "text" && $ns != " ") {
	preg_match_all("/\sxmlns([^=]*)=\"([^\"]*)\"/", $ns, $nsres, PREG_PATTERN_ORDER);
	preg_match("/<([^\?!].*)[\s\/>]/U", $content, $docelt);
	$doceltag = substr($content, strpos($content,$docelt[0]), strpos(substr($content, strpos($content,$docelt[0])),">"));
	preg_match_all("/\sxmlns([^=]*)=\"([^\"]*)\"/", $doceltag, $nsres2, PREG_PATTERN_ORDER);
	if(count($nsres2[1]) == 0) {
		$nstab = array_combine($nsres[1], $nsres[2]);
	} else {
		$nstab = array_merge(array_combine($nsres[1], $nsres[2]), array_combine($nsres2[1], $nsres2[2]));
	}
	$ns = "";
	foreach($nstab as $pf => $uri) {
		$ns .= " xmlns".$pf."=\"".$uri."\"";
	}
	$doceltattr = preg_replace("/\sxmlns[^=]*=\"[^\"]*\"/", "", substr($doceltag, strlen($docelt[1])+1));
	$content = substr($content,0,strpos($content,$doceltag))."<".$docelt[1].$ns.$doceltattr.substr($content,strpos($content,$doceltag)+strlen($doceltag));
}
if(strpos($content,"<default:") !== FALSE) {
	preg_match("/ xmlns:default=\"([^\"]*)\"/U", $content, $defns);
	preg_match("/<default:(.*)[\s\/>]/U", $content, $defelt);
	$defeltag = substr($content, strpos($content,$defelt[0]), strpos(substr($content, strpos($content,$defelt[0])),">"));
	if(strpos($defeltag," xmlns=") === FALSE) {
		$content = substr($content, 0, strpos($content, "<default:"))."<".$defelt[1]." xmlns=\"".$defns[1]."\"".substr($content, strpos($content, "<default:")+9+strlen($defelt[1]));
	}
	$content = str_replace("<default:", "<", str_replace("</default:", "</", $content));
}
switch($format) {
	case "xhtml":
		if(strpos($_SERVER["HTTP_ACCEPT"],"application/xhtml+xml") === FALSE && strpos($_SERVER["HTTP_USER_AGENT"],"W3C_Validator") === FALSE) {
			header("Content-Type: text/html");
			echo "<?xml version=\"1.0\"?>\n<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">".$content;
		} else {
			header("Content-Type: application/xhtml+xml");
			echo "<?xml version=\"1.0\"?>\n<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">".$content;
		}
		break;
	case "text":
		if(substr($content, 0, 9) == "<![CDATA[") {
			$content = substr($content, 9, strlen($content)-12);
		} else {
			$content = html_entity_decode($content);
		}
	default:
		header("Content-Type: ".$ctype);
		if( $attfn != "" ) {
			//header('Content-Disposition: attachment; filename="'.$attfn.'"');
		}
		echo $content;
		break;
}
?>