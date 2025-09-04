package com.example.webfluxssedemo.sse;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.One;

class SseControllerTest {

    record Message(long id, String message) {

    }

    @Test
    void sinkTest() {
        Sinks.One<Message> sink = Sinks.one();
        Mono<Message> mono = sink.asMono();

        mono.subscribe(v -> System.out.println("############# 받음: " + v));


        // 어떤 시점에 값 방출
        sink.tryEmitValue(new Message(1, "Hello world"));     // 성공하면 OK, 실패 시 실패코드 리턴
        // 또는: sink.emitValue("hello", Sinks.EmitFailureHandler.FAIL_FAST);
        sink.tryEmitValue(new Message(2, "Hello world"));

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void sinkManyTest() {
        Sinks.Many<Message> sink = Sinks.many().replay().limit(Duration.ofSeconds(2));
        Flux<Message> flux = sink.asFlux();

        flux.subscribe(v -> System.out.println("############# 받음: " + v));


        // 어떤 시점에 값 방출
        sink.tryEmitNext(new Message(1, "Hello world"));     // 성공하면 OK, 실패 시 실패코드 리턴

        try {
            Thread.sleep(5000);
            System.out.println("기다리는 듕");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        sink.tryEmitNext(new Message(2, "Hello world"));
        try {
            Thread.sleep(1000);
            System.out.println("기다리는 듕");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}