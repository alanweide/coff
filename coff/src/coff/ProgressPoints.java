package coff;

import java.util.HashMap;
import java.util.Map;

public class ProgressPoints {

	private static Map<String, Integer> progressPointCounts = new HashMap<String, Integer>();
	private static boolean coffEnabled = false;
	private volatile static boolean experimentInProgress = false;

	public static void CoffProgressNamed(final String name) {
		// if (experimentInProgress) {
		// try {
		// ProgressPoints.class.wait();
		// } catch (IllegalMonitorStateException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// } catch (InterruptedException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		// }
		if (coffEnabled) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					if (progressPointCounts.containsKey(name)) {
						int curCount = progressPointCounts.get(name);
						progressPointCounts.put(name, 1 + curCount);
					} else {
						progressPointCounts.put(name, 1);
					}
				}
			}).start();
		}
	}

	public static void turnOn() {
		coffEnabled = true;
	}

	public static Map<String, Integer> counts() {
		return progressPointCounts;
	}

	public static void beginReport() {
		experimentInProgress = true;
	}

	public static void endReport() {
		experimentInProgress = false;
		ProgressPoints.class.notifyAll();
	}
}
