package coff;

import org.jikesrvm.VM;
import org.jikesrvm.scheduler.RVMThread;

public class Coff {

	private static final boolean DEBUG = true;

	/**
	 * Base duration of performance experiment in ms
	 */
	private static final int PERFORMANCE_EXPERIMENT_DURATION = 1000;

	/**
	 * Time in ms between samples
	 */
	private static final int SAMPLE_GRANULARITY = 10;

	/**
	 * Time after experiment is completed to "cool down"
	 */
	private static final int COOLDOWN_TIME = 10;

	private static RVMThread[] currentThreads;
	private static int[] curThreadCounters;

	private static int lineToProfile;
	private static double optimizationLevel;
	private static int curGlobalCounter;

	public static void start() {
		if (DEBUG) {
			VM.sysWriteln("Starting coff...");
		}
		while (VM.mainThread.isAlive()) {
			lineToProfile = 7;
			optimizationLevel = 1.0;
			try {
				Thread.sleep(COOLDOWN_TIME);
				beginExperiment();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static void beginExperiment() throws InterruptedException {
		VM.sysWriteln("Beginning experiment on line " + lineToProfile);

		Thread.sleep(PERFORMANCE_EXPERIMENT_DURATION);
	}
}
