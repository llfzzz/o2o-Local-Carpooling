package com.o2o.carpooling.auth;

import com.o2o.carpooling.common.domain.UserAccount;
import com.o2o.carpooling.common.domain.UserRole;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Set;

/** Reads/creates the authoritative user record so roles come from the server, never the client. */
@FeignClient(name = "user-service", url = "${O2O_USER_SERVICE_URL:http://127.0.0.1:8102}")
interface UserFeignClient {

    @GetMapping("/api/users/{userId}")
    UserAccount get(@PathVariable("userId") String userId);

    @PostMapping("/api/users")
    UserAccount upsert(@RequestBody UpsertRequest request);

    record UpsertRequest(String userId, String phone, Set<UserRole> roles) {
    }
}
