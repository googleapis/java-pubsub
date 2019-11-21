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
import synthtool.gcp as gcp
import synthtool.languages.java as java

gapic = gcp.GAPICGenerator()

service = 'pubsub'
versions = ['v1']
config_pattern = '/google/pubsub/artman_pubsub.yaml'

for version in versions:
    java.gapic_library(
        service=service,
        version=version,
        config_pattern=config_pattern,
        package_pattern='com.google.{service}.{version}',
        gapic=gapic,
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

common_templates = gcp.CommonTemplates()
templates = common_templates.java_library()
s.copy(templates, excludes=[
    'README.md'
])