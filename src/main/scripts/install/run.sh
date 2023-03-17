#!/bin/bash

CONF_FOLDER=${pkg.installFolder}/conf
configfile=${pkg.name}.conf
jarfile=${pkg.installFolder}/bin/${pkg.name}.jar
installDir=${pkg.installFolder}/data

source "${CONF_FOLDER}/${configfile}"

run_user=${pkg.user}

su -s /bin/sh -c "java -cp ${jarfile} $JAVA_OPTS -Dloader.main=GeoSensorXCustomerApplication \
                    -Dinstall.data_dir=${installDir} \
                    -Dspring.jpa.hibernate.ddl-auto=none \
                    -Dlogging.config=${pkg.installFolder}/bin/install/logback.xml \
                    org.springframework.boot.loader.PropertiesLauncher" "$run_user"

exit $?