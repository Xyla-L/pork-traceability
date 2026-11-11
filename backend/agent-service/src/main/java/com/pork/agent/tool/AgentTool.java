package com.pork.agent.tool;

import com.pork.agent.support.AgentContext;

import java.util.List;
import java.util.Map;

/**
 * 只读工具。
 * <p>
 * <b>登记方式是描述式的</b>：每个工具自己说清「我能回答什么问题」和「我从哪取数」，
 * 新增能力只需再实现一个类并注册，<b>不要去改对话主流程</b>。
 * 这是为了让「后续加冷链退货这类功能」只影响一处——加一个工具 + 补一段状态映射，
 * 而不是回去改 if-else 长链。
 * <p>
 * 没有写方法，也不给模型调用业务的权限：模型只拿到 {@link ToolResult} 的文本。
 */
public interface AgentTool {

    /** 唯一名，落在 agent_message.tool 上 */
    String name();

    /** 中文来源名，展示给用户 */
    String label();

    /**
     * 一句话说明「我管哪些问题」，供模型兜底分诊时挑选。
     * <p>
     * 关键词路由命不中的问法（换了说法、追问指代）会由模型在这份目录里挑一个；
     * 描述要写成业务人员能看懂的样子，不写表名接口名。
     */
    default String description() {
        return label();
    }

    /** 优先级：数值小的先匹配，用于让更特异的工具先认领问题 */
    default int priority() {
        return 100;
    }

    /**
     * 允许使用本工具的角色（ADMIN / SUPERVISOR / FARMER / SLAUGHTER_OP / DISTRIBUTOR / RETAILER）。
     * <p>
     * 空列表＝全体。这是<b>能力边界</b>，不是数据过滤：报举报台账的工具不给养殖户，
     * 不是因为他的数据被裁掉，而是这件事根本不该由他在这里看。真正的数据圈定仍在各业务服务的
     * {@code OrgScope}/{@code FarmScope} 里，两者叠加才算完整。
     */
    default List<String> roles() {
        return List.of();
    }

    /**
     * 当前角色能不能用：没配白名单＝全体；角色为空（匿名/内部调用）时只给全体工具。
     * <p>
     * 空角色按最小权限处理——拿不到身份却给了专项能力，等于绕开角色收窄。
     */
    default boolean allows(String role) {
        List<String> allowed = roles();
        if (allowed.isEmpty()) return true;
        if (role == null || role.isBlank()) return false;
        return allowed.contains(role.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /** 空态里的一句话说明（「这个角色能用它做什么」），不写表名字段名 */
    default String hint() {
        return label();
    }

    /** 这个问题归不归我管（描述式判断，不做流程编排） */
    boolean supports(String question);

    /** 从问题里抽参数；抽不到返回空 Map，由 {@link #execute} 给出「缺什么」的说明 */
    Map<String, String> arguments(String question);

    /** 执行取数。允许抛 BusinessException，上层会转成人话 */
    ToolResult execute(Map<String, String> args, AgentContext ctx);
}
