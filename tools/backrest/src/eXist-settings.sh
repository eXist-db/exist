# -*-Shell-script-*-
# Common eXist script functions and settings
# $Id:eXist-settings.sh 7231 2008-01-14 22:33:35Z wolfgang_m $

get_exist_home() {
	case "$1" in
		/*)
			p="$1"
		;;
		*)
			p="$PWD/$1"
		;;
	esac
		(cd $(/usr/bin/dirname "$p") ; /bin/pwd)
}

check_exist_home() {
    if [ -z "${EXIST_BACKREST_HOME}" ]; then
	EXIST_BACKREST_HOME=$(get_exist_home "$1");
    fi

    if [ ! -f "${EXIST_BACKREST_HOME}/lib/exist-backrest.jar" ]; then
	echo "Unable to find exist-backrest.jar. Please set EXIST_BACKREST_HOME to point to your installation directory.";
	exit 1;
    fi
}

set_locale_lang() {
    if [ -n "${LANG}" ]; then
	OLD_LANG="${LANG}";
    fi
# set LANG to UTF-8
    if [ $(locale -a | grep -i "UTF-8" | head -n 1) ] || \
       [ $(locale -a | grep -i "utf8" | head -n 1) ]; then
	if [ $(echo "${LANG}" |grep "\.") ]; then
	    LANG=$(echo "${LANG}" | cut -f1 -d'.')
	    LANG="${LANG}".UTF-8
	else
	    LANG="${LANG}".UTF-8
	fi
    else
    # UTF-8 char map is unfortunately not available but we set it anyway...
	LANG=en_US.UTF-8
    fi
    echo "Using locale: ${LANG}"
    export LANG
}

restore_locale_lang() {
    if [ -n "${OLD_LANG}" ]; then
	LANG="${OLD_LANG}";
	export LANG;
    fi
}

set_library_path() {
    if [ -n "$LD_LIBRARY_PATH" ]; then
	OLD_LIBRARY_PATH="${LD_LIBRARY_PATH}";
    fi
# add lib/core to LD_LIBRARY_PATH for readline support
    LD_LIBRARY_PATH="${LD_LIBRARY_PATH}:${EXIST_BACKREST_HOME}/lib";
    export LD_LIBRARY_PATH;
}

restore_library_path() {
    if [ -n "${OLD_LIBRARY_PATH}" ]; then
	LD_LIBRARY_PATH="${OLD_LIBRARY_PATH}";
	export LD_LIBRARY_PATH;
    fi
}

set_client_java_options() {
    if [ -z "${CLIENT_JAVA_OPTIONS}" ]; then
	CLIENT_JAVA_OPTIONS="-Xms128m -Xmx512m -Dfile.encoding=UTF-8";
    fi
    JAVA_OPTIONS="${CLIENT_JAVA_OPTIONS}";
}

set_java_options() {
    if [ -z "${JAVA_OPTIONS}" ]; then
	JAVA_OPTIONS="-Xms128m -Xmx512m -Dfile.encoding=UTF-8";
    fi
    JAVA_OPTIONS="${JAVA_OPTIONS}";
}
set_exist_options() {
    OPTIONS="-Dexist.home=$EXIST_BACKREST_HOME"
}

set_jetty_home() {
if [ -n "$JETTY_HOME" ]; then
	OPTIONS="-Djetty.home=$JETTY_HOME $OPTIONS"
fi
}
