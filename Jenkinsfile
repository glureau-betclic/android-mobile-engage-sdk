@Library(['android-pipeline', 'general-pipeline']) _

node('master') {
    withSlack channel: 'jenkins', {
        timeout(20) {
            stage("init") {
                deleteDir()
                deviceCount shouldBe: env.ANDROID_DEVICE_COUNT,
                        action: { devices, message ->
                            slackMessage channel: 'jenkins', text: message
                        }
                git url: 'git@github.com:emartech/android-mobile-engage-sdk.git', branch: 'master'

                def testFileCount = sh(returnStdout: true, script: 'find . -name  "*Test.java" | wc -l').trim() as Integer
                def timeoutRuleCount = sh(returnStdout: true, script: 'grep -r "^\\s*public TestRule timeout = TimeoutUtils.getTimeoutRule();" . | wc -l').trim() as Integer
                if (testFileCount != timeoutRuleCount) {
                    error("$testFileCount tests found, but only $timeoutRuleCount timeout rules!")
                }
            }

            withEnv(['DEVELOPMENT_MODE=true']) {
                stage("build") {
                    androidBuild andArchive: '**/*.aar'
                }

                stage('lint') {
                    androidLint andArchive: '**/lint-results*.*'
                }

                stage("instrumentation-test") {
                    sh './gradlew uninstallDebugAndroidTest'
                    androidInstrumentationTest withScreenOn: true, withLock: env.ANDROID_DEVICE_FARM_LOCK, withRetryCount: 2, andArchive: '**/outputs/androidTest-results/connected/*.xml'
                }

                stage('local-maven-deploy') {
                    sh './gradlew install'
                }
            }

            def version = sh(script: 'git describe', returnStdout: true).trim()
            def statusCode = sh returnStdout: true, script: "curl -I https://jcenter.bintray.com/com/emarsys/mobile-engage-sdk/$version/ | head -n 1 | cut -d ' ' -f2".trim()
            def releaseExists = "200" == statusCode.trim()
            if (version ==~ /\d+\.\d+\.\d+/ && !releaseExists) {
                stage('release-bintray') {
                    slackMessage channel: 'jenkins', text: "Releasing Mobile Engage SDK $version."
                    sh './gradlew clean build -x lint -x test bintrayUpload'
                    slackMessage channel: 'jenkins', text: "Mobile Engage SDK $version released to Bintray."
                }
            }
        }
    }
}
