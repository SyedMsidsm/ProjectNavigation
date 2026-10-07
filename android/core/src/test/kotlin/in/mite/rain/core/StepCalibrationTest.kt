package `in`.mite.rain.core

import org.junit.Assert.*
import org.junit.Test

class StepCalibrationTest {
    @Test fun computesMeasuredDistancePerStepAndIgnoresLateEvents() {
        val trial = StepCalibration(); trial.start(14.0); repeat(20) { trial.step() }
        assertEquals(.7, trial.stop().stepLengthM, .00001)
        trial.step(); assertEquals(20, trial.steps)
    }
    @Test(expected = IllegalArgumentException::class) fun interruptedTrialCannotBeSaved() {
        val trial = StepCalibration(); trial.start(10.0); repeat(15) { trial.step() }; trial.interrupt(); trial.stop()
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInsufficientSteps() {
        val trial = StepCalibration(); trial.start(10.0); repeat(3) { trial.step() }; trial.stop()
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsImplausibleLength() {
        val trial = StepCalibration(); trial.start(50.0); repeat(10) { trial.step() }; trial.stop()
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnmeasuredDistance() { StepCalibration().start(Double.NaN) }
    @Test fun restartClearsInterruptedTrial() {
        val trial = StepCalibration(); trial.start(10.0); trial.step(); trial.interrupt()
        trial.start(14.0); repeat(20) { trial.step() }
        assertFalse(trial.interrupted); assertEquals(.7, trial.stop().stepLengthM, .00001)
    }
}
