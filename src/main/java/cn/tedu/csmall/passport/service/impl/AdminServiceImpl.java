package cn.tedu.csmall.passport.service.impl;

import cn.tedu.csmall.passport.ex.ServiceException;
import cn.tedu.csmall.passport.mapper.AdminMapper;
import cn.tedu.csmall.passport.mapper.AdminRoleMapper;
import cn.tedu.csmall.passport.pojo.dto.AdminAddNewDTO;
import cn.tedu.csmall.passport.pojo.dto.AdminLoginDTO;
import cn.tedu.csmall.passport.pojo.entity.Admin;
import cn.tedu.csmall.passport.pojo.entity.AdminRole;
import cn.tedu.csmall.passport.pojo.vo.AdminListItemVO;
import cn.tedu.csmall.passport.pojo.vo.AdminStandardVO;
import cn.tedu.csmall.passport.security.AdminDetails;
import cn.tedu.csmall.passport.service.IAdminService;
import cn.tedu.csmall.passport.web.ServiceCode;
import com.alibaba.fastjson.JSON;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理员模块的实现类
 *
 * @Author java.@Wqy
 * @Version 0.0.1
 */
@Slf4j
@Service
public class AdminServiceImpl implements IAdminService {

    // 读取配置文件application-dev.yml中的自定义配置
    @Value("${csmall.jwt.secret-key}")
    private String secretKey;
    @Value("${csmall.jwt.duration-in-minute}")
    private long durationInMinute;

    // 注入管理员表的Mapper
    @Autowired
    private AdminMapper adminMapper;

    // 注入关联表的mapper,向该表中批量插入数据(一个管理员可能对应多个角色,故批量)
    @Autowired
    private AdminRoleMapper adminRoleMapper;

    // 注入认证信息接口对象
    @Autowired
    private AuthenticationManager authenticationManager;

    public AdminServiceImpl() {
        log.info("创建业务对象：AdminServiceImpl");
    }

    /**
     * 管理员登录的业务
     *
     * @param adminLoginDTO 封装了管理员的用户名和密码
     */
    @Override
    public String login(AdminLoginDTO adminLoginDTO) {
        log.debug("开始处理[管理员登录]的业务,参数:{}", adminLoginDTO);

        // 1.处理认证
        //创建一个认证器,实例化UsernamePasswordAuthenticationToken类,传入需要认证的用户名和密码
        Authentication authentication
                = new UsernamePasswordAuthenticationToken(
                adminLoginDTO.getUsername(), adminLoginDTO.getPassword()
        );
        /* 【开始认证】
            会通过参数中的"用户名"来调用UserDetailsService接口类型的对象的loadUserByUsername()方法，
            当得到此方法的返回结果后，自动执行后续的判断,错误时抛出Spring Security框架对应的异常(例如密码是否匹配,账号是否被禁用)
            认证通过后会返回Authentication接口类型对象,包含(Principal（当事人）、Credentials（凭证）、Authorities（权限清单）)
         */
        Authentication authenticateResult
                = authenticationManager.authenticate(authentication);
        // 利用SecurityContextHolder获取上下文,并设置认证器中要认证的信息,保存到服务端的Session中(不推荐)
//        SecurityContextHolder.getContext().setAuthentication(authenticateResult);
        log.debug("认证通过,认证管理器返回:{}", authenticateResult);

        // 2.【认证成功后,从认证结果中获取当事人对象,将用于生成JWT】
        Object principal = authenticateResult.getPrincipal();// 获取认证的当事人对象Principal
        log.debug("认证结果中的当事人类型:{}", principal.getClass().getName());// cn.tedu.csmall.passport.security.AdminDetails
        AdminDetails adminDetails = (AdminDetails) principal;// 强转成AdminDetails当事人对象,这里的对象就是向SpringSecurity框架返回的UserDetails

        // 3.【获取认证结果】
        String username = adminDetails.getUsername();// 获取认证结果的用户名
        Long id = adminDetails.getId();// 获取认证结果的id
        Collection<GrantedAuthority> authorities = adminDetails.getAuthorities();// 获取认证结果的权限信息,Granted Authorities=[?,?...]
        String authoritiesJsonString = JSON.toJSONString(authorities);// ★将权限信息转换成JSON字符串,authoritiesJsonString:[{"authority":"?"},{"authority":"?"}...]

        // 生成JWT数据前需要将数据填充到JWT中
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("id", id);
        claims.put("authoritiesJsonString", authoritiesJsonString);
        log.debug("向JWT中存入用户名:{}", username);
        log.debug("向JWT中存入id:{}", id);
        log.debug("向JWT中存入authoritiesJsonString:{}", authoritiesJsonString);

        // 4.【生成JWT数据】----以下是生成JWT的固定代码
        Date date = new Date(System.currentTimeMillis() + durationInMinute * 60 * 1000L);
        String jwt = Jwts.builder() // 构建者模式
                // Header
                .setHeaderParam("alg", "HS256") // 指定算法
                .setHeaderParam("trp", "JWT") // 指定类型
                // Payload 载荷
                .setClaims(claims)// 传入数据Map
                // Signature 签名
                .setExpiration(date)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
        log.debug("生成的JWT：{}", jwt);
        return jwt;// 最终返回认证后的JWT
    }

    /**
     * 添加管理员的业务
     *
     * @param adminAddNewDTO 接收客户端的DTO对象
     */
    @Override
    public void adNew(AdminAddNewDTO adminAddNewDTO) {
        log.debug("开始处理添加[添加管理员]的业务!,参数：{}", adminAddNewDTO);

        log.debug("即将检查用户名是否被占用……");
        int selectByUsername = adminMapper.countByUsername(adminAddNewDTO.getUsername());
        if (selectByUsername > 0) {
            String message = "添加管理员失败,用户名[" + adminAddNewDTO.getUsername() + "]已经被占用!";
            log.debug(message);
            throw new ServiceException(ServiceCode.ERROR_CONFLICT, message);
        }

        log.debug("即将检查手机号码是否被占用……");
        int selectByPhone = adminMapper.countByPhone(adminAddNewDTO.getPhone());
        if (selectByPhone > 0) {
            String message = "添加管理员失败,手机号[" + adminAddNewDTO.getPhone() + "]已经被占用!";
            log.debug(message);
            throw new ServiceException(ServiceCode.ERROR_CONFLICT, message);
        }

        log.debug("即将检查电子邮箱是否被占用……");
        int selectByEmail = adminMapper.countByEmail(adminAddNewDTO.getEmail());
        if (selectByEmail > 0) {
            String message = "添加管理员失败,电子邮箱[" + adminAddNewDTO.getEmail() + "]已经被占用!";
            log.debug(message);
            throw new ServiceException(ServiceCode.ERROR_CONFLICT, message);
        }

        // 创建Admin对象
        Admin admin = new Admin();
        // 通过BeanUtils.copyProperties()方法将参数对象的各属性值复制到Admin对象中
        BeanUtils.copyProperties(adminAddNewDTO, admin);
        // TODO 取出密码，进行加密处理，并将密文封装回Admin对象中
        // 补全Admin对象中的属性值：loginCount >>> 0
        admin.setLoginCount(0);// 设置累计登录次数
        int rows = adminMapper.insert(admin);
        if (rows != 1) {
            String message = "添加管理员失败，服务器忙，请稍后再次尝试！";
            log.debug(message);
            throw new ServiceException(ServiceCode.ERR_INSERT, message);
        }

        /*
         执行插入管理员与角色关联的数据
         条件:
         (1).上次插入管理员后获取的roleIds=[?,?,?]
         (2).选择的角色对应的id数组
         */
        Long[] roleIds = adminAddNewDTO.getRoleIds();// 获取客户端选择角色传入的多个角色roleIds
        AdminRole[] adminRoles = new AdminRole[roleIds.length];// 创建一个角色管理员的引用数组,长度为用户选择的数量
        LocalDateTime now = LocalDateTime.now();// 获取当前的时间
        for (int i = 0; i < roleIds.length; i++) { // 遍历等长的数组向角色管理员关联表中插入数据(一个管理员对应多个角色)
            AdminRole adminRole = new AdminRole();// 创建角色管理员对象
            adminRole.setAdminId(admin.getId());// 设置插入后的管理员id
            adminRole.setRoleId(roleIds[i]);// 设置该管理员id下选择的所有角色id
            adminRole.setGmtCreate(now); // 设置当前时间
            adminRole.setGmtModified(now);
            adminRoles[i] = adminRole;// 将封装好的角色管理员对象放到AdminRole[]数组中
        }
        rows = adminRoleMapper.insertBatch(adminRoles);// 调用批量插入关联表的方法,传入要插入的管理员角色对象
        if (rows != roleIds.length) { // 判断如果影响的行数与插入的结果不一致时,抛出异常
            String message = "添加管理员失败，服务器忙，请稍后再次尝试！";
            log.debug(message);
            throw new ServiceException(ServiceCode.ERR_INSERT, message);
        }
    }

    /**
     * 删除管理员的功能
     * (1).判断id是否为1
     * (2).判断id下有无数据
     *
     * @param id 要删除的管理员id
     */
    @Override
    public void delete(Long id) {
        log.debug("开始处理[删除管理员]的业务,参数{}", id);
        // id值为1的管理不允许被删除
        if (id == 1) {
            String message = "删除失败,管理员数据不存在!";
            log.debug(message);
            throw new ServiceException(ServiceCode.ERR_DELETE, message);
        }
        // 判断该id是否存在
        AdminStandardVO admin = adminMapper.getStandardById(id);
        if (admin == null) {
            String message = "删除失败，该管理员id不存在！";
            log.debug(message);
            throw new ServiceException(ServiceCode.ERR_NOT_FOUND, message);
        }

        int rows = adminMapper.deleteById(id);
        if (rows != 1) {
            String message = "删除管理员失败，服务器忙，请稍后再尝试！";
            log.debug(message);
            throw new ServiceException(ServiceCode.ERR_DELETE, message);
        }

        // 删除管理员与角色的关联数据(★因为添加了事务注解,若此处发生异常,则会发生事务回滚)
        rows = adminRoleMapper.deleteByAdminId(id);
        if (rows < 1) { // 若影响的行数小于1,抛出删除异常
            String message = "删除管理员失败，服务器忙，请稍后再尝试！";
            log.debug(message);
            throw new ServiceException(ServiceCode.ERR_DELETE, message);
        }
    }

    /**
     * 实现查询管理员列表的方法
     *
     * @return List
     */
    @Override
    public List<AdminListItemVO> list() {
        log.debug("开始处理[查询管理员列表]的业务!");
        List<AdminListItemVO> list = adminMapper.list();
        Iterator<AdminListItemVO> iterator = list.iterator();
        while (iterator.hasNext()) { // 判断是否含有下一个元素
            AdminListItemVO item = iterator.next();// 获取下一个元素
            if (item.getId() == 1) { // 判断该元素对象的id是否为1
                iterator.remove(); // 如果是,则移除该对象
                break; // 跳出分支
            }
        }
        return list;
    }

    /**
     * 处理启用管理员的业务
     * 通过调用启用或禁用的方法,传入id和值,
     * (1).判断管理员id是否为1
     * (2).该id下是否有数据
     * (3).enable的值是否不同最终进行设置
     * (4).最终改变的数据是否正确
     *
     * @param id 启用的管理员id
     */
    @Override
    public void setEnable(Long id) {
        updateEnableById(id, 1);// 调用该方法传入id,设置为启用状态
    }

    /**
     * 处理禁用管理员的业务
     *
     * @param id 禁用的管理员id
     */
    @Override
    public void setDisable(Long id) {
        updateEnableById(id, 0);// 调用该方法传入id,设置为禁用状态
    }

    /**
     * 该方法用来处理启用与禁用的逻辑
     *
     * @param id     id
     * @param enable 是否启用或禁用
     */
    private void updateEnableById(Long id, Integer enable) {
        String[] tips = {"禁用", "启用"};
        log.debug("开始处理【{}管理员】的业务，id参数：{}", tips[enable], id);
        // 判断id是否为1(系统管理员)
        if (id == 1) {
            String message = tips[enable] + "管理员失败，尝试访问的数据不存在！";
            log.debug(message);
            throw new ServiceException(ServiceCode.ERR_NOT_FOUND, message);
        }
        // 根据id查询管理员详情
        AdminStandardVO queryResult = adminMapper.getStandardById(id);
        if (queryResult == null) {
            String message = tips[enable] + "管理员失败,尝试访问的数据不存在！";
            log.debug(message);
            throw new ServiceException(ServiceCode.ERR_NOT_FOUND, message);
        }
        // 判断查询结果中的enable与方法参数enable是否相同
        if (enable.equals(queryResult.getEnable())) {
            String message = tips[enable] + "管理员失败，管理员账号已经处于" + tips[enable] + "状态！";
            log.debug(message);
            throw new ServiceException(ServiceCode.ERROR_CONFLICT, message);
        }
        // 创建admin对象,并封装id和enable这2个属性的值,并进行修改
        Admin admin = new Admin();
        admin.setId(id);
        admin.setEnable(enable);
        int rows = adminMapper.updateById(admin);
        if (rows != 1) {
            String message = tips[enable] + "管理员失败，服务器忙，请稍后再次尝试！";
            throw new ServiceException(ServiceCode.ERR_UPDATE, message);
        }
        log.debug("修改成功!");
    }
}
