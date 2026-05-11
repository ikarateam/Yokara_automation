pipeline {
    agent any

    triggers {
        cron('0 7 * * *')
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

                        echo "===== CLEAN PORTS ====="

                        lsof -ti tcp:4723 | xargs kill -9 2>/dev/null || true
                        lsof -ti tcp:8100 | xargs kill -9 2>/dev/null || true
                        lsof -ti tcp:10100 | xargs kill -9 2>/dev/null || true

                        pkill -f appium || true
                        pkill -f WebDriverAgent || true

                        echo "===== START APPIUM ====="

                        nohup appium \
                          --address 127.0.0.1 \
                          --port 4723 \
                          --log-level info \
                          > appium.log 2>&1 &

                        APPIUM_PID=$!

                        echo "Appium PID=$APPIUM_PID"

                        echo "===== WAIT APPIUM ====="

                        for i in $(seq 1 40); do
                          if curl -sf http://127.0.0.1:4723/status >/dev/null 2>&1; then
                            echo "Appium Ready"
                            break
                          fi
                          sleep 1
                        done

                        echo "===== RUN MAVEN ====="

                        mvn clean test \
                          -DsuiteXmlFile=testng-multidevice.xml \
                          -DappiumServer=http://127.0.0.1:4723

                        TEST_EXIT=$?

                        echo "===== TEST EXIT ====="
                        echo $TEST_EXIT

                        echo "===== STOP APPIUM ====="

                        kill $APPIUM_PID || true

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

                    echo "===== GLOBAL CLEANUP ====="

                    pkill -f appium || true
                    pkill -f WebDriverAgent || true

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
            }
        }
    }
}