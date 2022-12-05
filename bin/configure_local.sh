#!/bin/bash

#
# Configure a local consul server
#

##########################################################################################
#
# Invoke from wherever you are, without needing to chdir or having cwd
# change underneath you, forwarding whatever args you give.
#

abspath () { case "$1" in /*)printf "%s\n" "$1";; *)printf "%s\n" "$PWD/$1";; esac; }
#
# Find our location in the filesystem.
#
# first, for the benefit of mksnt, convert slashes and mash $0.
#
arg_zero="${0%/*}"
myloc=$(cd "${arg_zero}" && test -f "$PWD"/configure_local.sh && echo "$PWD"/ || echo "")

if test ! -d "$myloc" ; then
  echo "can't find self in file system, bailing... (got '"${myloc}"')"
  exit 1
fi

##########################################################################################
# Consul

# This script is destructive and so can only be run against localhost
CONSUL_PORT="${CONSUL_PORT:-8500}"
CONSUL_SERVER=http://localhost:$CONSUL_PORT

function put() {
    consul kv put "-http-addr=$CONSUL_SERVER" "$1" "$2" >/dev/null || exit 11
    echo "$1 ↦ $2"
}

##########################################################################################

# 11-7-system
HOST="${HOST:-10.232.3.118}"
FABRIC_HOST="${FABRIC_HOST:-$HOST}"
FABRIC_PORT="${FABRIC_PORT:-9797}"
FABRIC_PROTOCOL="${FABRIC_PROTOCOL:-http}"
KAFKA_HOST=${KAFKA_HOST:-localhost}
KAFKA_PORT=${KAFKA_PORT:-9092}


# Fabric
put "telflow/env/fabric/protocol" "${FABRIC_PROTOCOL}"
put "telflow/env/fabric/host" "${FABRIC_HOST}"
put "telflow/env/fabric/port" "${FABRIC_PORT}"

#kafka
put telflow/env/kafka/protocol ""
put telflow/env/kafka/host "$KAFKA_HOST"
put telflow/env/kafka/port "$KAFKA_PORT"

put telflow/app/telflow-quote-generator/test/projectRoot" " "$(dirname $myloc)"
