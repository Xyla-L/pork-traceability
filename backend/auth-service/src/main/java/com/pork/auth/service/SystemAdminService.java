package com.pork.auth.service;

import com.pork.auth.dto.SystemRequests;
import com.pork.auth.entity.SysOrg;
import com.pork.auth.entity.SysUser;
import com.pork.core.result.PageResult;
import java.util.List;
import java.util.Map;

public interface SystemAdminService {
    PageResult<SysUser> users(String username, String realName, String role, Long orgId, String orgName, Integer status, long pageNum, long pageSize);
    SysUser user(Long id);
    SysUser createUser(SystemRequests.UserCreate request);
    void updateUser(Long id, SystemRequests.UserUpdate request);
    void deleteUser(Long id);
    void resetPassword(Long id);
    void setStatus(Long id, Integer status);
    List<Map<String, Object>> orgTree();
    SysOrg createOrg(SystemRequests.OrgCreate request);
    void updateOrg(Long id, SystemRequests.OrgCreate request);
    void deleteOrg(Long id);

    /**
     * 按 ID 查单个机构。
     * <p>
     * 供业务服务做「这个门店是不是真的存在」的核对 —— 订单/计划的目的地门店必须来自机构树，
     * 查不到就要转人工，绝不能默认放行（餐饮门店的召回圈范围全靠这个 ID 圈）。
     * 查不到返回 {@code null}，由调用方决定是转人工还是报错。
     */
    SysOrg org(Long id);

    /** 业务档案变更后同步机构（按 type+name 判重，幂等）。返回机构 ID。 */
    Long syncOrg(SystemRequests.SyncOrg request);

    /** 业务档案删除后移除对应机构；机构有下级或已关联用户时不动，返回是否真的删了。 */
    boolean removeOrgByName(String type, String name);

    /**
     * 按角色 / 机构查「活跃用户」的收件人清单（内部接口，供业务服务发通知用）。
     * <p>
     * 只回 {@code status=1} 的账号：给离职账号发召回通知等于没发。
     * role 与 orgIds 都可为空：为空表示不限。orgIds 为空字符串元素一律忽略。
     */
    List<Map<String, Object>> internalRecipients(String role, List<Long> orgIds);
}
