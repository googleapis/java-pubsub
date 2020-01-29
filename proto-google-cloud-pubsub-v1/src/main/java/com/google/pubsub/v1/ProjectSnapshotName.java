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
public class ProjectSnapshotName implements ResourceName {

  private static final PathTemplate PATH_TEMPLATE =
      PathTemplate.createWithoutUrlEncoding("projects/{project}/snapshots/{snapshot}");

  private volatile Map<String, String> fieldValuesMap;

  private final String project;
  private final String snapshot;

  public String getProject() {
    return project;
  }

  public String getSnapshot() {
    return snapshot;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  private ProjectSnapshotName(Builder builder) {
    project = Preconditions.checkNotNull(builder.getProject());
    snapshot = Preconditions.checkNotNull(builder.getSnapshot());
  }

  public static ProjectSnapshotName of(String project, String snapshot) {
    return newBuilder().setProject(project).setSnapshot(snapshot).build();
  }

  public static String format(String project, String snapshot) {
    return newBuilder().setProject(project).setSnapshot(snapshot).build().toString();
  }

  public static ProjectSnapshotName parse(String formattedString) {
    if (formattedString.isEmpty()) {
      return null;
    }
    Map<String, String> matchMap =
        PATH_TEMPLATE.validatedMatch(
            formattedString, "ProjectSnapshotName.parse: formattedString not in valid format");
    return of(matchMap.get("project"), matchMap.get("snapshot"));
  }

  public static List<ProjectSnapshotName> parseList(List<String> formattedStrings) {
    List<ProjectSnapshotName> list = new ArrayList<>(formattedStrings.size());
    for (String formattedString : formattedStrings) {
      list.add(parse(formattedString));
    }
    return list;
  }

  public static List<String> toStringList(List<ProjectSnapshotName> values) {
    List<String> list = new ArrayList<String>(values.size());
    for (ProjectSnapshotName value : values) {
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
          fieldMapBuilder.put("snapshot", snapshot);
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
    return PATH_TEMPLATE.instantiate("project", project, "snapshot", snapshot);
  }

  /** Builder for ProjectSnapshotName. */
  public static class Builder {

    private String project;
    private String snapshot;

    public String getProject() {
      return project;
    }

    public String getSnapshot() {
      return snapshot;
    }

    public Builder setProject(String project) {
      this.project = project;
      return this;
    }

    public Builder setSnapshot(String snapshot) {
      this.snapshot = snapshot;
      return this;
    }

    private Builder() {}

    private Builder(ProjectSnapshotName projectSnapshotName) {
      project = projectSnapshotName.project;
      snapshot = projectSnapshotName.snapshot;
    }

    public ProjectSnapshotName build() {
      return new ProjectSnapshotName(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof ProjectSnapshotName) {
      ProjectSnapshotName that = (ProjectSnapshotName) o;
      return (this.project.equals(that.project)) && (this.snapshot.equals(that.snapshot));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= project.hashCode();
    h *= 1000003;
    h ^= snapshot.hashCode();
    return h;
  }
}
