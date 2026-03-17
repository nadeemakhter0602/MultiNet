#!/bin/sh
#
# Gradle wrapper script — bootstraps the correct Gradle version automatically.
# You never need to install Gradle manually.

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$(dirname "$0")" && pwd)

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Use Android Studio's bundled JDK
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
JAVACMD="$JAVA_HOME/bin/java"

exec "$JAVACMD" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
