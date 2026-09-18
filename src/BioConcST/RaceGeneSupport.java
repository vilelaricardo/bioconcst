package BioConcST;

import java.util.List;

import CoverageInst.RacePoint;
import io.jenetics.Genotype;
import io.jenetics.IntegerGene;

/**
 * Converts a genotype's race-choice genes into the same
 * "destProcessId sender1,sender2" grammar CoverageInst.ReplaySchedule
 * already parses (ReplaySchedule.fromInlineString) - this class only builds
 * the string, it never touches ReplaySchedule's own API.
 *
 * Each race point contributes one line: the chosen winner (decoded from its
 * IntegerGene allele, an index into candidateSenderIds) listed first,
 * followed by the remaining candidates in their existing stable order. For
 * a race point with exactly 2 candidates (the only shape exercised by
 * quorum-handshake today) this fully and unambiguously determines the
 * arrival order; for more candidates, only the winner is decided by the
 * gene; the rest keep RacePoint's own deterministic (not evolved) order.
 */
public final class RaceGeneSupport {

	private RaceGeneSupport() {
	}

	public static String toScheduleInlineString(List<RacePoint> racePoints, Genotype<IntegerGene> genotype,
			int inputGeneCount) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < racePoints.size(); i++) {
			RacePoint racePoint = racePoints.get(i);
			int winnerIndex = genotype.get(inputGeneCount + i).get(0).allele();
			winnerIndex = Math.floorMod(winnerIndex, racePoint.candidateSenderIds.size());

			if (i > 0) {
				sb.append(';');
			}
			sb.append(racePoint.destinationProcessId).append(' ');
			sb.append(racePoint.candidateSenderIds.get(winnerIndex));
			for (int c = 0; c < racePoint.candidateSenderIds.size(); c++) {
				if (c != winnerIndex) {
					sb.append(',').append(racePoint.candidateSenderIds.get(c));
				}
			}
		}
		return sb.toString();
	}
}
