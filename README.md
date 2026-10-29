# 海航票价监控

和德州扑克部署在同一台机器上，端口、数据库、页面目录都分开。

| | 德州扑克 | 本系统 |
| --- | --- | --- |
| 后端 | 8080 | 8081 |
| 前端开发 | 3000 | 3001 |
| 页面 | http://47.253.226.88/school/ | http://47.253.226.88/ffl/ |
| 数据库 | poker_db | hnair_ffl |
| 静态目录 | /opt/poker/frontend/build | /opt/ffl/frontend/build |

开发时后端默认用本机 H2 文件库 `backend/data/ffl`，不占用扑克的 Postgres。生产用 `prod` profile，连 `hnair_ffl`。

## 本地启动

```bash
cd backend && mvn spring-boot:run
cd frontend && npm install && npm start
```

页面在 http://localhost:3001/ffl/ ，接口由开发服务器转到 8081 的 `/ffl`。直接打开根路径没有页面。

## 管理员

用户名 `admin`，密码 `admin123456`。管理员只发邀请码、改监控间隔和目标价格，不建监控。普通用户注册必须填邀请码，最多 10 条监控。

监控必填出发机场、到达机场、日期。航班号可选，只填数字，例如 `7428`。经济舱价格等于目标价（默认 199）算命中，余票写在结果里。`A` 表示 9 张以上。

首次查到库存会记一条历史，之后只有余票从无到有、从有到无，或余票数字变化（含 `A`）才再记一条。点开某一条监控可以看这些节点。每条监控可以分别勾选「有无变动」和「库存数字变动」邮件提醒，判定和历史是同一套。邮箱在页面里自己填 SMTP 登录信息和接收邮箱，没有统一的发信服务器。

## 查询

只请求官网低价接口 `airLowerPriceShop`，签名和 sens 按官网页面在没有设备指纹脚本时的做法生成。请求串行，两次之间至少隔 3 秒，单条最短 60 秒，默认 300 秒。返回访问受限就整站暂停 10 分钟。没有代理池，也不做验证码或指纹绕过。

## 生产

先建库 `hnair_ffl`，再：

```bash
cd backend && mvn -DskipTests package
SPRING_PROFILES_ACTIVE=prod DB_URL=jdbc:postgresql://127.0.0.1:5432/hnair_ffl DB_USERNAME=ffl DB_PASSWORD=... java -jar target/ffl-monitor-0.0.1-SNAPSHOT.jar
cd frontend && npm install && npm run build
```

把 `frontend/build` 放到 `/opt/ffl/frontend/build`，并做链接 `ln -sfn /opt/ffl/frontend/build /opt/www/ffl`。Nginx 使用 `deploy/nginx-ffl.conf`，和扑克那份相同，只启用其中一份。页面是 http://47.253.226.88/ffl/ ，根路径返回 404。后端只监听 `127.0.0.1:8081`，上下文是 `/ffl`。
