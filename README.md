# Shop 商城（shop-shixin-master）

一套「商城 + 内容社区 + 潮玩专区」的项目，同一个后端配三个前端：

| 端 | 目录 | 说明 | 本地地址 |
|---|---|---|---|
| 后端 | `shop/` | Spring Boot 多模块，同时提供后台接口与三端前台接口 | `:8800` |
| 管理后台 | `shop-admin/` | Vue 3 + Element Plus（动态菜单 / 权限 / 字典） | `:3000` |
| 官网前台 | `shop-web/` | Astro + Vue islands + Tailwind（四语 zh/en/ja/ko） | `:4321` |
| 移动端 | `shop-app/` | uni-app（H5 / 微信小程序 / App） | `pnpm dev` |

---

## 技术栈

**后端**：Java 17、Spring Boot 2.7.0、MyBatis-Plus 3.5.2、Sa-Token 1.39.0（Redis 存会话）、MySQL 8、RabbitMQ（spring-amqp）、Quartz、Knife4j（Swagger 2）、Hutool、WebSocket、邮件。
存储走 `x-file-storage`（本地 / 阿里 OSS / 腾讯 COS / 七牛 / MinIO / FastDFS 都能配）；微信支付/公众号、支付宝在 `shop-wx`；AI 助手（LangChain4j + Ollama/OpenAI）在 `shop-ai`。

**管理后台**：Vue 3、Vite、Element Plus、Pinia、Axios、unplugin-auto-import。

**官网**：Astro（`output: server`，Node 适配器）、Vue 3 islands、Tailwind CSS v4、Pinia、Axios、astro ClientRouter（站内换页不整页刷新）。

**移动端**：uni-app 3（Vue 3 + TypeScript + Vite + UnoCSS），工程规范见 [`shop-app/AGENTS.md`](shop-app/AGENTS.md) 与 `shop-app/hermes/`。

---

## 目录结构

```
shop-shixin-master/
├── shop/                      后端（Maven 多模块）
│   ├── shop-commom/           公共层：实体 / Mapper / VO / DTO / 跨模块 service / 工具
│   ├── shop-admin/            后台接口（/sys/**、/shop/** 等，@SaCheckPermission 管权限）
│   ├── shop-api/              前台接口（/api/web|app|comm/**）与定时任务入口
│   ├── shop-server/           启动模块（打包成 shop-admin.jar，:8800）
│   ├── shop-auth/             登录、Sa-Token 配置与权限查询
│   ├── shop-file/             文件存储（上传 / 下载 / 存储平台配置）
│   ├── shop-quartz/           定时任务调度（sys_job）
│   ├── shop-mq/               RabbitMQ（示例拓扑 + 通知的真实链路）
│   ├── shop-wx/               微信 / 支付宝
│   ├── shop-ai/               AI 智能体 / 知识库 / 会话
│   ├── sql/                   基线 DDL + 增量脚本
│   ├── docs/  logs/  libwebp/ 文档、运行日志、webp 压缩库
│   └── server.txt             三条常用命令（查端口 / 杀进程 / 打包 / 启动）
├── shop-admin/                管理后台（Vue 3 + Element Plus）
├── shop-web/                  官网前台（Astro + Vue）
├── shop-app/                  移动端（uni-app）
└── LICENSE                    Apache-2.0
```

---

## 功能概览

**前台（官网 + 移动端）**
- 商城：商品/规格（SKU）详情、搜索、品牌与门店页、购物车（同一规格按来源分行、整盒一行）、下单/支付回调、订单与退款、收货地址、浏览足迹。
- 营销：优惠券（代金券/折扣券/满减、指定商品或通用）、积分账户与流水、签到日历、徽章。
- 内容：广场（发布/审核/详情）、评论（商品/门店/品牌/广场/公园五类）、点赞、收藏、图片评论；个人中心把以上收在一页。
- 消息中心：站内通知（系统 / 互动两档）、未读红点、全部已读/清空，点消息可定位到对应业务页。
- 主题公园：园区（照片墙、地图、评论、收藏/点赞）、票种（入园时段多选）、购票出票（票码 + 票面二维码）、我的票种、后台园区照片审核。
- 潮玩专区 `/trendy`：潮玩系列、盲盒机（老虎机呈现，登录后**真抽并自动加购**）、图鉴（收集进度 + 集齐奖励主动领取）、主题公园专区、潮玩商品。

**管理后台**（菜单来自库里的 `sys_menu`，可动态配）
商品管理、门店管理（招商/培训）、营销活动（活动/优惠券/积分/签到/徽章）、潮玩管理（系列/IP/盲盒/主题公园）、消费者管理（订单/购物车/地址/收藏/足迹/钱包/个人信息）、配送管理、库存管理（入库/出库/库存/调拨/盘点）、订单管理（订单/退款/用户票种）、进销存（供应商/销售单/采购单/仓库）、站点管理（网站配置/公告/广场消息）、消息管理（评论/通知/留言）、AI 智能体、系统管理（用户/部门/角色/字典/菜单/参数/图标库/文件/日志/账号中心）、监控中心（服务/缓存/定时任务/调度日志/在线用户）、日志管理、系统工具（代码生成）。

---

## 本地启动

### 0. 依赖

- JDK 17、Maven 3.8+
- MySQL 8（库名 `shixin_shop`）
- Redis
- RabbitMQ（通知链路要用；只跑商城不下单不发通知可以先不启）
- Node 18+ / pnpm（`shop-web`、`shop-app` 用 pnpm；`shop-admin` 有 pnpm/npm 两份锁文件，都行）

### 1. 配置（必看）

`shop/**/src/main/resources/*.yml` **不进 git**（`shop/.gitignore` 忽略了 `*.yml`），换机器/部署要自己补：

- `application.yml`：数据库、Redis、Sa-Token、MyBatis-Plus、邮箱等
- `application-wx.yml`（`shop-wx`）、`application-file.yml`（`shop-file`）、`application-mq.yml`（`shop-mq`）由 `application.yml` 的 `spring.config.import` 引入
- `shop-web/.env`：`API_SERVER_BASE`（dev 代理目标，默认 `http://127.0.0.1:8800`）与地图钥匙
- `shop-admin/.env.development`：`VITE_APP_PORT`（默认 3000）与接口前缀

### 2. 后端

```bash
cd shop
mvn clean package -DskipTests
java -jar shop-server/target/shop-admin.jar      # :8800
```

### 3. 管理后台 / 官网 / 移动端

```bash
cd shop-admin && pnpm i && pnpm dev               # http://localhost:3000
cd shop-web   && pnpm i && pnpm dev               # http://localhost:4321（dev 下 /api 代理到 8800）
cd shop-app   && pnpm i && pnpm dev               # H5
cd shop-app   && pnpm dev:mp                      # 微信小程序
cd shop-app   && pnpm dev:app                     # App
```

`shop-web` 的检查：`pnpm typecheck`（vue-tsc）、`pnpm check`（astro check）；`shop-app` 合入门禁：`pnpm type-check && pnpm lint && pnpm test:run`。

---

## 接口与鉴权约定

- **统一信封**：`{ code, message, data }`；`code !== 200` 就是业务失败，**HTTP 仍是 200**（前端只认 code）。
- **路径分层**：
  - 后台接口在 `:8800` 上**不带 `/api`**（`/sys/**`、`/shop/**`），由 Sa-Token 拦截器 + `@SaCheckPermission` 管；
  - 前台三端走 `/api/web/**`（官网）、`/api/app/**`（App）、`/api/comm/**`（两端共用的动作），这些**不过 Sa-Token 拦截器**，要登录的动作由服务层自己 `requireLoginUser`。
- **token 只认请求头**：Sa-Token `token-name: Authorization`，且 `is-read-cookie: false`；三端各自把 token 放请求头（后台存在 `Neat-Admin-Token` cookie、官网存 localStorage、App 存本机存储）。
- **枚举文案走字典**：后台 `useDict`、官网 `src/lib/dict.ts` 读 `GET /api/web/dict/{types}`；不要在前端写死标签。

---

## 数据与 SQL

- 基线：`shop/sql/shixin_shop.sql`（首版结构快照，不含后续加的表）。
- 增量：`shop/sql/*.sql` 按需求逐个加，**幂等、带自检、跑完即删**；后台菜单 / 按钮权限 / 字典数据也在这类脚本里（`sys_menu`、`sys_dict`）。
- 约定：一个对象的类型 + id 用「类型列 + id 列」两格表达（如 `shop_product/ store/ brand` 的 `section_type` + `section_id`、评论的 `comment_type` + `business_id`），库上不建外键。

---

## 消息队列（shop-mq）

通知已经走 MQ（`NotificationsUtil` → `NotificationDispatcher` → RabbitMQ）：

```
业务提交（事务后）→ shop.notification.queue ──消费失败 nack──→ shop.notification.dlx
                          ↑                                        ↓
                          └──── 30 秒后（TTL 到期，死信指回主交换机）── shop.notification.retry.queue
```

- 最多自动重试 3 次；失败次数记在 `sys_notifications.fail_count`（0=已发送，>0=还没发出去），满 3 次停手并给平台账号发提醒，后台「通知管理」可手动重试。
- 监听是 **manual ack**（见 `application-mq.yml`），坏消息确认后丢弃，避免卡队列。
- `shop.demo.*` 是练习拓扑，业务队列不要绑上去。

---

## License

[Apache-2.0](LICENSE)
