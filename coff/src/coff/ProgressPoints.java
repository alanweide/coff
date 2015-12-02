package coff;

import java.util.HashMap;
import java.util.Map;

public class ProgressPoints {

	private static Map<String, Integer> progressPointCounts = new HashMap<String, Integer>();
	private static volatile boolean coffEnabled = false;

	public static void CoffProgressNamed(final String name) {
		if (true) {
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
}
