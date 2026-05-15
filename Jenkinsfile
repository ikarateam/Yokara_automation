pipeline {
    agent any

    triggers {
        cron('0 7 * * *')
    }

    parameters {
        choice(
            name: 'APP_ENV',
            choices: ['prod', 'dev'],
            description: 'Môi trường app cần test — prod: com.yokara / com.yokara.v3; dev: com.dev.yokara / com.yokara.dev.v1'
        )
    }

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 120, unit: 'MINUTES')
    }

    environment {
        USER_HOME = '/Users/inmobi'

        // JAVA 17
        JAVA_HOME = '/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home'

        // ANDROID
        ANDROID_HOME = '/Users/inmobi/Library/Android/sdk'
        ANDROID_SDK_ROOT = '/Users/inmobi/Library/Android/sdk'

        // TOOLS
        NODE_BIN = '/opt/homebrew/bin'
        PYTHON_BIN = '/usr/bin/python3'
        IDEVICE_ID_BIN = '/opt/homebrew/bin/idevice_id'
        APPIUM_BIN = '/opt/homebrew/bin/appium'
        ALLURE_BIN = '/opt/homebrew/bin/allure'

        // PATH
        FULL_PATH = '/opt/homebrew/opt/openjdk@17/bin:/Users/inmobi/.local/bin:/opt/homebrew/bin:/usr/local/bin:/Users/inmobi/Library/Android/sdk/platform-tools:/Users/inmobi/Library/Android/sdk/emulator:/Users/inmobi/Library/Android/sdk/cmdline-tools/latest/bin:/bin:/usr/bin:/usr/sbin:/sbin:/Users/inmobi/.nvm/versions/node/v20.18.0/bin'

        // PORTS
        APPIUM_BASE_PORT = '4700'
        IOS_WDA_BASE_PORT = '8100'
        IOS_MJPEG_BASE_PORT = '10100'
        ANDROID_SYSTEM_BASE_PORT = '8200'
        PORT_STEP = '10'

        // PUBLIC URL
        PUBLIC_JENKINS_URL = 'https://mac.ikara.co'
    }

    stages {

        stage('Checkout') {
            steps {
                withEnv([
                    "HOME=${env.USER_HOME}",
                    "JAVA_HOME=${env.JAVA_HOME}",
                    "PATH=${env.FULL_PATH}",
                    "ANDROID_HOME=${env.ANDROID_HOME}",
                    "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
                ]) {

                    deleteDir()

                    checkout scmGit(
                        branches: [[name: '*/main']],
                        extensions: [[
                            $class: 'CloneOption',
                            depth: 1,
                            noTags: true,
                            shallow: true
                        ]],
                        userRemoteConfigs: [[
                            url: 'https://github.com/ikarateam/Yokara_automation.git'
                        ]]
                    )
                }
            }
        }

        stage('Verify Environment') {
            steps {
                withEnv([
                    "HOME=${env.USER_HOME}",
                    "JAVA_HOME=${env.JAVA_HOME}",
                    "PATH=${env.FULL_PATH}",
                    "ANDROID_HOME=${env.ANDROID_HOME}",
                    "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
                ]) {

                    sh '''
                        set +e

                        export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
                        export PATH="$JAVA_HOME/bin:$PATH"

                        echo "===== VERIFY ENV ====="

                        echo "HOME=$HOME"
                        echo "JAVA_HOME=$JAVA_HOME"
                        echo "PATH=$PATH"
                        echo "ANDROID_HOME=$ANDROID_HOME"

                        echo "===== WHICH ====="

                        which java || true
                        which mvn || true
                        which node || true
                        which npm || true
                        which appium || true
                        which adb || true
                        which idevice_id || true
                        which allure || true

                        echo "===== VERSIONS ====="

                        java -version || true
                        mvn -v || true
                        node -v || true
                        npm -v || true
                        appium -v || true
                        adb version || true
                        allure --version || true

                        echo "===== APP ENV ====="
                        echo "APP_ENV=${APP_ENV}"
                        echo "(ConfigManager.resolveByEnv sẽ pick bundle theo env này)"

                        echo "===== APPIUM ====="

                        appium driver list --installed || true

                        echo "===== DEVICES ====="

                        adb devices || true
                        idevice_id -l || true
                    '''
                }
            }
        }

        stage('Run Tests') {
            steps {

                withEnv([
                    "HOME=${env.USER_HOME}",
                    "JAVA_HOME=${env.JAVA_HOME}",
                    "PATH=${env.FULL_PATH}",
                    "ANDROID_HOME=${env.ANDROID_HOME}",
                    "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
                ]) {

                    sh '''
                        set +e

                        export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
                        export PATH="$JAVA_HOME/bin:$PATH"

                        APPIUM_BASE_PORT="${APPIUM_BASE_PORT:-4700}"
                        PORT_STEP="${PORT_STEP:-10}"
                        PORT_RANGE_END=$((APPIUM_BASE_PORT + 20 * PORT_STEP))

                        echo "===== CLEAN PORTS (chỉ port Jenkins dùng ${APPIUM_BASE_PORT}..${PORT_RANGE_END}) ====="
                        # Cố ý KHÔNG kill 4723 / pkill -f appium / pkill -f WebDriverAgent —
                        # dev local có 1 Appium PM2 thường trực ở 4723, kill thô sẽ flap port + đứt session.
                        # Jenkins range bắt đầu từ APPIUM_BASE_PORT (default 4700), không đụng 4723.

                        for p in $(seq $APPIUM_BASE_PORT $PORT_STEP $PORT_RANGE_END); do
                          lsof -ti tcp:$p | xargs kill -9 2>/dev/null || true
                        done

                        echo "===== COMPILE + GENERATE SUITE (sinh testng-multidevice.xml + target/appium-ports.txt) ====="

                        mvn -q clean process-test-classes \
                          -Dappium.basePort=$APPIUM_BASE_PORT \
                          -Dappium.portStep=$PORT_STEP

                        GEN_EXIT=$?
                        if [ $GEN_EXIT -ne 0 ] || [ ! -f target/appium-ports.txt ]; then
                          echo "[Run Tests] Không sinh được target/appium-ports.txt (exit=$GEN_EXIT). Có thiết bị nào cắm không?"
                          adb devices || true
                          idevice_id -l || true
                          exit ${GEN_EXIT:-1}
                        fi

                        echo "===== DEVICE → APPIUM PORT MAP ====="
                        cat target/appium-ports.txt

                        echo "===== SPAWN APPIUM (1 server / device) ====="

                        APPIUM_PIDS=""
                        mkdir -p target/appium-logs
                        while IFS=$'\\t' read -r PORT PLATFORM UDID; do
                          [ -z "$PORT" ] && continue
                          SHORT_UDID=$(echo "$UDID" | tail -c 13)
                          LOG="target/appium-logs/appium-${PORT}-${SHORT_UDID}.log"
                          echo "  → port=$PORT  platform=$PLATFORM  udid=$UDID  log=$LOG"
                          nohup appium \
                            --address 127.0.0.1 \
                            --port "$PORT" \
                            --log-level info \
                            > "$LOG" 2>&1 &
                          APPIUM_PIDS="$APPIUM_PIDS $!"
                        done < target/appium-ports.txt

                        echo "Appium PIDs:$APPIUM_PIDS"

                        echo "===== WAIT APPIUM READY ====="

                        ALL_READY=1
                        while IFS=$'\\t' read -r PORT _ _; do
                          [ -z "$PORT" ] && continue
                          READY=0
                          for i in $(seq 1 40); do
                            if curl -sf "http://127.0.0.1:${PORT}/status" >/dev/null 2>&1; then
                              echo "  ✓ Appium :$PORT ready"
                              READY=1
                              break
                            fi
                            sleep 1
                          done
                          if [ $READY -ne 1 ]; then
                            echo "  ✗ Appium :$PORT KHÔNG ready sau 40s"
                            ALL_READY=0
                          fi
                        done < target/appium-ports.txt

                        if [ $ALL_READY -ne 1 ]; then
                          echo "[Run Tests] Một số Appium server không sẵn sàng — abort."
                          for pid in $APPIUM_PIDS; do kill $pid 2>/dev/null || true; done
                          exit 1
                        fi

                        echo "===== RUN MAVEN (surefire only — KHÔNG truyền -DappiumServer để suiteAppiumPort có hiệu lực) ====="

                        mvn surefire:test \
                          -DsuiteXmlFile=testng-multidevice.xml \
                          -Dappium.basePort=$APPIUM_BASE_PORT \
                          -Dappium.portStep=$PORT_STEP \
                          -Dapp.env=${APP_ENV}

                        TEST_EXIT=$?

                        echo "===== TEST EXIT ====="
                        echo $TEST_EXIT

                        echo "===== STOP APPIUM ====="
                        for pid in $APPIUM_PIDS; do
                          kill $pid 2>/dev/null || true
                        done

                        exit $TEST_EXIT
                    '''
                }
            }
        }

        stage('Generate Allure Report') {
            steps {

                withEnv([
                    "HOME=${env.USER_HOME}",
                    "JAVA_HOME=${env.JAVA_HOME}",
                    "PATH=${env.FULL_PATH}",
                    "ANDROID_HOME=${env.ANDROID_HOME}",
                    "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
                ]) {

                    sh '''
                        set +e

                        export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
                        export PATH="$JAVA_HOME/bin:$PATH"

                        rm -rf allure-report

                        allure generate target/allure-results \
                          --clean \
                          -o allure-report
                    '''

                    archiveArtifacts artifacts: 'allure-report/**', allowEmptyArchive: true
                }
            }
        }
    }

    post {

        always {

            withEnv([
                "HOME=${env.USER_HOME}",
                "JAVA_HOME=${env.JAVA_HOME}",
                "PATH=${env.FULL_PATH}",
                "ANDROID_HOME=${env.ANDROID_HOME}",
                "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
            ]) {

                sh '''
                    set +e

                    export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
                    export PATH="$JAVA_HOME/bin:$PATH"

                    echo "===== GLOBAL CLEANUP (chỉ port range Jenkins, không pkill thô) ====="
                    # Không dùng pkill -f appium / WebDriverAgent — dev local có PM2 Appium 4723
                    # và có thể đang chạy WDA tay; kill thô sẽ làm flap dịch vụ dev.
                    # Port-loop dưới đây chỉ giết process listen ở range Jenkins (Appium 4700-4900,
                    # WDA 8100-8300, MJPEG 10100-10300) — chính xác, không đụng dev.

                    for p in $(seq 4700 10 4900); do
                      lsof -ti tcp:$p | xargs kill -9 2>/dev/null || true
                    done

                    for p in $(seq 8100 10 8300); do
                      lsof -ti tcp:$p | xargs kill -9 2>/dev/null || true
                    done

                    for p in $(seq 10100 10 10300); do
                      lsof -ti tcp:$p | xargs kill -9 2>/dev/null || true
                    done
                '''

                allure([
                    includeProperties: false,
                    jdk: '',
                    properties: [],
                    reportBuildPolicy: 'ALWAYS',
                    results: [[path: 'target/allure-results']]
                ])

                catchError(buildResult: null, stageResult: 'SUCCESS', message: 'Slack notify failed') {
                    withCredentials([usernamePassword(
                        credentialsId: 'jenkins-allure-readonly',
                        usernameVariable: 'JENKINS_USER',
                        passwordVariable: 'JENKINS_PASS'
                    )]) {
                        sh '''
                            set +e

                            ${PYTHON_BIN} scripts/notify_slack_failures.py \
                                --build-url "$BUILD_URL" \
                                --build-number "$BUILD_NUMBER"
                            exit 0
                        '''
                    }
                }
            }
        }
    }
}