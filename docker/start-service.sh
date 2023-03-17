#!/bin/bash

CONF_FOLDER="${pkg.installFolder}/conf"
jarfile=${pkg.installFolder}/bin/${pkg.name}.jar
configfile=${pkg.name}.conf

source "${CONF_FOLDER}/${configfile}"

echo "Starting GeoSensorX Custom TB API Service..."

cd ${pkg.installFolder}/bin

exec java -cp ${jarfile} $JAVA_OPTS -Dloader.main=com.geosensorx.GeoSensorXCustomerApplication \
                    -Dlogging.config=${CONF_FOLDER}/logback.xml \
                    org.springframework.boot.loader.PropertiesLauncher