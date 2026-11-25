# 颐康智护平台

面向职业养老机构的一体化业务管理平台，在 RuoYi-Vue 3.9.0 基础上二次开发，覆盖老人档案、入住办理、护理计划、床位与智能硬件监测、AI 健康评估与微信小程序端，并集成大模型知识库问答助手。

> 本仓库所有 Maven 模块统一采用 `ykzh-XXX` 命名，Java 包名保持 `com.xhzb` 不变。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 基础框架 | Java 17、Spring Boot 3.5.0、Spring Security + JWT |
| AI 能力 | Spring AI 1.1.8、DeepSeek（OpenAI 兼容协议）、Ollama Embedding、Redis VectorStore、Dify Agent |
| 数据层 | MySQL、MyBatis-Plus 3.5.7、MyBatis 3.5.16、PageHelper 2.1.0、Druid 1.2.28 |
| 缓存 | Redis（会话记忆、向量库、设备最新数据、报警计数） |
| 物联网 | 华为云 IoTDA（AMQP 1.0 设备数据接入） |
| 文件存储 | 阿里云 OSS |
| 调度 | Quartz |
| 前端 | Vue 3、Element Plus、Vite（独立仓库） |
| 部署 | Docker、Jenkins、Nginx |

## 模块说明

| 模块 | 职责 |
| --- | --- |
| `ykzh-admin` | 启动入口、全局配置、系统管理接口 |
| `ykzh-framework` | 框架核心：安全配置、Redis、线程池、华为云 IoT 配置 |
| `ykzh-system` | 用户、角色、菜单、部门、字典、日志等系统管理 |
| `ykzh-common` | 通用工具、常量、基础实体、Excel 与安全组件 |
| `ykzh-nursing` | 护理业务核心：入住、合同、老人、床位、护理计划、设备、报警、健康评估、小程序端 |
| `ykzh-ai` | AI 模块：Spring AI 对话、RAG 检索、会话记忆、Function Calling、Dify 对接 |
| `ykzh-oss` | 阿里云对象存储 |
| `ykzh-quartz` | 定时任务调度 |
| `ykzh-generator` | 代码生成器 |

## 核心功能

### 1. AI 知识库问答（RAG）

- 基于 Spring AI `QuestionAnswerAdvisor` 构建检索增强问答，相似度阈值 0.7、topK=3。
- 知识库文档由 OSS 下载后经 `PagePdfDocumentReader` 解析、`TokenTextSplitter`（chunkSize 500）切分，写入 Redis VectorStore，Embedding 使用 Ollama `qwen3-embedding:0.6b`。
- 删除知识库时同步清理 OSS 文件与向量数据，避免脏数据残留。

### 2. 大模型对话与会话管理

- DeepSeek 通过 OpenAI 兼容协议接入，`/ai/chat` 返回 `Flux<String>` 流式响应。
- 自定义 `RedisChatMemory` 实现会话记忆持久化，按用户维度隔离会话，支持历史列表、详情与删除。
- 通过 Function Calling 注册护理项目查询工具，模型可自动调用业务查询。

### 3. Dify 智能体对接

- 自建 `DifyClient`（JDK HttpClient）封装对话、会话列表、消息查询、会话删除 4 个接口。
- SSE 流式透传，处理客户端断连、上游中断与超时，转发时关闭 Nginx 缓冲。

### 4. AI 健康评估

- 两阶段大模型推理：先结合老人能力等级、近期意外事件与疾病诊断输出初步等级，再解析体检报告 PDF 输出健康指数、风险等级与系统评分。
- 输出统一解析为 JSON，兼容纯 JSON、Markdown 围栏与夹杂文本；AI 失败降级不阻断主流程，写库前完成全部计算，避免半截数据。

### 5. 智能硬件与报警

- 通过华为云 IoTDA AMQP 订阅设备上报数据，异步线程池处理，批量入库并将最新值写入 Redis Hash。
- 报警规则四层校验：生效时段（支持跨天）、阈值比较（6 种运算符）、沉默周期、持续周期；接收人按角色路由并去重。

### 6. 护理业务

- 老人档案、入住办理、合同管理、床位与楼层管理、护理等级/项目/计划、参观预约与小程序端（微信登录、绑定老人、健康状态查询）。

## 项目结构

```
yikang
├── ykzh-admin                  # 启动模块与系统接口
├── ykzh-framework              # 框架核心配置
├── ykzh-system                 # 系统管理
├── ykzh-common                 # 公共组件
├── ykzh-nursing       # 护理业务核心
├── ykzh-ai                     # AI 能力模块
├── ykzh-oss                    # 对象存储
├── ykzh-quartz                 # 定时任务
├── ykzh-generator              # 代码生成
├── sql                         # 数据库脚本
├── Jenkinsfile                 # CI 构建脚本
└── pom.xml                     # 父 POM
```

## 本地启动

### 1. 准备环境

- JDK 17、Maven 3.8+
- MySQL 8.0、Redis 6+
- 可选：Ollama（本地对话与向量模型）、Dify 服务、华为云 IoTDA、阿里云 OSS

### 2. 初始化数据库

```bash
mysql -u root -p -e "CREATE DATABASE xhzb DEFAULT CHARSET utf8mb4;"
mysql -u root -p xhzb < sql/xhzb.sql
mysql -u root -p xhzb < sql/quartz.sql
```

### 3. 配置环境变量

复制 `.env.example` 为 `.env` 并填写实际值，或以环境变量方式注入。**请勿将真实密钥提交到仓库。**

### 4. 启动

```bash
mvn clean package -DskipTests
java -jar ykzh-admin/target/ykzh-admin.jar
```

默认访问地址：`http://localhost:9000`，接口文档：`http://localhost:9000/swagger-ui.html`。

## 配置说明

所有敏感配置均已改为环境变量占位，常用变量如下：

| 变量 | 说明 |
| --- | --- |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 数据库连接 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | 业务 Redis |
| `VECTOR_REDIS_HOST` / `VECTOR_REDIS_PORT` | 向量库专用 Redis |
| `DEEPSEEK_API_KEY` | DeepSeek API Key |
| `DIFY_BASE_URL` / `DIFY_API_KEY` | Dify 服务地址与密钥 |
| `WECHAT_APPID` / `WECHAT_SECRET` | 微信小程序 |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` / `OSS_BUCKET_NAME` | 阿里云 OSS |
| `HUAWEI_IOT_AK` / `HUAWEI_IOT_SK` / `HUAWEI_IOT_PROJECT_ID` | 华为云 IoTDA |
| `TOKEN_SECRET` | JWT 签名密钥，生产环境必须修改 |

## 说明

本项目在 RuoYi-Vue 开源框架基础上进行业务与 AI 能力扩展，框架自带功能（用户、角色、菜单、代码生成等）版权归原作者所有，遵循原项目 LICENSE。
