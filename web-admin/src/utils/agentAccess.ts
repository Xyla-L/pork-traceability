/**
 * 智能助手的使用角色。
 *
 * 现阶段只开给管理员：工具与事实口径还在迭代，一线岗位看到一条答得不完整的话，
 * 比看不到更糟。放开时改这一行即可——后端 `agent.access.roles` 也要同步改，
 * 两边都改才算真的放开（前端只管入口，后端才是闸门）。
 */
export const AGENT_ROLES: string[] = ['ADMIN']

export function agentEnabled(role?: string | null): boolean {
  return !!role && AGENT_ROLES.includes(role)
}
