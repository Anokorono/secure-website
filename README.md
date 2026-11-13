# 安全网站项目（Spring Boot + MySQL）

一个具备完整安全防护的登录注册网站，附带个性化起始页（搜索、导航、码农语录、主题切换）。技术栈：**Spring Boot 2.7.18 + MySQL 8 + Spring Data JPA + Thymeleaf + Spring Security**。

---

## 一、主要功能

### 1. 登录 / 注册
- 登录时从数据库检索账户，密码错误与用户不存在**统一提示「用户名或密码错误」**，防用户名枚举。
- 对「不存在的用户」执行一次**虚拟 BCrypt 校验**，使两条分支耗时一致，防时序侧信道枚举。
- 注册成功后跳转回登录页；登录成功后进入个性化起始页 `/home`。

### 2. 注册风险管控
- 同一 IP 短时间内无法注册第二个用户（默认 300 秒窗口）。
- 密码强度校验：必须同时包含大写字母、小写字母和数字，长度 8~64 位。
- 同一 IP 累计注册次数超限自动拉黑（默认 3 次）。
- 短时间注册第二个或并发注册多个用户会被拦截并拉黑。
- 同一 IP 的注册流程**串行化处理**，防止并发绕过频次限制（TOCTOU）。
- IP 拉黑时长可配置（默认 60 分钟），定时自动解封。

### 3. 安全防护
- **DDoS 防御**：基于 Guava 令牌桶的 IP 限流，超阈值自动封禁（默认 10 次/秒，封禁 30 秒）。
- **SQL 注入防御**：全程 JPA 参数化查询（PreparedStatement），杜绝字符串拼接。
- **密码安全**：BCrypt 单向哈希存储（不可逆）；数据库密码通过环境变量注入，不硬编码。
- **CSRF 防护**：Cookie 存储 token，前端随请求携带。
- **会话安全**：登录后更换会话 ID 防固定攻击；单账号最多 1 个并发会话；会话超时跳登录页。
- **登录防爆破**：
  - 连续失败达到阈值后强制图形验证码（`/auth/captcha`），一次性使用防重放；**验证码错误同样累计失败次数**，防止攻击者靠刷验证码绕过锁定。
  - 失败达到阈值触发**渐进式锁定**：第 1 次锁定 30 秒，之后每次翻倍（1 分钟 → 2 分钟 → 4 分钟 → 8 分钟……，封顶 24 小时），登录成功后重置。
  - 锁定期间前端显示**锁定倒计时**，后端返回剩余秒数 `lockRemainSeconds`。
  - 失败计数采用**单条数据库原子更新**，避免并发请求「读-改-写」竞态导致绕过锁定。
- **安全响应头**：CSP、HSTS、X-Frame-Options、X-Content-Type-Options、Referrer-Policy、Permissions-Policy。
- **IP 防伪造**：默认仅信任 socket 地址；信任代理时取 X-Forwarded-For 最右一跳。
- **日志防注入**：异常信息落库前清洗换行等控制字符，防 log forging。
- **全局异常兜底**：统一 JSON 返回，不泄露堆栈信息。

### 4. 个性化起始页（/home）
- **搜索框**：支持百度 / 必应 / 搜狗 / Google 四引擎切换（Ctrl+K 聚焦、Ctrl+↑↓ 切引擎），带搜索历史。
- **图标分组**：影音社交 / 开发技术 / 常用工具 三组导航卡片，自动适配列数。
- **码农语录**：打字机逐字输出 + 光标闪烁，点击换一句，支持复制和点赞，内置 131 条（含颜色字、颜文字）。
- **主题切换**：暗色 / 亮色两套主题，localStorage 持久化，未手动选择时跟随系统。
- **氛围细节**：实时时钟 + 分时段问候、hover 上浮动效、双栏响应式布局。

### 5. 个人中心与管理后台
- **个人中心**（`/profile`）：修改昵称、修改密码（需校验原密码）。
- **管理后台**（`/admin`，仅 `ROLE_ADMIN` 角色）：查看登录日志、查看 / 解除 IP 黑名单。
- **管理员角色**：基于用户表 `role` 字段（`USER` / `ADMIN`），登录时注入 `ROLE_ADMIN` / `ROLE_USER` 授权，避免硬编码用户名。

### 6. 健康检查
- `GET /health`：返回应用与数据库连接状态，供容器编排 / 负载均衡探活。

### 7. 管理员初始化
- 应用启动时自动确保 `admin` 账号存在且为 `ADMIN` 角色（`AdminInitializer`）。
- 首次创建管理员需设置环境变量 `ADMIN_PASSWORD`（密码须满足强度要求）。

---

## 二、搭建环境

### 环境要求

| 软件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 17+（项目配置 `java.version=21`） | 实测 JDK 25 可运行 |
| Maven | 3.6+ | 项目构建工具 |
| MySQL | 8.0 | 数据库（5.7 亦可） |

### 安装步骤（以 Linux 为例，Windows 对应到官网下载安装包）

**JDK 17：**
```bash
# RHEL / CentOS
yum install -y java-17-openjdk java-17-openjdk-devel
# Ubuntu / Debian
apt update && apt install -y openjdk-17-jdk
# 验证
java -version && javac -version
```

**Maven：**
```bash
wget https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz
tar -xzf apache-maven-3.9.9-bin.tar.gz -C /opt/
echo 'export MAVEN_HOME=/opt/apache-maven-3.9.9' >> ~/.bashrc
echo 'export PATH=$MAVEN_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc && mvn -v
```

**MySQL 8：**
```bash
# RHEL / CentOS
yum install -y mysql-server && systemctl start mysqld && systemctl enable mysqld
# Ubuntu / Debian
apt install -y mysql-server && systemctl start mysql
```

> 建议配置 Maven 国内镜像（阿里云）加速依赖下载，详见 `SETUP.md`。

---

## 三、配置方法

### 1. 初始化数据库

```bash
mysql -uroot -p < schema.sql
```

> ⚠️ `schema.sql` 会 `DROP TABLE IF EXISTS` 后重建，仅用于**首次初始化**，请勿在生产环境重复执行。

脚本会创建数据库 `secure_website` 及 4 张表：`sys_user`、`ip_register_record`、`ip_blacklist`、`login_log`。

**已有旧库升级**：若之前用旧版 `schema.sql` 建库（`sys_user` 表无 `role` 列），执行迁移脚本：
```bash
mysql -uroot -p < migration_add_role.sql
```
> 若旧库缺少渐进式锁定的 `lock_level` 列，执行 `migration_add_lock_level.sql`。

### 2. 配置数据库连接与环境变量

数据库密码通过环境变量注入（**不提供默认值**，避免弱口令泄漏）：

```bash
export DB_USERNAME=root
export DB_PASSWORD=你的数据库密码
# 首次创建管理员账号时，同时设置管理员密码（须满足大小写 + 数字 + 8 位要求）
export ADMIN_PASSWORD=你的管理员密码
```

`application.yml` 中已通过 `${DB_PASSWORD}` 读取该环境变量，无需修改代码。

### 3. 安全参数配置（`application.yml` 的 `security` 节点）

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `register-interval-seconds` | 同一 IP 注册间隔（秒） | 300 |
| `max-register-count-per-ip` | 同一 IP 最大注册次数 | 3 |
| `blacklist-duration-minutes` | IP 拉黑时长（分钟） | 60 |
| `ddos-max-requests-per-second` | 每秒最大请求数 | 10 |
| `ddos-ban-duration-seconds` | DDoS 封禁时长（秒） | 30 |
| `login-max-fail-count` | 登录失败锁定阈值（次） | 5 |
| `login-first-lock-seconds` | 首次锁定时长（秒） | 30 |
| `login-lock-multiplier` | 锁定档位倍增基数（2 表示每次翻倍） | 2 |
| `trust-proxy-headers` | 是否信任代理转发头 | false |
| `ip-whitelist` | IP 白名单（不限流不风控） | 127.0.0.1 等 |

---

## 四、使用方法

### 1. 启动项目

```bash
# 方式一：Maven 直接启动（开发推荐）
mvn spring-boot:run

# 方式二：打包后运行（生产推荐）
mvn clean package -DskipTests
java -jar target/secure-website-1.0.0.jar

# 方式三：启动脚本
./start.sh init      # 初始化数据库
./start.sh package   # 打包
./start.sh run       # 启动

# 方式四：Docker 运行
docker build -t secure-website .
docker run -d -p 8080:8080 -e DB_PASSWORD=xxx -e DB_USERNAME=root secure-website
```

### 2. 访问网站

浏览器打开 **http://localhost:8080**，自动跳转登录页 `/auth/login`。

### 3. 注册账号

1. 登录页点击「立即注册」。
2. 填写示例：用户名 `testuser`、密码 `Test1234`（含大小写 + 数字 + 8 位）。
3. 注册成功后自动跳回登录页。

### 4. 登录验证

输入刚注册的用户名和密码，登录成功后进入起始页 `/home`，包含四引擎搜索框、三组导航卡片、码农语录打字机、主题切换等。

### 5. 功能自测要点

| 测试项 | 操作 | 预期结果 |
|--------|------|---------|
| 防用户名枚举 | 分别输入不存在 / 存在的用户名（密码都填错） | 返回**完全一致**的「用户名或密码错误」 |
| 验证码 | 连续输错密码 3~4 次 | 强制要求图形验证码（点图可刷新） |
| 渐进式锁定 | 继续输错达到 5 次 | 账号锁定，前端显示倒计时，锁定时长逐次翻倍 |
| 注册频控 | 注册后立即再注册第二个 | 提示「该 IP 注册过于频繁」 |
| 密码强度 | 密码设为 `123456` | 提示「密码必须包含大写字母」 |
| DDoS 防御 | 快速发送大量请求 | 后续请求返回 429 |
| 健康检查 | `curl http://localhost:8080/health` | 返回应用与数据库 UP 状态 |

---

## 五、项目结构

```
secure-website/
├── pom.xml                              # Maven 配置
├── schema.sql                           # 数据库初始化脚本
├── migration_add_role.sql               # 迁移脚本（新增 role 列）
├── migration_add_lock_level.sql         # 迁移脚本（新增 lock_level 列）
├── Dockerfile                           # 多阶段构建镜像
├── start.sh                             # 启动脚本
├── README.md
├── SETUP.md                             # 详细搭建文档
└── src/main/
    ├── java/com/example/website/
    │   ├── WebsiteApplication.java      # 启动类
    │   ├── common/
    │   │   ├── ApiResult.java           # 统一返回结果
    │   │   └── GlobalExceptionHandler.java # 全局异常兜底
    │   ├── config/
    │   │   ├── SecurityConfig.java      # Spring Security 配置
    │   │   ├── SecurityProperties.java  # 安全配置读取
    │   │   └── AdminInitializer.java    # 管理员初始化
    │   ├── controller/
    │   │   ├── AuthController.java      # 登录注册
    │   │   ├── CaptchaController.java   # 图形验证码
    │   │   ├── HomeController.java      # 首页
    │   │   ├── UserProfileController.java # 个人中心
    │   │   ├── AdminController.java     # 管理后台
    │   │   ├── HealthController.java    # 健康检查
    │   │   └── CodeSloganController.java # 码农语录
    │   ├── entity/                      # JPA 实体（4 张表）
    │   ├── repository/                  # JPA Repository
    │   ├── security/DdosDefenseFilter.java # DDoS 防御
    │   ├── service/                     # 业务逻辑层
    │   └── util/                        # 工具类
    └── resources/
        ├── application.yml              # 主配置文件
        ├── logback-spring.xml           # 日志配置
        ├── static/
        │   ├── css/common.css
        │   ├── img/avatar.jpg
        │   └── js/common.js
        └── templates/
            ├── login.html               # 登录页（含验证码、锁定倒计时）
            ├── register.html            # 注册页
            ├── home.html                # 起始页
            └── admin.html               # 管理后台
```

---

## 六、安全注意事项

1. **生产环境数据库密码必须通过环境变量 `DB_PASSWORD` 注入**，不要硬编码。
2. 生产环境将 `application.yml` 中数据库连接改为 `useSSL=true`，并移除 `allowPublicKeyRetrieval`。
3. 建议在反向代理（Nginx）层也配置限流、HTTPS 和 WAF。
4. 管理员权限基于用户表 `role` 字段（`ADMIN`），登录时注入 `ROLE_ADMIN`；初始管理员由 `AdminInitializer` 自动创建 / 修正。
5. 生产环境将 `server.servlet.session.cookie.secure` 设为 `true`（仅 HTTPS 下发送 Cookie）。
6. `ip-whitelist` 只能配置本机回环 / 可信内网，**严禁**配置公网 IP 或 `0.0.0.0`。
7. Spring Boot 2.7.18 已 EOL，正式上线建议升级到 3.x（需迁移 `javax` → `jakarta`）。

> 更详细的安装、配置与故障排查（含常见问题 Q&A），请参阅 [SETUP.md](SETUP.md)。
