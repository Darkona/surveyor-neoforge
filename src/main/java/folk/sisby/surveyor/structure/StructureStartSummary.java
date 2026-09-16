package folk.sisby.surveyor.structure;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class StructureStartSummary {
	protected final List<StructurePieceSummary> children;
	protected BoundingBox boundingBox;

	public StructureStartSummary(List<StructurePieceSummary> children) {
		this.children = children;
	}

	public BoundingBox getBoundingBox() {
		if (boundingBox == null) {
			boundingBox = BoundingBox.encapsulatingBoxes(children.stream().map(StructurePieceSummary::getBoundingBox)::iterator).orElse(null);
			if (boundingBox == null) return new BoundingBox(0, 0, 0, 0, 0, 0);
		}
		return boundingBox;
	}

	public List<StructurePieceSummary> getChildren() {
		return children;
	}
}
