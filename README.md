# HyperSelect

简体中文 | [English](README_en.md)

面向 HyperOS 的定制模块，支持调整文本选择菜单、系统分享、打开方式与控制中心磁贴

---

## 特性

- **隐藏 AI 入口**：屏蔽选中文本时首位的「AI 写作」、「问小爱」
- **文本选择菜单**：支持自定义排序，隐藏无用选项
- **系统分享**：常用应用置顶与排序，隐藏多余分享目标
- **打开方式**：常用打开方式置顶与排序，收纳冷门选项
- **磁贴管理**：搜索应用磁贴，添加至控制中心，备份和恢复布局
- **即时生效**：模块启用并重启后，后续修改配置无需再次重启手机

---

## 效果预览

<p align="center">
  <img src="docs/preview_text_select.png" width="480" alt="文本选择对比" />
</p>

<p align="center">
  <img src="docs/preview_menu_sort.png" width="560" alt="菜单排序" />
</p>

<p align="center">
  <img src="docs/preview_share.png" width="380" alt="分享置顶" />
</p>

---

## 运行环境

- **系统**：Xiaomi HyperOS、
- **框架**：LSPosed（基于 LibXposed API 102）
- **作用域**：系统框架、IntentResolver
---

## 开源协议

[GNU General Public License v3.0](LICENSE)
