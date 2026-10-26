#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
STRUCT_TAG_JAVA=java
if [ -n "${JAVA_HOME:-}" ]; then STRUCT_TAG_JAVA="$JAVA_HOME/bin/java"; fi
exec "$STRUCT_TAG_JAVA" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
