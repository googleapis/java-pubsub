/*
 * Copyright 2016 Google LLC
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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Waiter}. */
@RunWith(JUnit4.class)
public class WaiterTest {

  @Test
  public void test() throws Exception {
    final Waiter waiter = new Waiter();
    waiter.incrementPendingCount(1);

    final Thread mainThread = Thread.currentThread();
    Thread t =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                while (mainThread.getState() != Thread.State.WAITING) {
                  Thread.yield();
                }
                waiter.incrementPendingCount(-1);
              }
            });
    t.start();

    waiter.waitComplete();
    t.join();

    assertEquals(0, waiter.pendingCount());
  }
}
