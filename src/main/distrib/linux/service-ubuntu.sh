#!/bin/bash
### BEGIN INIT INFO
# Provides:          gitblit
# Required-Start:    $remote_fs $syslog $network
# Required-Stop:     $remote_fs $syslog $network
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: Gitblit repository server
# Description:       Gitblit is a stand-alone service for managing, viewing and serving Git repositories.
### END INIT INFO

. /lib/init/vars.sh
. /lib/lsb/init-functions

PATH=/sbin:/bin:/usr/bin:/usr/sbin

# change theses values (default values)
GITBLIT_PATH=/opt/gitblit
GITBLIT_BASE_FOLDER=/opt/gitblit/data
GITBLIT_USER="gitblit"
source ${GITBLIT_PATH}/java-proxy-config.sh
ARGS="-server -Xmx1024M ${JAVA_PROXY_CONFIG} -Djava.awt.headless=true -cp gitblit.jar:ext/* com.gitblit.GitBlitServer --baseFolder $GITBLIT_BASE_FOLDER --dailyLogFile"

RETVAL=0

case "$1" in
  start)
    if [ -f $GITBLIT_PATH/gitblit.jar ];
      then
      echo $"Starting gitblit server"
      start-stop-daemon --start --quiet --background --oknodo --make-pidfile --pidfile /var/run/gitblit.pid --exec /usr/bin/java --chuid $GITBLIT_USER --chdir $GITBLIT_PATH -- $ARGS
      exit $RETVAL
    fi
  ;;

  stop)
    if [ -f $GITBLIT_PATH/gitblit.jar ];
      then
      echo $"Stopping gitblit server"
      start-stop-daemon --stop --quiet --oknodo --pidfile /var/run/gitblit.pid
      exit $RETVAL
    fi
  ;;
  
  force-reload|restart)
      $0 stop
      sleep 5
      $0 start
  ;;

  *)
    echo $"Usage: /etc/init.d/gitblit {start|stop|restart|force-reload}"
    exit 1
  ;;
esac

exit $RETVAL
