package CoverageInst;

/** Declares that a SEND in any process running `from` can reach a RECEIVE in any process running `to`. */
public final class RoleLink {

	public final String from;
	public final String to;

	public RoleLink(String from, String to) {
		this.from = from;
		this.to = to;
	}
}
