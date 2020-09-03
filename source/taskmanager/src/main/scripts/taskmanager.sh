#! /bin/sh

#Original /etc/init.d/skeleton modified for http://mydebian.blogdns.org

#Installation specific variables. Change these depending on the installation:

# Directory were the application is installed
DIR=/home/aerius/aerius/AERIUS_II-taskmanager

# The user that will run the script
USER=aerius

# Other variables, shouldn't need any changes normally
PATH=/sbin:/usr/sbin:/bin:/usr/bin
DESC="AERIUS Taskmanager service Daemon"
NAME="aerius-taskmanager-service"
DAEMON=/usr/bin/java
DAEMON_ARGS="-jar ${DIR}/aerius-taskmanager-*.jar -log ${DIR}/log4j.conf -config ${DIR}/taskmanager.config"
PIDFILE=/var/run/$NAME.pid
SCRIPTNAME=/etc/init.d/aerius-taskmanager

# NO NEED TO MODIFY THE LINES BELOW

# Load the VERBOSE setting and other rcS variables
. /lib/init/vars.sh

# Define LSB log_* functions.
# Depend on lsb-base (>= 3.0-6) to ensure that this file is present.
. /lib/lsb/init-functions

#
# Function that starts the daemon/service
#
do_start()
{

	start-stop-daemon -b --start --quiet --chdir $DIR --chuid $USER -m -p $PIDFILE --exec $DAEMON -- $DAEMON_ARGS \
		|| return 2
}

#
# Function that stops the daemon/service
#
do_stop()
{
	start-stop-daemon --stop --quiet --oknodo --pidfile $PIDFILE
	RETVAL="$?"
	rm -f $PIDFILE
	return "$RETVAL"
}

case "$1" in
  start)
	log_daemon_msg "Starting $DESC" "$NAME"
	do_start
	case "$?" in
		0|1) [ "$VERBOSE" != no ] && log_end_msg 0 ;;
		2) [ "$VERBOSE" != no ] && log_end_msg 1 ;;
	esac
	;;
  stop)
	log_daemon_msg "Stopping $DESC" "$NAME"
	do_stop
	case "$?" in
		0|1) [ "$VERBOSE" != no ] && log_end_msg 0 ;;
		2) [ "$VERBOSE" != no ] && log_end_msg 1 ;;
	esac
	;;
  restart)
	#
	# If the "reload" option is implemented then remove the
	# 'force-reload' alias
	#
	log_daemon_msg "Restarting $DESC" "$NAME"
	do_stop
	case "$?" in
	  0|1)
		do_start
		case "$?" in
			0) log_end_msg 0 ;;
			1) log_end_msg 1 ;; # Old process is still running
			*) log_end_msg 1 ;; # Failed to start
		esac
		;;
	  *)
	  	# Failed to stop
		log_end_msg 1
		;;
	esac
	;;
  *)
	echo "Usage: $SCRIPTNAME {start|stop|restart}" >&2
	exit 3
	;;
esac

:

