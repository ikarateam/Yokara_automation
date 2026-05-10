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
        USER_HOME = '/Users/quhuy'
        ANDROID_HOME = '/Users/quhuy/Library/Android/sdk'
        ANDROID_SDK_ROOT = '/Users/quhuy/Library/Android/sdk'

        NODE_BIN = '/Users/quhuy/.nvm/versions/node/v22.22.2/bin'
        PYTHON_BIN = '/opt/homebrew/bin/python3'
        IDEVICE_ID_BIN = '/opt/homebrew/bin/idevice_id'
        APPIUM_BIN = '/Users/quhuy/.nvm/versions/node/v22.22.2/bin/appium'
        ALLURE_BIN = '/opt/homebrew/bin/allure'

        FULL_PATH = '/Users/quhuy/.local/bin:/opt/homebrew/bin:/usr/local/bin:/Users/quhuy/Library/Android/sdk/platform-tools:/Users/quhuy/Library/Android/sdk/emulator:/Users/quhuy/Library/Android/sdk/cmdline-tools/latest/bin:/bin:/usr/bin:/usr/sbin:/sbin:/Users/quhuy/.nvm/versions/node/v22.22.2/bin'

        APPIUM_BASE_PORT = '4700'
        IOS_WDA_BASE_PORT = '8100'
        IOS_MJPEG_BASE_PORT = '10100'
        ANDROID_SYSTEM_BASE_PORT = '8200'
        PORT_STEP = '10'
    }

    stages {
        stage('Checkout') {
            steps {
                withEnv([
                    "HOME=${env.USER_HOME}",
                    "PATH=${env.FULL_PATH}",
                    "ANDROID_HOME=${env.ANDROID_HOME}",
                    "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
                ]) {
                    deleteDir()
                    checkout scmGit(
                        branches: [[name: '*/main']],
                        extensions: [[$class: 'CloneOption', depth: 1, noTags: true, shallow: true]],
                        userRemoteConfigs: [[url: 'https://github.com/ngocllb/Yokara']]
                    )
                    stash name: 'repo-source', includes: '**/*', useDefaultExcludes: false
                }
            }
        }

        stage('Verify Environment') {
            steps {
                withEnv([
                    "HOME=${env.USER_HOME}",
                    "PATH=${env.FULL_PATH}",
                    "ANDROID_HOME=${env.ANDROID_HOME}",
                    "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
                ]) {
                    sh '''
                        set +e
                        echo "===== VERIFY ENV ====="
                        echo "HOME=$HOME"
                        echo "PATH=$PATH"
                        echo "ANDROID_HOME=$ANDROID_HOME"
                        echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"

                        echo "----- WHICH -----"
                        which sh || true
                        which python3 || true
                        which adb || true
                        which appium || true
                        which idevice_id || true
                        which tidevice || true
                        which curl || true
                        which lsof || true
                        which mvn || true
                        which node || true
                        which allure || true

                        echo "----- VERSIONS -----"
                        python3 --version || true
                        adb version || true
                        appium --version || true
                        mvn -v || true
                        node -v || true
                        allure --version || true

                        echo "----- DEVICES -----"
                        adb devices || true
                        idevice_id -l || true
                        tidevice list || true
                    '''
                }
            }
        }

        stage('Detect Devices') {
            steps {
                withEnv([
                    "HOME=${env.USER_HOME}",
                    "PATH=${env.FULL_PATH}",
                    "ANDROID_HOME=${env.ANDROID_HOME}",
                    "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
                ]) {
                    script {
                        sh '''
                            rm -rf allure-merge allure-combined reports device-dashboard artifacts run-meta
                            mkdir -p allure-merge allure-combined reports/device reports/combined device-dashboard artifacts run-meta
                        '''

                        def raw = sh(
                            script: "${env.PYTHON_BIN} scripts/get_jenkins_devices.py",
                            returnStdout: true
                        ).trim()

                        echo "===== RAW DEVICE OUTPUT ====="
                        echo(raw ?: "(empty)")

                        if (!raw) {
                            error("No physically connected devices found.")
                        }

                        def lines = raw.split('\n').findAll { it?.trim() && it.contains('|') }
                        if (lines.isEmpty()) {
                            error("Device script returned no valid records.")
                        }

                        def allDevices = []

                        for (String line : lines) {
                            def parts = line.split('\\|')
                            if (parts.size() < 2) {
                                continue
                            }

                            def platform = parts[0].trim().toLowerCase()
                            def udid = parts[1].trim()

                            if (platform == 'ios' || platform == 'android') {
                                allDevices << [platform: platform, udid: udid]
                            }
                        }

                        if (allDevices.isEmpty()) {
                            error("No supported devices detected.")
                        }

                        def appiumBase = env.APPIUM_BASE_PORT.toInteger()
                        def iosWdaBase = env.IOS_WDA_BASE_PORT.toInteger()
                        def iosMjpegBase = env.IOS_MJPEG_BASE_PORT.toInteger()
                        def androidSystemBase = env.ANDROID_SYSTEM_BASE_PORT.toInteger()
                        def step = env.PORT_STEP.toInteger()

                        def deviceMatrix = []

                        for (int idx = 0; idx < allDevices.size(); idx++) {
                            def dev = allDevices[idx]
                            def shortUdid = dev.udid.size() > 8 ? dev.udid.substring(dev.udid.size() - 8) : dev.udid
                            def branchName = "${dev.platform}-${shortUdid}"

                            def item = [
                                slot       : idx,
                                platform   : dev.platform,
                                udid       : dev.udid,
                                shortUdid  : shortUdid,
                                branchName : branchName,
                                appiumPort : appiumBase + (idx * step)
                            ]

                            if (dev.platform == 'ios') {
                                item.wdaLocalPort = iosWdaBase + (idx * step)
                                item.mjpegServerPort = iosMjpegBase + (idx * step)
                                item.derivedDataPath = "/tmp/wda-${shortUdid}-${env.BUILD_NUMBER}"
                            } else {
                                item.systemPort = androidSystemBase + (idx * step)
                            }

                            deviceMatrix << item
                        }

                        echo "===== DEVICE MATRIX ====="
                        echo "TOTAL DEVICES = ${deviceMatrix.size()}"
                        deviceMatrix.each { d -> echo("${d}") }

                        writeJSON file: 'run-meta/device-matrix.json', json: deviceMatrix, pretty: 4
                        stash name: 'device-matrix', includes: 'run-meta/device-matrix.json'
                        archiveArtifacts artifacts: 'run-meta/**', allowEmptyArchive: true
                    }
                }
            }
        }

        stage('Run Parallel Tests') {
            steps {
                withEnv([
                    "HOME=${env.USER_HOME}",
                    "PATH=${env.FULL_PATH}",
                    "ANDROID_HOME=${env.ANDROID_HOME}",
                    "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
                ]) {
                    script {
                        unstash 'device-matrix'
                        def deviceMatrix = readJSON file: 'run-meta/device-matrix.json'
                        def branches = [:]

                        for (int i = 0; i < deviceMatrix.size(); i++) {
                            def d = deviceMatrix[i]
                            def device = d

                            branches["${device.platform.toUpperCase()}-${device.shortUdid}"] = {
                                timeout(time: 45, unit: 'MINUTES') {
                                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                        def platform = device.platform
                                        def udid = device.udid
                                        def shortUdid = device.shortUdid
                                        def branchName = device.branchName
                                        def slot = device.slot
                                        def appiumPort = device.appiumPort as Integer
                                        def appiumUrl = "http://127.0.0.1:${appiumPort}"

                                        def branchWs = "${env.WORKSPACE}@${branchName}"
                                        def mergeDir = "${env.WORKSPACE}/allure-merge/${branchName}"
                                        def dashboardDir = "${env.WORKSPACE}/device-dashboard/${branchName}"
                                        def artifactDir = "${env.WORKSPACE}/artifacts/${branchName}"

                                        ws(branchWs) {
                                            withEnv([
                                                "HOME=${env.USER_HOME}",
                                                "PATH=${env.FULL_PATH}",
                                                "ANDROID_HOME=${env.ANDROID_HOME}",
                                                "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
                                            ]) {
                                                deleteDir()
                                                unstash 'repo-source'

                                                def appiumPid = ''
                                                def resultStatus = 'UNKNOWN'
                                                def failureMessage = ''
                                                def startTs = System.currentTimeMillis()
                                                def mvnExit = 999

                                                try {
                                                    sh """
                                                        set +e
                                                        mkdir -p "target/allure-results-${udid}"
                                                        mkdir -p artifacts
                                                        echo "===== BRANCH INFO ====="
                                                        echo "Platform=${platform}"
                                                        echo "UDID=${udid}"
                                                        echo "ShortUDID=${shortUdid}"
                                                        echo "Slot=${slot}"
                                                        echo "Workspace=\$(pwd)"
                                                        echo "AppiumUrl=${appiumUrl}"
                                                    """

                                                    if (platform == 'ios') {
                                                        def iosAlive = sh(
                                                            script: "${env.IDEVICE_ID_BIN} -l | grep '${udid}' || true",
                                                            returnStdout: true
                                                        ).trim()
                                                        if (!iosAlive) {
                                                            error("iOS device ${udid} is not connected.")
                                                        }

                                                        sh """
                                                            set +e
                                                            lsof -ti tcp:${appiumPort} | xargs kill -9 2>/dev/null || true
                                                            lsof -ti tcp:${device.wdaLocalPort} | xargs kill -9 2>/dev/null || true
                                                            lsof -ti tcp:${device.mjpegServerPort} | xargs kill -9 2>/dev/null || true
                                                            rm -rf "${device.derivedDataPath}" || true
                                                            mkdir -p "${device.derivedDataPath}"
                                                        """
                                                    } else {
                                                        def androidAlive = sh(
                                                            script: "adb -s '${udid}' get-state 2>/dev/null || true",
                                                            returnStdout: true
                                                        ).trim()
                                                        if (androidAlive != 'device') {
                                                            error("Android device ${udid} is not ready. State=${androidAlive}")
                                                        }

                                                        sh """
                                                            set +e
                                                            lsof -ti tcp:${appiumPort} | xargs kill -9 2>/dev/null || true
                                                            lsof -ti tcp:${device.systemPort} | xargs kill -9 2>/dev/null || true
                                                        """
                                                    }

                                                    sh """
                                                        set -e
                                                        nohup env \\
                                                          HOME="${env.USER_HOME}" \\
                                                          PATH="${env.FULL_PATH}" \\
                                                          ANDROID_HOME="${env.ANDROID_HOME}" \\
                                                          ANDROID_SDK_ROOT="${env.ANDROID_SDK_ROOT}" \\
                                                          ${env.APPIUM_BIN} server \\
                                                          --address 127.0.0.1 \\
                                                          --port ${appiumPort} \\
                                                          --log-level info \\
                                                          > appium-${platform}-${shortUdid}.log 2>&1 &
                                                        echo \$! > appium-${platform}-${shortUdid}.pid
                                                    """

                                                    appiumPid = sh(
                                                        script: "cat appium-${platform}-${shortUdid}.pid",
                                                        returnStdout: true
                                                    ).trim()

                                                    sh """
                                                        set -e
                                                        echo "Waiting Appium status on ${appiumUrl}/status"
                                                        for i in \$(seq 1 40); do
                                                          if curl -sf ${appiumUrl}/status >/dev/null 2>&1; then
                                                            echo "Appium is ready on ${appiumUrl}"
                                                            exit 0
                                                          fi
                                                          sleep 1
                                                        done
                                                        echo "Appium did not become ready in time"
                                                        cat appium-${platform}-${shortUdid}.log || true
                                                        exit 1
                                                    """

                                                    if (platform == 'ios') {
                                                        mvnExit = sh(
                                                            script: """
                                                                set +e
                                                                mvn clean test \\
                                                                  -DsuiteXmlFile=testng-jenkins.xml \\
                                                                  -Dplatform=ios \\
                                                                  -DappiumServer=${appiumUrl} \\
                                                                  -Djenkins.branchName="${branchName}" \\
                                                                  -Djenkins.slot=${slot} \\
                                                                  -Djenkins.appiumPort=${appiumPort} \\
                                                                  -Djenkins.wdaLocalPort=${device.wdaLocalPort} \\
                                                                  -Djenkins.mjpegServerPort=${device.mjpegServerPort} \\
                                                                  -Dios.udid="${udid}" \\
                                                                  -Dios.wdaLocalPort=${device.wdaLocalPort} \\
                                                                  -Dios.mjpegServerPort=${device.mjpegServerPort} \\
                                                                  -Dios.derivedDataPath="${device.derivedDataPath}" \\
                                                                  -Dallure.results.directory=target/allure-results-${udid}
                                                                exit \$?
                                                            """,
                                                            returnStatus: true
                                                        )
                                                    } else {
                                                        mvnExit = sh(
                                                            script: """
                                                                set +e
                                                                mvn clean test \\
                                                                  -DsuiteXmlFile=testng-jenkins.xml \\
                                                                  -Dplatform=android \\
                                                                  -DappiumServer=${appiumUrl} \\
                                                                  -Djenkins.branchName="${branchName}" \\
                                                                  -Djenkins.slot=${slot} \\
                                                                  -Djenkins.appiumPort=${appiumPort} \\
                                                                  -Djenkins.systemPort=${device.systemPort} \\
                                                                  -Dandroid.udid="${udid}" \\
                                                                  -Dandroid.systemPort=${device.systemPort} \\
                                                                  -Dallure.results.directory=target/allure-results-${udid}
                                                                exit \$?
                                                            """,
                                                            returnStatus: true
                                                        )
                                                    }

                                                    echo "Maven exit code for ${branchName}: ${mvnExit}"

                                                    def resultCount = sh(
                                                        script: "find target/allure-results-${udid} -type f -name '*-result.json' | wc -l | tr -d ' '",
                                                        returnStdout: true
                                                    ).trim()

                                                    echo "Allure result count for ${branchName}: ${resultCount}"

                                                    if (mvnExit == 0) {
                                                        resultStatus = 'PASSED'
                                                    } else {
                                                        resultStatus = 'FAILED'
                                                        failureMessage = "mvn clean test returned exit code ${mvnExit}"
                                                    }

                                                    if (mvnExit != 0 && resultCount == '0') {
                                                        error("No Allure test result generated. mvn exit=${mvnExit}")
                                                    }
                                                } catch (err) {
                                                    resultStatus = 'FAILED'
                                                    failureMessage = err?.getMessage() ?: err?.toString() ?: 'Unknown error'
                                                    throw err
                                                } finally {
                                                    def durationSec = ((System.currentTimeMillis() - startTs) / 1000L) as Long

                                                    sh """
                                                        set +e

                                                        mkdir -p "target/allure-results-${udid}"

                                                        cat > "target/allure-results-${udid}/environment.properties" <<EOF
Platform=${platform}
DeviceUDID=${udid}
ShortUDID=${shortUdid}
BranchName=${branchName}
Slot=${slot}
AppiumServer=${appiumUrl}
AppiumPort=${appiumPort}
Result=${resultStatus}
DurationSec=${durationSec}
MavenExit=${mvnExit}
EOF

                                                        mkdir -p "${mergeDir}"
                                                        cp -R "target/allure-results-${udid}/." "${mergeDir}/" || true

                                                        mkdir -p "${dashboardDir}"
                                                        mkdir -p "${artifactDir}"
                                                        cp -f appium-${platform}-${shortUdid}.log "${artifactDir}/" 2>/dev/null || true
                                                        cp -R target/surefire-reports "${artifactDir}/surefire-reports" 2>/dev/null || true

                                                        echo "===== DEBUG ${branchName} ====="
                                                        echo "mergeDir=${mergeDir}"
                                                        find "${mergeDir}" -maxdepth 1 -type f | sed 's#^#MERGED: #' || true
                                                    """

                                                    writeJSON(
                                                        file: "${dashboardDir}/summary.json",
                                                        json: device + [
                                                            status      : resultStatus,
                                                            durationSec : durationSec,
                                                            errorMessage: failureMessage,
                                                            mavenExit   : mvnExit
                                                        ],
                                                        pretty: 4
                                                    )

                                                    sh """
                                                        set +e

                                                        if [ -n "${appiumPid}" ]; then
                                                          kill ${appiumPid} || true
                                                        fi

                                                        if [ -f appium-${platform}-${shortUdid}.pid ]; then
                                                          kill \$(cat appium-${platform}-${shortUdid}.pid) || true
                                                        fi

                                                        lsof -ti tcp:${appiumPort} | xargs kill -9 2>/dev/null || true
                                                    """

                                                    if (platform == 'ios') {
                                                        sh """
                                                            set +e
                                                            lsof -ti tcp:${device.wdaLocalPort} | xargs kill -9 2>/dev/null || true
                                                            lsof -ti tcp:${device.mjpegServerPort} | xargs kill -9 2>/dev/null || true
                                                            rm -rf "${device.derivedDataPath}" || true
                                                        """
                                                    } else {
                                                        sh """
                                                            set +e
                                                            lsof -ti tcp:${device.systemPort} | xargs kill -9 2>/dev/null || true
                                                        """
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        parallel branches + [failFast: false]
                    }
                }
            }
        }

        stage('Archive Branch Artifacts') {
            steps {
                archiveArtifacts artifacts: 'artifacts/**', allowEmptyArchive: true
            }
        }

        stage('Create Device Summary Results') {
            steps {
                withEnv([
                    "HOME=${env.USER_HOME}",
                    "PATH=${env.FULL_PATH}",
                    "ANDROID_HOME=${env.ANDROID_HOME}",
                    "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
                ]) {
                    script {
                        unstash 'device-matrix'
                        def deviceMatrix = readJSON file: 'run-meta/device-matrix.json'

                        deviceMatrix.each { d ->
                            def summaryFile = "device-dashboard/${d.branchName}/summary.json"
                            def status = 'FAILED'
                            def durationSec = 0
                            def errorMessage = ''
                            def mavenExit = ''

                            if (fileExists(summaryFile)) {
                                def item = readJSON file: summaryFile
                                status = item?.status == 'PASSED' ? 'PASSED' : 'FAILED'
                                durationSec = item?.durationSec ?: 0
                                errorMessage = item?.errorMessage ?: ''
                                mavenExit = item?.mavenExit?.toString() ?: ''
                            }

                            def uuid = java.util.UUID.randomUUID().toString()
                            def historyId = "device-summary-${d.branchName}"
                            def labels = [
                                [name: 'parentSuite', value: 'Devices'],
                                [name: 'suite', value: d.branchName],
                                [name: 'subSuite', value: "Appium Port ${d.appiumPort}"],
                                [name: 'device.branch', value: d.branchName],
                                [name: 'device.platform', value: d.platform],
                                [name: 'device.udid', value: d.udid],
                                [name: 'device.appiumPort', value: "${d.appiumPort}"]
                            ]

                            if (d.platform == 'ios') {
                                labels << [name: 'device.extraPort1', value: "${d.wdaLocalPort}"]
                                labels << [name: 'device.extraPort2', value: "${d.mjpegServerPort}"]
                            } else {
                                labels << [name: 'device.extraPort1', value: "${d.systemPort}"]
                            }

                            def message = fileExists(summaryFile)
                                ? "status=${status}, durationSec=${durationSec}, branch=${d.branchName}, mvnExit=${mavenExit}"
                                : "status=FAILED, reason=no summary.json (failed before test result), branch=${d.branchName}"

                            if (errorMessage?.trim()) {
                                message = "${message}, reason=${errorMessage}"
                            }

                            def result = [
                                uuid         : uuid,
                                historyId    : historyId,
                                name         : "Device Summary - ${d.branchName}",
                                fullName     : "device.summary.${d.branchName}",
                                status       : status == 'PASSED' ? 'passed' : 'failed',
                                stage        : 'finished',
                                labels       : labels,
                                statusDetails: [
                                    known  : true,
                                    muted  : false,
                                    flaky  : false,
                                    message: message,
                                    trace  : errorMessage ?: ''
                                ]
                            ]

                            sh "mkdir -p \"allure-merge/${d.branchName}\""
                            writeJSON file: "allure-merge/${d.branchName}/${uuid}-result.json", json: result, pretty: 4
                        }
                    }
                }
            }
        }

        stage('Generate Per-device Reports') {
            steps {
                withEnv([
                    "HOME=${env.USER_HOME}",
                    "PATH=${env.FULL_PATH}",
                    "ANDROID_HOME=${env.ANDROID_HOME}",
                    "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
                ]) {
                    script {
                        unstash 'device-matrix'
                        def deviceMatrix = readJSON file: 'run-meta/device-matrix.json'

                        deviceMatrix.each { d ->
                            def branchName = d.branchName

                            sh """
                                set +e
                                mkdir -p "reports/device/${branchName}"

                                result_count=\$(find "allure-merge/${branchName}" -type f -name '*-result.json' 2>/dev/null | wc -l | tr -d ' ')
                                if [ "\${result_count}" != "0" ]; then
                                  ${env.ALLURE_BIN} generate "allure-merge/${branchName}" --clean -o "reports/device/${branchName}" || true
                                else
                                  cat > "reports/device/${branchName}/index.html" <<EOF
<!doctype html>
<html>
<head><meta charset="utf-8"><title>${branchName}</title></head>
<body>
<h2>No Allure result json for ${branchName}</h2>
<p>Branch may have failed before producing test result files.</p>
</body>
</html>
EOF
                                fi
                            """
                        }

                        archiveArtifacts artifacts: 'reports/device/**', allowEmptyArchive: true
                    }
                }
            }
        }

        stage('Aggregate Allure Results') {
            steps {
                withEnv([
                    "HOME=${env.USER_HOME}",
                    "PATH=${env.FULL_PATH}",
                    "ANDROID_HOME=${env.ANDROID_HOME}",
                    "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
                ]) {
                    sh '''
                        set +e
                        rm -rf allure-combined
                        mkdir -p allure-combined

                        if [ ! -d allure-merge ]; then
                          echo "[Allure] allure-merge does not exist"
                          exit 0
                        fi

                        echo "===== MERGE ALLURE RESULTS ====="

                        find allure-merge -type f ! -name 'environment.properties' 2>/dev/null | while IFS= read -r f; do
                          [ -f "$f" ] || continue
                          cp -f "$f" allure-combined/ || true
                        done

                        total_devices=$(find allure-merge -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')
                        total_results=$(find allure-combined -type f -name '*-result.json' | wc -l | tr -d ' ')

                        cat > allure-combined/environment.properties <<EOF
ReportScope=combined
TotalDevices=${total_devices}
TotalResults=${total_results}
GeneratedBy=Jenkins
EOF

                        echo "[Allure] Combined file count: $(find allure-combined -type f | wc -l | tr -d ' ')"
                        echo "[Allure] Result json count: $(find allure-combined -type f -name '*-result.json' | wc -l | tr -d ' ')"
                        ls -la allure-combined | head -100 || true
                    '''

                    archiveArtifacts artifacts: 'allure-merge/**', allowEmptyArchive: true
                    archiveArtifacts artifacts: 'allure-combined/**', allowEmptyArchive: true
                }
            }
        }

        stage('Generate Combined Report') {
            steps {
                withEnv([
                    "HOME=${env.USER_HOME}",
                    "PATH=${env.FULL_PATH}",
                    "ANDROID_HOME=${env.ANDROID_HOME}",
                    "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
                ]) {
                    sh """
                        set +e
                        rm -rf reports/combined
                        mkdir -p reports/combined

                        if [ -d "allure-combined" ] && [ "\$(find allure-combined -type f | wc -l | tr -d ' ')" != "0" ]; then
                          ${env.ALLURE_BIN} generate allure-combined --clean -o reports/combined || true
                        else
                          cat > reports/combined/index.html <<EOF
<!doctype html>
<html>
<head><meta charset="utf-8"><title>Combined report</title></head>
<body>
<h2>No combined Allure results</h2>
</body>
</html>
EOF
                        fi
                    """
                    archiveArtifacts artifacts: 'reports/combined/**', allowEmptyArchive: true
                }
            }
        }

        stage('Build Dashboard Summary') {
            steps {
                withEnv([
                    "HOME=${env.USER_HOME}",
                    "PATH=${env.FULL_PATH}",
                    "ANDROID_HOME=${env.ANDROID_HOME}",
                    "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
                ]) {
                    script {
                        sh '''
                            mkdir -p device-dashboard
                            echo "platform,udid,shortUdid,slot,appiumPort,extraPort1,extraPort2,status,durationSec,branchName,mavenExit" > device-dashboard/device-summary.csv
                        '''

                        def rows = []
                        def jsonFiles = sh(
                            script: "find device-dashboard -name 'summary.json' 2>/dev/null || true",
                            returnStdout: true
                        ).trim()

                        if (jsonFiles) {
                            jsonFiles.split('\n').findAll { it?.trim() }.each { file ->
                                def item = readJSON file: file.trim()
                                def extraPort1 = item.platform == 'ios' ? item.wdaLocalPort : item.systemPort
                                def extraPort2 = item.platform == 'ios' ? item.mjpegServerPort : ''
                                def branchName = item.branchName ?: "${item.platform}-${item.shortUdid}"
                                def mavenExit = item.mavenExit ?: ''

                                sh """
                                    echo "${item.platform},${item.udid},${item.shortUdid},${item.slot},${item.appiumPort},${extraPort1},${extraPort2},${item.status},${item.durationSec},${branchName},${mavenExit}" >> device-dashboard/device-summary.csv
                                """

                                rows << item
                            }
                        }

                        def html = ""
                        html += "<!doctype html><html><head><meta charset='utf-8'>"
                        html += "<title>Device Dashboard</title>"
                        html += "<style>"
                        html += "body{font-family:Arial,sans-serif;margin:24px;background:#fafafa;color:#222;}"
                        html += "table{border-collapse:collapse;width:100%;margin-top:16px;background:#fff;}"
                        html += "th,td{border:1px solid #ddd;padding:8px;text-align:left;vertical-align:top;}"
                        html += "th{background:#f5f5f5;}"
                        html += ".ok{color:green;font-weight:bold;}"
                        html += ".bad{color:red;font-weight:bold;}"
                        html += "a{color:#0b57d0;text-decoration:none;}"
                        html += "code{background:#f6f8fa;padding:2px 4px;border-radius:4px;}"
                        html += "</style></head><body>"
                        html += "<h1>Jenkins Device Dashboard</h1>"
                        html += "<p><a href='../reports/combined/index.html'>Open Combined Allure Report</a></p>"
                        html += "<table>"
                        html += "<tr><th>Branch</th><th>Platform</th><th>UDID</th><th>Status</th><th>Duration(s)</th><th>Maven Exit</th><th>Ports</th><th>Report</th></tr>"

                        rows.each { item ->
                            def branchName = item.branchName ?: "${item.platform}-${item.shortUdid}"
                            def ports = item.platform == 'ios'
                                ? "appium=${item.appiumPort}<br>wda=${item.wdaLocalPort}<br>mjpeg=${item.mjpegServerPort}"
                                : "appium=${item.appiumPort}<br>system=${item.systemPort}"
                            def statusClass = item.status == 'PASSED' ? 'ok' : 'bad'
                            def mavenExit = item.mavenExit ?: ''

                            html += "<tr>"
                            html += "<td><code>${branchName}</code></td>"
                            html += "<td>${item.platform}</td>"
                            html += "<td>${item.udid}</td>"
                            html += "<td class='${statusClass}'>${item.status}</td>"
                            html += "<td>${item.durationSec}</td>"
                            html += "<td>${mavenExit}</td>"
                            html += "<td>${ports}</td>"
                            html += "<td><a href='../reports/device/${branchName}/index.html'>Open device report</a></td>"
                            html += "</tr>"
                        }

                        html += "</table></body></html>"

                        writeFile file: 'device-dashboard/index.html', text: html
                        archiveArtifacts artifacts: 'device-dashboard/**', allowEmptyArchive: true
                    }
                }
            }
        }
    }

    post {
        always {
            withEnv([
                "HOME=${env.USER_HOME}",
                "PATH=${env.FULL_PATH}",
                "ANDROID_HOME=${env.ANDROID_HOME}",
                "ANDROID_SDK_ROOT=${env.ANDROID_SDK_ROOT}"
            ]) {
                script {
                    sh '''
                        set +e

                        echo "===== GLOBAL CLEANUP PORTS ====="

                        for p in $(seq 4700 10 4900); do
                          lsof -ti tcp:$p | xargs kill -9 2>/dev/null || true
                        done

                        for p in $(seq 8100 10 8300); do
                          lsof -ti tcp:$p | xargs kill -9 2>/dev/null || true
                        done

                        for p in $(seq 10100 10 10300); do
                          lsof -ti tcp:$p | xargs kill -9 2>/dev/null || true
                        done

                        for p in $(seq 8200 10 8400); do
                          lsof -ti tcp:$p | xargs kill -9 2>/dev/null || true
                        done

                        pkill -f appium || true
                        pkill -f WebDriverAgent || true
                    '''

                    allure([
                        includeProperties: false,
                        jdk: '',
                        properties: [],
                        reportBuildPolicy: 'ALWAYS',
                        results: [[path: 'allure-combined']]
                    ])

                    catchError(buildResult: null, stageResult: 'SUCCESS', message: 'Slack notify failed') {
                        withCredentials([usernamePassword(
                            credentialsId: 'jenkins-allure-readonly',
                            usernameVariable: 'JENKINS_USER',
                            passwordVariable: 'JENKINS_PASS'
                        )]) {
                            sh """
                                set +e
                                ${env.PYTHON_BIN} scripts/notify_slack_failures.py \\
                                    --build-url "${env.BUILD_URL}" \\
                                    --build-number "${env.BUILD_NUMBER}"
                                exit 0
                            """
                        }
                    }
                }
            }
        }
    }
}
