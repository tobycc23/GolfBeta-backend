package com.golfbeta.notifications;

import com.golfbeta.notifications.dto.NotificationInboxDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationInboxService inbox;
    private final NotificationTokenService tokens;

    public NotificationController(NotificationInboxService inbox, NotificationTokenService tokens) {
        this.inbox = inbox;
        this.tokens = tokens;
    }

    @GetMapping
    public List<NotificationInboxDto> list(@AuthenticationPrincipal String uid) {
        return inbox.listForUser(uid);
    }

    @PostMapping("/{id}/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markSeen(@AuthenticationPrincipal String uid, @PathVariable("id") @NotNull Long id) {
        inbox.markSeen(uid, id);
    }

    @PostMapping("/token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerToken(@AuthenticationPrincipal String uid, @RequestBody @Valid TokenRequest body) {
        log.debug("Token register request for user {} platform {} prefix {}", uid, body.platform(), tokenPrefix(body.token()));
        tokens.register(uid, body.token(), body.platform());
    }

    @DeleteMapping("/token/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteToken(@PathVariable("token") @NotBlank String token) {
        tokens.unregister(token);
    }

    public record TokenRequest(@NotBlank String token, String platform) {}

    private String tokenPrefix(String token) {
        if (token == null) return "null";
        return token.length() <= 10 ? token : token.substring(0, 10);
    }
}
