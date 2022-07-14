package ValiPar;

public class Required_elements {

	protected RequiredElem c_use_association_requirements;
	protected RequiredElem c_use_requirements;
	protected RequiredElem def_requirements;
	protected RequiredElem edge_requirements;
	protected RequiredElem inter_m_c_use_association_requirements;
	protected RequiredElem inter_m_p_use_association_requirements;
	protected RequiredElem intra_m_c_use_association_requirements;
	protected RequiredElem intra_m_p_use_association_requirements;
	protected RequiredElem m_use_association_requirements;
	protected RequiredElem m_use_requirements;
	protected RequiredElem node_requirements;
	protected RequiredElem p_use_association_requirements;
	protected RequiredElem p_use_requirements;
	protected RequiredElem s_c_use_association_requirements;
	protected RequiredElem s_p_use_association_requirements;

	private RequiredElem sync_edge_requirements;

	public Required_elements(int testID) {
		/*
		 * c_use_association_requirements = new
		 * RequiredElem("c_use_association_requirements"); c_use_requirements = new
		 * RequiredElem("c_use_requirements"); def_requirements = new
		 * RequiredElem("def_requirements"); edge_requirements = new
		 * RequiredElem("edge_requirements"); inter_m_c_use_association_requirements =
		 * new RequiredElem("inter_m_c_use_association_requirements");
		 * inter_m_p_use_association_requirements = new
		 * RequiredElem("inter_m_p_use_association_requirements");
		 * intra_m_c_use_association_requirements = new
		 * RequiredElem("intra_m_c_use_association_requirements");
		 * intra_m_p_use_association_requirements = new
		 * RequiredElem("intra_m_p_use_association_requirements");
		 * m_use_association_requirements = new
		 * RequiredElem("m_use_association_requirements"); m_use_requirements = new
		 * RequiredElem("m_use_requirements"); node_requirements = new
		 * RequiredElem("node_requirements"); p_use_association_requirements = new
		 * RequiredElem("p_use_association_requirements"); p_use_requirements = new
		 * RequiredElem("p_use_requirements"); s_c_use_association_requirements = new
		 * RequiredElem("s_c_use_association_requirements");
		 * s_p_use_association_requirements = new
		 * RequiredElem("s_p_use_association_requirements");
		 */
		//sync_edge_requirements = new RequiredElem("sync_edge_requirements", testID);
	}

	public RequiredElem getC_use_association_requirements() {
		return c_use_association_requirements;
	}

	public RequiredElem getC_use_requirements() {
		return c_use_requirements;
	}

	public RequiredElem getDef_requirements() {
		return def_requirements;
	}

	public RequiredElem getEdge_requirements() {
		return edge_requirements;
	}

	public RequiredElem getInter_m_c_use_association_requirements() {
		return inter_m_c_use_association_requirements;
	}

	public RequiredElem getInter_m_p_use_association_requirements() {
		return inter_m_p_use_association_requirements;
	}

	public RequiredElem getIntra_m_c_use_association_requirements() {
		return intra_m_c_use_association_requirements;
	}

	public RequiredElem getIntra_m_p_use_association_requirements() {
		return intra_m_p_use_association_requirements;
	}

	public RequiredElem getM_use_association_requirements() {
		return m_use_association_requirements;
	}

	public RequiredElem getM_use_requirements() {
		return m_use_requirements;
	}

	public RequiredElem getNode_requirements() {
		return node_requirements;
	}

	public RequiredElem getP_use_association_requirements() {
		return p_use_association_requirements;
	}

	public RequiredElem getP_use_requirements() {
		return p_use_requirements;
	}

	public RequiredElem getS_c_use_association_requirements() {
		return s_c_use_association_requirements;
	}

	public RequiredElem getS_p_use_association_requirements() {
		return s_p_use_association_requirements;
	}

	public RequiredElem getSync_edge_requirements() {
		return sync_edge_requirements;
	}
/*
	public static ArrayList<RequiredElem> getAllRequiredElems() {
		ArrayList<RequiredElem> arrayRE = new ArrayList<RequiredElem>();
		arrayRE.add(getC_use_association_requirements());
		arrayRE.add(getC_use_requirements());
		arrayRE.add(getDef_requirements());
		arrayRE.add(getEdge_requirements());
		arrayRE.add(getInter_m_c_use_association_requirements());
		arrayRE.add(getInter_m_p_use_association_requirements());
		arrayRE.add(getIntra_m_c_use_association_requirements());
		arrayRE.add(getIntra_m_p_use_association_requirements());
		arrayRE.add(getM_use_association_requirements());
		arrayRE.add(getM_use_requirements());
		arrayRE.add(getNode_requirements());
		arrayRE.add(getP_use_association_requirements());
		arrayRE.add(getP_use_requirements());
		arrayRE.add(getS_c_use_association_requirements());
		arrayRE.add(getS_p_use_association_requirements());
		arrayRE.add(getSync_edge_requirements());

		return arrayRE;

	}
	*/
/*
	public static RequiredElem getRequiredElem(String required_element) { // Melhorar método com Reflection
																			// posteriormente

		switch (required_element) {
		case "c_use_association_requirements":
			return getC_use_association_requirements();
		case "c_use_requirements":
			return getC_use_requirements();
		case "def_requirements":
			return getDef_requirements();
		case "edge_requirements":
			return getEdge_requirements();
		case "inter_m_c_use_association_requirements":
			return getInter_m_c_use_association_requirements();
		case "inter_m_p_use_association_requirements":
			return getInter_m_p_use_association_requirements();
		case "intra_m_c_use_association_requirements":
			return getIntra_m_c_use_association_requirements();
		case "intra_m_p_use_association_requirements":
			return getIntra_m_p_use_association_requirements();
		case "m_use_association_requirements":
			return getM_use_association_requirements();
		case "m_use_requirements":
			return getM_use_requirements();
		case "node_requirements":
			return getNode_requirements();
		case "p_use_association_requirements":
			getP_use_association_requirements();
		case "p_use_requirements":
			return getP_use_requirements();
		case "s_c_use_association_requirements":
			return getS_c_use_association_requirements();
		case "s_p_use_association_requirements":
			return getS_p_use_association_requirements();
		case "sync_edge_requirements":
			return getSync_edge_requirements();
		default:
			return null;
		}
	}
*/
}
