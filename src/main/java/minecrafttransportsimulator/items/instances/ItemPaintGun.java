package minecrafttransportsimulator.items.instances;

import java.util.List;

import minecrafttransportsimulator.baseclasses.Point3i;
import minecrafttransportsimulator.blocks.components.ABlockBase.Axis;
import minecrafttransportsimulator.blocks.tileentities.components.ATileEntityBase;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityDecor;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityPole;
import minecrafttransportsimulator.guis.components.InterfaceGUI;
import minecrafttransportsimulator.guis.instances.GUIPaintGun;
import minecrafttransportsimulator.items.components.AItemBase;
import minecrafttransportsimulator.items.components.IItemVehicleInteractable;
import minecrafttransportsimulator.mcinterface.InterfaceCore;
import minecrafttransportsimulator.mcinterface.WrapperNBT;
import minecrafttransportsimulator.mcinterface.WrapperPlayer;
import minecrafttransportsimulator.mcinterface.WrapperWorld;
import minecrafttransportsimulator.packets.instances.PacketPlayerChatMessage;
import minecrafttransportsimulator.vehicles.main.EntityVehicleF_Physics;
import minecrafttransportsimulator.vehicles.parts.APart;

public class ItemPaintGun extends AItemBase implements IItemVehicleInteractable{
	
	@Override
	public void addTooltipLines(List<String> tooltipLines, WrapperNBT data){
		tooltipLines.add(InterfaceCore.translate("info.item.paintgun.use"));
	}
	
	@Override
	public CallbackType doVehicleInteraction(EntityVehicleF_Physics vehicle, APart part, WrapperPlayer player, PlayerOwnerState ownerState, boolean rightClick){
		//If the player isn't the owner of the vehicle, they can't interact with it.
		if(!ownerState.equals(PlayerOwnerState.USER)){
			if(rightClick){
				if(vehicle.world.isClient()){
					if(part != null){
						InterfaceGUI.openGUI(new GUIPaintGun(part, player));
					}else{
						InterfaceGUI.openGUI(new GUIPaintGun(vehicle, player));
					}
				}else{
					return CallbackType.PLAYER;
				}
			}
		}else{
			player.sendPacket(new PacketPlayerChatMessage("interact.failure.vehicleowned"));
		}
		return CallbackType.NONE;
	}
	
	@Override
	public boolean onBlockClicked(WrapperWorld world, WrapperPlayer player, Point3i point, Axis axis){
		if(world.isClient()){
			ATileEntityBase<?> tile = world.getTileEntity(point);
			if(tile instanceof TileEntityDecor){
				InterfaceGUI.openGUI(new GUIPaintGun((TileEntityDecor) tile, player));
				return true;
			}else if(tile instanceof TileEntityPole){
				//Change the axis to match the 8-dim axis for poles.  Blocks only get a 4-dim axis.
				axis = Axis.getFromRotation(player.getYaw()).getOpposite();
				if(((TileEntityPole) tile).components.containsKey(axis)){
					InterfaceGUI.openGUI(new GUIPaintGun((TileEntityPole) tile, axis, player));
				}
				return true;
			}
		}
		return false;
	}
}
