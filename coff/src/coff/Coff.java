package coff;

import static org.jikesrvm.runtime.SysCall.sysCall;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.StackTrace.Element;
import org.jikesrvm.scheduler.RVMThread;

public class Coff {

	private static final boolean DEBUG = false;

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
	private static final int MIN_SAMPLES_PER_EXPERIMENT = 0;

	private static final long NANOSEC_PER_MILLISEC = 1000000L;

	private static List<RVMThread> applicationThreads;
	private static int[] curThreadCounters;
	private static Map<String, Integer> totalSamplesByLine;
	private static Map<String, Integer> startCounts;

	private static long totalDelay;
	private static long realTotalDelay = 0;

	// private static int lineToProfile;
	// private static String fileToProfile;
	// private static double optimizationLevel;
	private static int curGlobalCounter;
	private static int totalSamplesThisExperiment;

	private static long startTime;

	static {
		totalSamplesByLine = new HashMap<String, Integer>();
	}

	/*
	 * ---------------- Public Methods ----------------
	 */

	public static Thread start() {
		if (DEBUG) {
			VM.sysWriteln("Starting coff...");
			System.out.println("Starting coff...");
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

	/*
	 * ---------------- Private Methods ----------------
	 */

	private static void performAnExperiment() throws InterruptedException {

		// TODO: actually select a random line
		String fileToProfile = "Test.java";
		int lineToProfile = randomLine(fileToProfile);
		double optimizationLevel = randOptLevel();
		curGlobalCounter = 0;
		totalDelay = 0;
		totalSamplesThisExperiment = 0;
		startCounts = new HashMap<String, Integer>(ProgressPoints.counts());

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
				RVMThread thr = RVMThread.threads[j]; // sometimes throws
														// abnormal termination
														// of RVMs
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
				curThreadCounters[j] = curGlobalCounter + getSamplesInThread(stack, lineToProfile, fileToProfile);
			}
			addDelays(optimizationLevel);

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

		/*
		 * Keep track of the number of samples in each line to report at the end
		 */
		String pureLine = fileToProfile + ":" + lineToProfile;
		if (totalSamplesByLine.containsKey(pureLine)) {
			int oldSamples = totalSamplesByLine.get(pureLine);
			oldSamples += totalSamplesThisExperiment;
			totalSamplesByLine.put(pureLine, oldSamples);
		} else {
			totalSamplesByLine.put(pureLine, totalSamplesThisExperiment);
		}

		realTotalDelay += totalDelay;

		reportExperimentResults(totalSamplesThisExperiment, experimentDelays, lineToProfile, fileToProfile,
				optimizationLevel);
		/*
		 * Increase the performance experiment duration for the rest of the
		 * execution if it has changed
		 */
		PERFORMANCE_EXPERIMENT_DURATION = samplesPerExperiment * SAMPLE_GRANULARITY;
	}

	private static int randomLine(String fileToProfile) {
		int numOfLines = 20;
		// FileNotFound Exception
		/*
		 * byte[] c = new byte[1024]; int readChars = 0; boolean empty = true;
		 * try{ InputStream is = new BufferedInputStream(new
		 * FileInputStream(fileToProfile)); try { while ((readChars =
		 * is.read(c)) != -1) { empty = false; for (int i = 0; i < readChars;
		 * ++i) { if (c[i] == '\n') { ++numOfLines; } } } } finally {
		 * is.close(); } }catch(IOException e){ e.printStackTrace(); }
		 * VM.sysWriteln("Number of line in "+fileToProfile+numOfLines);
		 */
		// int x = (int) (Math.random() * 100) % numOfLines;
		// return x;
		/*
		 * TODO: make this better
		 */
		return (Math.random() > 0.5) ? 9 : 15;
	}

	private static double randOptLevel() {
		// Get a random number from 0-1 (with appropriate distribution)
		double ans = 2.0 * Math.max(Math.random() - 0.5, 0.0);
		int intAns = (int) Math.floor(ans * 100);
		if (intAns != 0) {
			// Round it to nearest 5%
			intAns = 5 * (intAns / 5);
		}
		return intAns / 100.0;
	}

	private static void reportExperimentResults(int selectedSamples, long experimentDelays, int lineToProfile,
			String fileToProfile, double optimizationLevel) {
		VM.sysWrite("experiment\tselected=" + fileToProfile + ":" + lineToProfile);
		System.out.print("experiment\tselected=" + fileToProfile + ":" + lineToProfile);
		VM.sysWrite("\tspeedup=" + optimizationLevel);
		System.out.print("\tspeedup=" + optimizationLevel);
		VM.sysWrite("\tduration=" + ((PERFORMANCE_EXPERIMENT_DURATION * NANOSEC_PER_MILLISEC) - experimentDelays));
		System.out.print("\tduration=" + ((PERFORMANCE_EXPERIMENT_DURATION * NANOSEC_PER_MILLISEC) - experimentDelays));
		VM.sysWrite("\tselected-samples=" + selectedSamples);
		System.out.print("\tselected-samples=" + selectedSamples);
		// TODO: do something about progress points
		for (Entry<String, Integer> progressPoint : ProgressPoints.counts().entrySet()) {
			int startCount = startCounts.get(progressPoint.getKey());
			if (progressPoint.getValue() != startCount) {
				VM.sysWrite("\nthroughput-point\tname=" + progressPoint.getKey() + "\tdelta="
						+ (progressPoint.getValue() - startCount));
				System.out.print("\nthroughput-point\tname=" + progressPoint.getKey() + "\tdelta="
						+ (progressPoint.getValue() - startCount));
			}
		}
		VM.sysWriteln();
		System.out.println();
	}

	private static int getSamplesInThread(List<Element> stack, int lineToProfile, String fileToProfile) {
		int numSamplesInMethod = 0;
		for (int i = 0; i < stack.size(); i++) {
			Element e = stack.get(i);
			if (e.getLineNumber() == lineToProfile && e.getFileName().equals(fileToProfile)) {
				numSamplesInMethod++;
				/*
				 * TODO: this is an optimzation described in the paper; test it.
				 */
				// curGlobalCounter++;
				totalSamplesThisExperiment++;

				// Get at most one sample from this line in this stack;
				// recursive functions should only count once
				break;
			}
		}
		return numSamplesInMethod;
	}

	private static void addDelays(double optimizationLevel) {
		for (int threadCounter : curThreadCounters) {
			curGlobalCounter = Math.max(curGlobalCounter, threadCounter);
		}
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
		 * Need to create a separate thread to handshake with the thread we want
		 * to delay
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
		/*
		 * TODO: Report more useful things, including printing the output to a
		 * profile file
		 */
		long durationInMs = new Date().getTime() - startTime;
		VM.sysWriteln("runtime time=" + (durationInMs * NANOSEC_PER_MILLISEC - realTotalDelay));
		System.out.println("runtime time=" + (durationInMs * NANOSEC_PER_MILLISEC - realTotalDelay));
		for (Map.Entry<String, Integer> pair : totalSamplesByLine.entrySet()) {
			VM.sysWriteln("samples location=" + pair.getKey() + "\tcount=" + pair.getValue());
			System.out.println("samples location=" + pair.getKey() + "\tcount=" + pair.getValue());
		}

	}

	public static void beginProfilingThread(RVMThread t) {
		applicationThreads.add(t);
	}

	public static void stopProfilingThread(RVMThread t) {
		applicationThreads.remove(t);
	}
}
