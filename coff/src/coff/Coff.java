package coff;

import static org.jikesrvm.runtime.SysCall.sysCall;

import java.util.ArrayList;
import java.util.Date;
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

	private static final int MAX_PERFORMANCE_EXPERIMENT_DURATION = 1000;

	/**
	 * Time in ms between samples
	 */
	private static final int SAMPLE_GRANULARITY = 10;

	/**
	 * Time in ms after experiment is completed to "cool down"; should be >=
	 * SAMPLE_GRANULARITY
	 */
	private static final int COOLDOWN_TIME = 10;

	/**
	 * The minimum number of samples that need to fall in the selected function
	 * for an experiment to be deemed "valid". If there are not enough samples
	 * in a particular experiment, that experiment's time is doubled.
	 */
	private static final int MIN_SAMPLES_PER_EXPERIMENT = 2;

	private static final long MILLIS_TO_NANOS = 1000000L;

	private static List<RVMThread> currentThreads;
	private static int[] curThreadCounters;

	private static long totalDelay;

	private static int lineToProfile;
	private static String fileToProfile;
	private static double optimizationLevel;
	private static int curGlobalCounter;

	private static long totalSamples;
	private static long startTime;

	public static Thread start() {
		if (DEBUG) {
			VM.sysWriteln("Starting coff...");
		}
		Thread coffThread = new Thread(new Runnable() {

			@Override
			public void run() {
				startTime = new Date().getTime();
				while (VM.mainThread.isAlive()) {
					// TODO: select a random line and opt level
					lineToProfile = 7;
					fileToProfile = "Test.java";
					optimizationLevel = 0.1;
					try {
						performAnExperiment();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					sysCall.sysNanoSleep(COOLDOWN_TIME * MILLIS_TO_NANOS);
				}
			}
		});
		coffThread.start();
		return coffThread;
	}

	private static void performAnExperiment() throws InterruptedException {
		int samplesPerExperiment = PERFORMANCE_EXPERIMENT_DURATION / SAMPLE_GRANULARITY;
		for (int i = 1; i <= samplesPerExperiment; i++) {
			RVMThread.acctLock.lockNoHandshake();
			List<List<Element>> usefulStacks = new ArrayList<List<Element>>();
			currentThreads = new ArrayList<RVMThread>();
			for (int j = 0; j < RVMThread.numThreads; j++) {
				RVMThread thr = RVMThread.threads[j];
				if (thr != null && thr.isAlive() && !thr.isBootThread() && !thr.isDaemonThread()
						&& !thr.isSystemThread()) {
					thr.beginPairHandshake();
					if (thr.contextRegisters != null && !thr.ignoreHandshakesAndGC()) {
						List<Element> stack = RVMThread.getStack(thr.contextRegisters.getInnermostFramePointer());
						currentThreads.add(thr);
						usefulStacks.add(stack);
					}
					thr.endPairHandshake();
				}
			}
			RVMThread.acctLock.unlock();

			/*
			 * Get the number of times each thread's stack falls in the function
			 * of interest
			 */
			curThreadCounters = new int[usefulStacks.size()];
			for (int j = 0; j < usefulStacks.size(); j++) {
				List<Element> stack = usefulStacks.get(j);
				curThreadCounters[j] += getSamplesInThread(stack);
				// printStack(stack);
			}
			addDelays();

			if (curGlobalCounter < MIN_SAMPLES_PER_EXPERIMENT && i == samplesPerExperiment
					&& (samplesPerExperiment * SAMPLE_GRANULARITY) < MAX_PERFORMANCE_EXPERIMENT_DURATION) {
				samplesPerExperiment *= 2;
			}
			curGlobalCounter = 0;
			sysCall.sysNanoSleep(SAMPLE_GRANULARITY * MILLIS_TO_NANOS);
		}
	}

	private static int getSamplesInThread(List<Element> stack) {
		int numSamplesInMethod = 0;
		for (Element e : stack) {
			if (e.getLineNumber() == lineToProfile && e.getFileName().equals(fileToProfile)) {
				numSamplesInMethod++;
				curGlobalCounter++;
				totalSamples++;
			}
		}
		return numSamplesInMethod;
	}

	private static void addDelays() {
		for (int i = 0; i < currentThreads.size(); i++) {
			long delay = (long) (optimizationLevel
					* ((curGlobalCounter - curThreadCounters[i]) * SAMPLE_GRANULARITY * MILLIS_TO_NANOS));
			delayThread(currentThreads.get(i), delay);
			curThreadCounters[i] = curGlobalCounter;
			totalDelay += delay;
		}
	}

	private static void delayThread(final RVMThread thr, final long delay) {
		/*
		 * Need to delay thread on a separate thread from the main coff thread
		 */
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
			VM.sysWriteln(stackElement.getFileName() + ": " + stackElement.getClassName() + "."
					+ stackElement.getMethodName() + "() at line " + stackElement.getLineNumber());
		}
	}

	public static void report() {
		// TODO: Report more useful things, including printing the output to a
		// profile file
		long durationInMs = new Date().getTime() - startTime;
		VM.sysWriteln("\n-------- Begin Coff Results --------");
		VM.sysWriteln("Total Runtime: " + durationInMs / 1000.0 + " s");
		VM.sysWriteln("Total Delay Added: " + totalDelay / 1000000L + " ms");
		VM.sysWriteln("Line being optimized (opt amount: " + optimizationLevel + "): " + fileToProfile + " at line "
				+ lineToProfile + " was sampled " + totalSamples + " times");
		VM.sysWriteln("-------- End Coff Results --------\n");

	}
}
