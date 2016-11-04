#!/bin/bash
# -----------------------------------------------------------------------------
# install YAJSW daemon script
#

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

# Locate JAVA_HOME (if not set in env)
if [ -z "${JAVA_HOME}" ]; then
  echo -e "\nNo JAVA_HOME environment variable found!"
  echo "Attempting to determine JAVA_HOME (if this fails you must manually set it)..."
  java_bin=$(readlink -f `which java`)
  java_bin_dir=$(dirname "${java_bin}")
  JAVA_HOME=$(dirname "${java_bin_dir}")
  echo -e "Found JAVA_HOME=${JAVA_HOME}\n"
fi

# Set EXIST_HOME (if not set in env)
if [ -z "${EXIST_HOME}" ]; then
  echo -e "\nNo EXIST_HOME environment variable found!"
  echo "Attempting to derive EXIST_HOME (if this fails you must manually set it)..."
  exist_tools_dir=$(dirname "${wrapper_home}")
  EXIST_HOME=$(dirname "${exist_tools_dir}")
  echo -e "Derived EXIST_HOME=${EXIST_HOME}\n"
fi

function review_systemd_config {
    read -p "Would you like to review the systemd config (Y/n)? " eval_response;
    case $eval_response in
        [Yy])
            echo -e "$(eval "echo -e \"`<${wrapper_home}/templates/systemd.vm`\"")";

            read -p "Continue (Y/n)? " eval_response;
            case $eval_response in
                [Yy])
                    ;;
                *)
                    exit
                    ;;
            esac    
            ;;
        *)
            ;;
    esac
}

function install_systemd_config {
    if [ ! -d "$HOME/.config/systemd/user" ]; then
        echo "Creating directory \"$HOME/.config/systemd/user\"";
        mkdir -p "$HOME/.config/systemd/user";
    fi 
    if [ ! -d "$HOME/.local/share/systemd/user" ]; then
        echo "Creating directory \"$HOME/.local/share/systemd/user\"";
        mkdir -p "$HOME/.local/share/systemd/user";
    fi
    echo "Installing template ${wrapper_home}/templates/systemd.vm as non-privileged service $HOME/.local/share/systemd/user/eXist-db.service";
    echo -e "$(eval "echo -e \"`<${wrapper_home}/templates/systemd.vm`\"")" > "$HOME/.local/share/systemd/user/eXist-db.service";
    echo -e "\nEnabling the service (systemctl --user enable eXist-db) ...\n";
    systemctl --user enable eXist-db;
    echo -e "\nStart it with: \n===============================";
    echo -e "systemctl --user start eXist-db\n===============================\n";
}

# systemd stuff 20160901/ljo
function use_systemd {
    echo "Using systemd with template non-privileged";
    echo -e "\nPlease note that the environment variables JAVA_HOME, EXIST_HOME, and USER are used for the systemd service setup."
    echo -e "Please review them below and if they are not set correctly, please exit and set them in your environment before rerunning this script.\n\n";

    echo "JAVA_HOME=${JAVA_HOME}"
    echo "EXIST_HOME=${EXIST_HOME}"
    echo -e "USER=${USER}\n"

    read -p "Continue (Y/n)? " eval_response;
    case $eval_response in
        [Yy])
            review_systemd_config;
            install_systemd_config;
            ;;
        *)
            exit;
            ;;
    esac 
}

w_wrapper_pid_file="${wrapper_home}/work/wrapper.eXist-db.pid";
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
