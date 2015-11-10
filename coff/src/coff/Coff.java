package coff;

import static org.jikesrvm.runtime.SysCall.sysCall;

import java.util.ArrayList;
import java.util.List;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.StackTrace.Element;
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
						performExperiment();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});
		coffThread.start();
		return coffThread;
	}

	private static void performExperiment() throws InterruptedException {
		VM.sysWriteln("Beginning experiment on line " + lineToProfile);
		VM.sysWriteln("Attempting to dump the stack of all other live threads");
		RVMThread.acctLock.lockNoHandshake();
		VM.sysWriteln("Dumping all live threads");
		List<List<Element>> allStacks = new ArrayList<List<Element>>();
		for (int i = 0; i < RVMThread.numThreads; i++) {
			RVMThread thr = RVMThread.threads[i];
			if (thr != null && thr.isAlive() && !thr.isBootThread() && !thr.isDaemonThread() && !thr.isSystemThread()) {
				thr.beginPairHandshake();
				thr.dump();
				VM.sysWriteln();
				if (thr.contextRegisters != null && !thr.ignoreHandshakesAndGC()) {
					// VM.sysWriteln("Getting stack of thread...");
					// RVMThread.dumpStack(thr.contextRegisters.getInnermostFramePointer());
					List<Element> stack = RVMThread.getStack(thr.contextRegisters.getInnermostFramePointer());

					allStacks.add(stack);
				}
				thr.endPairHandshake();
			}
		}
		RVMThread.acctLock.unlock();

		for (List<Element> stack : allStacks) {
			VM.sysWriteln("Printing stack...");
			for (Element stackElement : stack) {
				// VM.sysWriteln(stackElement.toString());
				VM.sysWriteln(stackElement.getFileName() + ": " + stackElement.getClassName() + "."
						+ stackElement.getMethodName() + "() at line " + stackElement.getLineNumber());
			}
		}

		sysCall.sysNanoSleep(1000L * 1000L * PERFORMANCE_EXPERIMENT_DURATION);
	}
}
