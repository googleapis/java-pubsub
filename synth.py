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

GET_IAM_POLICY = """
  public final Policy getIamPolicy(String resource) {
    GetIamPolicyRequest request = GetIamPolicyRequest.newBuilder().setResource(resource).build();   
    return getIamPolicy(request);
  }
"""
GET_IAM_POLICY_PREVIOUS = r'(\s+public final Policy getIamPolicy\(GetIamPolicyRequest request\) {\n\s+return .*\n\s+})'

SET_IAM_POLICY = """
  public final Policy setIamPolicy(String resource, Policy policy) {    
    SetIamPolicyRequest request =   
        SetIamPolicyRequest.newBuilder().setResource(resource).setPolicy(policy).build();   
    return setIamPolicy(request);   
  }
"""
SET_IAM_POLICY_PREVIOUS = r'(\s+public final Policy setIamPolicy\(SetIamPolicyRequest request\) {\n\s+return .*\n\s+})'

TEST_IAM_PERMISSIONS = """
  public final TestIamPermissionsResponse testIamPermissions(   
      String resource, List<String> permissions) {  
    TestIamPermissionsRequest request = 
        TestIamPermissionsRequest.newBuilder()  
            .setResource(resource)  
            .addAllPermissions(permissions) 
            .build();   
    return testIamPermissions(request); 
  }
"""
TEST_IAM_PERMISSIONS_PREVIOUS = r'(\s+public final TestIamPermissionsResponse testIamPermissions\(TestIamPermissionsRequest request\) {\n\s+return .*\n\s+})'

CREATE_TOPIC = """
  public final Topic createTopic(ProjectTopicName name) {
    Topic request = Topic.newBuilder().setName(name == null ? null : name.toString()).build();
    return createTopic(request);
  }
"""

CREATE_TOPIC_PREVIOUS = r'(\s+public final Topic createTopic\(String name\) {\n\s+.*\n\s+return.*\n\s+})'

DELETE_TOPIC = """
  public final void deleteTopic(ProjectTopicName topic) {
    DeleteTopicRequest request =
        DeleteTopicRequest.newBuilder().setTopic(topic == null ? null : topic.toString()).build();
    deleteTopic(request);
  }
"""

GET_TOPIC_PREVIOUS = r'(\s+public final Topic getTopic\(String topic\) {\n\s+.*\n\s+return.*\n\s+})'

GET_TOPIC = """
  public final Topic getTopic(ProjectTopicName topic) {
    GetTopicRequest request =
        GetTopicRequest.newBuilder().setTopic(topic == null ? null : topic.toString()).build();
    return getTopic(request);
  }
"""

DELETE_TOPIC_PREVIOUS = r'(\s+public final void deleteTopic\(String topic\) {\n\s+.*\n\s+deleteTopic.*\n\s+})'

LIST_TOPIC_SUBSCRIPTIONS = """
  public final ListTopicSubscriptionsPagedResponse listTopicSubscriptions(ProjectTopicName topic) {
    ListTopicSubscriptionsRequest request =
        ListTopicSubscriptionsRequest.newBuilder()
            .setTopic(topic == null ? null : topic.toString())
            .build();
    return listTopicSubscriptions(request);
  }
"""

LIST_TOPIC_SUBSCRIPTIONS_PREVIOUS = r'(\s+public final ListTopicSubscriptionsPagedResponse listTopicSubscriptions\(String topic\) {\n\s+.*\n\s+.*\n\s+return.*\n\s+})'

CREATE_SUBSCRIPTION_PREVIOUS = r'(\s+public final Subscription createSubscription\(Subscription request\) {\n\s+return.*\n\s+})'

CREATE_SUBSCRIPTION = """
  public final Subscription createSubscription(
      ProjectSubscriptionName name,
      ProjectTopicName topic,
      PushConfig pushConfig,
      int ackDeadlineSeconds) {
    Subscription request =
    Subscription.newBuilder()
        .setName(name == null ? null : name.toString())
        .setTopic(topic == null ? null : topic.toString())
        .setPushConfig(pushConfig)
        .setAckDeadlineSeconds(ackDeadlineSeconds)
        .build();
    return createSubscription(request);
  }
"""

PACKAGE = 'package com.google.cloud.pubsub.v1;'

IMPORT_PROJECT_TOPIC_NAME = 'import com.google.pubsub.v1.ProjectTopicName;'

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

    s.replace(
        '**/*AdminClient.java',
        GET_IAM_POLICY_PREVIOUS,
        "\g<1>\n\n" + GET_IAM_POLICY
    )

    s.replace(
        '**/*AdminClient.java',
        SET_IAM_POLICY_PREVIOUS,
        "\g<1>\n\n" + SET_IAM_POLICY
    )

    s.replace(
        '**/*AdminClient.java',
        TEST_IAM_PERMISSIONS_PREVIOUS,
        "\g<1>\n\n" + TEST_IAM_PERMISSIONS
    )

    s.replace(
        '**/TopicAdminClient.java',
        CREATE_TOPIC_PREVIOUS,
        "\g<1>\n\n" + CREATE_TOPIC
    )

    s.replace(
        '**/TopicAdminClient.java',
        DELETE_TOPIC_PREVIOUS,
        "\g<1>\n\n" + DELETE_TOPIC
    )

    s.replace(
        '**/TopicAdminClient.java',
        LIST_TOPIC_SUBSCRIPTIONS_PREVIOUS,
        "\g<1>\n\n" + LIST_TOPIC_SUBSCRIPTIONS
    )

    s.replace(
        '**/TopicAdminClient.java',
        GET_TOPIC_PREVIOUS,
        "\g<1>\n\n" + GET_TOPIC
    )

    s.replace(
        '**/SubscriptionAdminClient.java',
        CREATE_SUBSCRIPTION_PREVIOUS,
        "\g<1>\n\n" + CREATE_SUBSCRIPTION
    )

    s.replace(
        '**/*AdminClient.java',
        PACKAGE,
        PACKAGE + '\n\n' + IMPORT_PROJECT_TOPIC_NAME + '\n'
    )

    java.format_code('google-cloud-pubsub/src')
    java.format_code(f'grpc-google-cloud-{service}-{version}/src')
    java.format_code(f'proto-google-cloud-{service}-{version}/src')

common_templates = gcp.CommonTemplates()
templates = common_templates.java_library()
s.copy(templates, excludes=[
    'README.md'
])