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
	private static final int PERFORMANCE_EXPERIMENT_DURATION = 100;

	/**
	 * Time in ms between samples
	 */
	private static final int SAMPLE_GRANULARITY = 10;

	/**
	 * Time in ms after experiment is completed to "cool down"
	 */
	private static final int COOLDOWN_TIME = 10;

	private static final long MILLIS_TO_NANOS = 1000000L;

	private static List<RVMThread> currentThreads;
	private static int[] curThreadCounters;
	private static long[] delays;

	private static int lineToProfile;
	private static String fileToProfile;
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
					fileToProfile = "Test.java";
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
		// VM.sysWriteln("Beginning experiment on line " + lineToProfile + " in
		// " + fileToProfile);
		// VM.sysWriteln("Attempting to dump the stack of all other live
		// threads");
		RVMThread.acctLock.lockNoHandshake();
		// VM.sysWriteln("Dumping all live threads");
		List<List<Element>> usefulStacks = new ArrayList<List<Element>>();
		currentThreads = new ArrayList<RVMThread>();
		for (int i = 0; i < RVMThread.numThreads; i++) {
			RVMThread thr = RVMThread.threads[i];
			if (thr != null && thr.isAlive() && !thr.isBootThread() && !thr.isDaemonThread() && !thr.isSystemThread()) {
				thr.beginPairHandshake();
				// thr.dump();
				// VM.sysWriteln();
				if (thr.contextRegisters != null && !thr.ignoreHandshakesAndGC()) {
					// VM.sysWriteln("Getting stack of thread...");
					// RVMThread.dumpStack(thr.contextRegisters.getInnermostFramePointer());
					List<Element> stack = RVMThread.getStack(thr.contextRegisters.getInnermostFramePointer());
					currentThreads.add(thr);
					usefulStacks.add(stack);
				}
				thr.endPairHandshake();
			}
		}
		RVMThread.acctLock.unlock();

		curThreadCounters = new int[usefulStacks.size()];
		for (int i = 0; i < usefulStacks.size(); i++) {
			List<Element> stack = usefulStacks.get(i);
			curThreadCounters[i] += getSamplesInThread(stack);
			// printStack(stack);
		}

		// VM.sysWriteln("Thread counters:");
		// for (int thisCounter : curThreadCounters) {
		// VM.sysWriteln(thisCounter);
		// }
		// TODO: Add delays to threads
		addDelays();

		curGlobalCounter = 0;
		sysCall.sysNanoSleep(1000L * 1000L * PERFORMANCE_EXPERIMENT_DURATION);
	}

	private static void addDelays() {
		delays = new long[currentThreads.size()];
		for (int i = 0; i < currentThreads.size(); i++) {
			long delay = (long) ((curGlobalCounter - curThreadCounters[i]) * optimizationLevel * SAMPLE_GRANULARITY
					* MILLIS_TO_NANOS);
			delayThread(currentThreads.get(i), delay);
			curThreadCounters[i] = curGlobalCounter;
		}
		// VM.sysWriteln("Delays by thread:");
		// for (long delay : delays) {
		// VM.sysWriteln(delay);
		// }
	}

	private static void delayThread(final RVMThread thr, final long delay) {
		Thread delayThread = new Thread(new Runnable() {

			@Override
			public void run() {
				thr.beginPairHandshake();
				sysCall.sysNanoSleep(delay);
				thr.endPairHandshake();
			}
		});
		delayThread.start();
	}

	private static void printStack(List<Element> stack) {
		VM.sysWriteln("\nPrinting stack...");
		for (Element stackElement : stack) {
			// VM.sysWriteln(stackElement.toString());
			VM.sysWriteln(stackElement.getFileName() + ": " + stackElement.getClassName() + "."
					+ stackElement.getMethodName() + "() at line " + stackElement.getLineNumber());
		}
	}

	private static int getSamplesInThread(List<Element> stack) {
		int numSamplesInMethod = 0;
		for (Element e : stack) {
			if (e.getLineNumber() == lineToProfile && e.getFileName().equals(fileToProfile)) {
				numSamplesInMethod++;
				curGlobalCounter++;
			}
		}
		return numSamplesInMethod;
	}
}
