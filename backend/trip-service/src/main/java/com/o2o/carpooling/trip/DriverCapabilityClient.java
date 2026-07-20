package com.o2o.carpooling.trip;

/** Answers "may this principal publish a trip as a driver?". Server-side truth, never client claim. */
interface DriverCapabilityClient {

    DriverCapability capability(String userId);

    record DriverCapability(String userId, boolean approved, boolean identityApproved, boolean documentsApproved) {
    }
}
