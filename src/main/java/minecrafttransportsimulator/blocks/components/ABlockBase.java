package minecrafttransportsimulator.blocks.components;

import java.util.List;

import minecrafttransportsimulator.baseclasses.BoundingBox;
import minecrafttransportsimulator.baseclasses.Point3i;
import minecrafttransportsimulator.wrappers.WrapperBlock;
import minecrafttransportsimulator.wrappers.WrapperPlayer;
import minecrafttransportsimulator.wrappers.WrapperWorld;

/**Base Block class.  This type is used in the constructor of {@link WrapperBlock} to allow us to use
 * completely custom code that is not associated with MC's standard block code that changes EVERY FREAKING VERSION.
 * Seriously guys, you make a game about blocks.  How many times you gonna re-invent them?
 * Anyways... This code contains methods for the block's hardness, blast resistance, and rotation.
 *
 * @author don_bruce
 */
public abstract class ABlockBase{
	public final float hardness;
	public final float blastResistance;
	
	protected static final BoundingBox SINGLE_BLOCK_BOUNDS = new BoundingBox(0, 0, 0, 0.5D, 0.5D, 0.5D);
	
	public ABlockBase(float hardness, float blastResistance){
		this.hardness = hardness;
		this.blastResistance = blastResistance;
	}
	
	/**
	 *  Called when this block is first placed in the world.  Can be used to set the
	 *  state of the associated TileEntity based on position or the item that was
	 *  used to place this block.
	 */
	public void onPlaced(WrapperWorld world, Point3i point, WrapperPlayer player){}
	
	/**
	 *  Called when this block is right-clicked.  Return true if this block does
	 *  a thing, false if the block just exists to be pretty.  Actions may
	 *  or may not be taken.  Note that this is called both on the server and
	 *  on the client, so watch your actions and packets!
	 */
	public boolean onClicked(WrapperWorld world, Point3i point, WrapperPlayer player){
		return false;
	}

	/**
	 *  Gets the current rotation of the block at the passed-in point.
	 *  Angle will be either 0, 90, 180, or 270.  This is internally
	 *  set by MC-standard methods when the player places the block, and is
	 *  not modifiable by any block-based code.
	 */
	public float getRotation(WrapperWorld world, Point3i point){
		return WrapperBlock.getRotation(world, point);
	}
	
	/**
	 *  Adds all collision boxes to the passed-in list.  This is sent back to MC
	 *  to handle collisions with this block.  May be based on state or TE data.
	 *  Note that all collisions are relative to the block's location.
	 */
	public void addCollisionBoxes(WrapperWorld world, Point3i point, List<BoundingBox> collidingBoxes){
		collidingBoxes.add(SINGLE_BLOCK_BOUNDS);
	}
}