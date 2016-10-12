#!/bin/bash
# -----------------------------------------------------------------------------
# install YAJSW daemon script
#

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
EXECUTABLE=installDaemonNoPriv.sh

# set java and conf file
source "$PRGDIR"/setenv.sh
export PRGDIR

# Check that target executable exists
if [ ! -x "$PRGDIR"/"$EXECUTABLE" ]; then
  echo "Cannot find $PRGDIR/$EXECUTABLE"
  echo "This file is needed to run this program"
  exit 1
fi

# systemd stuff 20160901/ljo
function use_systemd {
    echo "Using systemd with template non-privileged";
    echo -e "\nPlease notice the environment variables JAVA_HOME, EXIST_HOME, and USER are used, so set these variables and rerun if they are not set to what you want.\n\n";
    # envsubst < $PRGDIR/../templates/systemd.vm
    echo -e "$(eval "echo -e \"`<$PRGDIR/../templates/systemd.vm`\"")";
    read -p "Continue (Y/n)? " eval_response;
    case $eval_response in
	[Yy][Ee][Ss]|[YyJj])
	    if [ ! -d "$HOME/.config/systemd/user" ]; then
		echo "Creating directory \"$HOME/.config/systemd/user\"";
		mkdir -p "$HOME/.config/systemd/user";
	    fi
	    if [ ! -d "$HOME/.local/share/systemd/user" ]; then
		echo "Creating directory \"$HOME/.local/share/systemd/user\"";
		mkdir -p "$HOME/.local/share/systemd/user";
	    fi
	    echo "Installing template $PRGDIR/../templates/systemd.vm as non-privileged service $HOME/.local/share/systemd/user/eXist-db.service";
	    echo -e "$(eval "echo -e \"`<$PRGDIR/../templates/systemd.vm`\"")" > "$HOME/.local/share/systemd/user/eXist-db.service";
	    echo -e "\nEnabling the service (systemctl --user enable eXist-db)...\n";
	    systemctl --user enable eXist-db;
	    echo -e "\nStart it with: \n===============================";
	    echo -e "systemctl --user start eXist-db\n===============================\n";
	    ;;
	*)
	    exit;
	    ;;
    esac	
}

w_wrapper_pid_file="$EXIST_HOME/tools/yajsw/work/wrapper.eXist-db.pid";
if [ ! -e "$w_wrapper_pid_file" ]; then
    mkdir "$w_wrapper_pid_file";
fi
export w_wrapper_pid_file;

if [ `pgrep -P 1 systemd | head -n 1` ]; then
    echo -e "Detected systemd running.\n";
    read -p "Do you want to use it (Y=Run service with non-privileged systemd/N=continue with privileged systemV-init)? " systemd_response;
    case $systemd_response in
	[Yy][Ee][Ss]|[YyJj])
	    use_systemd;
	    ;;
	[Nn][Oo]|[Nn])
	    sudo "$PRGDIR"/"$EXECUTABLE"
	    ;;
    esac
else
    sudo "$PRGDIR"/"$EXECUTABLE"
fi
