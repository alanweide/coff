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
	private static int PERFORMANCE_EXPERIMENT_DURATION = 100;

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

	private static final long NANOSEC_PER_MILLISEC = 1000000L;

	private static List<RVMThread> applicationThreads;
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
					try {
						performAnExperiment();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					sysCall.sysNanoSleep(COOLDOWN_TIME * NANOSEC_PER_MILLISEC);
				}
			}
		});
		coffThread.start();
		return coffThread;
	}

	private static void performAnExperiment() throws InterruptedException {

		// TODO: select a random line
		String fileToProfile = "Test.java";
		int lineToProfile = randomLine(fileToProfile);
		int optimizationLevel = randOptLevel();

		int samplesPerExperiment = PERFORMANCE_EXPERIMENT_DURATION / SAMPLE_GRANULARITY;
		for (int i = 1; i <= samplesPerExperiment; i++) {

			/*
			 * TODO: do thread accounting on thread start and end from RVM, not
			 * every sample
			 */
			RVMThread.acctLock.lockNoHandshake();
			List<List<Element>> usefulStacks = new ArrayList<List<Element>>();
			applicationThreads = new ArrayList<RVMThread>();
			for (int j = 0; j < RVMThread.numThreads; j++) {
				RVMThread thr = RVMThread.threads[j];
				if (thr != null && thr.isAlive() && !thr.isBootThread() && !thr.isDaemonThread()
						&& !thr.isSystemThread()) {
					thr.beginPairHandshake();
					if (thr.contextRegisters != null && !thr.ignoreHandshakesAndGC()) {
						List<Element> stack = RVMThread.getStack(thr.contextRegisters.getInnermostFramePointer());
						applicationThreads.add(thr);
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
				curThreadCounters[j] = curGlobalCounter + getSamplesInThread(stack);
			}
			addDelays();

			if (curGlobalCounter < MIN_SAMPLES_PER_EXPERIMENT && i == samplesPerExperiment
					&& (samplesPerExperiment * SAMPLE_GRANULARITY) < MAX_PERFORMANCE_EXPERIMENT_DURATION) {
				samplesPerExperiment *= 2;
			}

			/*
			 * Wait to take the next sample
			 */
			sysCall.sysNanoSleep(SAMPLE_GRANULARITY * NANOSEC_PER_MILLISEC);
		}
		long experimentDelays = (long) (optimizationLevel
				* (curGlobalCounter * SAMPLE_GRANULARITY * NANOSEC_PER_MILLISEC));

		reportExperimentResults(experimentDelays);
		/*
		 * Increase the performance experiment duration for the rest of the
		 * execution if it has changed
		 */
		PERFORMANCE_EXPERIMENT_DURATION = samplesPerExperiment * SAMPLE_GRANULARITY;
	}

	private static int randomLine(String fileToProfile) {
		return (Math.random() < 0.5 ? 7 : 13);
		/*
		 * TODO: make this a random line from all source files
		 */
	}

	private static int randOptLevel() {
		// Get a random number from 0-1 (with appropriate distribution)
		double ans = 2.0 * Math.max(Math.random() - 0.5, 0.0);
		int intAns = 0;
		if (ans != 0.0) {
			// Round it to nearest 5%
			intAns = 5 * (int) (ans * (100 / 5));
		}

		return intAns;
	}

	private static void reportExperimentResults(long experimentDelays) {
		VM.sysWriteln("Effective duration = "
				+ (PERFORMANCE_EXPERIMENT_DURATION - (experimentDelays / NANOSEC_PER_MILLISEC)));
		VM.sysWriteln("Line sampled = " + fileToProfile + " line " + lineToProfile);
		VM.sysWriteln("Virtual optimization = " + optimizationLevel);
		VM.sysWriteln();
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
		for (int i = 0; i < applicationThreads.size(); i++) {
			long delay = (long) (optimizationLevel
					* ((curGlobalCounter - curThreadCounters[i]) * SAMPLE_GRANULARITY * NANOSEC_PER_MILLISEC));
			delayThread(applicationThreads.get(i), delay);
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

	public static void cleanup() {
		// TODO: Report more useful things, including printing the output to a
		// profile file
		long durationInMs = new Date().getTime() - startTime;
		VM.sysWriteln("\nTotal Runtime: " + durationInMs / 1000.0 + " s");

	}

	public static void beginProfilingThread(RVMThread t) {
		applicationThreads.add(t);
	}

	public static void stopProfilingThread(RVMThread t) {
		applicationThreads.remove(t);
	}
}
