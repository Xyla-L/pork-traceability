package com.pork.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChainEventPublisher {
    private final ApplicationEventPublisher events;

    public void publish(ChainTxMessage message) {
        events.publishEvent(message);
    }
}
