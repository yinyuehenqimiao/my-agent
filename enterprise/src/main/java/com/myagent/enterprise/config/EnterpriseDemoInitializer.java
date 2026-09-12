package com.myagent.enterprise.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.myagent.enterprise.dao.mapper.EnterpriseMapper;
import com.myagent.system.user.dao.entity.UserDO;
import com.myagent.system.user.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name={"myagent.enterprise.enabled","myagent.enterprise.demo-enabled"}, havingValue="true")
public class EnterpriseDemoInitializer implements ApplicationRunner {
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final EnterpriseMapper db;
    @Override @Transactional public void run(ApplicationArguments args) {
        String manager = user("manager", "演示主管");
        String hr = user("hr", "演示人事");
        String employee = user("employee", "演示员工");
        employee(manager,"演示主管","研发部","MANAGER",null,hr);
        employee(hr,"演示人事","人事部","HR",manager,null);
        employee(employee,"演示员工","研发部","EMPLOYEE",manager,hr);
    }
    private String user(String username,String name) {
        UserDO u = users.selectOne(Wrappers.lambdaQuery(UserDO.class).eq(UserDO::getUsername,username));
        if (u == null) {
            u = new UserDO(); u.setUsername(username); u.setNickname(name); u.setRole("user");
            u.setPassword(passwords.encode("Enterprise123!")); u.setDeleted(0);
            u.setCreateTime(LocalDateTime.now()); u.setUpdateTime(LocalDateTime.now()); users.insert(u);
        }
        return String.valueOf(u.getId());
    }
    private void employee(String id,String name,String department,String role,String manager,String hr) {
        if (db.employee(id,false) == null) db.update("INSERT INTO ent_employee(user_id,display_name,department,business_role,manager_id,hr_id,active) VALUES(?,?,?,?,?,?,TRUE)",id,name,department,role,manager,hr);
    }
}
