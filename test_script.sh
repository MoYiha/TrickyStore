./gradlew build -x test > /dev/null 2>&1
echo "Build result: $?"
./gradlew :service:testDebugUnitTest > /dev/null 2>&1
echo "Test result: $?"
