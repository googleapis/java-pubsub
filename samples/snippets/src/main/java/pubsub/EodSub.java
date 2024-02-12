package pubsub;

import com.google.cloud.pubsub.v1.AckReplyConsumerWithResponse;
import com.google.cloud.pubsub.v1.AckResponse;
import com.google.cloud.pubsub.v1.MessageReceiverWithAckResponse;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.threeten.bp.Duration;

public class EodSub {
  public static void main(String... args) throws Exception {
    // TODO(developer): Replace these variables before running the sample.
    String projectId = "cloud-pubsub-experiments";
    String subscriptionId = "mike-java-sub";

    subscribeWithExactlyOnceConsumerWithResponseExample(projectId, subscriptionId);
  }

  public static void subscribeWithExactlyOnceConsumerWithResponseExample(
      String projectId, String subscriptionId) {
    ProjectSubscriptionName subscriptionName =
        ProjectSubscriptionName.of("cloud-pubsub-experiments", subscriptionId);

    // Instantiate an asynchronous message receiver using `AckReplyConsumerWithResponse`
    // instead of `AckReplyConsumer` to get a future that tracks the result of the ack call.
    // When exactly once delivery is enabled on the subscription, the message is guaranteed
    // to not be delivered again if the ack future succeeds.
    MessageReceiverWithAckResponse receiverWithResponse =
        (PubsubMessage message, AckReplyConsumerWithResponse consumerWithResponse) -> {
          try {
            // Handle incoming message, then ack the message, and receive an ack response.
            // System.out.println("Message received: " + message.getData().toStringUtf8());
            Future<AckResponse> ackResponseFuture = consumerWithResponse.ack();

            // Retrieve the completed future for the ack response from the server.
            AckResponse ackResponse = ackResponseFuture.get();

            switch (ackResponse) {
              case SUCCESSFUL:
                // Success code means that this MessageID will not be delivered again.
                if (message.getData().toStringUtf8().equals("hello #499")) {
                  System.out.println("Message successfully acked: " + message.getData().toStringUtf8());
                }
                break;
              case INVALID:
                System.out.println(
                    "Message failed to ack with a response of Invalid. Id: "
                        + message.getMessageId());
                break;
              case PERMISSION_DENIED:
                System.out.println(
                    "Message failed to ack with a response of Permission Denied. Id: "
                        + message.getMessageId());
                break;
              case FAILED_PRECONDITION:
                System.out.println(
                    "Message failed to ack with a response of Failed Precondition. Id: "
                        + message.getMessageId());
                break;
              case OTHER:
                System.out.println(
                    "Message failed to ack with a response of Other. Id: "
                        + message.getMessageId());
                break;
              default:
                break;
            }
          } catch (InterruptedException | ExecutionException e) {
            System.out.println(
                "MessageId: " + message.getMessageId() + " failed when retrieving future");
          } catch (Throwable t) {
            System.out.println("Throwable caught" + t.getMessage());
          }
        };

    Subscriber subscriber = null;
    try {
      subscriber = Subscriber.newBuilder(subscriptionName, receiverWithResponse)
        .setMaxDurationPerAckExtension(Duration.ofSeconds(600))
        .setMinDurationPerAckExtension(Duration.ofSeconds(300))
	.build();
      // Start the subscriber.
      subscriber.startAsync().awaitRunning();
      System.out.printf("Listening for messages on %s:\n", subscriptionName.toString());
      // Allow the subscriber to run for 30s unless an unrecoverable error occurs.
      subscriber.awaitTerminated(7200, TimeUnit.SECONDS);
    } catch (TimeoutException timeoutException) {
      // Shut down the subscriber after 30s. Stop receiving messages.
      subscriber.stopAsync();
    }
  }
}
