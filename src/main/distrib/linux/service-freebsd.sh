#!/bin/sh

# PROVIDE: gitblit
# BEFORE:  LOGIN
# KEYWORD: shutdown

PATH="/sbin:/bin:/usr/sbin:/usr/bin:/usr/local/sbin:/usr/local/bin"

. /etc/rc.subr

name="gitblit"
rcvar="gitblit_enable"

pidfile="/var/run/${name}.pid"

start_cmd="${name}_start"
stop_cmd="${name}_stop"
restart_cmd="${name}_restart"


# change theses values (default values)
GITBLIT_PATH=/opt/gitblit
GITBLIT_BASE_FOLDER=/opt/gitblit/data
. ${GITBLIT_PATH}/java-proxy-config.sh
COMMAND_LINE="java -server -Xmx1024M ${JAVA_PROXY_CONFIG} -Djava.awt.headless=true -cp gitblit.jar:ext/* com.gitblit.GitBlitServer --baseFolder $GITBLIT_BASE_FOLDER"

gitblit_start()
{
  echo "Starting Gitblit Server..."
  cd $GITBLIT_PATH
  $COMMAND_LINE --dailyLogFile &
}

gitblit_stop()
{
  echo "Stopping Gitblit Server..."
  cd $GITBLIT_PATH
  $COMMAND_LINE --stop > /dev/null &
}

gitblit_restart()
{
  $0 stop
  sleep 5
  $0 start
}

load_rc_config $name
run_rc_command "$1"
