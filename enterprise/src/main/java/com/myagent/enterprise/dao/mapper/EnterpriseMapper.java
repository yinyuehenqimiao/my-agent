package com.myagent.enterprise.dao.mapper;

import com.myagent.enterprise.dto.EnterpriseTypes.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

/** Parameterized SQL only. Row locks are acquired by transactional application services. */
@Repository
@RequiredArgsConstructor
public class EnterpriseMapper {
    private final JdbcTemplate jdbc;
    public <T> List<T> list(Class<T> type, String sql, Object... args) {
        return jdbc.query(sql, new DataClassRowMapper<>(type), args);
    }
    public <T> T one(Class<T> type, String sql, Object... args) {
        List<T> rows = list(type, sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public int update(String sql, Object... args) { return jdbc.update(sql, args); }
    public boolean exists(String sql, Object... args) { return !jdbc.queryForList(sql, args).isEmpty(); }
    public Employee employee(String id, boolean lock) {
        return one(Employee.class, "SELECT * FROM ent_employee WHERE user_id=?" + (lock ? " FOR UPDATE" : ""), id);
    }
    public LeaveRequest request(String id, boolean lock) {
        return one(LeaveRequest.class, "SELECT * FROM ent_leave_request WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    }
    public Action action(String id, boolean lock) {
        return one(Action.class, "SELECT * FROM ent_action WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    }
}
