package coff;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ProgressPoints {

	private static ConcurrentMap<String, Integer> progressPointCounts = new ConcurrentHashMap<String, Integer>();

	public static void CoffProgressNamed(String name) {
		if (progressPointCounts.containsKey(name)) {
			progressPointCounts.put(name, 1 + progressPointCounts.get(name));
		} else {
			progressPointCounts.put(name, 1);
		}
	}

	public static ConcurrentMap<String, Integer> counts() {
		return progressPointCounts;
	}
}
