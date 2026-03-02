#!/bin/bash
# Copyright 2019 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -eo pipefail

## Get the directory of the build script
scriptDir=$(realpath $(dirname "${BASH_SOURCE[0]}"))
## cd to the parent directory, i.e. the root of the git repo
cd ${scriptDir}/..

# include common functions
source ${scriptDir}/common.sh

# Print out Maven & Java version
mvn -version
echo ${JOB_TYPE}

# Store the current Java version since the version may change when installing sdk-platform-java
current_java_home=$JAVA_HOME

# Get the current proto runtime version used in this repo
CURRENT_PROTO_VERSION=$(mvn -ntp help:effective-pom |
sed -n "/<artifactId>protobuf-java<\/artifactId>/,/<\/dependency>/ {
  /<version>/{
      s/<version>\(.*\)<\/version>/\1/p
      q
  }
}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
echo "The current proto version is: ${CURRENT_PROTO_VERSION}"

# Find the latest proto runtime version available
LATEST_PROTO_VERSION="4.28.2"
echo "The latest proto version is: ${LATEST_PROTO_VERSION}"

# Only reinstall shared-deps again to test for a newer proto version
if [[ "${CURRENT_PROTO_VERSION}" != "${LATEST_PROTO_VERSION}" ]]; then
  # testing-infra-docker has Java 11 installed in java8 docker container. Use this as sdk-platform-java
  # needs Java 11+ to run with GraalVM. For GH actions, JAVA11_HOME does not exist and would skip this.
  if [ ! -z "${JAVA11_HOME}" ]; then
    export JAVA_HOME="${JAVA11_HOME}"
    export PATH=${JAVA_HOME}/bin:$PATH
  fi

  pushd /tmp
  git clone https://github.com/googleapis/sdk-platform-java.git
  pushd sdk-platform-java
  pushd gapic-generator-java-pom-parent
  sed -i "/<protobuf.version>.*<\/protobuf.version>/s/\(.*<protobuf.version>\).*\(<\/protobuf.version>\)/\1${LATEST_PROTO_VERSION}\2/" pom.xml
  # sdk-platform-java
  popd

  pushd sdk-platform-java-config
  # Get current Shared-Deps version in sdk-platform-java
  SHARED_DEPS_VERSION=$(mvn -ntp help:effective-pom |
  sed -n "/<artifactId>sdk-platform-java-config<\/artifactId>/,/<\/dependency>/ {
    /<version>/{
        s/<version>\(.*\)<\/version>/\1/p
        q
    }
  }" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
  echo "Shared-Deps Version: ${SHARED_DEPS_VERSION}"
  # sdk-platform-java
  popd

  mvn clean install -q -ntp \
      -DskipTests=true \
      -Dclirr.skip=true \
      -Denforcer.skip=true \
      -T 1C
  # /tmp
  popd

  # Back to the original directory of the repo
  popd
  # Find all the poms with a reference to shared-deps and update to the new local version
  poms=($(find . -name pom.xml))
  for pom in "${poms[@]}"; do
    if grep -q "sdk-platform-java-config" "${pom}"; then
      echo "Updating the pom: ${pom} to use shared-deps version: ${SHARED_DEPS_VERSION}"
      sed -i "/<artifactId>sdk-platform-java-config<\/artifactId>/,/<\/parent>/ s/<version>.*<\/version>/<version>$SHARED_DEPS_VERSION<\/version>/" "${pom}"
#      xmlstarlet ed --inplace -N x="http://maven.apache.org/POM/4.0.0" \
#        -u "//x:project/x:parent[x:artifactId='sdk-platform-java-config']/x:version" \
#        -v "${SHARED_DEPS_VERSION}" \
#        "${pom}"
    fi
  done

  # Reset back to the original Java version if changed
  export JAVA_HOME="${current_java_home}"
  export PATH=${JAVA_HOME}/bin:$PATH
fi

# attempt to install 3 times with exponential backoff (starting with 10 seconds)
retry_with_backoff 3 10 \
  mvn install -B -V -ntp \
    -DskipTests=true \
    -Dclirr.skip=true \
    -Denforcer.skip=true \
    -Dmaven.javadoc.skip=true \
    -Dgcloud.download.skip=true \
    -T 1C

# if GOOGLE_APPLICATION_CREDENTIALS is specified as a relative path, prepend Kokoro root directory onto it
if [[ ! -z "${GOOGLE_APPLICATION_CREDENTIALS}" && "${GOOGLE_APPLICATION_CREDENTIALS}" != /* ]]; then
    export GOOGLE_APPLICATION_CREDENTIALS=$(realpath ${KOKORO_GFILE_DIR}/${GOOGLE_APPLICATION_CREDENTIALS})
fi

RETURN_CODE=0
set +e

case ${JOB_TYPE} in
test)
    echo "SUREFIRE_JVM_OPT: ${SUREFIRE_JVM_OPT}"
    mvn test -B -ntp -Dclirr.skip=true -Denforcer.skip=true ${SUREFIRE_JVM_OPT}
    RETURN_CODE=$?
    ;;
lint)
    mvn com.spotify.fmt:fmt-maven-plugin:check
    RETURN_CODE=$?
    ;;
javadoc)
    mvn javadoc:javadoc javadoc:test-javadoc
    RETURN_CODE=$?
    ;;
integration)
    mvn -B ${INTEGRATION_TEST_ARGS} \
      -ntp \
      -Penable-integration-tests \
      -DtrimStackTrace=false \
      -Dclirr.skip=true \
      -Denforcer.skip=true \
      -fae \
      verify
    RETURN_CODE=$?
    ;;
graalvm)
    # Run Unit and Integration Tests with Native Image
    mvn -B ${INTEGRATION_TEST_ARGS} -ntp -Pnative -Penable-integration-tests test
    RETURN_CODE=$?
    ;;
graalvm17)
    # Run Unit and Integration Tests with Native Image
    mvn -B ${INTEGRATION_TEST_ARGS} -ntp -Pnative -Penable-integration-tests test
    RETURN_CODE=$?
    ;;
samples)
    SAMPLES_DIR=samples
    # only run ITs in snapshot/ on presubmit PRs. run ITs in all 3 samples/ subdirectories otherwise.
    if [[ ! -z ${KOKORO_GITHUB_PULL_REQUEST_NUMBER} ]]
    then
      SAMPLES_DIR=samples/snapshot
    fi

    if [[ -f ${SAMPLES_DIR}/pom.xml ]]
    then
        for FILE in ${KOKORO_GFILE_DIR}/secret_manager/*-samples-secrets; do
          [[ -f "$FILE" ]] || continue
          source "$FILE"
        done

        pushd ${SAMPLES_DIR}
        mvn -B \
          -Penable-samples \
          -ntp \
          -DtrimStackTrace=false \
          -Dclirr.skip=true \
          -Denforcer.skip=true \
          -fae \
          verify
        RETURN_CODE=$?
        popd
    else
        echo "no sample pom.xml found - skipping sample tests"
    fi
    ;;
presubmit-against-pubsublite-samples)
    ## cd to the directory one level above the root of the repo
    cd ${scriptDir}/../..
    git clone https://github.com/googleapis/java-pubsublite.git
    pushd java-pubsublite/

    SAMPLES_DIR=samples
    # Only run ITs in in snippets/ on presubmit PRs.
    if [[ ! -z ${KOKORO_GITHUB_PULL_REQUEST_NUMBER} ]]
    then
      SAMPLES_DIR=samples/snippets
    fi

    if [[ -f ${SAMPLES_DIR}/pom.xml ]]
    then
        for FILE in ${KOKORO_GFILE_DIR}/secret_manager/*-samples-secrets; do
          [[ -f "$FILE" ]] || continue
          source "$FILE"
        done

        pushd ${SAMPLES_DIR}
        mvn -B \
          -Penable-samples \
          -ntp \
          -DtrimStackTrace=false \
          -Dclirr.skip=true \
          -Denforcer.skip=true \
          -fae \
          verify
        RETURN_CODE=$?
        popd
    else
        echo "no sample pom.xml found - skipping sample tests"
    fi
    ;;
clirr)
    mvn -B -Denforcer.skip=true clirr:check
    RETURN_CODE=$?
    ;;
*)
    ;;
esac

if [ "${REPORT_COVERAGE}" == "true" ]
then
  bash ${KOKORO_GFILE_DIR}/codecov.sh
fi

# fix output location of logs
bash .kokoro/coerce_logs.sh

if [[ "${ENABLE_BUILD_COP}" == "true" ]]
then
    chmod +x ${KOKORO_GFILE_DIR}/linux_amd64/flakybot
    ${KOKORO_GFILE_DIR}/linux_amd64/flakybot -repo=googleapis/java-pubsub
fi

echo "exiting with ${RETURN_CODE}"
exit ${RETURN_CODE}
