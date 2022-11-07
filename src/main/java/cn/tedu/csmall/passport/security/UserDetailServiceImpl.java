package cn.tedu.csmall.passport.security;

import cn.tedu.csmall.passport.mapper.AdminMapper;
import cn.tedu.csmall.passport.pojo.vo.AdminLoginInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 基于Spring Security的登录认证创建的用户登录的(UserDetailsService接口)实现类
 * 最终返回UserDetails,以便进行认证
 *
 * @java.@Wqy
 * @Version 0.0.1
 */
@Slf4j
@Service
public class UserDetailServiceImpl implements UserDetailsService {

    // 注入AdminMapper
    @Autowired
    private AdminMapper adminMapper;

    // 重写loadUserByUsername(String s)"根据用户名加载用户的方法",Spring Security会自动传入用户名进行处理
    @Override
    public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {
        log.debug("Spring Security框架自动调用UserDetailServiceImpl中的loadUserByUsername方法,参数{}", s);

        AdminLoginInfoVO admin = adminMapper.getLoginInfoByUsername(s);//根据传入的用户名向数据库查询数据
        log.debug("从数据库中根据用户名[{}]查询管理员信息,结果:{}", s, admin);

        if (admin == null) {// 判断查询的admin是否为Null?
            log.debug("没有与用户名[{}]匹配的用户信息,即将抛出异常", s);
            String message = "登录失败,用户名不存在!";
            throw new BadCredentialsException(message);// Spring Security提供的异常
        }

        // Spring Security框架会内置一个User对象(UserDetails接口的实现)来处理登录的用户名和密码等一系列机制
        UserDetails userDetails = User.builder() //盗用builder()方法来构建登录的信息
                .username(admin.getUsername()) // 用户名
                .password(admin.getPassword()) // 根据配置类返回的解码器来对应不同类型的密码,这里使用BCrypt编译器,就必须使用该算法加密后的结果
                .disabled(admin.getEnable() == 0) // 是否禁用和启用enable
                .accountLocked(false) // 此项目为设计"账号锁定"的机制,固定false
                .accountExpired(false) // 此项目为设计"账号过期"的机制,固定false
                .credentialsExpired(false) // 此项目为设计"凭证锁定"的机制,固定false
                .authorities("暂时给出的假的权限标识") // 权限
                .build();
        log.debug("即将向Spring Security框架返回UserDetail对象:{}", userDetails);
        return userDetails; // 最终返回登录认证所需要的信息
    }
}
