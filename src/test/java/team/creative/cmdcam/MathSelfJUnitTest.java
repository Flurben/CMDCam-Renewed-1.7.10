package team.creative.cmdcam;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Runs {@link MathSelfTest} as part of the Gradle build. MathSelfTest itself stays free of JUnit, so it also runs with just a JDK. */
public class MathSelfJUnitTest {

    @Test
    public void allChecksPass() {
        Assertions.assertEquals(0, MathSelfTest.runAll(), "failed checks, see the test output");
    }

}
