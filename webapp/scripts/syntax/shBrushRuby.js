/*
 * JsMin
 * Javascript Compressor
 * http://www.crockford.com/
 * http://www.smallsharptools.com/
*/

dp.sh.Brushes.Ruby=function()
{var keywords='alias and BEGIN begin break case class def define_method defined do each else elsif '+'END end ensure false for if in module new next nil not or raise redo rescue retry return '+'self super then throw true undef unless until when while yield';var builtins='Array Bignum Binding Class Continuation Dir Exception FalseClass File::Stat File Fixnum Fload '+'Hash Integer IO MatchData Method Module NilClass Numeric Object Proc Range Regexp String Struct::TMS Symbol '+'ThreadGroup Thread Time TrueClass'
this.regexList=[{regex:dp.sh.RegexLib.SingleLinePerlComments,css:'comment'},{regex:dp.sh.RegexLib.DoubleQuotedString,css:'string'},{regex:dp.sh.RegexLib.SingleQuotedString,css:'string'},{regex:new RegExp(':[a-z][A-Za-z0-9_]*','g'),css:'symbol'},{regex:new RegExp('(\\$|@@|@)\\w+','g'),css:'variable'},{regex:new RegExp(this.GetKeywords(keywords),'gm'),css:'keyword'},{regex:new RegExp(this.GetKeywords(builtins),'gm'),css:'builtin'}];this.CssClass='dp-rb';this.Style='.dp-rb .symbol { color: #a70; }'+'.dp-rb .variable { color: #a70; font-weight: bold; }';}
dp.sh.Brushes.Ruby.prototype=new dp.sh.Highlighter();dp.sh.Brushes.Ruby.Aliases=['ruby','rails','ror'];
