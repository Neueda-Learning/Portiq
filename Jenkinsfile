// Continuous delivery for Portiq.
//
// GitHub Actions owns CI - it runs the backend test suite and the frontend build
// on every push and pull request. This pipeline owns CD: it builds the container
// images and deploys the stack onto the Linux host Jenkins runs on.
//
// Setup instructions, including the credentials referenced below, are in
// DEPLOYMENT.md.

pipeline {
    agent any

    options {
        timestamps()
        // Two deploys writing the same compose project at once would race.
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        COMPOSE_FILE         = 'docker-compose.prod.yml'
        COMPOSE_PROJECT_NAME = 'portiq'
        // 8090, not 8080: Jenkins itself defaults to 8080 and would collide.
        APP_PORT             = '8090'
        IMAGE_TAG            = "${env.BUILD_NUMBER}"

        // Compose interpolates these when it parses the file, which happens on
        // every compose command - not just 'up'. They are declared at pipeline
        // level so 'build' and 'config' see them too. Jenkins masks the values
        // in the console log.
        DB_PASSWORD       = credentials('portiq-db-password')
        DB_ROOT_PASSWORD  = credentials('portiq-db-root-password')
        JWT_SECRET        = credentials('portiq-jwt-secret')
        DB_ENCRYPTION_KEY = credentials('portiq-encryption-key')
        OWNER_PASSWORD    = credentials('portiq-owner-password')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git --no-pager log -1 --oneline'
            }
        }

        stage('Verify tooling') {
            steps {
                sh '''
                    set -eu
                    docker version
                    docker compose version
                '''
            }
        }

        stage('Validate compose') {
            steps {
                // Fails fast on a malformed file or a missing required variable,
                // before anything is built or torn down.
                sh 'docker compose config --quiet'
            }
        }

        stage('Build images') {
            steps {
                sh 'docker compose build --pull'
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    set -eu

                    # Optional AI features - the portfolio summary and statement
                    # image import. Read from a file on the host rather than a
                    # Jenkins credential precisely because they are optional: a
                    # missing credential in the environment block above aborts
                    # the whole build, whereas a deployment with no key here just
                    # reports "not configured" for those two features and works
                    # normally otherwise. Create it with:
                    #   sudo install -d -m 750 -o jenkins -g jenkins /etc/portiq
                    #   sudo -u jenkins tee /etc/portiq/insights.env <<'EOF'
                    #   INSIGHTS_API_KEY=...
                    #   EOF
                    if [ -f /etc/portiq/insights.env ]; then
                        echo "Loading optional AI configuration"
                        set -a; . /etc/portiq/insights.env; set +a
                    else
                        echo "No /etc/portiq/insights.env - AI features stay disabled"
                    fi

                    docker compose up -d --remove-orphans
                    docker compose ps
                '''
            }
        }

        stage('Smoke test') {
            steps {
                // Proves the whole chain: nginx is serving, the proxy route
                // works, the backend booted, and it reached MySQL.
                sh '''
                    set -eu

                    echo "Waiting for the backend to report healthy..."
                    for attempt in $(seq 1 30); do
                        if curl -fsS "http://localhost:${APP_PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
                            echo "Backend is UP (attempt ${attempt})"
                            break
                        fi
                        if [ "${attempt}" -eq 30 ]; then
                            echo "Backend did not become healthy within 150s"
                            exit 1
                        fi
                        sleep 5
                    done

                    echo "Checking the SPA is served..."
                    curl -fsS -o /dev/null "http://localhost:${APP_PORT}/"

                    echo "Checking client-side routing falls back to the shell..."
                    curl -fsS -o /dev/null "http://localhost:${APP_PORT}/holdings"

                    echo "Smoke test passed."
                '''
            }
        }
    }

    post {
        failure {
            // The container logs are what you actually need to diagnose a failed
            // deploy, and they are gone once the workspace is reused.
            sh '''
                docker compose ps || true
                docker compose logs --tail 200 --no-color || true
            '''
        }
        always {
            // Capture the deployed stack's logs as a build artifact. Printing them to the
            // console is not enough - console output is trimmed by the log rotator after
            // 20 builds, while an archived artifact stays attached to the build that
            // produced it, which is what makes "it broke three deploys ago" answerable.
            sh '''
                mkdir -p logs/deploy
                docker compose ps > logs/deploy/compose-ps.txt 2>&1 || true
                docker compose logs --no-color --tail 2000 > logs/deploy/compose.log 2>&1 || true
                docker compose logs --no-color --tail 2000 backend > logs/deploy/backend.log 2>&1 || true
            '''
            archiveArtifacts artifacts: 'logs/deploy/*', allowEmptyArchive: true, fingerprint: false
            sh 'docker image prune -f --filter "until=168h" || true'
        }
    }
}
