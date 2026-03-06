package io.github.prakharr0.ai.rca.spring.boot.starter.web;

import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionOccurrence;
import io.github.prakharr0.ai.rca.spring.boot.core.store.ExceptionTimelineStore;
import io.github.prakharr0.ai.rca.spring.boot.core.util.RcaTimeParser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai-rca")
public class AiRcaEventsController {

    private final ExceptionTimelineStore timelineStore;

    public AiRcaEventsController(ExceptionTimelineStore timelineStore) {
        this.timelineStore = timelineStore;
    }

    @GetMapping("/events")
    public List<ExceptionOccurrence> events(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "50") int limit
    ) {
        ZoneId zoneId = ZoneId.systemDefault();
        Instant fromInstant = parseOrNull(from, zoneId, "from");
        Instant toInstant = parseOrNull(to, zoneId, "to");
        return timelineStore.findBetween(fromInstant, toInstant, limit);
    }

    @GetMapping("/events/at")
    public Map<String, Object> eventAt(
            @RequestParam String time,
            @RequestParam(defaultValue = "1800") int toleranceSeconds
    ) {
        ZoneId zoneId = ZoneId.systemDefault();
        Instant target = RcaTimeParser.parseInstant(time, zoneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to parse time: " + time));

        ExceptionOccurrence event = timelineStore.findAt(target, Duration.ofSeconds(Math.max(60, toleranceSeconds)));

        Map<String, Object> response = new HashMap<>();
        response.put("requestedTime", target);
        response.put("toleranceSeconds", Math.max(60, toleranceSeconds));
        response.put("event", event);
        return response;
    }

    private Instant parseOrNull(String value, ZoneId zoneId, String parameterName) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return RcaTimeParser.parseInstant(value, zoneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to parse " + parameterName + ": " + value));
    }
}
