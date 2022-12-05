#!/bin/bash

#
# Delete the analytics DB if it exists and recreate
#

#
# make sure we don't get broken by someone aliasing cp or rm or whatever.
# You'd be surprised.
#
unalias -a

LANG=en_AU.UTF-8
LC_CTYPE=en_AU.UTF-8
export LANG
export LC_CTYPE

##########################################################################################
# Consul

CONSUL_HOST="${CONSUL_HOST:-localhost}"
CONSUL_PORT="${CONSUL_PORT:-8500}"
CONSUL_SERVER="${CONSUL_SERVER:-http://$CONSUL_HOST:$CONSUL_PORT}"

function get() {
    consul kv get "-http-addr=$CONSUL_SERVER" "$1"
}

##########################################################################################

LOG_LEVEL="${LOG_LEVEL:-INFO}"
CONSUL_SERVER=$CONSUL_SERVER DISABLE_AUTH_HTTP=true LOG_LEVEL=$LOG_LEVEL mvn exec:java -Dlog.console=ALL -Dlog.jsonstdout=OFF
