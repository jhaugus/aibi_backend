#使用java和maven环境
FROM openjdk:8

# 项目的端口，内部服务端口
EXPOSE 8101
# 切换到容器内部的 /app
WORKDIR /app

## 添加要运行的jar文件
COPY tech_bi-0.0.1.jar /app/App.jar
# 容器启动后运行的命令

CMD ["java","-jar","/app/App.jar","--spring.profiles.active=prod"]
