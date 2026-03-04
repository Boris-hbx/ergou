# 规格: memory-crud

> **关联设计**: [../../design.md](../../design.md)
> **状态**: final

---

## REQ-1: 记忆保存

系统 SHALL 支持通过API和Tool两种方式创建记忆。每条记忆 SHALL 包含 category（fact/habit/personality/intent）和 content（不超过200字符）。

#### Scenario: 通过Tool保存
- **GIVEN** 用户在对话中说"帮我记住我对花生过敏"
- **WHEN** 二狗调用 save_memory(category: "fact", content: "对花生过敏")
- **THEN** 数据库新增一条记忆
- **AND** 记忆的 created_at 和 last_accessed_at 为当前时间
- **AND** access_count 为 0

#### Scenario: content超长
- **WHEN** save_memory 的 content 超过200字符
- **THEN** 系统自动截断到200字符后保存

#### Scenario: category非法
- **WHEN** save_memory 的 category 不在 [fact, habit, personality, intent] 中
- **THEN** 返回 400 错误，不保存

---

## REQ-2: 记忆查询

系统 SHALL 支持查询用户的所有记忆，支持按 category 过滤。结果按 last_accessed_at DESC 排序。

#### Scenario: 查询全部
- **WHEN** GET /api/memories
- **THEN** 返回该用户的所有记忆，按最近访问时间倒序
- **AND** 不返回其他用户的记忆

#### Scenario: 按分类过滤
- **WHEN** GET /api/memories?category=fact
- **THEN** 只返回 category 为 fact 的记忆

---

## REQ-3: 记忆删除

系统 SHALL 支持删除单条记忆和清除全部记忆。删除 SHALL 是物理删除（不是软删除）。

#### Scenario: 删除单条
- **WHEN** DELETE /api/memories/:id
- **THEN** 该记忆从数据库中物理删除
- **AND** 返回 204

#### Scenario: 删除不存在的记忆
- **WHEN** DELETE /api/memories/:id 且 id 不存在
- **THEN** 返回 404

#### Scenario: 清除全部
- **WHEN** DELETE /api/memories（带确认header）
- **THEN** 该用户的所有记忆被物理删除
- **AND** 不影响其他用户的记忆

---

## REQ-4: 记忆注入到Prompt

系统 SHALL 在构建对话prompt时，自动注入用户最近访问的记忆（最多20条）。

#### Scenario: 正常注入
- **GIVEN** 用户有 N 条记忆（N > 0）
- **WHEN** 用户发送消息触发对话
- **THEN** system prompt 中包含 `## 你对这个用户的记忆` 段落
- **AND** 最多包含20条记忆（N > 20时取最近访问的20条）
- **AND** 每条记忆标注分类和ID
- **AND** 被注入记忆的 last_accessed_at 更新为当前时间
- **AND** 被注入记忆的 access_count 加 1

#### Scenario: 新用户无记忆
- **GIVEN** 用户有 0 条记忆
- **WHEN** 用户发送消息
- **THEN** system prompt 中 **不包含** 记忆段落

#### Scenario: 记忆内容sanitize
- **WHEN** 记忆content包含 `<script>alert('xss')</script>`
- **THEN** 注入prompt时 `<` `>` 被替换为空格
- **AND** 不影响LLM的正常理解

---

## REQ-5: 二狗记忆行为规范

二狗在涉及记忆时 SHALL 遵循以下行为规范。

#### Scenario: 自然引用
- **WHEN** 对话话题与已有记忆相关
- **THEN** 二狗自然地在回复中融入记忆信息
- **AND** 不使用"根据我的记忆..."这种机械表达
- **AND** 不逐条列出所有记忆

#### Scenario: 不编造记忆
- **WHEN** 用户问起二狗没有记录的信息
- **THEN** 二狗 SHALL NOT 编造不存在的记忆
- **AND** 二狗可以说"这个我没记过"或"你之前没跟我说过"

#### Scenario: 记忆保存确认
- **WHEN** 用户要求记住某信息
- **THEN** 二狗调用tool后给出简洁确认
- **AND** 确认语气符合毒舌损友人设（不是"好的亲，已帮您记录~"）

---

## 验收检查清单

- [ ] 所有 Scenario 通过
- [ ] 记忆不能跨用户泄露
- [ ] prompt注入测试通过（含恶意记忆内容）
- [ ] 200字截断正常工作
- [ ] 二狗引用记忆时语气符合人设
