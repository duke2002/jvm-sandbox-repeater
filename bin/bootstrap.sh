#!/usr/bin/env bash

# exit shell with err_code
# $1 : err_code
# $2 : err_msg
exit_on_err()
{
    [[ ! -z "${2}" ]] && echo "${2}" 1>&2
    exit ${1}
}

PID=$(ps -ef | grep "repeater-bootstrap.jar" | grep "java" | grep -v grep | awk '{print $2}')

expr ${PID} "+" 10 &> /dev/null

# if occurred error,exit
if [ ! $? -eq 0 ] || [ "" = "${PID}" ] ;then
    echo ""
else
    echo "found target pid exist, pid is ${PID}, kill it..."
    kill -9 ${PID}
fi

if [ ! -f "${HOME}/.sandbox-module/repeater-bootstrap.jar" ]; then
    echo "repeater-bootstrap.jar not found, try to install";
    sh ./install-local.sh || exit_on_err 1 "install repeater failed"
fi

#如果以 agent 模式启动，建议将 suspend=n 改为 suspend=y 即可
#agent 模式下的启停都跟随应用，配置好参数后，应用启动则启动，应用停止则停止。
#启动成功检查方法：
#   1. 应用正常启动，无报错
#   2. 看日志，日志路径~/logs/sandbox/repeater/repeater.log。
#需要关注的日志内容主要是是以下几种：
#   1. 以 enable plugin开头的，查看插件挂载情况
#   2. register event bus success in repeat-register，说明插件加载完成，模块加载完成，可以开始录制和回放的行为。
#PS：其中会夹杂很多以Register bean:name=的日志，这些日志说明了被 repeater 记录下来的运行在应用 jvm 中的实例，会在 java 回放的时候用到。但是与启动 repeater 关系不大，在启动阶段可忽略。

${JAVA_HOME}/bin/java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000 \
     -javaagent:${HOME}/sandbox/lib/sandbox-agent.jar=server.port=8820\;server.ip=0.0.0.0 \
     -Dapp.name=repeater \
     -Dapp.env=daily \
     -jar ${HOME}/.sandbox-module/repeater-bootstrap.jar
