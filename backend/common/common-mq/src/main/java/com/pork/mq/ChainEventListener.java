package com.pork.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChainEventListener {
    private final RabbitTemplate rabbitTemplate;
    private final RetryTemplate chainPublishRetryTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void send(ChainTxMessage message) {
        chainPublishRetryTemplate.execute(context -> rabbitTemplate.invoke(operations -> {
            operations.convertAndSend(ChainMqConstants.EXCHANGE, ChainMqConstants.ROUTING_KEY, message);
            operations.waitForConfirmsOrDie(5_000);
            return null;
        }));
    }
}
