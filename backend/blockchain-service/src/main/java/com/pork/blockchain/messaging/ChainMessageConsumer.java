package com.pork.blockchain.messaging;

import com.pork.blockchain.service.BlockchainService;
import com.pork.mq.ChainMqConstants;
import com.pork.mq.ChainTxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChainMessageConsumer {
    private final BlockchainService service;

    @RabbitListener(queues = ChainMqConstants.QUEUE)
    public void consume(ChainTxMessage message) {
        service.store(message);
    }
}
