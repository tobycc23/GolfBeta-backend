package com.golfbeta.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminConsoleConfigController {

    private final String firebaseWebApiKey;

    public AdminConsoleConfigController(
            @Value("${firebase.web-api-key:}") String firebaseWebApiKey) {
        this.firebaseWebApiKey = firebaseWebApiKey;
    }

    @GetMapping(value = "/admin-console/config.js", produces = "application/javascript")
    public String config() {
        return "window.ADMIN_CONSOLE_CONFIG = { firebaseWebApiKey: \"" + firebaseWebApiKey + "\" };";
    }
}
