# Handsome 选课系统

基于 **积分竞价** 机制的高并发选课系统，学生通过消耗积分预选课程，系统按积分高低与选课时间排序自动分配名额。

## 技术栈

| 层次 | 技术 |
|---|---|
| 后端框架 | Spring Boot 3.5 + Spring Security |
| ORM | MyBatis |
| 数据库 | MySQL |
| 缓存 | Redis（Lua 脚本保证原子性） |
| 消息队列 | Apache RocketMQ |
| 前端框架 | Vue 3 + Vite 7 + Element Plus |
| 状态管理 | Pinia |
| 语言 | Java 17 / JavaScript (ES Module) |

## 核心流程

```
┌──────────────────────── 选课前 ────────────────────────┐
│  管理员设置课程 startTime / endTime                       │
│  定时调度器在 startTime 前 1 分钟预加载全部学生积分到 Redis  │
└──────────────────────────────────────────────────────────┘
                           ↓ startTime 到达
┌──────────────────────── 竞价阶段 ──────────────────────┐
│  课程状态 → published                                    │
│  全程 Redis 操作（Lua 脚本原子竞价/改价/取消）             │
│  积分放大 1000 倍为整数，避免浮点精度问题                   │
└──────────────────────────────────────────────────────────┘
                           ↓ endTime 到达
┌──────────────────────── 结算阶段 ──────────────────────┐
│  课程状态 → closed（乐观锁）                              │
│  Redis 竞价数据排序 → 录取前 N 名 → 退还其余积分           │
│  结果通过 RocketMQ 异步持久化到 MySQL                     │
│  消费者入库成功后清除 Redis 缓存                           │
└──────────────────────────────────────────────────────────┘
```

## 项目结构

```
handsome/
├── src/main/java/org/example/handsome/
│   ├── config/            # Redis、Security、调度 配置
│   ├── controller/        # REST 接口（管理员 / 学生）
│   ├── dao/               # MyBatis Mapper
│   ├── pojo/              # 实体类 & DTO
│   └── service/
│       ├── impl/          # 业务实现（含 CourseScheduler、Lua 脚本调用）
│       └── *.java         # 服务接口
├── src/main/resources/
│   ├── application.yml    # 应用配置
│   ├── db/migration.sql   # 数据库迁移脚本
│   ├── lua/               # Redis Lua 脚本（bid / update / cancel）
│   └── static/            # Vue 前端项目
│       ├── src/
│       │   ├── api/       # Axios 接口封装
│       │   ├── views/     # 页面组件（admin / student）
│       │   ├── router/    # Vue Router
│       │   └── store/     # Pinia 状态管理
│       ├── package.json
│       └── vite.config.js
└── pom.xml
```

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Apache RocketMQ 5.0+
- Node.js 18+

### 1. 数据库初始化

执行 `src/main/resources/db/migration.sql` 中的 SQL 语句。

### 2. 修改配置

编辑 `src/main/resources/application.yml`，配置数据库、Redis、RocketMQ 连接信息。

### 3. 启动后端

```bash
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8081`。

### 4. 启动前端（开发模式）

```bash
cd src/main/resources/static
npm install
npm run dev
```

前端开发服务器默认运行在 `http://localhost:5173`，已配置代理转发 `/api` 请求到后端。

## 设计亮点

- **高并发防护**：竞价全程在 Redis 中完成，Lua 脚本保证扣分、记录、计数的原子性，避免超卖
- **定时调度**：基于 `TaskScheduler` 精确到秒的课程开始/结束调度，支持启动补偿
- **预加载策略**：选课前 1 分钟通过 Redis Pipeline 批量加载全部学生积分，消除高峰期 DB 压力
- **乐观锁**：课程状态变更使用 `version` 字段防止并发冲突
- **异步入库**：结算结果通过 RocketMQ 异步持久化，解耦结算与入库
- **积分放大**：`BigDecimal` 放大 1000 倍为 `long`，Redis 原子操作无精度损失
- **重复录取防护**：课程重新开放时，已录取学生 ID 缓存到 Redis Set，Lua 脚本拦截重复竞价

