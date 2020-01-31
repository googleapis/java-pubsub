/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.pubsub.v1;

import com.google.api.pathtemplate.PathTemplate;
import com.google.api.resourcenames.ResourceName;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** AUTO-GENERATED DOCUMENTATION AND CLASS */
@javax.annotation.Generated("by GAPIC protoc plugin")
public class ProjectSubscriptionName implements ResourceName {

  private static final PathTemplate PATH_TEMPLATE =
      PathTemplate.createWithoutUrlEncoding("projects/{project}/subscriptions/{subscription}");

  private volatile Map<String, String> fieldValuesMap;

  private final String project;
  private final String subscription;

  public String getProject() {
    return project;
  }

  public String getSubscription() {
    return subscription;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  private ProjectSubscriptionName(Builder builder) {
    project = Preconditions.checkNotNull(builder.getProject());
    subscription = Preconditions.checkNotNull(builder.getSubscription());
  }

  public static ProjectSubscriptionName of(String project, String subscription) {
    return newBuilder().setProject(project).setSubscription(subscription).build();
  }

  public static String format(String project, String subscription) {
    return newBuilder().setProject(project).setSubscription(subscription).build().toString();
  }

  public static ProjectSubscriptionName parse(String formattedString) {
    if (formattedString.isEmpty()) {
      return null;
    }
    Map<String, String> matchMap =
        PATH_TEMPLATE.validatedMatch(
            formattedString, "ProjectSubscriptionName.parse: formattedString not in valid format");
    return of(matchMap.get("project"), matchMap.get("subscription"));
  }

  public static List<ProjectSubscriptionName> parseList(List<String> formattedStrings) {
    List<ProjectSubscriptionName> list = new ArrayList<>(formattedStrings.size());
    for (String formattedString : formattedStrings) {
      list.add(parse(formattedString));
    }
    return list;
  }

  public static List<String> toStringList(List<ProjectSubscriptionName> values) {
    List<String> list = new ArrayList<String>(values.size());
    for (ProjectSubscriptionName value : values) {
      if (value == null) {
        list.add("");
      } else {
        list.add(value.toString());
      }
    }
    return list;
  }

  public static boolean isParsableFrom(String formattedString) {
    return PATH_TEMPLATE.matches(formattedString);
  }

  public Map<String, String> getFieldValuesMap() {
    if (fieldValuesMap == null) {
      synchronized (this) {
        if (fieldValuesMap == null) {
          ImmutableMap.Builder<String, String> fieldMapBuilder = ImmutableMap.builder();
          fieldMapBuilder.put("project", project);
          fieldMapBuilder.put("subscription", subscription);
          fieldValuesMap = fieldMapBuilder.build();
        }
      }
    }
    return fieldValuesMap;
  }

  public String getFieldValue(String fieldName) {
    return getFieldValuesMap().get(fieldName);
  }

  @Override
  public String toString() {
    return PATH_TEMPLATE.instantiate("project", project, "subscription", subscription);
  }

  /** Builder for ProjectSubscriptionName. */
  public static class Builder {

    private String project;
    private String subscription;

    public String getProject() {
      return project;
    }

    public String getSubscription() {
      return subscription;
    }

    public Builder setProject(String project) {
      this.project = project;
      return this;
    }

    public Builder setSubscription(String subscription) {
      this.subscription = subscription;
      return this;
    }

    private Builder() {}

    private Builder(ProjectSubscriptionName projectSubscriptionName) {
      project = projectSubscriptionName.project;
      subscription = projectSubscriptionName.subscription;
    }

    public ProjectSubscriptionName build() {
      return new ProjectSubscriptionName(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof ProjectSubscriptionName) {
      ProjectSubscriptionName that = (ProjectSubscriptionName) o;
      return (this.project.equals(that.project)) && (this.subscription.equals(that.subscription));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= project.hashCode();
    h *= 1000003;
    h ^= subscription.hashCode();
    return h;
  }
}
