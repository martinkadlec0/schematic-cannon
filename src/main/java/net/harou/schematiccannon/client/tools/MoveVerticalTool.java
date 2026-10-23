package net.harou.schematiccannon.client.tools;


public class MoveVerticalTool extends SchematicToolBase {

	@Override
	public boolean handleMouseWheel(int delta) {
		if (schematicHandler.isDeployed()) {
			schematicHandler.getTransformation()
				.move(0, delta, 0);
			schematicHandler.markDirty();
		}
		return true;
	}

}
