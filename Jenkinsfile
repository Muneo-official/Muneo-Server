// Jenkins Multibranch Pipeline environment:
// GHCR_IMAGE, DEV01_HOST, DEV01_USER, DEV01_SSH_PORT,
// DEV01_DEPLOY_PATH, DEV_DOCKER_NETWORK
//
// Agent template environment:
// BUILDKIT_HOST=unix:///run/buildkit/buildkitd.sock
//
// Jenkins credentials:
// ghcr-credential, muneo-dev01-ssh, muneo-dev01-known-hosts
//
// The dynamic agent labeled "docker-agent" needs git, buildctl v0.30.0,
// ssh, scp and base64. buildctl connects to the daemon through BUILDKIT_HOST.
pipeline {
    agent { label 'docker-agent' }

    options {
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
        timestamps()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT = sh(
                        script: 'git rev-parse HEAD',
                        returnStdout: true
                    ).trim()
                }
            }
        }

        stage('Prepare image reference') {
            when {
                branch 'dev'
            }
            steps {
                sh '''
                    set -eu
                    : "${GHCR_IMAGE:?Configure GHCR_IMAGE in Jenkins}"
                    : "${BUILDKIT_HOST:?Configure BUILDKIT_HOST in Jenkins}"
                    : "${GIT_COMMIT:?GIT_COMMIT is not available}"
                '''
                script {
                    env.SPRING_IMAGE_REF = "${env.GHCR_IMAGE}:${env.GIT_COMMIT}"
                }
            }
        }

        stage('Build and push Spring image') {
            when {
                branch 'dev'
            }
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'ghcr-credential',
                        usernameVariable: 'GHCR_PUSH_USER',
                        passwordVariable: 'GHCR_PUSH_TOKEN'
                    )
                ]) {
                    sh '''
                        set -eu

                        DOCKER_CONFIG="$(mktemp -d)"
                        export DOCKER_CONFIG
                        trap 'rm -rf "${DOCKER_CONFIG}"' EXIT HUP INT TERM
                        umask 077

                        GHCR_AUTH="$(printf '%s:%s' "${GHCR_PUSH_USER}" "${GHCR_PUSH_TOKEN}" \
                            | base64 | tr -d '\\n')"
                        printf '{"auths":{"ghcr.io":{"auth":"%s"}}}\\n' "${GHCR_AUTH}" \
                            > "${DOCKER_CONFIG}/config.json"

                        buildctl --version
                        buildctl --addr "${BUILDKIT_HOST}" build \
                            --progress plain \
                            --frontend dockerfile.v0 \
                            --local context=. \
                            --local dockerfile=. \
                            --opt filename=Dockerfile \
                            --import-cache "type=registry,ref=${GHCR_IMAGE}:buildcache" \
                            --export-cache "type=registry,ref=${GHCR_IMAGE}:buildcache,mode=max" \
                            --output "type=image,name=${SPRING_IMAGE_REF},push=true"
                    '''
                }
            }
        }

        stage('Deploy DEV') {
            when {
                branch 'dev'
            }
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'ghcr-credential',
                        usernameVariable: 'GHCR_PULL_USER',
                        passwordVariable: 'GHCR_PULL_TOKEN'
                    ),
                    file(
                        credentialsId: 'muneo-dev01-known-hosts',
                        variable: 'DEV01_KNOWN_HOSTS'
                    )
                ]) {
                    sshagent(credentials: ['muneo-dev01-ssh']) {
                        sh '''
                            set -eu
                            : "${DEV01_HOST:?Configure DEV01_HOST in Jenkins}"
                            : "${DEV01_USER:?Configure DEV01_USER in Jenkins}"
                            : "${DEV01_DEPLOY_PATH:?Configure DEV01_DEPLOY_PATH in Jenkins}"

                            DEV01_SSH_PORT="${DEV01_SSH_PORT:-22}"
                            DEV_DOCKER_NETWORK="${DEV_DOCKER_NETWORK:-muneo-dev-internal}"
                            SSH_TARGET="${DEV01_USER}@${DEV01_HOST}"

                            ssh \
                                -p "${DEV01_SSH_PORT}" \
                                -o "UserKnownHostsFile=${DEV01_KNOWN_HOSTS}" \
                                -o StrictHostKeyChecking=yes \
                                "${SSH_TARGET}" \
                                "mkdir -p '${DEV01_DEPLOY_PATH}'"

                            scp \
                                -P "${DEV01_SSH_PORT}" \
                                -o "UserKnownHostsFile=${DEV01_KNOWN_HOSTS}" \
                                -o StrictHostKeyChecking=yes \
                                docker-compose.yml \
                                "${SSH_TARGET}:${DEV01_DEPLOY_PATH}/docker-compose.yml"

                            printf '%s' "${GHCR_PULL_TOKEN}" \
                                | ssh \
                                    -p "${DEV01_SSH_PORT}" \
                                    -o "UserKnownHostsFile=${DEV01_KNOWN_HOSTS}" \
                                    -o StrictHostKeyChecking=yes \
                                "${SSH_TARGET}" \
                                "docker login ghcr.io --username '${GHCR_PULL_USER}' --password-stdin"

                            ssh \
                                -p "${DEV01_SSH_PORT}" \
                                -o "UserKnownHostsFile=${DEV01_KNOWN_HOSTS}" \
                                -o StrictHostKeyChecking=yes \
                                "${SSH_TARGET}" \
                                "set -eu; \
                                 trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT; \
                                 cd '${DEV01_DEPLOY_PATH}'; \
                                 test -f .env; \
                                 printf '%s\\n' 'SPRING_IMAGE=${SPRING_IMAGE_REF}' > .env.spring; \
                                 docker network inspect '${DEV_DOCKER_NETWORK}' >/dev/null 2>&1 \
                                     || docker network create '${DEV_DOCKER_NETWORK}'; \
                                 docker compose --env-file .env --env-file .env.spring config >/dev/null; \
                                 docker compose --env-file .env --env-file .env.spring pull spring; \
                                 docker compose --env-file .env --env-file .env.spring up -d --no-build --wait --wait-timeout 120; \
                                 docker compose --env-file .env --env-file .env.spring ps"
                        '''
                    }
                }
            }
        }
    }

    post {
        success {
            echo "DEV image: ${env.SPRING_IMAGE_REF ?: 'not built'}"
        }
    }
}
