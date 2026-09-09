package com.pork.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.auth.dto.SystemRequests;
import com.pork.auth.entity.SysOrg;
import com.pork.auth.entity.SysUser;
import com.pork.auth.enums.RoleEnum;
import com.pork.auth.mapper.SysOrgMapper;
import com.pork.auth.mapper.SysUserMapper;
import com.pork.auth.service.SystemAdminService;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.result.PageResult;
import com.pork.security.util.SM3Util;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SystemAdminServiceImpl implements SystemAdminService {
    private final SysUserMapper userMapper;
    private final SysOrgMapper orgMapper;

    @Value("${security.default-password:${BOOTSTRAP_ADMIN_PASSWORD:ChangeMe123!}}")
    private String defaultPassword;

    @Override
    public PageResult<SysUser> users(String username, String realName, String role, Long orgId, Integer status,
                                     long pageNum, long pageSize) {
        Page<SysUser> page = userMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<SysUser>lambdaQuery()
                .like(StringUtils.hasText(username), SysUser::getUsername, username)
                .like(StringUtils.hasText(realName), SysUser::getRealName, realName)
                .eq(StringUtils.hasText(role), SysUser::getRole, role)
                .eq(orgId != null, SysUser::getOrgId, orgId)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreateTime));
        return PageResult.of(page);
    }

    @Override
    public SysUser user(Long id) { return require(userMapper.selectById(id), "用户不存在"); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser createUser(SystemRequests.UserCreate request) {
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, request.username())) > 0)
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        validateRole(request.role());
        validateOrg(request.orgId());
        SysUser row = new SysUser();
        BeanUtils.copyProperties(request, row);
        String salt = UUID.randomUUID().toString().replace("-", "");
        row.setPasswordSalt(salt);
        row.setPasswordHash(SM3Util.hashWithSalt(request.password(), salt));
        row.setStatus(request.status() == null ? 1 : request.status());
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        userMapper.insert(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long id, SystemRequests.UserUpdate request) {
        SysUser row = user(id);
        if (request.role() != null) validateRole(request.role());
        validateOrg(request.orgId());
        boolean removesActiveAdmin = "ADMIN".equals(row.getRole()) && Integer.valueOf(1).equals(row.getStatus())
                && ((request.role() != null && !"ADMIN".equals(request.role()))
                || Integer.valueOf(0).equals(request.status()));
        if (removesActiveAdmin) protectLastAdmin(row, 0);
        if (request.nickname() != null) row.setNickname(request.nickname());
        if (request.realName() != null) row.setRealName(request.realName());
        if (request.phone() != null) row.setPhone(request.phone());
        if (request.email() != null) row.setEmail(request.email());
        if (request.orgId() != null) row.setOrgId(request.orgId());
        if (request.role() != null) row.setRole(request.role());
        if (request.status() != null) row.setStatus(request.status());
        row.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        SysUser row = user(id);
        protectLastAdmin(row, 0);
        userMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id) {
        SysUser row = user(id);
        String salt = UUID.randomUUID().toString().replace("-", "");
        row.setPasswordSalt(salt);
        row.setPasswordHash(SM3Util.hashWithSalt(defaultPassword, salt));
        row.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setStatus(Long id, Integer status) {
        SysUser row = user(id);
        protectLastAdmin(row, status);
        row.setStatus(status);
        row.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(row);
    }

    @Override
    public List<Map<String, Object>> orgTree() {
        List<SysOrg> orgs = orgMapper.selectList(Wrappers.<SysOrg>lambdaQuery().orderByAsc(SysOrg::getId));
        Map<Long, List<SysOrg>> children = new HashMap<>();
        for (SysOrg org : orgs) children.computeIfAbsent(Objects.requireNonNullElse(org.getParentId(), 0L), key -> new ArrayList<>()).add(org);
        return buildTree(0L, children, new HashSet<>());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysOrg createOrg(SystemRequests.OrgCreate request) {
        validateParent(request.parentId(), null);
        SysOrg row = new SysOrg();
        BeanUtils.copyProperties(request, row);
        row.setParentId(Objects.requireNonNullElse(request.parentId(), 0L));
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        orgMapper.insert(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrg(Long id, SystemRequests.OrgCreate request) {
        SysOrg row = require(orgMapper.selectById(id), "机构不存在");
        validateParent(request.parentId(), id);
        BeanUtils.copyProperties(request, row);
        row.setParentId(Objects.requireNonNullElse(request.parentId(), 0L));
        row.setUpdateTime(LocalDateTime.now());
        orgMapper.updateById(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrg(Long id) {
        require(orgMapper.selectById(id), "机构不存在");
        if (orgMapper.selectCount(Wrappers.<SysOrg>lambdaQuery().eq(SysOrg::getParentId, id)) > 0)
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "机构存在下级节点，不能删除");
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getOrgId, id)) > 0)
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "机构仍有关联用户，不能删除");
        orgMapper.deleteById(id);
    }

    private List<Map<String, Object>> buildTree(Long parent, Map<Long, List<SysOrg>> grouped, Set<Long> path) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (SysOrg org : grouped.getOrDefault(parent, List.of())) {
            if (!path.add(org.getId())) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "机构树存在循环引用");
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", org.getId());
            node.put("parentId", org.getParentId());
            node.put("type", org.getType());
            node.put("name", org.getName());
            node.put("label", org.getName());
            node.put("manager", org.getManager());
            node.put("phone", org.getPhone());
            node.put("address", org.getAddress());
            node.put("remark", org.getRemark());
            node.put("children", buildTree(org.getId(), grouped, new HashSet<>(path)));
            nodes.add(node);
        }
        return nodes;
    }

    private void validateRole(String role) {
        try { RoleEnum.fromCode(role); }
        catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.PARAM_ERROR, "角色编码非法"); }
    }

    private void validateOrg(Long orgId) {
        if (orgId != null && orgMapper.selectById(orgId) == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "所属机构不存在");
    }

    private void validateParent(Long parentId, Long currentId) {
        if (parentId == null || parentId == 0) return;
        if (Objects.equals(parentId, currentId)) throw new BusinessException(ErrorCode.PARAM_ERROR, "机构不能以自身为上级");
        require(orgMapper.selectById(parentId), "上级机构不存在");
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null && cursor != 0) {
            if (!visited.add(cursor) || Objects.equals(cursor, currentId))
                throw new BusinessException(ErrorCode.PARAM_ERROR, "机构上级关系不能形成循环");
            SysOrg parent = orgMapper.selectById(cursor);
            cursor = parent == null ? 0L : parent.getParentId();
        }
    }

    private void protectLastAdmin(SysUser user, int targetStatus) {
        if (targetStatus == 0 && "ADMIN".equals(user.getRole()) && Integer.valueOf(1).equals(user.getStatus())
                && userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getRole, "ADMIN").eq(SysUser::getStatus, 1)) <= 1)
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "必须保留至少一个启用的管理员");
    }

    private <T> T require(T value, String message) {
        if (value == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, message);
        return value;
    }
}
