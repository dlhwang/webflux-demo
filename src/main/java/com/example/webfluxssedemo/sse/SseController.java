package com.example.webfluxssedemo.sse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@RestController
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);
    // 최근 100건을 보관하는 리플레이 싱크 (신규 구독/재연결 시 유용)
    private final Sinks.Many<Message> sink = Sinks.many().replay().limit(100);
    private final AtomicLong seq = new AtomicLong(0);

    @Operation(
        summary = "메시지 스트리밍 (SSE)",
        description = """
            Server-Sent Events 스트림을 시작합니다.
            브라우저/클라이언트는 'Last-Event-ID' 헤더로 재연결 시 마지막 수신 ID를 넘길 수 있습니다.
            """)
    @ApiResponse(responseCode = "200",
        description = "SSE 스트림 시작",
        content = @Content(
            mediaType = "text/event-stream",
            schema = @Schema(implementation = Message.class)
        )
    )
    @GetMapping(value = "/sse/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Message>> sse(
        @RequestHeader(value = "Last-Event-ID", required = false) String lastId) {
        Long last = parse(lastId);
        var events = sink.asFlux()
            // Last-Event-ID 이후만 전달 (리플레이에 중복 섞이지 않도록)
            .filter(m -> last == null || m.id() > last)
            .map(m -> {
                log.debug("SSE: {}", m);
                return ServerSentEvent.<Message>builder(m)
                    .id(String.valueOf(m.id()))   // 브라우저가 재연결 시 이 값을 Last-Event-ID로 보냄
                    .event("message")
                    .build();
            });

        // heartbeat(코멘트 라인): LB/프록시 idle timeout 방지
        var heartbeat = Flux.interval(Duration.ofSeconds(15))
            .map(tick -> ServerSentEvent.<Message>builder()
                .comment("hb").build());

        return Flux.merge(events, heartbeat);
    }

    @Operation(
        summary = "메시지 발행",
        description = "본문 텍스트를 받아 새 메시지를 발행하고 스트림 구독자에게 전송합니다.")
    @ApiResponse(responseCode = "200", description = "발행 성공")
    @PostMapping(path = "/api/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void publish(@RequestBody Map<String, String> body) {
        var text = body.getOrDefault("text", "");
        var msg = new Message(seq.incrementAndGet(), text, Instant.now());
        sink.tryEmitNext(msg);
        log.debug("publish {}", msg);
    }

    private static Long parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }


    public record Message(long id, String message, Instant now) {

    }
}
