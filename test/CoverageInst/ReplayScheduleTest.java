package CoverageInst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * ReplaySchedule is what makes a selected GA individual's execution
 * deterministically reproducible - it records, per destination process, the
 * exact order senders' packets arrived during a free run, purely by reading
 * RECEIVE lines back off the trace log. A wrong order or a dropped
 * destination here silently breaks replay determinism (a champion's replay
 * bundle disagreeing with what it actually scored on - the "champions show
 * 0/6 coverage" class of bug this session root-caused), so both the
 * trace-reading and the two serialization round trips (file and inline
 * string) are covered directly.
 */
class ReplayScheduleTest {

	private static File traceDir(String... linesByProcess) throws Exception {
		File dir = Files.createTempDirectory("coverageinst-replay-test").toFile();
		for (int processId = 0; processId < linesByProcess.length; processId++) {
			if (linesByProcess[processId] != null) {
				Files.writeString(new File(dir, "trace-" + processId + ".log").toPath(), linesByProcess[processId]);
			}
		}
		return dir;
	}

	@Test
	void recordsReceiveOrderExactlyAsItAppearsInTheTraceLog() throws Exception {
		File dir = traceDir("SEND A#main:0 1\nRECEIVE B#main:0 2\nRECEIVE B#main:1 1\nRECEIVE B#main:2 2\n");

		ReplaySchedule schedule = ReplaySchedule.buildFromTrace(dir, List.of(0));

		assertEquals(List.of(2, 1, 2), schedule.senderOrderFor(0));
	}

	@Test
	void nonReceiveLinesAreIgnoredWhenBuildingTheSchedule() throws Exception {
		File dir = traceDir("SEND A#main:0 1\nSEM_RELEASE X#run:0 555\nNODE A#main:B0 -\nRECEIVE B#main:0 3\n");

		ReplaySchedule schedule = ReplaySchedule.buildFromTrace(dir, List.of(0));

		assertEquals(List.of(3), schedule.senderOrderFor(0));
	}

	@Test
	void anUnresolvedReceiveCorrelationIsSkippedRatherThanGuessed() throws Exception {
		File dir = traceDir("RECEIVE B#main:0 1\nRECEIVE B#main:1 UNKNOWN(127.0.0.1:9)\nRECEIVE B#main:2 2\n");

		ReplaySchedule schedule = ReplaySchedule.buildFromTrace(dir, List.of(0));

		assertEquals(List.of(1, 2), schedule.senderOrderFor(0));
	}

	@Test
	void aProcessWithNoReceivesIsAbsentFromTheScheduleEntirely() throws Exception {
		File dir = traceDir("SEND A#main:0 1\n");

		ReplaySchedule schedule = ReplaySchedule.buildFromTrace(dir, List.of(0));

		assertTrue(schedule.senderOrderFor(0).isEmpty());
	}

	@Test
	void senderOrderForAnUnknownDestinationIsAnEmptyListNotNull() throws Exception {
		ReplaySchedule schedule = ReplaySchedule.buildFromTrace(traceDir(), List.of());

		assertEquals(List.of(), schedule.senderOrderFor(99));
	}

	@Test
	void inlineStringRoundTripsMultipleDestinationsAndSenders() {
		ReplaySchedule original = ReplaySchedule
				.fromInlineString("0 1,2,1;3 0,0");

		String inline = original.toInlineString();
		ReplaySchedule roundTripped = ReplaySchedule.fromInlineString(inline);

		assertEquals(List.of(1, 2, 1), roundTripped.senderOrderFor(0));
		assertEquals(List.of(0, 0), roundTripped.senderOrderFor(3));
	}

	@Test
	void fileRoundTripPreservesEveryDestinationsSenderOrder() throws Exception {
		File dir = traceDir("RECEIVE B#main:0 1\nRECEIVE B#main:1 2\n", "RECEIVE C#main:0 0\n");
		ReplaySchedule original = ReplaySchedule.buildFromTrace(dir, List.of(0, 1));

		File saved = new File(Files.createTempDirectory("coverageinst-replay-save").toFile(), "schedule.txt");
		original.save(saved);
		ReplaySchedule loaded = ReplaySchedule.load(saved);

		assertEquals(List.of(1, 2), loaded.senderOrderFor(0));
		assertEquals(List.of(0), loaded.senderOrderFor(1));
	}

	@Test
	void aDestinationWithNoSendersInTheInlineFormIsHandledGracefully() {
		ReplaySchedule schedule = ReplaySchedule.fromInlineString("5 ");

		assertFalse(schedule.senderOrderFor(5) == null);
		assertTrue(schedule.senderOrderFor(5).isEmpty());
	}
}
