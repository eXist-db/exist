#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# remove YAJSW daemon script
#
# -----------------------------------------------------------------------------

set -e

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done
 
PRGDIR=`dirname "$PRG"`
EXECUTABLE=uninstallDaemonNoPriv.sh

# set java and conf file
source "$PRGDIR"/setenv.sh
export PRGDIR

# Check that target executable exists
if [ ! -x "$PRGDIR"/"$EXECUTABLE" ]; then
  echo "Cannot find $PRGDIR/$EXECUTABLE"
  echo "This file is needed to run this program"
  exit 1
fi

if [ `pgrep -P 1 systemd | head -n 1` ]; then
    echo -e "Detected systemd running.\n";
    read -p "Did you install the service wrapper for non-privileged systemd (Y=uninstall non-privileged systemd/N=continue with uninstalling privileged systemV-init)? " systemd_response;
    case $systemd_response in
	[Yy][Ee][Ss]|[YyJj])
	    echo "Stopping service ...";
	    sudo systemctl stop $w_name;
	    echo "Disabling service ...";
	    sudo systemctl disable $w_name;

            systemd_sys_dir="/etc/systemd/system"
            systemd_service="${systemd_sys_dir}/${w_name}.service"
	    if [ -e "$systemd_service" ]; then
			sudo rm -f "$systemd_service";
	    fi
        sudo systemctl daemon-reload

	    ;;
	[Nn][Oo]|[Nn])
	    sudo "$PRGDIR"/"$EXECUTABLE"
	    ;;
    esac
else
    sudo "$PRGDIR"/"$EXECUTABLE"
fi

