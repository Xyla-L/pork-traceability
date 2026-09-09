package com.pork.auth.service;

import com.pork.auth.dto.SystemRequests;
import com.pork.auth.entity.SysOrg;
import com.pork.auth.entity.SysUser;
import com.pork.core.result.PageResult;
import java.util.List;
import java.util.Map;

public interface SystemAdminService {
    PageResult<SysUser> users(String username, String realName, String role, Long orgId, Integer status, long pageNum, long pageSize);
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
}
