package com.o2o.carpooling.trip;

import com.o2o.carpooling.common.domain.RouteSnapshot;
import com.o2o.carpooling.common.domain.TripOffer;
import org.springframework.stereotype.Service;

@Service
class TripPublishService {

    private final TripRepository tripRepository;
    private final MapClient mapClient;

    TripPublishService(TripRepository tripRepository, MapClient mapClient) {
        this.tripRepository = tripRepository;
        this.mapClient = mapClient;
    }

    TripOffer publish(PublishTripCommand command) {
        RouteSnapshot route = mapClient.quoteRoute(command.originText(), command.destinationText(), command.city());
        return tripRepository.create(command, route);
    }
}
