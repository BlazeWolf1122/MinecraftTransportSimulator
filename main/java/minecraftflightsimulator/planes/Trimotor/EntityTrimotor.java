package minecraftflightsimulator.planes.Trimotor;

import minecraftflightsimulator.MFS;
import minecraftflightsimulator.containers.ContainerParent;
import minecraftflightsimulator.containers.GUIParent;
import minecraftflightsimulator.containers.SlotBucket;
import minecraftflightsimulator.containers.SlotFuel;
import minecraftflightsimulator.containers.SlotItem;
import minecraftflightsimulator.containers.SlotPassenger;
import minecraftflightsimulator.containers.SlotPilot;
import minecraftflightsimulator.entities.core.EntityPlane;
import minecraftflightsimulator.helpers.InstrumentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

public class EntityTrimotor extends EntityPlane{
	private static final ResourceLocation foregroundGUI = new ResourceLocation("mfs", "textures/gui_mc172.png");
	private static final ResourceLocation backplateTexture = new ResourceLocation("mfs", "textures/trimotor_side.png");
	private static final ResourceLocation moldingTexture = new ResourceLocation("mfs", "textures/trimotor_side_rotated.png");
	
	public EntityTrimotor(World world){
		super(world);
	}
	
	public EntityTrimotor(World world, float posX, float posY, float posZ, float rotation, int textureCode){
		super(world, posX, posY, posZ, rotation, textureCode);
		
	}

	@Override
	protected void initPlaneProperties(){
		//TODO set new properties
		hasFlaps = false;
		maxFuel = 5000;
		emptyMass=800;
		momentRoll=1285;
		momentPitch=1825;
		momentYaw=2667;
		wingspan=12;
		wingArea=16;
		tailDistance=7;
		rudderArea=1.5F;
		elevatorArea=3.0F;
		defaultElevatorAngle=-3;
		initialDragCoeff=0.0341F;
		dragAtCriticalAoA=0.12F;
		dragCoeffOffset = (float) ((dragAtCriticalAoA - initialDragCoeff)/Math.pow(15 - 0, 2));		
	}
	
	@Override
	protected void initChildPositions(){
		//TODO set new positions
		addCenterGearPosition(new float[]{0, -1F, -10F});
		addLeftGearPosition(new float[]{-3F, -2F, -1.5F});
		addRightGearPosition(new float[]{3F, -2F, -1.5F});
		addEnginePosition(new float[]{0, -0.3F, 1.65F});
		addPropellerPosition(new float[]{0, -0.375F, 2.5F});
		addPilotPosition(new float[]{0, -.1F, 0});
		addPassengerPosition(new float[]{0, -.1F, -1});
	}
	
	@Override
	public float[][] getCoreLocations(){
		return new float[][]{{0, -1F, 1F}, {0, -1F, -5F}, {0, 0.5F, -2.5F}};
	}
	
	@Override
	public void drawHUD(int width, int height){
		InstrumentHelper.drawBasicHUD(this, width, height, backplateTexture, moldingTexture);
	}
	
	@Override
	public GUIParent getGUI(EntityPlayer player){
		//TODO make new GUI
		return new GUIParent(player, this, foregroundGUI);
	}
	
	@Override
	public void initParentContainerSlots(ContainerParent container){
		//TODO make new GUI
		container.addSlotToContainer(new SlotItem(this, 6, 6, 1, MFS.proxy.wheelSmall));
		container.addSlotToContainer(new SlotItem(this, 6, 24, 2, MFS.proxy.wheelLarge));
		container.addSlotToContainer(new SlotItem(this, 6, 42, 4, MFS.proxy.wheelLarge));
		container.addSlotToContainer(new SlotItem(this, 90, 30, 6, MFS.proxy.engineLarge));
		container.addSlotToContainer(new SlotItem(this, 90, 11, 10, MFS.proxy.propeller));
		container.addSlotToContainer(new SlotPilot(this, 90, 51));
		container.addSlotToContainer(new SlotPassenger(this, 90, 69));
		container.addSlotToContainer(new SlotFuel(this, 125, 11));
		container.addSlotToContainer(new SlotBucket(this, 143, 11));	
	}
}