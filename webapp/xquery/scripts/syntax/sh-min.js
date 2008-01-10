var dp={sh:{Toolbar:{},Utils:{},RegexLib:{},Brushes:{},Strings:{AboutDialog:"<html><head><title>About...</title></head><body class=\"dp-about\"><table cellspacing=\"0\"><tr><td class=\"copy\"><p class=\"title\">dp.SyntaxHighlighter</div><div class=\"para\">Version: {V}</p><p><a href=\"http://www.dreamprojections.com/syntaxhighlighter/?ref=about\" target=\"_blank\">http://www.dreamprojections.com/syntaxhighlighter</a></p>&copy;2004-2007 Alex Gorbatchev.</td></tr><tr><td class=\"footer\"><input type=\"button\" class=\"close\" value=\"OK\" onClick=\"window.close()\"/></td></tr></table></body></html>"},ClipboardSwf:null,Version:"1.5.1"}};
dp.SyntaxHighlighter=dp.sh;
dp.sh.Toolbar.Commands={ExpandSource:{label:"+ expand source",check:function(_1){
return _1.collapse;
},func:function(_2,_3){
_2.parentNode.removeChild(_2);
_3.div.className=_3.div.className.replace("collapsed","");
}},ViewSource:{label:"view plain",func:function(_4,_5){
var _6=dp.sh.Utils.FixForBlogger(_5.originalCode).replace(/</g,"&lt;");
var _7=window.open("","_blank","width=750, height=400, location=0, resizable=1, menubar=0, scrollbars=0");
_7.document.write("<textarea style=\"width:99%;height:99%\">"+_6+"</textarea>");
_7.document.close();
}},CopyToClipboard:{label:"copy to clipboard",check:function(){
return window.clipboardData!=null||dp.sh.ClipboardSwf!=null;
},func:function(_8,_9){
var _a=dp.sh.Utils.FixForBlogger(_9.originalCode).replace(/&lt;/g,"<").replace(/&gt;/g,">").replace(/&amp;/g,"&");
if(window.clipboardData){
window.clipboardData.setData("text",_a);
}else{
if(dp.sh.ClipboardSwf!=null){
var _b=_9.flashCopier;
if(_b==null){
_b=document.createElement("div");
_9.flashCopier=_b;
_9.div.appendChild(_b);
}
_b.innerHTML="<embed src=\""+dp.sh.ClipboardSwf+"\" FlashVars=\"clipboard="+encodeURIComponent(_a)+"\" width=\"0\" height=\"0\" type=\"application/x-shockwave-flash\"></embed>";
}
}
alert("The code is in your clipboard now");
}},PrintSource:{label:"print",func:function(_c,_d){
var _e=document.createElement("IFRAME");
var _f=null;
_e.style.cssText="position:absolute;width:0px;height:0px;left:-500px;top:-500px;";
document.body.appendChild(_e);
_f=_e.contentWindow.document;
dp.sh.Utils.CopyStyles(_f,window.document);
_f.write("<div class=\""+_d.div.className.replace("collapsed","")+" printing\">"+_d.div.innerHTML+"</div>");
_f.close();
_e.contentWindow.focus();
_e.contentWindow.print();
alert("Printing...");
document.body.removeChild(_e);
}},About:{label:"?",func:function(_10){
var wnd=window.open("","_blank","dialog,width=300,height=150,scrollbars=0");
var doc=wnd.document;
dp.sh.Utils.CopyStyles(doc,window.document);
doc.write(dp.sh.Strings.AboutDialog.replace("{V}",dp.sh.Version));
doc.close();
wnd.focus();
}}};
dp.sh.Toolbar.Create=function(_13){
var div=document.createElement("DIV");
div.className="tools";
for(var _15 in dp.sh.Toolbar.Commands){
var cmd=dp.sh.Toolbar.Commands[_15];
if(cmd.check!=null&&!cmd.check(_13)){
continue;
}
div.innerHTML+="<a href=\"#\" onclick=\"dp.sh.Toolbar.Command('"+_15+"',this);return false;\">"+cmd.label+"</a>";
}
return div;
};
dp.sh.Toolbar.Command=function(_17,_18){
var n=_18;
while(n!=null&&n.className.indexOf("dp-highlighter")==-1){
n=n.parentNode;
}
if(n!=null){
dp.sh.Toolbar.Commands[_17].func(_18,n.highlighter);
}
};
dp.sh.Utils.CopyStyles=function(_1a,_1b){
var _1c=_1b.getElementsByTagName("link");
for(var i=0;i<_1c.length;i++){
if(_1c[i].rel.toLowerCase()=="stylesheet"){
_1a.write("<link type=\"text/css\" rel=\"stylesheet\" href=\""+_1c[i].href+"\"></link>");
}
}
};
dp.sh.Utils.FixForBlogger=function(str){
return (dp.sh.isBloggerMode==true)?str.replace(/<br\s*\/?>|&lt;br\s*\/?&gt;/gi,"\n"):str;
};
dp.sh.RegexLib={MultiLineCComments:new RegExp("/\\*[\\s\\S]*?\\*/","gm"),SingleLineCComments:new RegExp("//.*$","gm"),SingleLinePerlComments:new RegExp("#.*$","gm"),DoubleQuotedString:new RegExp("\"(?:\\.|(\\\\\\\")|[^\\\"\"\\n])*\"","g"),SingleQuotedString:new RegExp("'(?:\\.|(\\\\\\')|[^\\''\\n])*'","g")};
dp.sh.Match=function(_1f,_20,css){
this.value=_1f;
this.index=_20;
this.length=_1f.length;
this.css=css;
};
dp.sh.Highlighter=function(){
this.noGutter=false;
this.addControls=true;
this.collapse=false;
this.tabsToSpaces=true;
this.wrapColumn=80;
this.showColumns=true;
};
dp.sh.Highlighter.SortCallback=function(m1,m2){
if(m1.index<m2.index){
return -1;
}else{
if(m1.index>m2.index){
return 1;
}else{
if(m1.length<m2.length){
return -1;
}else{
if(m1.length>m2.length){
return 1;
}
}
}
}
return 0;
};
dp.sh.Highlighter.prototype.CreateElement=function(_24){
var _25=document.createElement(_24);
_25.highlighter=this;
return _25;
};
dp.sh.Highlighter.prototype.GetMatches=function(_26,css){
var _28=0;
var _29=null;
while((_29=_26.exec(this.code))!=null){
this.matches[this.matches.length]=new dp.sh.Match(_29[0],_29.index,css);
}
};
dp.sh.Highlighter.prototype.AddBit=function(str,css){
if(str==null||str.length==0){
return;
}
var _2c=this.CreateElement("SPAN");
str=str.replace(/ /g,"&nbsp;");
str=str.replace(/</g,"&lt;");
str=str.replace(/\n/gm,"&nbsp;<br>");
if(css!=null){
if((/br/gi).test(str)){
var _2d=str.split("&nbsp;<br>");
for(var i=0;i<_2d.length;i++){
_2c=this.CreateElement("SPAN");
_2c.className=css;
_2c.innerHTML=_2d[i];
this.div.appendChild(_2c);
if(i+1<_2d.length){
this.div.appendChild(this.CreateElement("BR"));
}
}
}else{
_2c.className=css;
_2c.innerHTML=str;
this.div.appendChild(_2c);
}
}else{
_2c.innerHTML=str;
this.div.appendChild(_2c);
}
};
dp.sh.Highlighter.prototype.IsInside=function(_2f){
if(_2f==null||_2f.length==0){
return false;
}
for(var i=0;i<this.matches.length;i++){
var c=this.matches[i];
if(c==null){
continue;
}
if((_2f.index>c.index)&&(_2f.index<c.index+c.length)){
return true;
}
}
return false;
};
dp.sh.Highlighter.prototype.ProcessRegexList=function(){
for(var i=0;i<this.regexList.length;i++){
this.GetMatches(this.regexList[i].regex,this.regexList[i].css);
}
};
dp.sh.Highlighter.prototype.ProcessSmartTabs=function(_33){
var _34=_33.split("\n");
var _35="";
var _36=4;
var tab="\t";
function InsertSpaces(_38,pos,_3a){
var _3b=_38.substr(0,pos);
var _3c=_38.substr(pos+1,_38.length);
var _3d="";
for(var i=0;i<_3a;i++){
_3d+=" ";
}
return _3b+_3d+_3c;
}
function ProcessLine(_3f,_40){
if(_3f.indexOf(tab)==-1){
return _3f;
}
var pos=0;
while((pos=_3f.indexOf(tab))!=-1){
var _42=_40-pos%_40;
_3f=InsertSpaces(_3f,pos,_42);
}
return _3f;
}
for(var i=0;i<_34.length;i++){
_35+=ProcessLine(_34[i],_36)+"\n";
}
return _35;
};
dp.sh.Highlighter.prototype.SwitchToList=function(){
var _44=this.div.innerHTML.replace(/<(br)\/?>/gi,"\n");
var _45=_44.split("\n");
if(this.addControls==true){
this.bar.appendChild(dp.sh.Toolbar.Create(this));
}
if(this.showColumns){
var div=this.CreateElement("div");
var _47=this.CreateElement("div");
var _48=10;
var i=1;
while(i<=150){
if(i%_48==0){
div.innerHTML+=i;
i+=(i+"").length;
}else{
div.innerHTML+="&middot;";
i++;
}
}
_47.className="columns";
_47.appendChild(div);
this.bar.appendChild(_47);
}
for(var i=0,lineIndex=this.firstLine;i<_45.length-1;i++,lineIndex++){
var li=this.CreateElement("LI");
var _4c=this.CreateElement("SPAN");
li.className=(i%2==0)?"alt":"";
_4c.innerHTML=_45[i]+"&nbsp;";
li.appendChild(_4c);
this.ol.appendChild(li);
}
this.div.innerHTML="";
};
dp.sh.Highlighter.prototype.Highlight=function(_4d){
function Trim(str){
return str.replace(/^\s*(.*?)[\s\n]*$/g,"$1");
}
function Chop(str){
return str.replace(/\n*$/,"").replace(/^\n*/,"");
}
function Unindent(str){
var _51=dp.sh.Utils.FixForBlogger(str).split("\n");
var _52=new Array();
var _53=new RegExp("^\\s*","g");
var min=1000;
for(var i=0;i<_51.length&&min>0;i++){
if(Trim(_51[i]).length==0){
continue;
}
var _56=_53.exec(_51[i]);
if(_56!=null&&_56.length>0){
min=Math.min(_56[0].length,min);
}
}
if(min>0){
for(var i=0;i<_51.length;i++){
_51[i]=_51[i].substr(min);
}
}
return _51.join("\n");
}
function Copy(_58,_59,_5a){
return _58.substr(_59,_5a-_59);
}
var pos=0;
if(_4d==null){
_4d="";
}
this.originalCode=_4d;
this.code=Chop(Unindent(_4d));
this.div=this.CreateElement("DIV");
this.bar=this.CreateElement("DIV");
this.ol=this.CreateElement("OL");
this.matches=new Array();
this.div.className="dp-highlighter";
this.div.highlighter=this;
this.bar.className="bar";
this.ol.start=this.firstLine;
if(this.CssClass!=null){
this.ol.className=this.CssClass;
}
if(this.collapse){
this.div.className+=" collapsed";
}
if(this.noGutter){
this.div.className+=" nogutter";
}
if(this.tabsToSpaces==true){
this.code=this.ProcessSmartTabs(this.code);
}
this.ProcessRegexList();
if(this.matches.length==0){
this.AddBit(this.code,null);
this.SwitchToList();
this.div.appendChild(this.bar);
this.div.appendChild(this.ol);
return;
}
this.matches=this.matches.sort(dp.sh.Highlighter.SortCallback);
for(var i=0;i<this.matches.length;i++){
if(this.IsInside(this.matches[i])){
this.matches[i]=null;
}
}
for(var i=0;i<this.matches.length;i++){
var _5e=this.matches[i];
if(_5e==null||_5e.length==0){
continue;
}
this.AddBit(Copy(this.code,pos,_5e.index),null);
this.AddBit(_5e.value,_5e.css);
pos=_5e.index+_5e.length;
}
this.AddBit(this.code.substr(pos),null);
this.SwitchToList();
this.div.appendChild(this.bar);
this.div.appendChild(this.ol);
};
dp.sh.Highlighter.prototype.GetKeywords=function(str){
return "\\b"+str.replace(/ /g,"\\b|\\b")+"\\b";
};
dp.sh.BloggerMode=function(){
dp.sh.isBloggerMode=true;
};
dp.sh.HighlightAll=function(_60,_61,_62,_63,_64,_65){
function FindValue(){
var a=arguments;
for(var i=0;i<a.length;i++){
if(a[i]==null){
continue;
}
if(typeof (a[i])=="string"&&a[i]!=""){
return a[i]+"";
}
if(typeof (a[i])=="object"&&a[i].value!=""){
return a[i].value+"";
}
}
return null;
}
function IsOptionSet(_68,_69){
for(var i=0;i<_69.length;i++){
if(_69[i]==_68){
return true;
}
}
return false;
}
function GetOptionValue(_6b,_6c,_6d){
var _6e=new RegExp("^"+_6b+"\\[(\\w+)\\]$","gi");
var _6f=null;
for(var i=0;i<_6c.length;i++){
if((_6f=_6e.exec(_6c[i]))!=null){
return _6f[1];
}
}
return _6d;
}
function FindTagsByName(_71,_72,_73){
var _74=document.getElementsByTagName(_73);
for(var i=0;i<_74.length;i++){
if(_74[i].getAttribute("name")==_72){
_71.push(_74[i]);
}
}
}
var _76=[];
var _77=null;
var _78={};
var _79="innerHTML";
FindTagsByName(_76,_60,"pre");
FindTagsByName(_76,_60,"textarea");
if(_76.length==0){
return;
}
for(var _7a in dp.sh.Brushes){
var _7b=dp.sh.Brushes[_7a].Aliases;
if(_7b==null){
continue;
}
for(var i=0;i<_7b.length;i++){
_78[_7b[i]]=_7a;
}
}
for(var i=0;i<_76.length;i++){
var _7e=_76[i];
var _7f=FindValue(_7e.attributes["class"],_7e.className,_7e.attributes["language"],_7e.language);
var _80="";
if(_7f==null){
continue;
}
_7f=_7f.split(":");
_80=_7f[0].toLowerCase();
if(_78[_80]==null){
continue;
}
_77=new dp.sh.Brushes[_78[_80]]();
_7e.style.display="none";
_77.noGutter=(_61==null)?IsOptionSet("nogutter",_7f):!_61;
_77.addControls=(_62==null)?!IsOptionSet("nocontrols",_7f):_62;
_77.collapse=(_63==null)?IsOptionSet("collapse",_7f):_63;
_77.showColumns=(_65==null)?IsOptionSet("showcolumns",_7f):_65;
var _81=document.getElementsByTagName("head")[0];
if(_77.Style&&_81){
var _82=document.createElement("style");
_82.setAttribute("type","text/css");
if(_82.styleSheet){
_82.styleSheet.cssText=_77.Style;
}else{
var _83=document.createTextNode(_77.Style);
_82.appendChild(_83);
}
_81.appendChild(_82);
}
_77.firstLine=(_64==null)?parseInt(GetOptionValue("firstline",_7f,1)):_64;
_77.Highlight(_7e[_79]);
_77.source=_7e;
_7e.parentNode.insertBefore(_77.div,_7e);
}
};

dp.sh.Brushes.Xml=function(){
this.CssClass="dp-xml";
this.Style=".dp-xml .cdata { color: #ff1493; }"+".dp-xml .tag, .dp-xml .tag-name { color: #069; font-weight: bold; }"+".dp-xml .attribute { color: red; }"+".dp-xml .attribute-value { color: blue; }";
};
dp.sh.Brushes.Xml.prototype=new dp.sh.Highlighter();
dp.sh.Brushes.Xml.Aliases=["xml","xhtml","xslt","html","xhtml"];
dp.sh.Brushes.Xml.prototype.ProcessRegexList=function(){
function push(_1,_2){
_1[_1.length]=_2;
}
var _3=0;
var _4=null;
var _5=null;
this.GetMatches(new RegExp("(&lt;|<)\\!\\[[\\w\\s]*?\\[(.|\\s)*?\\]\\](&gt;|>)","gm"),"cdata");
this.GetMatches(new RegExp("(&lt;|<)!--\\s*.*?\\s*--(&gt;|>)","gm"),"comments");
_5=new RegExp("([:\\w-.]+)\\s*=\\s*(\".*?\"|'.*?'|\\w+)*|(\\w+)","gm");
while((_4=_5.exec(this.code))!=null){
if(_4[1]==null){
continue;
}
push(this.matches,new dp.sh.Match(_4[1],_4.index,"attribute"));
if(_4[2]!=undefined){
push(this.matches,new dp.sh.Match(_4[2],_4.index+_4[0].indexOf(_4[2]),"attribute-value"));
}
}
this.GetMatches(new RegExp("(&lt;|<)/*\\?*(?!\\!)|/*\\?*(&gt;|>)","gm"),"tag");
_5=new RegExp("(?:&lt;|<)/*\\?*\\s*([:\\w-.]+)","gm");
while((_4=_5.exec(this.code))!=null){
push(this.matches,new dp.sh.Match(_4[1],_4.index+_4[0].indexOf(_4[1]),"tag-name"));
}
};

dp.sh.Brushes.JScript=function(){
var _1="abstract boolean break byte case catch char class const continue debugger "+"default delete do double else enum export extends false final finally float "+"for function goto if implements import in instanceof int interface long native "+"new null package private protected public return short static super switch "+"synchronized this throw throws transient true try typeof var void volatile while with";
this.regexList=[{regex:dp.sh.RegexLib.SingleLineCComments,css:"comment"},{regex:dp.sh.RegexLib.MultiLineCComments,css:"comment"},{regex:dp.sh.RegexLib.DoubleQuotedString,css:"string"},{regex:dp.sh.RegexLib.SingleQuotedString,css:"string"},{regex:new RegExp("^\\s*#.*","gm"),css:"preprocessor"},{regex:new RegExp(this.GetKeywords(_1),"gm"),css:"keyword"}];
this.CssClass="dp-c";
};
dp.sh.Brushes.JScript.prototype=new dp.sh.Highlighter();
dp.sh.Brushes.JScript.Aliases=["js","jscript","javascript"];

dp.sh.Brushes.Java=function(){
var _1="abstract assert boolean break byte case catch char class const "+"continue default do double else enum extends "+"false final finally float for goto if implements import "+"instanceof int interface long native new null "+"package private protected public return "+"short static strictfp super switch synchronized this throw throws true "+"transient try void volatile while";
this.regexList=[{regex:dp.sh.RegexLib.SingleLineCComments,css:"comment"},{regex:dp.sh.RegexLib.MultiLineCComments,css:"comment"},{regex:dp.sh.RegexLib.DoubleQuotedString,css:"string"},{regex:dp.sh.RegexLib.SingleQuotedString,css:"string"},{regex:new RegExp("\\b([\\d]+(\\.[\\d]+)?|0x[a-f0-9]+)\\b","gi"),css:"number"},{regex:new RegExp("(?!\\@interface\\b)\\@[\\$\\w]+\\b","g"),css:"annotation"},{regex:new RegExp("\\@interface\\b","g"),css:"keyword"},{regex:new RegExp(this.GetKeywords(_1),"gm"),css:"keyword"}];
this.CssClass="dp-j";
this.Style=".dp-j .annotation { color: #646464; }"+".dp-j .number { color: #C00000; }";
};
dp.sh.Brushes.Java.prototype=new dp.sh.Highlighter();
dp.sh.Brushes.Java.Aliases=["java"];

dp.sh.Brushes.CSS=function(){
var _1="ascent azimuth background-attachment background-color background-image background-position "+"background-repeat background baseline bbox border-collapse border-color border-spacing border-style border-top "+"border-right border-bottom border-left border-top-color border-right-color border-bottom-color border-left-color "+"border-top-style border-right-style border-bottom-style border-left-style border-top-width border-right-width "+"border-bottom-width border-left-width border-width border cap-height caption-side centerline clear clip color "+"content counter-increment counter-reset cue-after cue-before cue cursor definition-src descent direction display "+"elevation empty-cells float font-size-adjust font-family font-size font-stretch font-style font-variant font-weight font "+"height letter-spacing line-height list-style-image list-style-position list-style-type list-style margin-top "+"margin-right margin-bottom margin-left margin marker-offset marks mathline max-height max-width min-height min-width orphans "+"outline-color outline-style outline-width outline overflow padding-top padding-right padding-bottom padding-left padding page "+"page-break-after page-break-before page-break-inside pause pause-after pause-before pitch pitch-range play-during position "+"quotes richness size slope src speak-header speak-numeral speak-punctuation speak speech-rate stemh stemv stress "+"table-layout text-align text-decoration text-indent text-shadow text-transform unicode-bidi unicode-range units-per-em "+"vertical-align visibility voice-family volume white-space widows width widths word-spacing x-height z-index";
var _2="above absolute all always aqua armenian attr aural auto avoid baseline behind below bidi-override black blink block blue bold bolder "+"both bottom braille capitalize caption center center-left center-right circle close-quote code collapse compact condensed "+"continuous counter counters crop cross crosshair cursive dashed decimal decimal-leading-zero default digits disc dotted double "+"embed embossed e-resize expanded extra-condensed extra-expanded fantasy far-left far-right fast faster fixed format fuchsia "+"gray green groove handheld hebrew help hidden hide high higher icon inline-table inline inset inside invert italic "+"justify landscape large larger left-side left leftwards level lighter lime line-through list-item local loud lower-alpha "+"lowercase lower-greek lower-latin lower-roman lower low ltr marker maroon medium message-box middle mix move narrower "+"navy ne-resize no-close-quote none no-open-quote no-repeat normal nowrap n-resize nw-resize oblique olive once open-quote outset "+"outside overline pointer portrait pre print projection purple red relative repeat repeat-x repeat-y rgb ridge right right-side "+"rightwards rtl run-in screen scroll semi-condensed semi-expanded separate se-resize show silent silver slower slow "+"small small-caps small-caption smaller soft solid speech spell-out square s-resize static status-bar sub super sw-resize "+"table-caption table-cell table-column table-column-group table-footer-group table-header-group table-row table-row-group teal "+"text-bottom text-top thick thin top transparent tty tv ultra-condensed ultra-expanded underline upper-alpha uppercase upper-latin "+"upper-roman url visible wait white wider w-resize x-fast x-high x-large x-loud x-low x-slow x-small x-soft xx-large xx-small yellow";
var _3="[mM]onospace [tT]ahoma [vV]erdana [aA]rial [hH]elvetica [sS]ans-serif [sS]erif";
this.regexList=[{regex:dp.sh.RegexLib.MultiLineCComments,css:"comment"},{regex:dp.sh.RegexLib.DoubleQuotedString,css:"string"},{regex:dp.sh.RegexLib.SingleQuotedString,css:"string"},{regex:new RegExp("\\#[a-zA-Z0-9]{3,6}","g"),css:"value"},{regex:new RegExp("(-?\\d+)(.\\d+)?(px|em|pt|:|%|)","g"),css:"value"},{regex:new RegExp("!important","g"),css:"important"},{regex:new RegExp(this.GetKeywordsCSS(_1),"gm"),css:"keyword"},{regex:new RegExp(this.GetValuesCSS(_2),"g"),css:"value"},{regex:new RegExp(this.GetValuesCSS(_3),"g"),css:"value"}];
this.CssClass="dp-css";
this.Style=".dp-css .value { color: black; }"+".dp-css .important { color: red; }";
};
dp.sh.Highlighter.prototype.GetKeywordsCSS=function(_4){
return "\\b([a-z_]|)"+_4.replace(/ /g,"(?=:)\\b|\\b([a-z_\\*]|\\*|)")+"(?=:)\\b";
};
dp.sh.Highlighter.prototype.GetValuesCSS=function(_5){
return "\\b"+_5.replace(/ /g,"(?!-)(?!:)\\b|\\b()")+":\\b";
};
dp.sh.Brushes.CSS.prototype=new dp.sh.Highlighter();
dp.sh.Brushes.CSS.Aliases=["css"];

dp.sh.Brushes.XQuery=function(){
this.CssClass="dp-xquery";
};
dp.sh.Brushes.XQuery.prototype=new dp.sh.Highlighter();
dp.sh.Brushes.XQuery.Aliases=["xquery"];
dp.sh.Brushes.XQuery.prototype.ProcessRegexList=function(){
var _1="element to div mod text or and child parent self attribute comment document document-node"+" collection ancestor descendant descendant-or-self ancestor-or-self preceding-sibling following-sibling following"+" preceding item empty version xquery variable namespace if then else for let default function external as union"+" intersect except order by some every is isnot module import at cast return instance of declare collation"+" boundary-space preserve strip ordering construction ordered unordered typeswitch encoding base-uri"+" update replace delete value insert with into rename option case validate schema"+"treat no-preserve inherit no-inherit";
function push(_2,_3){
_2[_2.length]=_3;
}
var _4=0;
var _5=null;
var _6=null;
this.GetMatches(new RegExp(this.GetKeywords(_1),"gm"),"keyword");
this.GetMatches(dp.sh.RegexLib.DoubleQuotedString,"string");
this.GetMatches(dp.sh.RegexLib.SingleQuotedString,"string");
this.GetMatches(new RegExp("\\$[\\w-_.]+","g"),"variable");
this.GetMatches(new RegExp("<\\!\\[[\\w\\s]*?\\[(.|\\s)*?\\]\\]>","gm"),"cdata");
this.GetMatches(new RegExp("<!--\\s*.*\\s*?-->","gm"),"comments");
this.GetMatches(new RegExp("\\(:\\s*.*\\s*?:\\)","gm"),"comments");
this.GetMatches(new RegExp("</*\\?*(?!\\!)|/*\\?*>","gm"),"tag");
_6=new RegExp("</*\\?*\\s*([\\w-.]+)","gm");
while((_5=_6.exec(this.code))!=null){
push(this.matches,new dp.sh.Match(_5[1],_5.index+_5[0].indexOf(_5[1]),"tag-name"));
}
};

