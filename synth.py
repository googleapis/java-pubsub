# Copyright 2018 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""This script is used to synthesize generated parts of this library."""

import synthtool as s
import synthtool.languages.java as java

AUTOSYNTH_MULTIPLE_COMMITS = True

service = 'pubsub'
versions = ['v1']

for version in versions:
    java.bazel_library(
        service=service,
        version=version,
        proto_path=f'google/{service}/{version}',
        bazel_target=f'//google/{service}/{version}:google-cloud-{service}-{version}-java',
    )
    s.replace(
        '**/stub/SubscriberStubSettings.java',
        r'setMaxInboundMessageSize\(Integer.MAX_VALUE\)',
        'setMaxInboundMessageSize(20 << 20)'
    )
    s.replace(
        f"proto-google-cloud-{service}-{version}/src/**/*.java",
        java.BAD_LICENSE,
        java.GOOD_LICENSE,
    )

    java.format_code('google-cloud-pubsub/src')
    java.format_code(f'grpc-google-cloud-{service}-{version}/src')
    java.format_code(f'proto-google-cloud-{service}-{version}/src')

java.common_templates(excludes=[
    'README.md'
])
