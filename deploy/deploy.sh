#!/usr/bin/env bash

APP_NAME=feed-ingest-job
JOB_NAME=${APP_NAME}
SCHEDULER_SERVICE_NAME=scheduler-joshlong

#https://docs.cloudfoundry.org/devguide/deploy-apps/healthchecks.html#setting_health_checks
cf d -f ${APP_NAME} # delete if it already exists, just in case
cf push -b java_buildpack -u none --no-route --no-start -p target/${APP_NAME}.jar ${APP_NAME}
cf set-health-check $APP_NAME none


# scheduler
cf s | grep ${SCHEDULER_SERVICE_NAME} || cf cs scheduler-for-pcf standard ${SCHEDULER_SERVICE_NAME}
cf bs ${APP_NAME} ${SCHEDULER_SERVICE_NAME}

REDIS_NAME=feed-ingest-cache
cf s | grep ${REDIS_NAME} || cf cs rediscloud 100mb ${REDIS_NAME}
cf bs ${APP_NAME} ${REDIS_NAME}

cf set-env ${APP_NAME} PINBOARD_TOKEN ${PINBOARD_TOKEN}

cf restage ${APP_NAME}

# delete the job IF it already exists
cf jobs  | grep $JOB_NAME && cf delete-job -f ${JOB_NAME}
cf create-job ${APP_NAME} ${JOB_NAME} ".java-buildpack/open_jdk_jre/bin/java org.springframework.boot.loader.JarLauncher"
cf schedule-job ${JOB_NAME} "1 * ? * *"

cf run-job ${JOB_NAME}