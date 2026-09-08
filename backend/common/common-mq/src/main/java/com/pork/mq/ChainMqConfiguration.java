package com.pork.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class ChainMqConfiguration {

    @Bean
    DirectExchange chainExchange() {
        return new DirectExchange(ChainMqConstants.EXCHANGE, true, false);
    }

    @Bean
    Queue chainQueue() {
        return QueueBuilder.durable(ChainMqConstants.QUEUE)
                .deadLetterExchange(ChainMqConstants.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(ChainMqConstants.ROUTING_KEY)
                .build();
    }

    @Bean
    Binding chainBinding(Queue chainQueue, DirectExchange chainExchange) {
        return BindingBuilder.bind(chainQueue).to(chainExchange).with(ChainMqConstants.ROUTING_KEY);
    }

    @Bean
    DirectExchange chainDeadLetterExchange() {
        return new DirectExchange(ChainMqConstants.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue chainDeadLetterQueue() {
        return QueueBuilder.durable(ChainMqConstants.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding chainDeadLetterBinding(Queue chainDeadLetterQueue, DirectExchange chainDeadLetterExchange) {
        return BindingBuilder.bind(chainDeadLetterQueue)
                .to(chainDeadLetterExchange)
                .with(ChainMqConstants.ROUTING_KEY);
    }

    @Bean
    MessageConverter chainMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate chainRabbitTemplate(ConnectionFactory connectionFactory, MessageConverter chainMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(chainMessageConverter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    RetryTemplate chainPublishRetryTemplate() {
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(200);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(2_000);
        RetryTemplate retry = new RetryTemplate();
        retry.setRetryPolicy(new SimpleRetryPolicy(3));
        retry.setBackOffPolicy(backOff);
        return retry;
    }
}
