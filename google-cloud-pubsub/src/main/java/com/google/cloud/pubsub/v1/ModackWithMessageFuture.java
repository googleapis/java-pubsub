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

class ModackWithMessageFuture {
  final int deadlineExtensionSeconds;
  List<AckIdMessageFuture> ackIdMessageFutures;

  ModackWithMessageFuture(int deadlineExtensionSeconds) {
    this.deadlineExtensionSeconds = deadlineExtensionSeconds;
    this.ackIdMessageFutures = new ArrayList<AckIdMessageFuture>();
  }

  ModackWithMessageFuture(int deadlineExtensionSeconds, AckIdMessageFuture... ackIdMessageFutures) {
    this.deadlineExtensionSeconds = deadlineExtensionSeconds;
    this.ackIdMessageFutures = Arrays.asList(ackIdMessageFutures);
  }

  ModackWithMessageFuture(
      int deadlineExtensionSeconds, List<AckIdMessageFuture> ackIdMessageFutures) {
    this.deadlineExtensionSeconds = deadlineExtensionSeconds;
    this.ackIdMessageFutures = ackIdMessageFutures;
  }

  public int getDeadlineExtensionSeconds() {
    return deadlineExtensionSeconds;
  }

  public List<AckIdMessageFuture> getAckIdMessageFutures() {
    return ackIdMessageFutures;
  }

  public ModackWithMessageFuture addAckIdMessageFuture(AckIdMessageFuture ackIdMessageFuture) {
    this.ackIdMessageFutures.add(ackIdMessageFuture);
    return this;
  }

  public ModackWithMessageFuture addAllAckIdMessageFuture(
      List<AckIdMessageFuture> ackIdMessageFutures) {
    this.ackIdMessageFutures.addAll(ackIdMessageFutures);
    return this;
  }

  public List<ModackWithMessageFuture> partitionByAckId(int batchSize) {
    // Helper function to return a new list of ModackWithMessageFutures with a batchSize number of
    // ackIds
    List<ModackWithMessageFuture> modackWithMessageFutures =
        new ArrayList<ModackWithMessageFuture>();
    for (List<AckIdMessageFuture> ackIdMessageFutures :
        Lists.partition(this.ackIdMessageFutures, batchSize)) {
      modackWithMessageFutures.add(
          new ModackWithMessageFuture(this.deadlineExtensionSeconds, ackIdMessageFutures));
    }
    return modackWithMessageFutures;
  }

  public static List<ModackWithMessageFuture> partitionByAckId(
      List<ModackWithMessageFuture> modackWithMessageFutures, int batchSize) {
    // Static helper function to partition a list of ModackWithMessageFutures by AckId
    List<ModackWithMessageFuture> partitionedModackWithMessageFutures = new ArrayList<>();
    for (ModackWithMessageFuture modackWithMessageFuture : modackWithMessageFutures) {
      for (List<AckIdMessageFuture> ackIdMessageFutures :
          Lists.partition(modackWithMessageFuture.ackIdMessageFutures, batchSize)) {
        partitionedModackWithMessageFutures.add(
            new ModackWithMessageFuture(
                modackWithMessageFuture.deadlineExtensionSeconds, ackIdMessageFutures));
      }
    }
    return partitionedModackWithMessageFutures;
  }
}
