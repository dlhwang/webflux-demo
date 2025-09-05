package com.example.webfluxssedemo.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.data.redis.connection.ReactiveSubscription.Message;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSseBridge {
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper om;

    // channel별 로컬 multicast sink
    private final Map<String, ChannelBroadcaster> channels = new ConcurrentHashMap<>();

    private static final String TOPIC_PREFIX = "sse:";
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    /** Redis 구독 스트림: sse:* 모든 채널 수신 */
    @PostConstruct
    public void subscribeRedis() {
        redis.listenToPattern(TOPIC_PREFIX + "*")
            .doOnSubscribe(s -> log.info("Subscribed Redis pattern '{}*'", TOPIC_PREFIX))
            .flatMap(msg -> {
                String topic = Objects.requireNonNull(msg.getChannel());
                String channel = topic.substring(TOPIC_PREFIX.length());
                String payload = msg.getMessage();

                try {
                    EventMessage event = om.readValue(payload, EventMessage.class);
                    var broadcaster = channels.computeIfAbsent(channel, k -> new ChannelBroadcaster());
                    broadcaster.emit(event);
                } catch (Exception e) {
                    log.error("Failed to decode message: {}", payload, e);
                }
                return Flux.empty();
            })
            .onErrorContinue((t, v) -> log.error("Redis listener error: {}", v, t))
            .subscribe();
    }

    /** 외부 발행 → Redis Pub/Sub publish */
    public Publisher<Long> publish(String channel, EventMessage message) {
        String topic = TOPIC_PREFIX + channel;
        try {
            String json = om.writeValueAsString(message);
            return redis.convertAndSend(topic, json);
        } catch (JsonProcessingException e) {
            return Flux.error(e);
        }
    }

    /** SSE 구독 스트림 (channel별) */
    public Flux<EventMessage> subscribe(String channel) {
        var broadcaster = channels.computeIfAbsent(channel, k -> new ChannelBroadcaster());
        return broadcaster.flux()
            // 클라이언트 연결 유지용 주기적 하트비트(코멘트 이벤트로 보낼 예정이라 data엔 영향 없음)
            .mergeWith(Flux.interval(HEARTBEAT_INTERVAL).map(i ->
                EventMessage.builder()
                    .id("hb-" + i)
                    .type(Type.HEARTBEAT)
                    .payload("keepalive")
                    .ts(System.currentTimeMillis())
                    .build()
            ))
            .doOnSubscribe(s -> {
                int count = broadcaster.inc();
                log.info("SSE subscribed: channel={}, subs={}", channel, count);
            })
            .doFinally(signal -> {
                int count = broadcaster.dec();
                log.info("SSE disconnected({}): channel={}, subs={}", signal, channel, count);
                if (count <= 0) {
                    // 구독자 0명이면 메모리 회수
                    channels.remove(channel);
                    log.info("Channel '{}' removed (no subscribers).", channel);
                }
            });
    }

    /** 채널 단위 멀티캐스트 sink + 구독자 수 카운팅 */
    static class ChannelBroadcaster {
        private final Sinks.Many<EventMessage> sink =
            Sinks.many().multicast().onBackpressureBuffer(1024, false);
        private final AtomicInteger subs = new AtomicInteger(0);

        public void emit(EventMessage event) {
            sink.tryEmitNext(event);
        }

        public Flux<EventMessage> flux() {
            return sink.asFlux();
        }

        public int inc() { return subs.incrementAndGet(); }

        public int dec() { return subs.decrementAndGet(); }
    }

    public enum Type {
        CHAT, NOTICE, SYSTEM, HEARTBEAT
    }

    @Builder
    public record EventMessage(String id, Type type, String payload, long ts) {}
}
