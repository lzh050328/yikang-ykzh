pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    parameters {
        string(name: 'GIT_URL', defaultValue: 'https://github.com/lzh050328/yikang.git', description: '代码仓库地址')
        string(name: 'GIT_TAG', defaultValue: 'refs/heads/day06', description: '分支，如 refs/heads/day06 或 */master')
        string(name: 'services', defaultValue: 'ykzh-admin', description: '要构建/部署的模块（多个用逗号分隔）')
        string(name: 'DOCKER_TAG', defaultValue: 'latest', description: '镜像 tag，建议用构建号')
    }

    tools {
        // 项目已统一到 Java 17（pom 的 java.version=17），Jenkins 里用已有的 jdk17 即可
        jdk 'jdk17'
        maven 'maven'
    }

    stages {
        stage('清除工作空间') {
            steps {
                cleanWs()
            }
        }

        stage('拉取Git代码') {
            steps {
                echo "正在拉取代码... 分支: ${params.GIT_TAG}，服务: ${params.services}"
                checkout([$class: 'GitSCM',
                          branches: [[name: params.GIT_TAG]],
                          doGenerateSubmoduleConfigurations: false,
                          extensions: [],
                          submoduleCfg: [],
                          userRemoteConfigs: [[credentialsId: 'Gitee_ID', url: params.GIT_URL]]
                ])
                sh 'pwd && ls -la'
            }
        }

        stage('Maven打包') {
            steps {
                sh '''
                    set -e
                    # tools 里配了 maven 时 mvn 应在 PATH 中；没有就退回 Jenkins 容器内固定路径
                    if command -v mvn >/dev/null 2>&1; then
                        MVN=mvn
                    elif [ -x /var/jenkins_home/maven/bin/mvn ]; then
                        MVN=/var/jenkins_home/maven/bin/mvn
                    else
                        echo "找不到 mvn，请检查 Jenkins 全局工具配置"; exit 1
                    fi
                    $MVN -v
                    java -version
                    # JDK 版本自检：低于 17 直接给出明确提示，避免出现 "invalid flag: -proc:full" 这种迷惑报错
                    JAVA_VER=$(java -version 2>&1 | awk -F'"' '/version/ {print $2}' | cut -d. -f1)
                    echo "检测到 JDK 主版本: $JAVA_VER"
                    if [ "$JAVA_VER" -lt 17 ]; then
                        echo "JDK 版本过低（需要 17+），请检查 Jenkins 全局工具配置里的 jdk17"
                        exit 1
                    fi
                    # -Dmaven.test.skip=true：连测试代码都不编译（src/test 里有调外部 API 的 Demo）
                    $MVN clean package -Dmaven.test.skip=true -B
                '''
            }
            post {
                success {
                    archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                }
            }
        }

        stage('构建镜像') {
            steps {
                script {
                    for (ds in params.services.tokenize(',')) {
                        echo "构建镜像: ${ds}:${params.DOCKER_TAG}"
                        sh "cd ./${ds}/target/ && docker build -t ${ds}:${params.DOCKER_TAG} -f ../Dockerfile ."
                    }
                }
            }
        }

        stage('部署服务') {
            steps {
                script {
                    for (ws in params.services.tokenize(',')) {
                        echo "部署升级: ${ws}"
                        sh "chmod +x ./${ws}/deploy.sh && sh ./${ws}/deploy.sh ${ws} ${params.DOCKER_TAG}"
                    }
                }
            }
        }
    }

    post {
        success {
            echo "构建部署成功: ${params.services}:${params.DOCKER_TAG}"
        }
        failure {
            echo "构建失败，请查看上面的日志"
        }
        always {
            echo '任务构建完毕'
        }
    }
}
