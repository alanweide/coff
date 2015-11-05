package coff;

import static org.jikesrvm.runtime.SysCall.sysCall;

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
	 * Time in ms after experiment is completed to "cool down"
	 */
	private static final int COOLDOWN_TIME = 10;

	private static RVMThread[] currentThreads;
	private static int[] curThreadCounters;

	private static int lineToProfile;
	private static double optimizationLevel;
	private static int curGlobalCounter;

	public static Thread start() {
		if (DEBUG) {
			VM.sysWriteln("Starting coff...");
		}
		Thread coffThread = new Thread(new Runnable() {

			@Override
			public void run() {
				while (VM.mainThread.isAlive()) {
					lineToProfile = 7;
					optimizationLevel = 0.5;
					try {
						// sysCall.sysNanoSleep(1000L * 1000L * COOLDOWN_TIME);
						beginExperiment();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});
		coffThread.start();
		return coffThread;
	}

	private static void beginExperiment() throws InterruptedException {
		VM.sysWriteln("Beginning experiment on line " + lineToProfile);
		// RVMThread.dumpVirtualMachine();
		VM.sysWriteln("Attempting to dump the stack of all other live threads");
		// VM.sysWriteln("This is somewhat risky since if the thread is running
		// we're going to be quite confused");
		// for (int i = 0; i < RVMThread.numThreads; i++) {
		// RVMThread thr = RVMThread.threads[i];
		// if (thr != null && thr != RVMThread.getCurrentThread() &&
		// thr.isAlive() && !thr.isDaemonThread()
		// && !thr.isBootThread() && !thr.isSystemThread()) {
		// thr.suspend();
		// thr.dump();
		// // PNT: FIXME: this won't work so well since the context
		// // registers
		// // don't tend to have sane values
		// if (thr.contextRegisters != null && !thr.ignoreHandshakesAndGC()) {
		// RVMThread.dumpStack(thr.contextRegisters.getInnermostFramePointer());
		// }
		// thr.resume();
		// }
		// }
		RVMThread.acctLock.lockNoHandshake();
		VM.sysWriteln("Dumping all live threads");
		for (int i = 0; i < RVMThread.numThreads; ++i) {
			RVMThread thr = RVMThread.threads[i];
			if (thr != null && thr.isAlive() && !thr.isBootThread() && !thr.isDaemonThread() && !thr.isSystemThread()) {
				thr.beginPairHandshake();
				thr.dump();
				VM.sysWriteln();
				if (thr.contextRegisters != null && !thr.ignoreHandshakesAndGC()) {
					VM.sysWriteln("Dumping stack of thread...");
					RVMThread.dumpStack(thr.contextRegisters.getInnermostFramePointer());
				}
				sysCall.sysNanoSleep((long) (1000L * 1000L * PERFORMANCE_EXPERIMENT_DURATION * optimizationLevel));
				thr.endPairHandshake();
			}
		}
		RVMThread.acctLock.unlock();

		sysCall.sysNanoSleep(1000L * 1000L * PERFORMANCE_EXPERIMENT_DURATION);
	}
}
