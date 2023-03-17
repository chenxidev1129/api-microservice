#!/bin/bash
#
# The Thingsboard Authors ("COMPANY") CONFIDENTIAL
#
# Copyright © 2016-2020 The Thingsboard Authors All Rights Reserved.
#
# NOTICE: All information contained herein is, and remains
# the property of The Thingsboard Authors and its suppliers,
# if any.  The intellectual and technical concepts contained
# herein are proprietary to The Thingsboard Authors
# and its suppliers and may be covered by U.S. and Foreign Patents,
# patents in process, and are protected by trade secret or copyright law.
#
# Dissemination of this information or reproduction of this material is strictly forbidden
# unless prior written permission is obtained from COMPANY.
#
# Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
# managers or contractors who have executed Confidentiality and Non-disclosure agreements
# explicitly covering such access.
#
# The copyright notice above does not evidence any actual or intended publication
# or disclosure  of  this source code, which includes
# information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
# ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
# OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
# THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
# AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
# THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
# DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
# OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
#


echo "Build GSX Maven Repository..."

REPOSITORY_ID=gsx-local-maven-repository
VERSION=3.3.4.1PE-GSX
M2_HOME_FOLDER="${HOME}/.m2/repository";

cp ${M2_HOME_FOLDER}/org/thingsboard/thingsboard/${VERSION}/thingsboard-${VERSION}.pom /tmp
cp ${M2_HOME_FOLDER}/org/thingsboard/common/${VERSION}/common-${VERSION}.pom /tmp
cp ${M2_HOME_FOLDER}/org/thingsboard/common/data/${VERSION}/data-${VERSION}.jar /tmp
cp ${M2_HOME_FOLDER}/org/thingsboard/rest-client/${VERSION}/rest-client-${VERSION}.jar /tmp

mvn deploy:deploy-file -Dfile=/tmp/thingsboard-${VERSION}.pom \
    -DgroupId=org.thingsboard \
    -DartifactId=thingsboard \
    -Dversion=${VERSION} \
    -DrepositoryId=${REPOSITORY_ID} \
    -Durl=file://${REPOSITORY_ID} \
    -Dpackaging=pom

mvn deploy:deploy-file -Dfile=/tmp/common-${VERSION}.pom \
    -DgroupId=org.thingsboard \
    -DartifactId=common \
    -Dversion=${VERSION} \
    -DrepositoryId=${REPOSITORY_ID} \
    -Durl=file://${REPOSITORY_ID} \
    -Dpackaging=pom

mvn deploy:deploy-file -Dfile=/tmp/data-${VERSION}.jar \
    -DgroupId=org.thingsboard.common \
    -DartifactId=data \
    -Dversion=${VERSION} \
    -DrepositoryId=${REPOSITORY_ID} \
    -Durl=file://${REPOSITORY_ID} \
    -Dpackaging=jar

mvn deploy:deploy-file -Dfile=/tmp/rest-client-${VERSION}.jar \
    -DgroupId=org.thingsboard \
    -DartifactId=rest-client \
    -Dversion=${VERSION} \
    -DrepositoryId=${REPOSITORY_ID} \
    -Durl=file://${REPOSITORY_ID} \
    -Dpackaging=jar

rm /tmp/thingsboard-${VERSION}.pom
rm /tmp/common-${VERSION}.pom
rm /tmp/data-${VERSION}.jar
rm /tmp/rest-client-${VERSION}.jar
