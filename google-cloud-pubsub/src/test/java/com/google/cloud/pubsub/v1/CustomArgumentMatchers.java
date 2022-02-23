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

import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import java.util.*;
import org.mockito.ArgumentMatcher;

/** Shared Custom Argument Matchers for Tests w/ Mock Futures */
public class CustomArgumentMatchers {
  public static class AcknowledgeRequestMatcher implements ArgumentMatcher<AcknowledgeRequest> {
    private AcknowledgeRequest left;

    AcknowledgeRequestMatcher(AcknowledgeRequest acknowledgeRequest) {
      this.left = acknowledgeRequest;
    }

    @Override
    public boolean matches(AcknowledgeRequest right) {
      Set<String> leftAckIdSet = new HashSet<String>(this.left.getAckIdsList());
      Set<String> rightAckIdSet = new HashSet<String>(right.getAckIdsList());
      return this.left.getSubscription().equals(right.getSubscription())
          && leftAckIdSet.equals(rightAckIdSet);
    }
  }

  public static class ModifyAckDeadlineRequestMatcher
      implements ArgumentMatcher<ModifyAckDeadlineRequest> {
    private ModifyAckDeadlineRequest left;

    ModifyAckDeadlineRequestMatcher(ModifyAckDeadlineRequest modifyAckDeadlineRequest) {
      this.left = modifyAckDeadlineRequest;
    }

    @Override
    public boolean matches(ModifyAckDeadlineRequest right) {
      Set<String> leftAckIdSet = new HashSet<String>(this.left.getAckIdsList());
      Set<String> rightAckIdSet = new HashSet<String>(right.getAckIdsList());
      return this.left.getSubscription().equals(right.getSubscription())
          && this.left.getAckDeadlineSeconds() == right.getAckDeadlineSeconds()
          && leftAckIdSet.equals(rightAckIdSet);
    }
  }

  public static class AckIdMessageFutureMatcher implements ArgumentMatcher<AckIdMessageFuture> {
    private AckIdMessageFuture left;

    private static Comparator<AckIdMessageFuture> comparator =
        new Comparator<AckIdMessageFuture>() {

          @Override
          public int compare(AckIdMessageFuture ackIdMessageFuture, AckIdMessageFuture t1) {
            return ackIdMessageFuture.getAckId().compareTo(t1.getAckId());
          }
        };

    AckIdMessageFutureMatcher(AckIdMessageFuture left) {
      this.left = left;
    }

    @Override
    public boolean matches(AckIdMessageFuture right) {
      return this.left.getAckId() == right.getAckId();
    }
  }

  public static class AckIdMessageFutureListMatcher
      implements ArgumentMatcher<List<AckIdMessageFuture>> {
    private List<AckIdMessageFuture> left;

    AckIdMessageFutureListMatcher(List<AckIdMessageFuture> ackIdMessageFutureList) {
      this.left = ackIdMessageFutureList;
    }

    @Override
    public boolean matches(List<AckIdMessageFuture> right) {
      // We only really care about the ackIds, the futures will be mocked
      if (this.left.size() != right.size()) {
        return false;
      }

      // We just want to compare the ackIds not the futures and do not care about order (or
      // duplicates)
      this.left.sort(AckIdMessageFutureMatcher.comparator);
      right.sort(AckIdMessageFutureMatcher.comparator);

      Iterator<AckIdMessageFuture> iteratorLeft = this.left.iterator();
      Iterator<AckIdMessageFuture> iteratorRight = right.iterator();

      while (iteratorLeft.hasNext() && iteratorRight.hasNext()) {
        if (iteratorLeft.next().getAckId() != iteratorRight.next().getAckId()) {
          return false;
        }
      }
      return true;
    }
  }

  public static class ModackWithMessageFutureMatcher
      implements ArgumentMatcher<ModackWithMessageFuture> {
    private ModackWithMessageFuture left;

    private static Comparator<ModackWithMessageFuture> comparator =
        new Comparator<ModackWithMessageFuture>() {

          @Override
          public int compare(ModackWithMessageFuture left, ModackWithMessageFuture right) {
            // Compare deadline extensions first
            int deadlineExtensionDifference =
                left.getDeadlineExtensionSeconds() - right.getDeadlineExtensionSeconds();
            if (deadlineExtensionDifference != 0) {
              return deadlineExtensionDifference;
            }

            // Then sort and compare ackIds
            List<AckIdMessageFuture> ackIdMessageFutureListLeft = left.getAckIdMessageFutures();
            List<AckIdMessageFuture> ackIdMessageFutureListRight = right.getAckIdMessageFutures();

            ackIdMessageFutureListLeft.sort(AckIdMessageFutureMatcher.comparator);
            ackIdMessageFutureListRight.sort(AckIdMessageFutureMatcher.comparator);

            Iterator<AckIdMessageFuture> iteratorLeft = ackIdMessageFutureListLeft.iterator();
            Iterator<AckIdMessageFuture> iteratorRight = ackIdMessageFutureListRight.iterator();
            int compareAcks;

            while (iteratorLeft.hasNext() && iteratorRight.hasNext()) {
              String ackIdLeft = iteratorLeft.next().getAckId();
              String ackIdRight = iteratorRight.next().getAckId();
              compareAcks = ackIdLeft.compareTo(ackIdRight);

              if (compareAcks != 0) {
                return compareAcks;
              }
            }

            if (iteratorLeft.hasNext()) {
              return 1;
            }
            if (iteratorRight.hasNext()) {
              return -1;
            } else {
              return 0;
            }
          }
        };

    ModackWithMessageFutureMatcher(ModackWithMessageFuture left) {
      this.left = left;
    }

    @Override
    public boolean matches(ModackWithMessageFuture right) {
      return ModackWithMessageFutureMatcher.comparator.compare(this.left, right) == 0;
    }
  }

  public static class ModackWithMessageFutureListMatcher
      implements ArgumentMatcher<List<ModackWithMessageFuture>> {
    private List<ModackWithMessageFuture> left;

    ModackWithMessageFutureListMatcher(List<ModackWithMessageFuture> modackWithMessageFutureList) {
      this.left = modackWithMessageFutureList;
    }

    @Override
    public boolean matches(List<ModackWithMessageFuture> right) {
      // First check size
      if (this.left.size() != right.size()) {
        return false;
      }

      // Sort first
      this.left.sort(ModackWithMessageFutureMatcher.comparator);
      right.sort(ModackWithMessageFutureMatcher.comparator);

      Iterator<ModackWithMessageFuture> iteratorLeft = this.left.iterator();
      Iterator<ModackWithMessageFuture> iteratorRight = right.iterator();

      ModackWithMessageFuture modackWithMessageFutureLeft;
      ModackWithMessageFuture modackWithMessageFutureRight;

      while (iteratorLeft.hasNext() && iteratorRight.hasNext()) {

        ModackWithMessageFutureMatcher modackWithMessageFutureMatcher =
            new ModackWithMessageFutureMatcher(iteratorLeft.next());

        if (!modackWithMessageFutureMatcher.matches(iteratorRight.next())) {
          return false;
        }
      }

      return true;
    }
  }
}
