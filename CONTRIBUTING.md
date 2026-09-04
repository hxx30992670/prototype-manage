# 参与贡献

感谢你对开源核心的兴趣。请只向**公开仓库**提交开源范围内的改动。

## 公开仓库可以接收什么

- 资产库、版本、预览沙箱、评论、附件、分享、权限、主题等开源功能
- 文档、测试、无障碍和安全修复
- 不依赖空间标注实现的通用改进

请基于公开仓库的 `master` 开分支，例如 `fix/preview-toolbar`，然后向 `master` 发 Pull Request。

## 请不要做的事

- 不要把空间标注、定位评论覆盖层、标注桥接脚本或相关数据表实现提交到公开仓库
- 不要执行 `git push --all origin`，也不要把 `feat/spatial-annotation` 等标注分支推到公开 remote
- 不要在 Issue 里粘贴商业版源码

空间标注是商业功能。如需该能力，请通过 README 中的联系方式洽谈，而不是在公开仓库实现一份替代品并要求合并。

## 本地开发

见 [docs/runbooks/local-development.md](docs/runbooks/local-development.md)。

提交前请在 `apps/web` 运行已有的 `pnpm typecheck` 与相关测试；不要为纯文案或样式改动新建空测试文件。
