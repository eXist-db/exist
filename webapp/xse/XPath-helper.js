// Copyright J.M. Vanel 2003 - under GNU public licence
// jmvanel@free.fr
// Worldwide Botanical Knowledge Base
// http://wwbota.free.fr/
// $Header$

var inputID = "q";
function addCriterium(target) {
  var input = document.getElementById(inputID);
  var criterium = target.innerHTML;
  input.value += " " + criterium + ": ";
}

function addCriteriumPre(target) {
  var input = document.getElementById(inputID);
  var criterium = target.innerHTML;
  // alert( target );
  input.value += criterium;
}


function clearSearchField(){
  var input = document.getElementById(inputID);
  input.value = "";
}
