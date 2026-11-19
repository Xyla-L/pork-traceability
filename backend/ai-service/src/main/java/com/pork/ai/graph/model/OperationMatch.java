package com.pork.ai.graph.model;

import java.util.List;

/**
 * 一次检索命中。
 *
 * {@code hits} 保留命中的词，不是为了好看 —— 排查「为什么它选了个错的接口」时，
 * 唯一能看的就是这个：词命中了什么、分值多少。没有它，检索就是个黑盒，
 * 调 Prompt 也调不动（因为问题根本不在 Prompt）。
 */
public record OperationMatch(ApiOperation operation, double score, List<String> hits) {
}
