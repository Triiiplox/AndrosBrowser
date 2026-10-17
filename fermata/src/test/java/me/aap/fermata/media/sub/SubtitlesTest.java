package me.aap.fermata.media.sub;

import androidx.annotation.NonNull;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

import me.aap.utils.concurrent.HandlerExecutor;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.IntConsumer;
import me.aap.utils.function.LongSupplier;

/**
 * @author Andrey Pavlenko
 */
public class SubtitlesTest extends Assert {

	@Test
	public void testGetNext() {
		int n = 1000;
		var rnd = new Random();
		Subtitles s = new Subtitles(b -> {});
		assertNull(s.getNext(1));

		s = new Subtitles(b -> {
			for (int i = 0; i < n; i++) {
				b.add("Text " + i, rnd.nextInt(100000), rnd.nextInt(100) + 1);
			}
		});

		for (int i = 0, c = s.size(); i < c; i++) {
			assertEquals(i, s.get(i).getIndex());
		}
		for (var t : s) {
			assertSame(t, s.getNext(t.getTime()));
		}
		s.get(s.size() / 2);
		for (int i = s.size() - 1; i >= 0; i--) {
			Subtitles.Text t = s.get(i);
			assertSame(t, s.getNext(t.getTime()));
		}
	}

	@Test
	public void testSrtSubtitles() throws IOException {
		// @formatter:off
		var text =
"""
WEBVTT
Kind: captions
Language: en

1
00:00:00.900 --> 00:00:01.100
{\\an7}TOP_LEFT

2
00:01:01,000 --> 00:01:03,000
{\\an5}MIDDLE_CENTER

3
09:59:59,999 --> 10:59:59.999
{\\an2}BOTTOM_CENTER

4
11:00:00.000 --> 11:00:00,001
BOTTOM_CENTER

5
12:00:00,000 --> 12:00:00,001
BOTTOM_CENTER
Multi line
""";
		// @formatter:on

		var sg = FileSubtitles.load(new ByteArrayInputStream(text.getBytes()));
		var s = sg.get(SubGrid.Position.TOP_LEFT).get(0);
		assertEquals(900, s.getTime());
		assertEquals(200, s.getDuration());
		assertEquals("TOP_LEFT", s.getText());
		s = sg.get(SubGrid.Position.MIDDLE_CENTER).get(0);
		assertEquals(61000, s.getTime());
		assertEquals(2000, s.getDuration());
		assertEquals("MIDDLE_CENTER", s.getText());
		s = sg.get(SubGrid.Position.BOTTOM_CENTER).get(0);
		assertEquals(9 * 60 * 60000 + 59 * 60000 + 59000 + 999, s.getTime());
		assertEquals(3600000, s.getDuration());
		assertEquals("BOTTOM_CENTER", s.getText());
		s = sg.get(SubGrid.Position.BOTTOM_CENTER).get(1);
		assertEquals(11 * 60 * 60000, s.getTime());
		assertEquals(1, s.getDuration());
		assertEquals("BOTTOM_CENTER", s.getText());
		s = sg.get(SubGrid.Position.BOTTOM_CENTER).get(2);
		assertEquals(12 * 60 * 60000, s.getTime());
		assertEquals(1, s.getDuration());
		assertEquals("BOTTOM_CENTER\nMulti line", s.getText());
	}

	@Test
	public void testScheduler() throws IOException {
		StringBuilder sb = new StringBuilder();
		IntConsumer itos = i -> {
			if (i < 10) {
				sb.append("00").append(i);
			} else if (i < 100) {
				sb.append("0").append(i);
			} else {
				sb.append(i);
			}
		};

		int n = 49;
		int dur = 10;
		List<String> expect = new ArrayList<>(2 * n);
		List<String> received = new ArrayList<>(2 * n);

		for (int i = 1, d = dur; i <= n; i++, d += dur) {
			var s = String.valueOf(i);
			expect.add(s);
			sb.append(s).append('\n');
			sb.append("00:00:00,");
			itos.accept(d);
			d += dur;
			sb.append(" --> 00:00:00,");
			itos.accept(d);
			sb.append('\n').append(s).append("\n\n");
		}

		var sg = FileSubtitles.load(new ByteArrayInputStream(sb.toString().getBytes()));
		// Virtual time: the 10ms subtitles are shorter than a busy CI runner takes to start a
		// thread, so a real clock made the test skip a subtitle and wait forever.
		var exec = new VirtualTimeExecutor();
		BiConsumer<SubGrid.Position, Subtitles.Text> consumer = (p, t) -> {
			if (t != null) received.add(t.getText());
		};
		SubScheduler sched = new SubScheduler(exec, sg, consumer, exec);
		float[][] runs = {{0, 1}, {10, 1}, {10, 2}, {10, 0.5f}, {0, 1.5f}, {7, 3}, {3, 0.7f}};

		for (var r : runs) {
			received.clear();
			sched.start(0, (int) r[0], r[1]);
			exec.runAll();
			assertArrayEquals("delay " + r[0] + ", speed " + r[1], expect.toArray(),
					received.toArray());
			sched.stop(false);
		}
	}

	private static final class VirtualTimeExecutor extends HandlerExecutor implements LongSupplier {
		private final PriorityQueue<Task> queue = new PriorityQueue<>();
		private long now;
		private long seq;

		@Override
		public synchronized Cancellable schedule(@NonNull Runnable task, long delay) {
			var t = new Task(now + delay, seq++, task);
			queue.add(t);
			return () -> queue.remove(t);
		}

		@Override
		public long getAsLong() {
			return now;
		}

		void runAll() {
			for (int i = 0; !queue.isEmpty(); i++) {
				assertTrue("Scheduler does not stop", i < 100000);
				var t = queue.poll();
				now = Math.max(now, t.time);
				t.task.run();
			}
		}

		private record Task(long time, long seq, Runnable task) implements Comparable<Task> {
			@Override
			public int compareTo(Task o) {
				return (time != o.time) ? Long.compare(time, o.time) : Long.compare(seq, o.seq);
			}
		}
	}
}
