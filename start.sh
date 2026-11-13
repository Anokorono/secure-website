#!/bin/bash
# 项目启动脚本
# 用法: ./start.sh [init|run|package]

ACTION=${1:-run}

case "$ACTION" in
    init)
        echo ">>> 初始化数据库..."
        mysql -uroot -p < schema.sql
        echo ">>> 数据库初始化完成"
        ;;
    package)
        echo ">>> 打包项目..."
        mvn clean package -DskipTests
        echo ">>> 打包完成，jar 位于 target/secure-website-1.0.0.jar"
        ;;
    run)
        echo ">>> 启动项目..."
        # 生产环境请通过环境变量注入敏感信息（未设置则使用 application.yml 中的默认值）
        # export DB_USERNAME=root
        # export DB_PASSWORD=你的数据库密码
        mvn spring-boot:run
        ;;
    *)
        echo "用法: $0 {init|run|package}"
        echo "  init   - 初始化数据库（需要先配置 MySQL）"
        echo "  run    - 启动项目"
        echo "  package- 打包项目"
        exit 1
        ;;
esac
