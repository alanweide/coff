package coff;

import java.util.Date;

public class Test {
	private static void a() {
		int i = 0;
		while (i < 2000000000) {
			i++;
		}
	}

	private static void b() {
		int i = 0;
		while (i < 1900000000) {
			i++;
		}
	}

	public static void main(String[] args) throws InterruptedException {
		Date start = new Date();
		Thread a = new Thread(new Runnable() {

			@Override
			public void run() {
				a();
			}
		});
		Thread b = new Thread(new Runnable() {

			@Override
			public void run() {
				b();
			}
		});
		a.start();
		b.start();
		// System.out.println("Started both threads");
		b.join();
		a.join();
		double secondsElapsed = (new Date().getTime() - start.getTime()) / 1000.0;
		// System.out.println("Ran test in " + secondsElapsed + " seconds");
	}
}
