# 安全网站详细搭建步骤

> 本文档手把手指导你从零搭建并运行本项目。技术栈：SpringBoot 2.7 + MySQL 8 + JPA + Thymeleaf。

---

## 一、环境准备

### 1.1 所需软件及版本

| 软件 | 最低版本 | 推荐版本 | 说明 |
|------|---------|---------|------|
| JDK  | 1.8     | 17      | Java 运行环境 |
| Maven| 3.6     | 3.9     | 项目构建工具 |
| MySQL| 5.7     | 8.0     | 数据库 |

### 1.2 安装 JDK

**Linux（yum）：**
```bash
yum install -y java-17-openjdk java-17-openjdk-devel
# 验证
java -version
javac -version
```

**Linux（apt）：**
```bash
apt update && apt install -y openjdk-17-jdk
java -version
```

**Windows：**
1. 访问 https://adoptium.net 下载 JDK 17 安装包
2. 双击安装，记住安装路径（如 `C:\Program Files\Java\jdk-17`）
3. 配置环境变量：
   - 新建系统变量 `JAVA_HOME` = `C:\Program Files\Java\jdk-17`
   - 在 `Path` 中添加 `%JAVA_HOME%\bin`
4. 打开 cmd 执行 `java -version` 验证

### 1.3 安装 Maven

**Linux：**
```bash
# 下载并解压
wget https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz
tar -xzf apache-maven-3.9.9-bin.tar.gz -C /opt/
# 配置环境变量
echo 'export MAVEN_HOME=/opt/apache-maven-3.9.9' >> ~/.bashrc
echo 'export PATH=$MAVEN_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
# 验证
mvn -v
```

**Windows：**
1. 访问 https://maven.apache.org/download.cgi 下载 `apache-maven-3.9.9-bin.zip`
2. 解压到 `C:\Program Files\apache-maven-3.9.9`
3. 配置环境变量：
   - 新建 `MAVEN_HOME` = `C:\Program Files\apache-maven-3.9.9`
   - 在 `Path` 中添加 `%MAVEN_HOME%\bin`
4. cmd 执行 `mvn -v` 验证

### 1.4 配置 Maven 国内镜像（加速依赖下载）

编辑 `~/.m2/settings.xml`（Windows 为 `C:\Users\你的用户名\.m2\settings.xml`），若文件不存在则新建：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>阿里云公共仓库</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```

### 1.5 安装 MySQL

**Linux（yum）：**
```bash
yum install -y mysql-server
systemctl start mysqld
systemctl enable mysqld
# 查看初始密码
grep 'temporary password' /var/log/mysqld.log
# 登录后修改密码
mysql -uroot -p
```

**Linux（apt）：**
```bash
apt install -y mysql-server
systemctl start mysql
mysql -uroot
```

**Windows：**
1. 访问 https://dev.mysql.com/downloads/installer/ 下载安装包
2. 安装时选择 "Server only"，记住设置的 root 密码
3. 安装完成后 MySQL 服务会自动启动

---

## 二、数据库初始化

### 2.1 方式一：命令行执行脚本

```bash
mysql -uroot -p < schema.sql
```

### 2.2 方式二：登录 MySQL 后执行

```bash
mysql -uroot -p
```

```sql
-- 在 MySQL 命令行中执行
source /path/to/schema.sql;
-- 或直接复制 schema.sql 内容粘贴执行

-- 验证表是否创建成功
USE secure_website;
SHOW TABLES;
-- 应显示：ip_blacklist, ip_register_record, login_log, sys_user
```

### 2.3 验证数据库

```sql
USE secure_website;
DESC sys_user;              -- 查看用户表结构
SELECT COUNT(*) FROM sys_user;  -- 应为 0
```

---

## 三、项目配置

### 3.1 解压项目

将收到的 `secure-website.zip` 解压到任意目录：

```bash
unzip secure-website.zip -d /opt/
cd /opt/secure-website
```

Windows 用户右键解压即可。

### 3.2 修改数据库连接配置

编辑 `src/main/resources/application.yml`，修改 `spring.datasource` 部分：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/secure_website?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root          # ← 改成你的 MySQL 用户名
    password: 你的密码       # ← 改成你的 MySQL 密码
    driver-class-name: com.mysql.cj.jdbc.Driver
```

**配置项说明：**
- `url` 中 `localhost:3306` 为数据库地址和端口，按需修改
- `serverTimezone=Asia/Shanghai` 为时区，海外服务器改为对应时区

### 3.3 修改安全配置（可选）

`application.yml` 中 `security` 节点可根据需要调整：

```yaml
security:
  register-interval-seconds: 300    # 同一IP注册间隔（秒）
  max-register-count-per-ip: 3      # 同一IP最大注册次数
  blacklist-duration-minutes: 60    # IP拉黑时长（分钟）
  ddos-max-requests-per-second: 10  # 每秒最大请求数
  ddos-ban-duration-seconds: 30     # DDoS封禁时长（秒）
```

> ⚠️ 生产环境数据库密码请通过环境变量 `DB_PASSWORD` 注入，不要硬编码。

---

## 四、启动项目

### 4.1 方式一：Maven 直接启动（开发推荐）

```bash
cd /opt/secure-website
mvn spring-boot:run
```

看到如下日志即启动成功：
```
Tomcat started on port(s): 8080 (http)
Started WebsiteApplication in 5.234 seconds
```

### 4.2 方式二：打包后运行（生产推荐）

```bash
cd /opt/secure-website
# 打包（跳过测试）
mvn clean package -DskipTests
# 运行 jar
java -jar target/secure-website-0.1.0.jar
```

### 4.3 方式三：使用启动脚本

```bash
cd /opt/secure-website
chmod +x start.sh
./start.sh init      # 初始化数据库
./start.sh package   # 打包
./start.sh run       # 启动
```

### 4.4 方式四：IDE 中运行（IntelliJ IDEA）

1. 用 IDEA 打开项目根目录
2. 等待 Maven 自动下载依赖
3. 找到 `WebsiteApplication.java`，右键 → Run

---

## 五、访问与验证

### 5.1 访问网站

浏览器打开：**http://localhost:8080**

会自动跳转到登录页面 `/auth/login`。

### 5.2 注册账号

1. 在登录页点击"立即注册"
2. 填写信息（示例）：
   - 用户名：`testuser`
   - 密码：`Test1234`（必须含大写+小写+数字，≥8位）
   - 确认密码：`Test1234`
3. 点击"注册"按钮，成功后自动跳转登录页

### 5.3 登录验证

1. 输入刚注册的用户名和密码
2. 登录成功后进入起始页 `/home`
3. 起始页包含搜索框（四引擎切换）、三组导航卡片、码农语录（打字机）、主题切换等

### 5.4 验证安全功能

**测试"用户枚举防护"：**
- 登录页分别输入一个不存在的用户名（如 `nobody123`）和一个存在的用户名（密码都填错）
- 两者应返回**完全一致**的提示「用户名或密码错误」，攻击者无法判断用户名是否存在

**测试"登录防爆破（验证码 + 渐进式锁定）"：**
- 连续输错同一账号的密码约 3~4 次
- 之后登录会强制要求输入图形验证码（点图片可刷新），无验证码无法继续尝试
- 继续输错（含验证码错误）会累计失败次数，达到阈值（默认 5 次）后账号被锁定
- **渐进式锁定**：第 1 次锁定 30 秒，之后每次锁定时长翻倍（1 分钟 → 2 分钟 → 4 分钟 → 8 分钟……）
- 验证码一次性使用，校验后立即失效，无法重放
- 登录成功后，失败计数与锁定档位自动重置

**测试"IP 注册频率限制"：**
- 注册一个账号后，立即再注册第二个账号
- 应提示"该IP注册过于频繁，请XXX秒后再试"

**测试"密码强度校验"：**
- 注册时密码设为 `123456`（无大写字母）
- 应提示"密码必须包含大写字母"

**测试"DDoS 防御"：**
```bash
# 快速发送大量请求触发限流
for i in $(seq 1 50); do curl -s http://localhost:8080/auth/login > /dev/null & done
# 之后请求应返回 429
curl http://localhost:8080/auth/login
```

**测试"SQL 注入防御"：**
```bash
# 发送带 SQL 注入特征的请求
curl -X POST "http://localhost:8080/auth/login" \
     -d "username=admin' OR 1=1 --&password=123"
# 不会产生注入效果：本项目所有数据库操作均通过 JPA 参数化查询（PreparedStatement）执行，
# 恶意字符串会被当作普通用户名处理，返回"用户名或密码错误"，
# 而不会改变 SQL 语义。用户名格式校验也会拦截含单引号等非法字符的输入。
```

**测试"健康检查"：**
```bash
curl http://localhost:8080/health
# 应返回 {"code":200,"message":"UP","data":{"status":"UP","database":"UP",...}}
```

---

## 六、常见问题

### Q1：启动报错 "Communications link failure"
**原因：** MySQL 未启动或连接信息错误。
**解决：**
```bash
systemctl status mysqld        # 检查 MySQL 是否运行
mysql -uroot -p -e "SELECT 1"  # 测试能否连接
```
确认 `application.yml` 中 url/username/password 正确。

### Q2：启动报错 "Access denied for user"
**原因：** 数据库用户名或密码错误。
**解决：** 重新设置 MySQL 密码或修改 `application.yml`。

### Q3：启动报错 "Unknown database 'secure_website'"
**原因：** 数据库未初始化。
**解决：** 执行 `mysql -uroot -p < schema.sql`。

### Q4：页面样式丢失 / 404
**原因：** 静态资源被过滤器拦截。
**解决：** 本项目已放行 `/css/`、`/js/` 等路径，若仍异常检查 `DdosDefenseFilter` 的放行配置。

### Q5：注册提示"您的IP已被拉黑"
**原因：** 触发了风控规则。
**解决：** 等待拉黑时长过期（默认 60 分钟），或手动清理：
```sql
DELETE FROM ip_blacklist WHERE ip = '你的IP';
```

### Q6：端口 8080 被占用
**解决：** 修改 `application.yml` 中 `server.port` 为其他端口（如 8081）。

### Q7：JDK 版本不兼容
**原因：** 项目默认 Java 8，若用 JDK 17 需确认 SpringBoot 版本兼容。
**解决：** 本项目 SpringBoot 2.7.18 兼容 JDK 8-21，无需修改。若编译报错可在 `pom.xml` 中调整 `<java.version>`。

---

## 七、项目结构总览

```
secure-website/
├── pom.xml                              # Maven 配置（依赖、构建）
├── schema.sql                           # 数据库初始化脚本
├── README.md                            # 项目说明
├── SETUP.md                             # 本搭建文档
├── start.sh                             # 启动脚本
└── src/main/
    ├── java/com/example/website/
    │   ├── WebsiteApplication.java      # ★ 启动入口
    │           ├── common/
        │   ├── ApiResult.java           # 统一返回结果
        │   └── GlobalExceptionHandler.java # 全局异常兜底
        │   ├── config/
        │   │   ├── SecurityConfig.java  # ★ Spring Security 配置
        │   │   ├── SecurityProperties.java # 安全配置读取
        │   │   └── AdminInitializer.java # 管理员初始化（确保 admin 为 ADMIN 角色）
        │   ├── controller/
        │   │   ├── AuthController.java      # ★ 登录注册接口
        │   │   ├── CaptchaController.java   # 图形验证码
        │   │   ├── HomeController.java      # 首页
        │   │   ├── UserProfileController.java # 个人中心
        │   │   ├── AdminController.java     # 管理后台
        │   │   ├── HealthController.java    # 健康检查
        │   │   └── CodeSloganController.java # 码农语录
        │   ├── entity/                      # JPA 实体（4个表）
        │   ├── repository/                  # 数据访问层
        │   ├── security/DdosDefenseFilter.java # ★ DDoS 防御
        │   ├── service/                     # 业务逻辑层
        │   └── util/                        # 工具类
        └── resources/
            ├── application.yml              # ★ 主配置文件
            ├── static/
            │   ├── css/common.css
            │   ├── img/avatar.jpg
            │   └── js/common.js
            └── templates/
                ├── login.html               # ★ 登录页（含验证码）
                ├── register.html            # ★ 注册页
                ├── home.html                # ★ 起始页
                └── admin.html               # 管理后台
```

---

## 八、功能清单对照表

| 需求 | 实现位置 | 状态 |
|------|---------|------|
| 登录界面（含验证码） | templates/login.html + CaptchaController | ✅ |
| 注册界面 | templates/register.html | ✅ |
| 登录检索数据库（统一文案防枚举） | AuthController.doLogin | ✅ |
| 注册 IP 短时间限制 | RegisterRiskService.checkRegisterAllowed | ✅ |
| 密码含大小写数字 8~64 位 | PasswordValidator.validate | ✅ |
| 短时间多次注册拦截拉黑 | RegisterRiskService.blacklistIp | ✅ |
| 注册成功跳转登录页 | AuthController.doRegister (redirect) | ✅ |
| 登录成功进起始页 | AuthController.doLogin (redirect /home) | ✅ |
| 起始页搜索框（四引擎切换） | home.html search-bar | ✅ |
| 起始页导航卡片（三组） | home.html link-grid | ✅ |
| 码农语录（打字机） | home.html + CodeSloganController | ✅ |
| 主题切换（暗色/亮色） | home.html data-theme | ✅ |
| 个人中心（改昵称/密码） | UserProfileController | ✅ |
| 管理后台（日志/黑名单） | AdminController + admin.html | ✅ |
| 健康检查 | HealthController (/health) | ✅ |
| DDoS 防御 | DdosDefenseFilter | ✅ |
| SQL 注入防御 | JPA 参数化查询（无字符串拼接） | ✅ |
| 密码安全 | BCrypt 单向哈希 + Spring Security | ✅ |
| CSRF 防护 | SecurityConfig + common.js token | ✅ |
| 登录防爆破（验证码） | AuthController + CaptchaController | ✅ |
| 安全响应头（CSP/HSTS等） | SecurityConfig | ✅ |
| SpringBoot + MySQL | pom.xml + application.yml | ✅ |

---

## 九、生产部署建议

1. **数据库密码注入**：通过环境变量 `DB_PASSWORD` 注入，不要硬编码在配置文件

2. **使用 Nginx 反向代理**：在 Nginx 层也配置限流和 HTTPS

3. **数据库连接池调优**：在 application.yml 中添加 HikariCP 配置

4. **日志监控**：关注 `[DDoS]`、`[注册风控]` 开头的日志

5. **定期备份**：备份 MySQL 数据库和拉黑策略

---

如遇问题，请先查看「六、常见问题」，仍未解决可对照项目结构检查对应文件。
