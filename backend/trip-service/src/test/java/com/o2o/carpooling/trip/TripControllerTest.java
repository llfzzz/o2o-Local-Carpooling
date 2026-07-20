package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.foundation.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the S37 fix for the gap recorded in {@code docs/security.md}: publish used to take
 * {@code driverId} from the request body, so any authenticated user could publish as anybody.
 */
class TripControllerTest {

    @Test
    void publishesAsTheAuthenticatedPrincipal() {
        RecordingPublishService service = new RecordingPublishService();
        TripController controller = new TripController(service, null);

        controller.publish("user-real", request(null));

        assertThat(service.lastCommand.driverId()).isEqualTo("user-real");
        // The request record has no driverId component at all now, so a client cannot even
        // express the spoof the old contract accepted.
        assertThat(TripController.PublishTripRequest.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .doesNotContain("driverId");
    }

    @Test
    void rejectsPublishWithoutAnAuthenticatedPrincipal() {
        RecordingPublishService service = new RecordingPublishService();
        TripController controller = new TripController(service, null);

        assertThatThrownBy(() -> controller.publish(null, request(null)))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo("AUTH_REQUIRED"));
        assertThatThrownBy(() -> controller.publish("  ", request(null)))
            .isInstanceOf(BusinessException.class);
        assertThat(service.lastCommand).isNull();
    }

    @Test
    void passesTheIdempotencyKeyThroughAndIgnoresClientSuppliedRouteNumbers() {
        RecordingPublishService service = new RecordingPublishService();
        TripController controller = new TripController(service, null);

        controller.publish("user-real", new TripController.PublishTripRequest(
            "软件园三期", "集美大学", "厦门",
            Instant.parse("2026-06-24T11:00:00Z"),
            3, "publish-001", null, null));

        assertThat(service.lastCommand.idempotencyKey()).isEqualTo("publish-001");
        assertThat(service.lastCommand.totalSeats()).isEqualTo(3);
        // PublishTripCommand has no distance/duration components at all — pricing stays server-side.
        assertThat(PublishTripCommand.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .doesNotContain("distanceMeters", "durationSeconds");
    }

    private TripController.PublishTripRequest request(String idempotencyKey) {
        return new TripController.PublishTripRequest(
            "软件园三期", "集美大学", "厦门",
            Instant.parse("2026-06-24T11:00:00Z"), 3, idempotencyKey, null, null);
    }

    private static final class RecordingPublishService extends TripPublishService {
        private PublishTripCommand lastCommand;

        RecordingPublishService() {
            super(null, null, null);
        }

        @Override
        com.o2o.carpooling.common.domain.TripOffer publish(PublishTripCommand command) {
            this.lastCommand = command;
            return null;
        }
    }
}
