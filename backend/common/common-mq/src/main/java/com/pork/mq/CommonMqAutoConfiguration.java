package com.pork.mq;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Registers the shared chain-event infrastructure for services that depend on common-mq.
 */
@AutoConfiguration
@Import({ChainMqConfiguration.class, ChainEventPublisher.class, ChainEventListener.class})
public class CommonMqAutoConfiguration {
}
