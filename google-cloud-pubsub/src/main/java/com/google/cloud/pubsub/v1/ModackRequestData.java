/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.pubsub.v1;

import com.google.common.collect.Lists;
import java.util.*;

class ModackRequestData {
  private final int deadlineExtensionSeconds;
  private List<AckRequestData> ackRequestData;

  ModackRequestData(int deadlineExtensionSeconds) {
    this.deadlineExtensionSeconds = deadlineExtensionSeconds;
    this.ackRequestData = new ArrayList<AckRequestData>();
  }

  ModackRequestData(int deadlineExtensionSeconds, AckRequestData... ackRequestData) {
    this.deadlineExtensionSeconds = deadlineExtensionSeconds;
    this.ackRequestData = Arrays.asList(ackRequestData);
  }

  ModackRequestData(int deadlineExtensionSeconds, List<AckRequestData> ackRequestData) {
    this.deadlineExtensionSeconds = deadlineExtensionSeconds;
    this.ackRequestData = ackRequestData;
  }

  public int getDeadlineExtensionSeconds() {
    return deadlineExtensionSeconds;
  }

  public List<AckRequestData> getAckIdMessageFutures() {
    return ackRequestData;
  }

  public ModackRequestData addAckIdMessageFuture(AckRequestData ackRequestData) {
    this.ackRequestData.add(ackRequestData);
    return this;
  }

  public ModackRequestData addAllAckIdMessageFuture(List<AckRequestData> ackRequestData) {
    this.ackRequestData.addAll(ackRequestData);
    return this;
  }

  public List<ModackRequestData> partitionByAckId(int batchSize) {
    // Helper function to return a new list of ModackWithMessageFutures with a batchSize number of
    // ackIds
    List<ModackRequestData> modackRequestData = new ArrayList<ModackRequestData>();
    for (List<AckRequestData> ackRequestData : Lists.partition(this.ackRequestData, batchSize)) {
      modackRequestData.add(new ModackRequestData(this.deadlineExtensionSeconds, ackRequestData));
    }
    return modackRequestData;
  }

  public static List<ModackRequestData> partitionByAckId(
      List<ModackRequestData> modackRequestDataList, int batchSize) {
    // Static helper function to partition a list of ModackWithMessageFutures by AckId
    List<ModackRequestData> partitionedModackRequestData = new ArrayList<>();
    for (ModackRequestData modackRequestData : modackRequestDataList) {
      for (List<AckRequestData> ackRequestData :
          Lists.partition(modackRequestData.ackRequestData, batchSize)) {
        partitionedModackRequestData.add(
            new ModackRequestData(modackRequestData.deadlineExtensionSeconds, ackRequestData));
      }
    }
    return partitionedModackRequestData;
  }
}
