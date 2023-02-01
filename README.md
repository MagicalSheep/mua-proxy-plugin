# MUA-Proxy-Plugin

将代理服务器列表与 [frp](https://github.com/fatedier/frp) 节点进行同步的 [Velocity](https://github.com/PaperMC/Velocity) 插件，简单、安全地通过 frp 动态增减子服，组建联合 Minecraft 服务器。

![](/structure.png)

## 如何使用

在根目录下使用如下指令进行编译：

```shell
./gradlew shadowJar
```

编译完成后，在 `/build/libs` 目录下找到插件，将插件放入 Velocity 的插件目录 `plugins` 中，启动 Velocity。

在 frp 的服务端配置文件 `frps.ini` 中，添加下列配置：

```ini
[plugin.register]
addr = 127.0.0.1:8080
path = /register
ops = NewProxy

[plugin.unregister]
addr = 127.0.0.1:8080
path = /unregister
ops = CloseProxy

[plugin.login]
addr = 127.0.0.1:8080
path = /login
ops = Login
```

每一个接入 frp 服务端的代理节点，都将被视作一个 Minecraft 服务器自动热更新至 Velocity 的代理服务器列表中。当 frp 代理节点断开时，也将自动从 Velocity 的代理服务器列表中移除。

## 注意事项

请确保运行 frp 服务端的服务器网络仅开放管理端口，否则将引起安全问题。推荐使用 [Docker 桥接网络](https://docs.docker.com/network/bridge/) 运行 Velocity 与 frp 服务端。