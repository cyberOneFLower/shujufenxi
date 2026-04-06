// 套利监控 — Jenkins 声明式流水线：测试 → 构建镜像 → 推送仓库 → SSH 热部署（pull + up 替换容器）
//
// ========== Jenkins 侧准备 ==========
// 1. 安装插件：Pipeline、SSH Agent、Credentials Binding
// 2. Agent 需已装 Docker，执行构建的用户能运行 docker（如在 docker 组）
// 3. 新建「流水线」任务，SCM 选 Git，脚本路径：Jenkinsfile
// 4. 凭据（名称可改，但需同步修改下面 credentialsId）：
//    - arb-docker-registry：Username with password（推镜像用，对应 docker login 的仓库域名）
//    - arb-ssh-deploy：SSH Username with private key（热部署登录目标机）
// 5. 构建参数里把 REGISTRY 改成你的仓库前缀（全小写），如 ghcr.io/myname
//
// ========== 部署机 ==========
// 拷贝本仓库 deploy/ 到服务器 DEPLOY_PATH（含 docker-compose.prod.yml）
// 私有镜像仓库时：在部署机先 docker login 同一仓库

pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds(abortPrevious: true)
  }

  parameters {
    string(name: 'REGISTRY', defaultValue: 'ghcr.io/changeme', description: '镜像仓库前缀（小写），不含镜像名。例：ghcr.io/owner')
    booleanParam(name: 'PUSH_IMAGE', defaultValue: true, description: '是否推送镜像（需 arb-docker-registry）')
    booleanParam(name: 'RUN_DEPLOY', defaultValue: true, description: '是否 SSH 热部署（需 arb-ssh-deploy）')
    string(name: 'DEPLOY_HOST', defaultValue: '', description: '部署机 IP/域名，空则跳过部署')
    string(name: 'DEPLOY_USER', defaultValue: 'root', description: 'SSH 用户')
    string(name: 'DEPLOY_PATH', defaultValue: '/opt/arb/deploy', description: '服务器上 deploy 目录（含 docker-compose.prod.yml）')
  }

  environment {
    IMAGE_NAME = 'arb-monitor'
  }

  stages {
    stage('检出') {
      steps {
        checkout scm
      }
    }

    stage('测试') {
      steps {
        sh 'cd backend && mvn -B -q test'
        sh '''
          cd web
          if [ -f package-lock.json ]; then npm ci; else npm install; fi
          npm run build
        '''
      }
    }

    stage('构建镜像') {
      steps {
        script {
          env.GIT_SHORT = sh(returnStdout: true, script: 'git rev-parse --short=7 HEAD').trim()
          env.FULL_IMAGE = "${params.REGISTRY}/${env.IMAGE_NAME}:${env.GIT_SHORT}"
          env.FULL_IMAGE_LATEST = "${params.REGISTRY}/${env.IMAGE_NAME}:latest"
        }
        sh 'docker build -t "$FULL_IMAGE" -t "$FULL_IMAGE_LATEST" -f Dockerfile .'
      }
    }

    stage('推送镜像') {
      when {
        expression {
          return params.PUSH_IMAGE
        }
      }
      steps {
        script {
          env.REG_LOGIN_HOST = params.REGISTRY.split('/')[0]
        }
        withCredentials([
          usernamePassword(credentialsId: 'arb-docker-registry', passwordVariable: 'REG_PASS', usernameVariable: 'REG_USER')
        ]) {
          sh '''
            echo "$REG_PASS" | docker login "$REG_LOGIN_HOST" -u "$REG_USER" --password-stdin
            docker push "$FULL_IMAGE"
            docker push "$FULL_IMAGE_LATEST"
          '''
        }
      }
    }

    stage('热部署') {
      when {
        allOf {
          expression { return params.RUN_DEPLOY }
          expression { return params.DEPLOY_HOST?.trim() ? true : false }
        }
      }
      steps {
        sshagent(credentials: ['arb-ssh-deploy']) {
          sh """
            ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
              ${params.DEPLOY_USER}@${params.DEPLOY_HOST} \
              "set -e; cd '${params.DEPLOY_PATH}'; export ARB_IMAGE='${env.FULL_IMAGE}'; \
               docker compose -f docker-compose.prod.yml pull app; \
               docker compose -f docker-compose.prod.yml up -d --remove-orphans; \
               docker image prune -f"
          """
        }
      }
    }
  }

  post {
    success {
      echo "镜像: ${env.FULL_IMAGE}"
    }
  }
}
