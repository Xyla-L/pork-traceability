package com.pork.mq;

public final class ChainMqConstants {

    public static final String EXCHANGE = "pork.trace.chain";
    public static final String QUEUE = "pork.trace.chain.store";
    public static final String DEAD_LETTER_EXCHANGE = "pork.trace.chain.dlx";
    public static final String DEAD_LETTER_QUEUE = "pork.trace.chain.store.dlq";
    public static final String ROUTING_KEY = "chain.store";

    private ChainMqConstants() {
    }
}
