#!/bin/bash

# Copyright 2019 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -eo pipefail

export NPM_CONFIG_PREFIX=/home/node/.npm-global

if [ -f ${KOKORO_KEYSTORE_DIR}/73713_github-magic-proxy-url-release-please ]; then
  # Groom the release PR as new commits are merged.
  npx release-please release-pr --token=${KOKORO_KEYSTORE_DIR}/73713_github-magic-proxy-token-release-please \
    --repo-url=googleapis/java-pubsub \
    --package-name="pubsub" \
    --api-url=${KOKORO_KEYSTORE_DIR}/73713_github-magic-proxy-url-release-please \
    --proxy-key=${KOKORO_KEYSTORE_DIR}/73713_github-magic-proxy-key-release-please \
    --release-type=java-yoshi
fi
