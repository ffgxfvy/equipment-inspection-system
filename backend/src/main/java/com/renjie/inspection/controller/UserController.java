package com.renjie.inspection.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.renjie.inspection.common.R;
import com.renjie.inspection.entity.SysUser;
import com.renjie.inspection.mapper.SysUserMapper;
import com.renjie.inspection.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口。
 *
 * <p>本项目只需"查巡检员列表用于分派"这一个功能，因此没有做完整的用户管理模块。
 * 如果需要扩展，参照 EquipmentController 的写法即可。
 */
@Tag(name = "06. 用户", description = "巡检员列表")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final SysUserMapper userMapper;

    @Operation(summary = "巡检员列表（任务分派下拉框用）")
    @RequireRole("ADMIN")
    @GetMapping("/inspectors")
    public R<List<SysUser>> inspectors() {
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, "INSPECTOR")
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        // 不返回密码字段（实体上已有 @JsonIgnore，这里再显式置空一次，避免误改注解后泄漏）
        users.forEach(u -> u.setPassword(null));
        return R.ok(users);
    }
}
