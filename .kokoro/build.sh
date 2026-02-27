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

# TODO: We'll change define this value in a different place later
if [[ "${JOB_TYPE}" == "integration" ]]; then
  SHARED_DEPS_OVERRIDE=3.45.2-SNAPSHOT
fi
if [ -n "${SHARED_DEPS_OVERRIDE}" ]; then
  echo "Account used for Artifact Registry authentication"
  echo "gcloud config get-value core/account:"
  gcloud config get-value core/account
  echo "wget a file from Artifact Registry:"
  wget --quiet --header="Authorization: Bearer $(gcloud auth print-access-token)" \
      --output-document=/dev/null \
      https://us-maven.pkg.dev/suztomo-cloud-function-test/test-maven-repo/com/google/cloud/google-cloud-shared-dependencies/3.45.2-latest-deps-2024-0418-102802/google-cloud-shared-dependencies-3.45.2-latest-deps-2024-0418-102802.pom

  # Tell the integration tests to use the specific shared dependencies available in the snapshot repository.
  INTEGRATION_TEST_ARGS="${INTEGRATION_TEST_ARGS} -Dgoogle-cloud-shared-dependencies.version=${SHARED_DEPS_OVERRIDE} -Puse-snapshot-repo"
  echo "Showing dependency tree"
  mvn -B ${INTEGRATION_TEST_ARGS} -ntp dependency:tree
  echo "End of dependency tree"
fi

mvn install -B -V -ntp \
  ${INTEGRATION_TEST_ARGS} \
  -DskipTests=true \
  -Dclirr.skip=true \
  -Denforcer.skip=true \
  -Dcheckstyle.skip=true \
  -Dmaven.javadoc.skip=true \
  -Dgcloud.download.skip=true \
  -T 1C
echo "mvn instal succeeded"

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
