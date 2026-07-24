# Codex UI 发布指南

Codex UI 的首次版本需要在 JetBrains Marketplace 手动提交。首次审核通过后，
后续版本由受保护的 GitHub Actions 工作流自动测试、签名和发布。

## 相关入口

- [GitHub Actions](https://github.com/ichaly/codex-launcher/actions)
- [Release 工作流](https://github.com/ichaly/codex-launcher/actions/workflows/release.yml)
- [GitHub Releases](https://github.com/ichaly/codex-launcher/releases)
- [JetBrains Marketplace 插件页](https://plugins.jetbrains.com/plugin/33103-codex-ui)
- [JetBrains Marketplace 作者后台](https://plugins.jetbrains.com/author/me)
- [GitHub Actions 变量设置](https://github.com/ichaly/codex-launcher/settings/variables/actions)
- [marketplace-release 环境设置](https://github.com/ichaly/codex-launcher/settings/environments)

## 一次性配置

发布工作流使用受保护的 GitHub Environment `marketplace-release`。该环境要求
仓库所有者 `iChaly` 审批，且只允许 `v*` 标签触发部署。

环境中需要配置以下 GitHub Actions Secrets：

- `CERTIFICATE_CHAIN`：插件签名证书内容
- `PRIVATE_KEY`：加密私钥内容
- `PRIVATE_KEY_PASSWORD`：私钥密码
- `PUBLISH_TOKEN`：JetBrains Marketplace Token

仓库级 Actions Variable `JETBRAINS_MARKETPLACE_ENABLED` 必须设置为 `true`，
自动发布步骤才会调用 JetBrains Marketplace。变量本身不会触发工作流。

禁止把证书、私钥、密码或 Token 提交到仓库。

## 后续版本发布流程

以下示例把版本从 `1.1.18` 升级到 `1.1.19`。

1. 在 `build.gradle.kts` 中更新 `version`，并同步更新 `plugin.xml` 的版本说明。
2. 本地验证：

   ```bash
   ./gradlew test buildPlugin
   ```

3. 提交并推送到 `main`：

   ```bash
   git add build.gradle.kts src/main/resources/META-INF/plugin.xml
   git commit -m "chore: release 1.1.19"
   git push origin main
   ```

4. 在已经进入 `main` 的发布提交上创建并推送同版本标签：

   ```bash
   git tag -a v1.1.19 -m "Codex UI v1.1.19"
   git push origin v1.1.19
   ```

5. 打开 [GitHub Actions](https://github.com/ichaly/codex-launcher/actions)，
   进入等待审批的 `Release` 运行。
6. 点击 **Review deployments**，勾选 `marketplace-release`，再点击
   **Approve and deploy**。
7. 审批后，工作流会自动：

   - 校验标签版本与 `build.gradle.kts` 的版本一致；
   - 校验标签对应提交已经合并到 `main`；
   - 运行测试并签名插件；
   - 发布新版本到 JetBrains Marketplace；
   - 创建 GitHub Release 并上传签名 ZIP。

## 发布保护

- 只有推送 `v*` 标签才会触发 Release 工作流。
- 标签必须指向已经进入 `origin/main` 的提交。
- 标签版本必须与项目版本完全一致。
- 发布前必须由 `iChaly` 审批 `marketplace-release` 环境。
- 环境审批前，工作流无法读取其中的签名与发布 Secrets。

因此，普通 Pull Request、未合并代码或未经审批的标签都不能直接发布插件。

## 失败处理

在 [Release 工作流](https://github.com/ichaly/codex-launcher/actions/workflows/release.yml)
中打开失败的运行，先查看具体步骤日志。修复后应提交到 `main` 并使用新的版本号和
标签重新发布；不要随意删除或覆盖已经对外发布的版本标签。

JetBrains 官方资料：

- [发布插件](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)
- [插件签名](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)
