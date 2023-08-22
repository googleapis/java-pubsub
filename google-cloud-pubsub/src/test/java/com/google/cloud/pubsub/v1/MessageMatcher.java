package com.google.cloud.pubsub.v1;

import com.google.pubsub.v1.PubsubMessage;
import org.mockito.ArgumentMatcher;

public class MessageMatcher implements ArgumentMatcher<PubsubMessage> {

  private PubsubMessage message1;

  public MessageMatcher(PubsubMessage message) {
    message1 = message;
  }
  @Override
  public boolean matches(PubsubMessage message2) {
    return (message1 == message2);
  }
}
