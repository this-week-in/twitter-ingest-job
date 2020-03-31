#!/usr/bin/env bash

source "$(cd $(dirname $0) && pwd)/env.sh"

APP_NAME=twitter-ingest-job
JOB_NAME=${APP_NAME}
SCHEDULER_SERVICE_NAME=scheduler-joshlong
REDIS_NAME=redis-cache


cf d -f ${APP_NAME} 
cf push -b java_buildpack -u none --no-route --no-start -p target/${APP_NAME}.jar ${APP_NAME}
cf set-health-check $APP_NAME none

cf s | grep ${SCHEDULER_SERVICE_NAME} || cf cs scheduler-for-pcf standard ${SCHEDULER_SERVICE_NAME}
cf bs ${APP_NAME} ${SCHEDULER_SERVICE_NAME}

cf s | grep ${REDIS_NAME} || cf cs rediscloud 100mb ${REDIS_NAME}
cf bs ${APP_NAME} ${REDIS_NAME}

cf set-env ${APP_NAME} PINBOARD_TOKEN "${PINBOARD_TOKEN}"
cf set-env ${APP_NAME} TWITTER_TWI_CLIENT_KEY ${TWITTER_TWI_CLIENT_KEY}
cf set-env ${APP_NAME} TWITTER_TWI_CLIENT_KEY_SECRET ${TWITTER_TWI_CLIENT_KEY_SECRET}
cf set-env ${APP_NAME} JBP_CONFIG_OPEN_JDK_JRE '{ jre: { version: 11.+}}'

cf restart ${APP_NAME}

cf jobs  | grep $JOB_NAME && cf delete-job -f ${JOB_NAME}
cf create-job ${APP_NAME} ${JOB_NAME} ".java-buildpack/open_jdk_jre/bin/java org.springframework.boot.loader.JarLauncher"
cf schedule-job ${JOB_NAME} "*/15 * ? * *"

cf run-job ${JOB_NAME}