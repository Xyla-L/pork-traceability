package com.pork.ai.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分词与 JSON 抽取的单元测试。
 *
 * <p>这两个函数是「模型输出 → 程序可执行」之间的唯一转换层，
 * 它们出错的方式很安静：分词不对只是排序变了，JSON 抽取不对就是静默降级。
 * 所以要单独锁住。</p>
 */
class GraphTextTest {

    @Test
    @DisplayName("中文按 2-gram 切，且保留整段短语")
    void chineseBigram() {
        List<String> t = GraphText.tokens("退货单");
        assertTrue(t.contains("退货"));
        assertTrue(t.contains("货单"));
        assertTrue(t.contains("退货单"), "短词组应保留整体，便于短语命中");
    }

    @Test
    @DisplayName("英文转小写，camelCase 拆子词")
    void englishCamel() {
        List<String> t = GraphText.tokens("safeBuy");
        assertTrue(t.contains("safebuy"));
        assertTrue(t.contains("safe"));
        assertTrue(t.contains("buy"));
    }

    @Test
    @DisplayName("混合输入不互相污染")
    void mixed() {
        List<String> t = GraphText.tokens("导出 return-order 数据");
        assertTrue(t.contains("导出"));
        assertTrue(t.contains("return"));
        assertTrue(t.contains("order"));
    }

    @Test
    @DisplayName("路径分词跳过 {id} 占位符")
    void pathTokensSkipPlaceholders() {
        List<String> t = GraphText.pathTokens("/distribution/batches/{id}");
        assertTrue(t.contains("distribution"));
        assertTrue(t.contains("batches"));
        assertFalse(t, "id");
    }

    private void assertFalse(List<String> tokens, String unwanted) {
        assertTrue(tokens.stream().noneMatch(s -> s.equals(unwanted)),
                "不应该包含占位符 " + unwanted + "，实际: " + tokens);
    }

    @Test
    @DisplayName("空输入返回空列表而不是抛异常")
    void blankInput() {
        assertEquals(List.of(), GraphText.tokens(null));
        assertEquals(List.of(), GraphText.tokens("   "));
        assertEquals(List.of(), GraphText.pathTokens(null));
    }

    @Test
    @DisplayName("从模型输出里截 JSON：前面带客套话也能截出来")
    void extractJsonWithPrefix() {
        String raw = "好的，结果如下：\n{\"operation\":\"op:x\",\"slots\":{}}\n希望有帮助";
        assertEquals("{\"operation\":\"op:x\",\"slots\":{}}", SlotFiller.extractJson(raw));
    }

    @Test
    @DisplayName("markdown 代码块也能截")
    void extractJsonFromFence() {
        String raw = "```json\n{\"operation\":null,\"question\":\"不支持\"}\n```";
        assertEquals("{\"operation\":null,\"question\":\"不支持\"}", SlotFiller.extractJson(raw));
    }

    @Test
    @DisplayName("嵌套对象按括号配平截取，不会在第一个 } 处截断")
    void extractJsonNested() {
        String raw = "{\"operation\":\"a\",\"slots\":{\"body\":{\"x\":1}}} 后面还有字";
        assertEquals("{\"operation\":\"a\",\"slots\":{\"body\":{\"x\":1}}}", SlotFiller.extractJson(raw));
    }

    @Test
    @DisplayName("字符串里的花括号不参与配平")
    void extractJsonBracesInString() {
        String raw = "{\"question\":\"格式是 {a} 还是 {b}？\"}";
        assertEquals("{\"question\":\"格式是 {a} 还是 {b}？\"}", SlotFiller.extractJson(raw));
    }

    @Test
    @DisplayName("根本没有 JSON 时返回 null，交给上层降级")
    void extractJsonMissing() {
        assertNull(SlotFiller.extractJson("我不太确定"));
        assertNull(SlotFiller.extractJson(null));
        assertNull(SlotFiller.extractJson("{\"unclosed\":"));
    }
}
