package com.myagent.system.auth.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myagent.system.auth.dao.entity.SysUserRoleDO;

/** 用户-平台角色关联 Mapper，供后续管理后台 CRUD 使用；权限决策走 PolicyDecisionService（JdbcTemplate）。 */
public interface SysUserRoleMapper extends BaseMapper<SysUserRoleDO> {
}
