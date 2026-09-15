package com.shop.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.Constants;
import com.shop.common.RedisConstants;
import com.shop.common.Result;
import com.shop.config.properties.*;
import com.shop.dto.Captcha;
import com.shop.dto.EmailRegisterDto;
import com.shop.dto.LoginDTO;
import com.shop.dto.user.LoginUserInfo;
import com.shop.entity.SysConfig;
import com.shop.entity.SysRole;
import com.shop.enums.LoginTypeEnum;
import com.shop.holder.QrLoginHolder;
import com.shop.mapper.SysConfigMapper;
import com.shop.service.AuthService;
import com.shop.entity.SysUser;
import com.shop.enums.MenuTypeEnum;
import com.shop.exception.ServiceException;
import com.shop.mapper.SysMenuMapper;
import com.shop.mapper.SysRoleMapper;
import com.shop.mapper.SysUserMapper;
import com.shop.utils.*;
import com.shop.vo.QrLoginStateVo;
import com.shop.vo.QrLoginVo;
import com.shop.vo.user.SysUserVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.*;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.async.DeferredResult;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {


    private final SysUserMapper userMapper;

    private final SysRoleMapper roleMapper;

    private final SysMenuMapper menuMapper;

    private final EmailUtil emailUtil;

    private final RedisUtil redisUtil;

    private final SysUserMapper sysUserMapper;

    private final String[] avatarList = {
            "https://api.dicebear.com/6.x/pixel-art/svg?seed=Raccoon",
            "https://api.dicebear.com/6.x/pixel-art/svg?seed=Kitty",
            "https://api.dicebear.com/6.x/pixel-art/svg?seed=Puppy",
            "https://api.dicebear.com/6.x/pixel-art/svg?seed=Bunny",
            "https://api.dicebear.com/6.x/pixel-art/svg?seed=Fox"
    };
    private final SysRoleMapper sysRoleMapper;

    private final GiteeConfigProperties giteeConfigProperties;

    private final GithubConfigProperties githubConfigProperties;

    private final QqConfigProperties qqConfigProperties;

    private final WeiboConfigProperties weiboConfigProperties;

    private final WechatProperties wechatProperties;

    private final SysConfigMapper sysConfigMapper;

    private final QrLoginHolder qrLoginHolder;

    /**
     * 二维码边长（像素）
     */
    private static final int QR_CODE_SIZE = 300;

    /**
     * 长轮询单次挂起时长。必须小于 WebMvcConfig 里配置的 async 全局超时（30s），
     * 否则会先被 MVC 判超时、拿到一个不体面的错误响应。前端超时也要比它更长，
     * 见 blog-admin/src/api/system/auth.ts 里 pollQrLoginApi 的 timeout。
     */
    private static final long POLL_TIMEOUT_MS = 25_000L;


    @Override
    public LoginUserInfo login(LoginDTO loginDTO) {

        SysConfig verifySwitch = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, "slider_verify_switch"));
        if (verifySwitch != null && verifySwitch.getConfigValue().equals("Y")) {
            //校验验证码
            CaptchaUtil.checkImageCode(loginDTO.getNonceStr(), loginDTO.getValue());
        }


        // 查询用户
        SysUser user = userMapper.selectByUsername(loginDTO.getUsername());

        //校验是否能够登录
        validateLogin(loginDTO, user);

        // 执行登录
        StpUtil.login(user.getId());
        String tokenValue = StpUtil.getTokenValue();

        // 返回用户信息
        LoginUserInfo loginUserInfo = BeanCopyUtil.copyObj(user, LoginUserInfo.class);
        loginUserInfo.setToken(tokenValue);

        StpUtil.getSession().set(Constants.CURRENT_USER, loginUserInfo);
        return loginUserInfo;
    }

    private static void validateLogin(LoginDTO loginDTO, SysUser user) {
        if (user == null) {
            throw new ServiceException("登录用户不存在");
        }

        // 验证密码
        if (!BCrypt.checkpw(loginDTO.getPassword(), user.getPassword())) {
            throw new ServiceException("用户名或密码错误");
        }

        // 验证状态
        if (user.getStatus() != 1) {
            throw new ServiceException("账号已被禁用");
        }

        if (user.getUsername().equals(Constants.TEST) && loginDTO.getSource().equalsIgnoreCase("PC")) {
            throw new ServiceException("演示用户不允许门户登录！");
        }
    }

    @Override
    public SysUserVo getLoginUserInfo(String source) {
        // 获取当前登录用户ID
        Integer userId = StpUtil.getLoginIdAsInt();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        SysUserVo sysUserVo = BeanCopyUtil.copyObj(user, SysUserVo.class);

        //获取菜单权限列表
        if (source.equalsIgnoreCase(Constants.ADMIN)) {
            List<String> permissions;
            List<String> roles = roleMapper.selectRolesCodeByUserId(userId);
            if (roles.contains(Constants.ADMIN)) {
                permissions = menuMapper.getPermissionList(MenuTypeEnum.BUTTON.getCode());
            } else {
                permissions = menuMapper.getPermissionListByUserId(userId, MenuTypeEnum.BUTTON.getCode());
            }
            sysUserVo.setRoles(roles);
            sysUserVo.setPermissions(permissions);

        }

        return sysUserVo;
    }

    @Override
    public Boolean updateProfile(SysUser user) {
        user.setId(StpUtil.getLoginIdAsInt()); // 设置当前登录用户ID
        user.setUpdateTime(LocalDateTime.now());
        return userMapper.updateById(user) == 1 ;
    }

    // type 1:注册 2:修改
    @Override
    public String sendEmailCode(String email, Integer type) throws MessagingException {
        SysUser sysUser = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email));
        if (sysUser != null && type == 1) {
            throw new ServiceException("当前邮箱已注册！");
        }
        if (sysUser == null && type == 2) {
            throw new ServiceException("当前邮箱未绑定！");
        }
        return emailUtil.sendCode(email);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean register(EmailRegisterDto dto) {

        validateEmailCode(dto);

        SysUser sysUser = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (sysUser != null) {
            throw new ServiceException("账户名已存在");
        }

        //获取随机头像
        String avatar = avatarList[(int) (Math.random() * avatarList.length)];
        sysUser = SysUser.builder()
                .username(dto.getUsername())
                .password(BCrypt.hashpw(dto.getPassword()))
                .nickname(dto.getUsername())
                .email(dto.getEmail())
                .avatar(avatar)
                .status(Constants.YES)
                .loginType(LoginTypeEnum.ACCOUNT.getType())
                .build();
        sysUserMapper.insert(sysUser);

        //添加用户角色信息
        insertRole(sysUser);

        redisUtil.delete(RedisConstants.CAPTCHA_CODE_KEY + dto.getEmail());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean forgot(EmailRegisterDto dto) {
        validateEmailCode(dto);
        SysUser sysUser = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, dto.getEmail()));
        sysUser.setPassword(BCrypt.hashpw(dto.getPassword()));
        sysUserMapper.updateById(sysUser);
        redisUtil.delete(RedisConstants.CAPTCHA_CODE_KEY + dto.getEmail());
        return true;
    }

    @Override
    public String getWechatLoginCode() {
        //随机获取4位数字
        String code = "DL" + (int) ((Math.random() * 9 + 1) * 1000);
        redisUtil.set(RedisConstants.WX_LOGIN_USER_CODE + code, "NOT-LOGIN", RedisConstants.MINUTE_EXPIRE, TimeUnit.SECONDS);
        return code;
    }

    @Override
    public LoginUserInfo getWechatIsLogin(String loginCode) {
        Object value = redisUtil.get(RedisConstants.WX_LOGIN_USER + loginCode);

        if (value == null) {
            throw new ServiceException("登录失败");
        }

        LoginUserInfo loginUserInfo = JSONUtil.toBean(JSONUtil.parseObj(value), LoginUserInfo.class);

        StpUtil.login(loginUserInfo.getId());
        loginUserInfo.setToken(StpUtil.getTokenValue());

        return loginUserInfo;
    }

    @Override
    public QrLoginVo generateQrLogin() {
        // 用 UUID 而不是微信验证码那种 4 位数字：这里 code 就是换 token 的唯一凭据，
        // 必须不可枚举，不能靠猜
        String code = UUID.randomUUID().toString().replace("-", "");

        redisUtil.set(RedisConstants.QR_LOGIN + code, JSONUtil.toJsonStr(QrLoginStateVo.waiting()),
                RedisConstants.MINUTE_EXPIRE, TimeUnit.SECONDS);

        String qrCodeImage;
        try {
            byte[] png = QrCodeUtils.generatePng(code, QR_CODE_SIZE);
            qrCodeImage = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        } catch (IOException e) {
            log.error("生成扫码登录二维码失败", e);
            throw new ServiceException("生成二维码失败，请重试");
        }

        return QrLoginVo.of(code, qrCodeImage, (int) RedisConstants.MINUTE_EXPIRE);
    }

    @Override
    public DeferredResult<Result<QrLoginStateVo>> pollQrLogin(String code) {
        DeferredResult<Result<QrLoginStateVo>> deferredResult =
                new DeferredResult<>(POLL_TIMEOUT_MS, Result.success(QrLoginStateVo.waiting()));

        // 先挂起再读状态。反过来的话，读完发现还是待扫码、还没来得及挂起时 App 刚好确认了，
        // 这次轮询就只能干等到超时才返回
        qrLoginHolder.register(code, deferredResult);

        QrLoginStateVo state = currentState(code);
        if (!QrLoginStateVo.WAITING.equals(state.getState())) {
            qrLoginHolder.complete(code, state);
        }
        return deferredResult;
    }

    @Override
    public QrLoginStateVo scanQrLogin(String code) {
        SysUser user = getCurrentSysUser();
        assertNotFinished(code);

        QrLoginStateVo scanned = QrLoginStateVo.scanned(user.getNickname(), user.getAvatar());
        // 扫描后重置有效期，避免"扫到了、但还没点确认就过期"这种最难受的情况
        redisUtil.set(RedisConstants.QR_LOGIN + code, JSONUtil.toJsonStr(scanned),
                RedisConstants.MINUTE_EXPIRE, TimeUnit.SECONDS);

        qrLoginHolder.complete(code, scanned);
        return scanned;
    }

    @Override
    public LoginUserInfo confirmQrLogin(String code) {
        QrLoginStateVo current = readState(code);
        if (QrLoginStateVo.CONFIRMED.equals(current.getState())) {
            throw new ServiceException("该二维码已确认，请刷新后重试");
        }
        // 强制走完"先扫码 -> 再确认"两步，防止误扫直接登录
        if (!QrLoginStateVo.SCANNED.equals(current.getState())) {
            throw new ServiceException("请先扫描二维码");
        }

        SysUser user = getCurrentSysUser();
        LoginUserInfo loginUserInfo = BeanCopyUtil.copyObj(user, LoginUserInfo.class);

        // 用 createLoginSession 而不是 login：这个请求是 App 发起的，login 会把新 token
        // 注入到 App 自己的请求上下文里，污染 App 的会话。createLoginSession 不碰当前上下文，
        // 但同样会触发 SaTokenListener.doLogin（登录时间、在线用户记录照常）
        loginUserInfo.setToken(StpUtil.createLoginSession(user.getId()));

        QrLoginStateVo confirmed = QrLoginStateVo.confirmed(loginUserInfo);
        // 有效期放宽到 5 分钟：确认可能发生在二维码有效期的最后一刻，浏览器还没来得及轮询到，
        // 不能让它跟着那 1 分钟的 key 一起消失
        redisUtil.set(RedisConstants.QR_LOGIN + code, JSONUtil.toJsonStr(confirmed),
                RedisConstants.FIVE_MINUTES_EXPIRE, TimeUnit.SECONDS);

        qrLoginHolder.complete(code, confirmed); // 更新DeferredResult状态
        return loginUserInfo;
    }

    @Override
    public void cancelQrLogin(String code) {
        assertNotFinished(code);

        QrLoginStateVo canceled = QrLoginStateVo.canceled();
        redisUtil.set(RedisConstants.QR_LOGIN + code, JSONUtil.toJsonStr(canceled),
                RedisConstants.MINUTE_EXPIRE, TimeUnit.SECONDS);

        qrLoginHolder.complete(code, canceled);
    }

    /**
     * 读取状态用于轮询。已确认的顺带消费掉（一次性），防止同一个 code 被反复换 token。
     * key 不存在时返回 EXPIRED 而不是抛异常——轮询要把"过期"当作正常结果返回给前端。
     */
    private QrLoginStateVo currentState(String code) {
        Object value = redisUtil.get(RedisConstants.QR_LOGIN + code);
        if (value == null) {
            return QrLoginStateVo.expired();
        }
        QrLoginStateVo state = JSONUtil.toBean(JSONUtil.parseObj(value), QrLoginStateVo.class);
        if (QrLoginStateVo.CONFIRMED.equals(state.getState())) {
            redisUtil.delete(RedisConstants.QR_LOGIN + code);
        }
        return state;
    }

    /**
     * 读取状态，key 不存在直接报"已过期"（用于 scan/confirm/cancel 这些操作）
     */
    private QrLoginStateVo readState(String code) {
        Object value = redisUtil.get(RedisConstants.QR_LOGIN + code);
        if (value == null) {
            throw new ServiceException("二维码已过期，请刷新后重试");
        }
        return JSONUtil.toBean(JSONUtil.parseObj(value), QrLoginStateVo.class);
    }

    /**
     * 二维码一旦确认或取消，就不能再被操作
     */
    private void assertNotFinished(String code) {
        QrLoginStateVo current = readState(code);
        if (QrLoginStateVo.CONFIRMED.equals(current.getState())) {
            throw new ServiceException("该二维码已确认，请刷新后重试");
        }
        if (QrLoginStateVo.CANCELED.equals(current.getState())) {
            throw new ServiceException("该二维码已取消，请刷新后重试");
        }
    }

    /**
     * 取当前登录的 App 用户并校验账号可用
     */
    private SysUser getCurrentSysUser() {
        Integer userId = StpUtil.getLoginIdAsInt();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        if (user.getStatus() != Constants.YES) {
            throw new ServiceException("账号已被禁用");
        }
        return user;
    }

//    @Override
//    public String wechatLogin(WxMpXmlMessage message) {
//        String code = message.getContent().toUpperCase();
//        //先判断登录码是否已过期
//        Object e = redisUtil.hasKey(RedisConstants.WX_LOGIN_USER_CODE + code);
//        if (e == null) {
//            return "验证码已过期";
//        }
//        LoginUserInfo loginUserInfo = wechatLogin(message.getFromUser());
//        //修改redis缓存 以便监听是否已经授权成功
//        redisUtil.set(RedisConstants.WX_LOGIN_USER + code, JSONUtil.toJsonStr(loginUserInfo), RedisConstants.MINUTE_EXPIRE, TimeUnit.SECONDS);
//        return "网站登录成功！(若页面长时间未跳转请刷新验证码)";
//    }

    @Override
    public String renderAuth(String source) {
        AuthRequest authRequest = getAuthRequest(source);
        return authRequest.authorize(AuthStateUtils.createState());
    }


    @Override
    public void authLogin(AuthCallback callback,String source, HttpServletResponse httpServletResponse) throws IOException {
        AuthRequest authRequest = getAuthRequest(source);
        AuthResponse<AuthUser> response = authRequest.login(callback);

        if (response.getData() == null) {
            log.info("用户取消了 {} 第三方登录",source);
            httpServletResponse.sendRedirect("https://www.shiyit.com");
            return;
        }
        String result = com.alibaba.fastjson.JSONObject.toJSONString(response.getData());
        log.info("第三方登录验证结果:{}", result);

        com.alibaba.fastjson.JSONObject jsonObject = JSON.parseObject(result);
        Object uuid = jsonObject.get("uuid");
        // 获取用户ip信息
        String ipAddress = IpUtil.getIp();
        String ipSource = IpUtil.getIp2region(ipAddress);
        // 判断是否已注册
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, uuid));
        if (ObjectUtils.isEmpty(user)) {
            // 保存账号信息
            user = SysUser.builder()
                    .username(uuid.toString())
                    .password(UUID.randomUUID().toString())
                    .loginType(source)
                    .lastLoginTime(LocalDateTime.now())
                    .ipLocation(ipAddress)
                    .ip(ipSource)
                    .status(Constants.YES)
                    .nickname(source + "-" +getRandomString(6))
                    .avatar(jsonObject.get("avatar").toString())
                    .build();
            userMapper.insert(user);
            //添加角色
            insertRole(user);
        }

        StpUtil.login(user.getId());
        httpServletResponse.sendRedirect("https://www.shiyit.com/?token=" + StpUtil.getTokenValue());
    }

    @Override
    public LoginUserInfo appletLogin(String code, JSONObject userInfo) {
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + wechatProperties.getAppletAppId()
                + "&secret=" + wechatProperties.getAppletSecret() + "&js_code=" + code + "&grant_type=authorization_code";
        String result = HttpUtil.get(url);
        com.alibaba.fastjson.JSONObject jsonObject = JSON.parseObject(result);
        String openid = jsonObject.getString("openid");
        if (openid == null) {
            throw new ServiceException("登录失败");
        }

        // 查询用户
        SysUser user = userMapper.selectByUsername(openid);

        if (user == null) {
            String ip = IpUtil.getIp();
            String avatar = avatarList[(int) (Math.random() * avatarList.length)];
            user = SysUser.builder()
                    .username(openid)
                    .openid(openid)
                    .password(UUID.randomUUID().toString())
                    .loginType(LoginTypeEnum.APPLET.getType())
                    .lastLoginTime(LocalDateTime.now())
                    .ipLocation(IpUtil.getIp2region(ip))
                    .ip(ip)
                    .status(Constants.YES)
                    .nickname("wx_"+getRandomString(4))
                    .avatar(userInfo.getString("avatarUrl"))
                    .sex(0)
                    .build();
            userMapper.insert(user);
            //添加用户角色信息
            this.insertRole(user);
        }else {
            if (user.getStatus() == Constants.NO) {
                throw new ServiceException("账号已被禁用，请联系管理员");
            }
        }

        LoginUserInfo loginUserInfo = BeanCopyUtil.copyObj(user, LoginUserInfo.class);

        StpUtil.login(loginUserInfo.getId());
        StpUtil.getSession().set(Constants.CURRENT_USER, loginUserInfo);
        loginUserInfo.setToken(StpUtil.getTokenValue());

        return loginUserInfo;
    }

    @Override
    public Captcha getCaptcha() {
        Captcha captcha = new Captcha();
        CaptchaUtil.getCaptcha(captcha);
        return captcha;
    }

    private void validateEmailCode(EmailRegisterDto dto) {
        Object code = redisUtil.get(RedisConstants.CAPTCHA_CODE_KEY + dto.getEmail());
        if (code == null || !code.equals(dto.getCode())) {
            throw new ServiceException("验证码已过期或输入错误");
        }
    }

    @Override
    public LoginUserInfo wechatAuthLogin(JSONObject userInfo) {
        String openid = userInfo.getString("openid");
        SysUser user = userMapper.selectByUsername(openid);
        if (ObjectUtils.isEmpty(user)) {
            String ip = IpUtil.getIp();
            String ipSource = IpUtil.getIp2region(ip);
            // 保存账号信息
            user = SysUser.builder()
                    .username(openid)
                    .openid(openid)
                    .password(BCrypt.hashpw(openid))
                    .nickname(userInfo.getString("nickname"))
                    .avatar(userInfo.getString("avatarUrl"))
                    .loginType(LoginTypeEnum.WECHAT.getType())
                    .lastLoginTime(LocalDateTime.now())
                    .ip(ip)
                    .ipLocation(ipSource)
                    .status(Constants.YES)
                    .sex(userInfo.getInteger("sex"))
                    .build();
            userMapper.insert(user);
            //添加用户角色信息
            this.insertRole(user);
        }
        LoginUserInfo loginUserInfo = BeanCopyUtil.copyObj(user, LoginUserInfo.class);
        StpUtil.login(loginUserInfo.getId());
        StpUtil.getSession().set(Constants.CURRENT_USER, loginUserInfo);
        loginUserInfo.setToken(StpUtil.getTokenValue());

        return loginUserInfo;
    }

    /**
     * 添加用户角色信息
     * @param user
     */
    private void insertRole(SysUser user) {
        SysRole sysRole = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, Constants.USER));
        sysRoleMapper.addRoleUser(user.getId(), Collections.singletonList(sysRole.getId()));
    }

    /**
     * 随机生成6位数的字符串
     */
    public static String getRandomString(int length) {
        String str = "QWERTYUIOPASDFGHJKLZXCVBNMqwertyuiopasdfghjklzxcvbnm0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(str.length());
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }


    private @NotNull AuthRequest getAuthRequest(String source) {
        AuthRequest authRequest = null;
        switch (source) {
            case "gitee":
                authRequest = new AuthGiteeRequest(AuthConfig.builder()
                        .clientId(giteeConfigProperties.getAppId())
                        .clientSecret(giteeConfigProperties.getAppSecret())
                        .redirectUri(giteeConfigProperties.getRedirectUrl())
                        .build());
                break;
            case "qq":
                authRequest = new AuthQqRequest(AuthConfig.builder()
                        .clientId(qqConfigProperties.getAppId())
                        .clientSecret(qqConfigProperties.getAppSecret())
                        .redirectUri(qqConfigProperties.getRedirectUrl())
                        .build());
                break;
            case "weibo":
                authRequest = new AuthWeiboRequest(AuthConfig.builder()
                        .clientId(weiboConfigProperties.getAppId())
                        .clientSecret(weiboConfigProperties.getAppSecret())
                        .redirectUri(weiboConfigProperties.getRedirectUrl())
                        .build());
                break;
            case "github":
                authRequest = new AuthGithubRequest(AuthConfig.builder()
                        .clientId(githubConfigProperties.getAppId())
                        .clientSecret(githubConfigProperties.getAppSecret())
                        .redirectUri(githubConfigProperties.getRedirectUrl())
                        .build());
                break;
            default:
                break;
        }
        if (null == authRequest) {
            throw new AuthException("授权地址无效");
        }
        return authRequest;
    }

}
