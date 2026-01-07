# Cursor 规则索引

本目录包含DialTestCenter项目的Cursor规则。这些规则有助于在整个代码库中维护代码质量、一致性和最佳实践。

## 规则分类

### 🏗️ 项目结构与架构
- **[project-structure.mdc](project-structure.mdc)** - 项目概览、目录结构和关键组件
- **[backend-java.mdc](backend-java.mdc)** - Spring Boot后端Java开发指南
- **[frontend-react.mdc](frontend-react.mdc)** - React TypeScript前端开发指南
- **[database.mdc](database.mdc)** - 数据库模式和迁移指南
- **[api-design.mdc](api-design.mdc)** - API设计和RESTful约定

### 📝 文档与标准
- **[javadoc.mdc](javadoc.mdc)** - Java类和方法的Javadoc文档要求
- **[yaml-documentation.mdc](yaml-documentation.mdc)** - YAML API文档生成要求
- **[internationalization.mdc](internationalization.mdc)** - 国际化(i18n)指南

### 🔧 代码质量与最佳实践
- **[conditional-statements.mdc](conditional-statements.mdc)** - 条件语句和循环块必须使用大括号
- **[else-branch.mdc](else-branch.mdc)** - else if链必须包含else分支
- **[instanceof-check.mdc](instanceof-check.mdc)** - 向下转换前必须进行instanceof检查
- **[exception-handling.mdc](exception-handling.mdc)** - 异常处理标准和最佳实践
- **[logging.mdc](logging.mdc)** - 日志记录标准和最佳实践

### 🧪 测试与开发
- **[llt-generation.mdc](llt-generation.mdc)** - 单元测试要求和指南

## 快速参考

### Java开发
1. 从[project-structure.mdc](project-structure.mdc)开始了解项目概览
2. 遵循[backend-java.mdc](backend-java.mdc)的开发指南
3. 应用[javadoc.mdc](javadoc.mdc)的文档标准
4. 使用[conditional-statements.mdc](conditional-statements.mdc)、[else-branch.mdc](else-branch.mdc)和[instanceof-check.mdc](instanceof-check.mdc)确保代码质量
5. 遵循[exception-handling.mdc](exception-handling.mdc)和[logging.mdc](logging.mdc)进行错误处理
6. 使用[llt-generation.mdc](llt-generation.mdc)生成测试

### 前端开发
1. 从[project-structure.mdc](project-structure.mdc)开始了解项目概览
2. 遵循[frontend-react.mdc](frontend-react.mdc)的React TypeScript指南
3. 应用[internationalization.mdc](internationalization.mdc)进行国际化支持

### API开发
1. 遵循[api-design.mdc](api-design.mdc)的RESTful约定
2. 使用[yaml-documentation.mdc](yaml-documentation.mdc)生成API文档
3. 应用[backend-java.mdc](backend-java.mdc)实现控制器

## 规则应用

### 自动应用
- **project-structure.mdc** - 始终应用以提供项目上下文
- **conditional-statements.mdc** - 应用于Java、JavaScript、TypeScript文件
- **else-branch.mdc** - 应用于Java、JavaScript、TypeScript文件
- **instanceof-check.mdc** - 应用于Java、JavaScript、TypeScript文件
- **javadoc.mdc** - 应用于Java文件
- **backend-java.mdc** - 应用于Java文件
- **frontend-react.mdc** - 应用于React TypeScript文件
- **exception-handling.mdc** - 应用于Java文件
- **logging.mdc** - 应用于Java文件

### 手动应用
- **database.mdc** - 在处理数据库模式时应用
- **api-design.mdc** - 在设计REST API时应用
- **yaml-documentation.mdc** - 在生成API文档时应用
- **internationalization.mdc** - 在处理i18n功能时应用
- **llt-generation.mdc** - 在生成单元测试时应用

## 规则依赖

某些规则引用其他规则以实现完整覆盖：

- **backend-java.mdc** → 引用所有代码质量和文档规则
- **project-structure.mdc** → 引用所有开发需求规则
- **javadoc.mdc** → 与**llt-generation.mdc**集成用于测试文档

## 贡献指南

添加新规则时：
1. 遵循既定的命名约定：`rule-name.mdc`
2. 包含带有`globs`或`alwaysApply`元数据的正确frontmatter
3. 提供带有✅正确和❌错误模式的清晰示例
4. 添加相关规则的交叉引用
5. 使用新规则信息更新此README.md

## 验证

使用提供的验证脚本确保规则合规性：
- `./scripts/validate-code-format.sh` - 验证代码格式规则
- `./scripts/validate-conditional-statements.sh` - 验证条件语句规则
- `./scripts/validate-javadoc.sh` - 验证Javadoc文档规则
- `./scripts/validate-cursor-rules.sh` - 验证所有Cursor规则
