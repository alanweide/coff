package coff;

import org.jikesrvm.VM;
import org.jikesrvm.scheduler.RVMThread;

public class Coff {

	private static final boolean DEBUG = true;

	private static RVMThread[] currentThreads;

	private static int lineToProfile;
	private static int curGlobalCounter;
	private static int[] curThreadCounters;

	public static void start() {
		if (DEBUG) {
			VM.sysWriteln("Starting coff...");
		}
	}

	private static void beginExperiment(int line) {

	}
}
