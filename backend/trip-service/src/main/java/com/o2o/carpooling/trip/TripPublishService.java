package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.domain.TripOffer;
import com.o2o.carpooling.common.foundation.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
class TripPublishService {

    private final TripRepository tripRepository;
    private final MapClient mapClient;
    private final DriverCapabilityClient driverCapabilityClient;

    TripPublishService(
        TripRepository tripRepository,
        MapClient mapClient,
        DriverCapabilityClient driverCapabilityClient
    ) {
        this.tripRepository = tripRepository;
        this.mapClient = mapClient;
        this.driverCapabilityClient = driverCapabilityClient;
    }

    TripOffer publish(PublishTripCommand command) {
        requireDriverCapability(command.driverId());

        // Idempotency first: a retried publish must not burn another provider route quote.
        if (StringUtils.hasText(command.idempotencyKey())) {
            Optional<TripOffer> existing =
                tripRepository.findByIdempotency(command.driverId(), command.idempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // A resolved command skips geocoding entirely and yields a quote carrying geometry and
        // adcodes; the text form still exists for older clients.
        RouteSnapshot route = command.isResolved()
            ? mapClient.quoteRoute(command.origin(), command.destination())
            : mapClient.quoteRoute(command.originText(), command.destinationText(), command.city());
        return tripRepository.create(command, route);
    }

    /**
     * Publishing is a driver action, so it requires server-verified driver capability: real-name
     * identity APPROVED and an APPROVED document review. Holding a {@code DRIVER} role claim in a
     * token proves neither — the check must hit the services that own those records.
     */
    private void requireDriverCapability(String driverId) {
        DriverCapabilityClient.DriverCapability capability = driverCapabilityClient.capability(driverId);
        if (capability == null || !capability.approved()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DRIVER_NOT_APPROVED",
                "driver capability is required to publish a trip");
        }
    }
}
