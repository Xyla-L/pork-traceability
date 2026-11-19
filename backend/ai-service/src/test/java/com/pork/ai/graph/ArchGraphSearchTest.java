package com.pork.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.ai.config.GraphProperties;
import com.pork.ai.graph.model.ApiOperation;
import com.pork.ai.graph.model.OperationMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图谱检索的回归测试。
 *
 * <p>为什么要锁死这几条：检索是整个链路的第一环，它错了后面全错，
 * 而且错得很隐蔽 —— 模型会「礼貌地」挑一个不相干的接口，
 * 界面上看起来一切正常。这几个用例覆盖的是最容易被问到的几类说法。</p>
 */
class ArchGraphSearchTest {

    private ArchGraphService service;

    @BeforeEach
    void setUp() {
        GraphProperties props = new GraphProperties();
        props.setSeedResource("arch-graph-seed.json");
        props.setMaxCandidates(8);
        props.setMinScore(0.12);
        props.getSynonyms().add(new GraphProperties.Synonym(List.of("养殖场"), List.of("farm", "farms")));
        props.getSynonyms().add(new GraphProperties.Synonym(List.of("批次"), List.of("batch", "batches")));
        props.getSynonyms().add(new GraphProperties.Synonym(List.of("检疫"), List.of("quarantine", "cert")));
        props.getSynonyms().add(new GraphProperties.Synonym(List.of("门店"), List.of("store")));
        props.getSynonyms().add(new GraphProperties.Synonym(List.of("零售"), List.of("retail", "sale")));
        props.getSynonyms().add(new GraphProperties.Synonym(List.of("导出"), List.of("export")));
        props.getStopwords().add("帮我");
        props.getStopwords().add("一下");

        // 不传 WebClient / Collector：这套测试只验检索，不拉 OpenAPI
        service = new ArchGraphService(props, new ObjectMapper(), null, null);
        service.init();
    }

    @Test
    @DisplayName("种子文件能被加载成图谱")
    void seedIsLoaded() {
        assertTrue(service.snapshot().operations().size() > 100,
                "种子应该包含全部 Controller 的接口，实际 " + service.snapshot().operations().size());
        assertTrue(service.snapshot().modules().size() > 20);
    }

    @Test
    @DisplayName("种子里的服务间接口必须全部标记 internal")
    void internalFlagIsConsistent() {
        List<String> leaks = service.snapshot().operations().stream()
                .filter(o -> o.path().toLowerCase().contains("/internal"))
                .filter(o -> !o.internal())
                .map(ApiOperation::id)
                .toList();
        assertTrue(leaks.isEmpty(), "路径含 /internal 却没标 internal（等于对外开放）: " + leaks);
    }

    @Test
    @DisplayName("「查一下所有养殖场」应命中 farms 查询而不是创建")
    void findFarmList() {
        List<OperationMatch> matches = service.search("查一下所有养殖场", 5);
        assertFalse(matches.isEmpty(), "应该有候选");

        ApiOperation top = matches.get(0).operation();
        assertTrue(top.path().contains("/farms"), "实际命中: " + top.method() + " " + top.path());
        assertTrue("GET".equals(top.method()), "查询意图不该命中写接口，实际: " + top.method());
    }

    @Test
    @DisplayName("「新建养殖场」应命中 POST /farms")
    void createFarm() {
        List<OperationMatch> matches = service.search("帮我新建一个养殖场", 5);
        assertFalse(matches.isEmpty());
        ApiOperation top = matches.get(0).operation();
        assertTrue(top.path().contains("/farms"), "实际命中: " + top.method() + " " + top.path());
        assertTrue("POST".equals(top.method()), "实际: " + top.method());
    }

    @Test
    @DisplayName("删除意图应该命中 DELETE，而不是同路径的 GET")
    void deleteIntentPrefersDelete() {
        List<OperationMatch> matches = service.search("删掉这个养殖场", 5);
        assertFalse(matches.isEmpty());
        assertTrue(matches.stream().anyMatch(m -> "DELETE".equals(m.operation().method())),
                "候选里应该出现 DELETE 接口");
        assertTrue("DELETE".equals(matches.get(0).operation().method()),
                "第一名应该是 DELETE，实际: " + matches.get(0).operation().method()
                        + " " + matches.get(0).operation().path());
    }

    @Test
    @DisplayName("完全不相干的话不应该硬凑一个接口")
    void irrelevantQueryScoresLow() {
        List<OperationMatch> matches = service.search("今天天气怎么样", 5);
        if (!matches.isEmpty()) {
            assertTrue(matches.get(0).score() < 0.6,
                    "不相干的问法不该出现高分命中: " + matches.get(0).score()
                            + " " + matches.get(0).operation().path());
        }
    }

    @Test
    @DisplayName("内部接口标记为 internal，供执行器拒绝")
    void internalOperationsAreMarked() {
        long internal = service.snapshot().operations().stream()
                .filter(ApiOperation::internal).count();
        assertTrue(internal > 0, "种子里应该识别出内部接口");
    }

    @Test
    @DisplayName("findById 能取回检索命中的接口")
    void findByIdWorks() {
        List<OperationMatch> matches = service.search("养殖场", 3);
        assertFalse(matches.isEmpty());
        String id = matches.get(0).operation().id();
        assertNotNull(service.findById(id));
    }
}
