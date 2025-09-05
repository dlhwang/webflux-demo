package com.example.webfluxssedemo.sse;

import com.example.webfluxssedemo.sse.RedisSseBridge.EventMessage;
import com.example.webfluxssedemo.sse.RedisSseBridge.Type;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class SseWithRedisController {

    private final RedisSseBridge bridge;

    /** SSE 구독: GET /sse/{channel} */
    @GetMapping(value = "/sse/{channel}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<EventMessage>> subscribe(@PathVariable String channel) {
        return bridge.subscribe(channel)
            .map(msg -> {
                if (Type.HEARTBEAT ==  msg.type()) {
                    // SSE 주석(코멘트)로 keep-alive를 보낼 수도 있지만,
                    // 여기서는 data 이벤트로 단순 처리
                    return ServerSentEvent.<EventMessage>builder()
                        .event(Type.HEARTBEAT.name())
                        .data(msg)
                        .build();
                }
                return ServerSentEvent.<EventMessage>builder()
                    .event(msg.type().name())
                    .id(msg.id())
                    .data(msg)
                    .build();
            });
    }

    /** 발행: POST /api/publish/{channel} */
    @PostMapping("/api/publish/{channel}")
    public Mono<Long> publish(@PathVariable String channel, @Valid @RequestBody EventMessage message) {
        return Mono.from(bridge.publish(channel, message));
    }
}
