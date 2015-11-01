package coff;

import java.util.Date;

public class Test {
	private static void a() {
		for (long i = 0; i < 10000000000L; i++) {

		}
	}

	private static void b() {
		for (long i = 0; i < 9000000000L; i++) {

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
		a.run();
		b.run();
		a.join();
		b.join();
		double secondsElapsed = (new Date().getTime() - start.getTime()) / 1000.0;
		System.out.println("Ran test in " + secondsElapsed + " seconds");
	}
}
