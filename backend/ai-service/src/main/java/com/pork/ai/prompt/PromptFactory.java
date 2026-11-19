package com.pork.ai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 双角色 Prompt 工厂。
 *
 * 为什么把 Prompt 放在后端而不是 Dify 里：
 *   实验室那个 Chatflow（Qwen3.8-27B）没有定义自定义输入变量（user_input_form 为空），
 *   没法通过 inputs 传 role 做分流。所以后端负责"组装语境 + 定制任务说明书"，
 *   Dify 只当模型网关用。将来你在 Dify 里加了变量，把这里换成 inputs 即可，接口不用动。
 *
 * 写 Prompt 的三条铁律（也是排查效果不好的顺序）：
 *   1. 先说身份和受众 —— 决定语气。消费者要"人话"，监管要"公文"
 *   2. 再说禁止项 —— 模型最容易犯的错是编造数据里没有的细节
 *   3. 最后给格式 —— 给结构比给形容词有效，"3 条要点"比"简洁一点"管用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptFactory {

    /**
     * 数据太长会拖慢推理、也容易超上下文，超过就截断。
     * 8000 是实测折中：一条全链路记录裁剪后约 4~6 千字，
     * 再算上平台总览，6000 会把后面的内容切掉（踩过：门店流向被切，模型答「查不到」）。
     */
    private static final int MAX_DATA_CHARS = 8000;

    /**
     * 字段名不许漏给用户（踩过：回答里出现「`transportCount`（在途批次）值为 5」）。
     * 模型看见 JSON 键名就原样抄，必须明说「键名不出口」+ 给常用对照，
     * 否则它分不清 transportCount 该说成什么。
     */
    private static final String FIELD_NAMING_RULE = """
                【字段名规则——硬性】
                - 回答里禁止出现 transportCount、qrCode、batchNo 这类 JSON 字段名或英文标识符：
                  数据里的键名只是给你看的，不是给用户看的，一律换成中文说法。
                  常用对照：transportCount=在途批次数、qrCode=溯源码、batchNo=批次号、
                  storeName=门店名称、packageCount=包装数、weight=重量、status=状态、
                  时间戳类=时间。对照里没有的字段，按含义起个中文名，不许照抄原文
                - 数据里的"值"可以是系统内部代码（如 status=4），数值可以引用，
                  但要写成「状态代码 4」这种带中文说明的形式，不要让代码裸奔""";

    private final ObjectMapper objectMapper;

    /**
     * 消费者端：把溯源表格翻译成"让人放心的话"。
     *
     * 痛点来源：普通用户对检疫证号、批次号、哈希值这些没有感知，
     * 看到一堆表格反而更慌。所以这里的任务不是"展示数据"，而是"给结论 + 给理由"。
     */
    public String consumerInterpret(Map<String, Object> data) {
        String json = toJson(data);
        return """
                你是一名耐心、诚实的食品安全科普员，正在为一位普通消费者解读这块猪肉的溯源信息。

                【受众特点】
                对方不是从业者，看不懂检疫证号、批次编号、哈希值这类术语，看到表格会焦虑。
                他真正想知道的只有三件事：这块肉能不能放心吃、它从哪来、出问题能不能找到人。

                【输出要求】
                1. 先用一句话给出结论（例如"这块肉可以放心购买"），再展开说明
                2. 用 3 到 5 条短句讲清楚它经历了哪些环节，每条不超过 30 字
                3. 全程用生活化语言，禁止出现：哈希、上链、批次编号、检测项目代码等术语
                   —— 必须提区块链时，就说"信息已经存证，改不了"
                4. 结尾给一句让人安心的话，但不要夸张承诺（禁止说"绝对安全""百分百"）
                5. 总字数控制在 200 到 300 字，用纯文本，不要 Markdown 标题
                6. 只使用下面数据里真实存在的信息。数据里没写的，就不要提；
                   如果关键信息缺失，直接说明"这项信息暂未记录"，不要猜

                """ + FIELD_NAMING_RULE + """

                【溯源数据】
                %s
                """.formatted(json);
    }

    /**
     * 监管端：把零散的异常数据整理成可直接上报的专业报告。
     *
     * 痛点来源：监管人员要人工翻记录、按产地和原因归类、还要写处置建议，
     * 一条一条整理非常耗时。这里要求模型做的是"结构化归纳"，不是"写作文"。
     */
    public String adminReport(Map<String, Object> data) {
        String json = toJson(data);
        return """
                你是一名生猪产品追溯监管领域的数据分析员，正在为监管部门撰写一份当日监管简报。

                【受众特点】
                读者是有执法权的监管人员，熟悉业务术语，关心的是"哪里有问题、多严重、先处理谁"。
                他们不需要科普，需要能直接写进报告的结论和依据。

                【输出要求】
                严格按以下结构输出 Markdown：

                ## 一、总体情况
                用 2 到 3 句话概括批次总量、异常数量、异常占比。数字必须来自数据。

                ## 二、异常研判
                用 Markdown 表格列出异常项，列为：异常类型 | 涉及数量 | 主要产地/环节 | 风险等级
                风险等级按影响范围判断，用"高/中/低"标注。

                ## 三、处置建议
                按优先级排序，每条要写清楚"做什么 + 针对哪类异常"，可执行、不含糊。

                ## 四、需要持续关注
                指出数据中呈现的趋势或薄弱环节，1 到 2 条即可。

                【硬性约束】
                1. 所有数字、产地、批次必须来自数据，禁止估算或编造
                2. 数据不足以支撑某个结论时，明确写"数据不足，建议补充采集"，不要硬凑
                3. 不要输出与监管无关的内容，不要写客套话
                4. 总量为 0 或数据为空时，直接说明"当前无异常数据"，不要虚构

                """ + FIELD_NAMING_RULE + """

                【监管数据】
                %s
                """.formatted(json);
    }

    /**
     * 消费者自由追问（"这个疫苗打了没""保质期多久"）。
     * 带着上一轮的溯源数据做上下文，避免模型瞎答。
     */
    public String consumerFollowUp(Map<String, Object> data, String question) {
        String json = toJson(data);
        return """
                你是食品安全科普员。下面是一位消费者追问的问题，请结合这块肉的溯源数据回答。

                回答要求：
                1. 先直接回答问题，不要绕
                2. 数据里能查到依据的，说清楚依据；查不到的，明确说"溯源信息里没有记录这项"
                3. 200 字以内，用生活化语言

                """ + FIELD_NAMING_RULE + """

                【消费者的问题】
                %s

                【溯源数据】
                %s
                """.formatted(question == null ? "" : question, json);
    }

    /**
     * 监管端自由问答。
     *
     * 与简报的差别：简报是「固定结构 + 全量数据」，问答是「用户指向 + 相关数据」，
     * 所以这里不规定章节，重点是「不许超出数据说话」——监管场景里编一个数字
     * 比答不出来严重得多。
     */
    public String adminAsk(String question, Map<String, Object> data) {
        String json = toJson(data);
        return """
                你是生猪产品追溯监管领域的数据分析员，正在回答监管人员提出的一个具体问题。

                【回答要求】
                1. 先给结论（一句话），再给依据；依据必须带具体数字、批次号或时间
                2. 只能使用【可用数据】里出现过的内容。数据里没有的，直接说"当前数据里查不到这一项"，
                   不要推测、不要用常识补全、不要编示例数字；也不要用数量做推算或外推
                   （例如"共 20 包、每店 8 条，所以还剩 20 包"这种结论不要给）
                3. 如果数据里写着"未取到任何数据"，只回答"暂时取不到数据，请稍后重试"，
                   不要输出任何分析
                4. 涉及多个对象时用短表格或列表，不要写成大段叙述
                5. 200 到 400 字，Markdown 格式；不要复述问题，不要写客套话
                6. 你只能给"该看什么、该查什么"的建议，不能替监管人员下执法结论
                   （禁止出现"应当处罚""必须查封"这类定性表述）
                7. status、type、isActivated 这类取值是系统内部代码，数值可引用但必须带中文说明
                   （详见下方字段名规则），不要自行翻译成业务含义
                   （例如不要把 status=4 解释成"未售出"），需要说明时统一写"具体含义按系统口径核对"
                8. 数据里标注"还有 N 条未列出"时，如实说明只看到部分记录，不要把已列出的当作全部

                """ + FIELD_NAMING_RULE + """

                【监管人员的问题】
                %s

                【可用数据】
                %s
                """.formatted(question == null ? "" : question, json);
    }

    private String toJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "（暂无数据）";
        }
        try {
            String json = objectMapper.writeValueAsString(data);
            if (json.length() > MAX_DATA_CHARS) {
                log.debug("溯源数据过长({}字符)，截断到 {}", json.length(), MAX_DATA_CHARS);
                return json.substring(0, MAX_DATA_CHARS) + "...（数据过长已截断）";
            }
            return json;
        } catch (Exception e) {
            log.warn("溯源数据序列化失败", e);
            return String.valueOf(data);
        }
    }
}
