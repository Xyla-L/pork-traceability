package com.pork.auth.controller;

import com.pork.auth.dto.SystemRequests;
import com.pork.auth.entity.SysOrg;
import com.pork.auth.entity.SysUser;
import com.pork.auth.service.SystemAdminService;
import com.pork.core.query.PageQuery;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {
    private final SystemAdminService service;

    @GetMapping("/users")
    public Result<PageResult<SysUser>> users(String username, String realName, String role, Long orgId, Integer status, @Valid PageQuery page) {
        return Result.success(service.users(username, realName, role, orgId, status, page.getPageNum(), page.getPageSize()));
    }
    @GetMapping("/users/{id}") public Result<SysUser> user(@PathVariable Long id) { return Result.success(service.user(id)); }
    @PostMapping("/users") public Result<SysUser> createUser(@Valid @RequestBody SystemRequests.UserCreate request) { return Result.success(service.createUser(request)); }
    @PutMapping("/users/{id}") public Result<Void> updateUser(@PathVariable Long id, @Valid @RequestBody SystemRequests.UserUpdate request) { service.updateUser(id, request); return Result.success(); }
    @DeleteMapping("/users/{id}") public Result<Void> deleteUser(@PathVariable Long id) { service.deleteUser(id); return Result.success(); }
    @PutMapping("/users/{id}/reset-password") public Result<Void> reset(@PathVariable Long id) { service.resetPassword(id); return Result.success(); }
    @PutMapping("/users/{id}/status") public Result<Void> status(@PathVariable Long id, @Valid @RequestBody SystemRequests.Status request) { service.setStatus(id, request.status()); return Result.success(); }

    @GetMapping("/orgs/tree") public Result<List<Map<String, Object>>> tree() { return Result.success(service.orgTree()); }
    @PostMapping("/orgs") public Result<SysOrg> createOrg(@Valid @RequestBody SystemRequests.OrgCreate request) { return Result.success(service.createOrg(request)); }
    @PutMapping("/orgs/{id}") public Result<Void> updateOrg(@PathVariable Long id, @Valid @RequestBody SystemRequests.OrgCreate request) { service.updateOrg(id, request); return Result.success(); }
    @DeleteMapping("/orgs/{id}") public Result<Void> deleteOrg(@PathVariable Long id) { service.deleteOrg(id); return Result.success(); }
}
