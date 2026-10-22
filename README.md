# TcsBaiduCdnRefresh

Minecraft 插件，通过命令调用百度云 CDN 缓存刷新接口。

## 命令

| 命令 | 说明 |
|---|---|
| `/baiducdnrefresh <url> [directory\|file]` | 提交 CDN 刷新任务 |

### 参数

- `<url>`：要刷新的 URL
- `[directory|file]`：刷新类型，默认 `file`；为 `directory` 时 URL 必须以 `/` 结尾

### 示例
