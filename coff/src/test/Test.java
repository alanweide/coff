package test;

import java.util.Date;

import coff.ProgressPoints;

public class Test {
	private static void a() {
		for (int i = 0; i < 20000000; i++) {
			ProgressPoints.CoffProgressNamed("a");
		}
	}

	private static void b() {
		for (int i = 0; i < 19000000; i++) {
			ProgressPoints.CoffProgressNamed("b");
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
		ProgressPoints.CoffProgressNamed("end");
		// System.out.println("Ran test in " + secondsElapsed + " seconds");
	}
}
