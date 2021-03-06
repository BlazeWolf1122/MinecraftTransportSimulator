package minecrafttransportsimulator.vehicles.main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import minecrafttransportsimulator.baseclasses.BeaconManager;
import minecrafttransportsimulator.baseclasses.FluidTank;
import minecrafttransportsimulator.baseclasses.Point3d;
import minecrafttransportsimulator.baseclasses.RadioBeacon;
import minecrafttransportsimulator.items.instances.ItemInstrument;
import minecrafttransportsimulator.items.instances.ItemPart;
import minecrafttransportsimulator.jsondefs.JSONSubDefinition;
import minecrafttransportsimulator.jsondefs.JSONText;
import minecrafttransportsimulator.jsondefs.JSONVehicle.VehiclePart;
import minecrafttransportsimulator.mcinterface.InterfaceClient;
import minecrafttransportsimulator.mcinterface.WrapperEntity;
import minecrafttransportsimulator.mcinterface.WrapperNBT;
import minecrafttransportsimulator.mcinterface.WrapperPlayer;
import minecrafttransportsimulator.mcinterface.WrapperWorld;
import minecrafttransportsimulator.packets.components.InterfacePacket;
import minecrafttransportsimulator.packets.instances.PacketVehicleControlAnalog;
import minecrafttransportsimulator.packets.instances.PacketVehicleControlDigital;
import minecrafttransportsimulator.packets.instances.PacketVehiclePartEngine;
import minecrafttransportsimulator.packets.instances.PacketVehiclePartEngine.Signal;
import minecrafttransportsimulator.rendering.components.ITextProvider;
import minecrafttransportsimulator.rendering.components.LightType;
import minecrafttransportsimulator.rendering.instances.ParticleMissile;
import minecrafttransportsimulator.sound.IRadioProvider;
import minecrafttransportsimulator.sound.InterfaceSound;
import minecrafttransportsimulator.sound.Radio;
import minecrafttransportsimulator.sound.SoundInstance;
import minecrafttransportsimulator.systems.ConfigSystem;
import minecrafttransportsimulator.systems.PackParserSystem;
import minecrafttransportsimulator.vehicles.parts.APart;
import minecrafttransportsimulator.vehicles.parts.PartEngine;
import minecrafttransportsimulator.vehicles.parts.PartGroundDevice;
import minecrafttransportsimulator.vehicles.parts.PartGun;
import minecrafttransportsimulator.vehicles.parts.PartInteractable;

/**This class adds engine components for vehicles, such as fuel, throttle,
 * and electricity.  Contains numerous methods for gauges, HUDs, and fuel systems.
 * This is added on-top of the D level to keep the crazy movement calculations
 * separate from the vehicle power overhead bits.  This is the first level of
 * class that can be used for references in systems as it's the last common class for
 * vehicles.  All other sub-levels are simply functional building-blocks to keep this
 *  class from having 1000+ lines of code and to better segment things out.
 * 
 * @author don_bruce
 */
abstract class EntityVehicleE_Powered extends EntityVehicleD_Moving implements IRadioProvider, ITextProvider{
	
	//External state control.
	public boolean hornOn;
	public boolean sirenOn;
	public boolean reverseThrust;
	public boolean gearUpCommand;
	public boolean beingFueled;
	public static final byte MAX_THROTTLE = 100;
	public byte throttle;
	
	
	//Internal states.
	public int gearMovementTime;
	public double electricPower;
	public double electricUsage;
	public double electricFlow;
	public String selectedBeaconName;
	public RadioBeacon selectedBeacon;
	public FluidTank fuelTank;
	/**Map containing text lines for saved text.  Note that parts have their own text, so it's not saved here.**/
	public final LinkedHashMap<JSONText, String> text = new LinkedHashMap<JSONText, String>();
	
	//Part maps.
	public final Map<Integer, ItemInstrument> instruments = new HashMap<Integer, ItemInstrument>();
	public final Map<Byte, PartEngine> engines = new HashMap<Byte, PartEngine>();
	public final List<PartGroundDevice> wheels = new ArrayList<PartGroundDevice>();
	public final HashMap<ItemPart, List<PartGun>> guns = new LinkedHashMap<ItemPart, List<PartGun>>();
	
	//Map containing incoming missiles, sorted by distance.
	public final TreeMap<Double, ParticleMissile> missilesIncoming = new TreeMap<Double, ParticleMissile>();
	
	//Internal radio variables.
	private final Radio radio;
	
	public EntityVehicleE_Powered(WrapperWorld world, WrapperEntity wrapper, WrapperNBT data){
		super(world, wrapper, data);
		
		//Load simple variables.
		this.hornOn = data.getBoolean("hornOn");
		this.sirenOn = data.getBoolean("sirenOn");
		this.reverseThrust = data.getBoolean("reverseThrust");
		this.gearUpCommand = data.getBoolean("gearUpCommand");
		this.throttle = (byte) data.getInteger("throttle");
		this.electricPower = data.getDouble("electricPower");
		this.selectedBeaconName = data.getString("selectedBeaconName");
		this.selectedBeacon = BeaconManager.getBeacon(world, selectedBeaconName);
		this.fuelTank = new FluidTank(data, definition.motorized.fuelCapacity, world.isClient());
		
		//Load text.
		if(definition.rendering.textObjects != null){
			for(int i=0; i<definition.rendering.textObjects.size(); ++i){
				text.put(definition.rendering.textObjects.get(i), data.getString("textLine" + i));
			}
		}
		
		//Load instruments.
		for(int i = 0; i<definition.motorized.instruments.size(); ++i){
			String instrumentPackID = data.getString("instrument" + i + "_packID");
			String instrumentSystemName = data.getString("instrument" + i + "_systemName");
			if(!instrumentPackID.isEmpty()){
				ItemInstrument instrument = PackParserSystem.getItem(instrumentPackID, instrumentSystemName);
				//Check to prevent loading of faulty instruments due to updates.
				if(instrument != null){
					instruments.put(i, instrument);
				}
			}
		}
		
		//Create radio.
		this.radio = new Radio(this, data);
	}
	
	@Override
	public void update(){
		super.update();
		//Start sounds if we haven't already.  We have to do this via the update check, as some mods will create
		//vehicles in random locations for their code.  I'm looking at YOU, The One Probe!
		if(ticksExisted == 1 && world.isClient()){
			startSounds();
			for(APart part : parts){
				part.startSounds();
			}
		}
		
		//If we have space for fuel, and we have tanks with it, transfer it.
		if(!world.isClient() && fuelTank.getFluidLevel() < definition.motorized.fuelCapacity - 100){
			for(APart part : parts){
				if(part instanceof PartInteractable && part.definition.interactable.feedsVehicles){
					FluidTank tank = ((PartInteractable) part).tank;
					if(tank != null){
						double amountFilled = tank.drain(fuelTank.getFluid(), 1, true);
						if(amountFilled > 0){
							fuelTank.fill(fuelTank.getFluid(), amountFilled, true);
						}
					}
				}
			}
		}
		
		//Check to make sure the selected beacon is still correct.
		//It might not be valid if it has been removed from the world,
		//or one might have been placed that matches our selection.
		if(definition.general.isAircraft && ticksExisted%20 == 0){
			selectedBeacon = BeaconManager.getBeacon(world, selectedBeaconName);
		}
		
		//Do trailer-specific logic, if we are one and towed.
		//Otherwise, do normal update logic for DRLs.
		if(definition.motorized.isTrailer){
			//Check to make sure vehicle isn't dead for some reason.
			if(towedByVehicle != null && !towedByVehicle.isValid){
				towedByVehicle = null;
			}else{
				//If we are being towed update our variables to match the vehicle we are being towed by.
				//Also set the brake state to the same as the towing vehicle.
				//If we aren't being towed, set the parking brake.
				if(towedByVehicle != null){
					variablesOn.clear();
					variablesOn.addAll(towedByVehicle.variablesOn);
					parkingBrakeOn = false;
					brake = towedByVehicle.brake;
				}else{
					parkingBrakeOn = true;
				}
			}
		}else{
			//Turn on the DRLs if we have an engine on.
			variablesOn.remove(LightType.DAYTIMELIGHT.lowercaseName);
			for(PartEngine engine : engines.values()){
				if(engine.state.running){
					variablesOn.add(LightType.DAYTIMELIGHT.lowercaseName);
					break;
				}
			}
		}
		
		//Set electric usage based on light status.
		if(electricPower > 2){
			for(LightType light : LightType.values()){
				if(light.hasBeam && light.isInCollection(variablesOn)){
					electricUsage += 0.0005F;
				}
			}
		}
		electricPower = Math.max(0, Math.min(13, electricPower -= electricUsage));
		electricFlow = electricUsage;
		electricUsage = 0;
		
		//Adjust gear variables.
		if(gearUpCommand && gearMovementTime < definition.motorized.gearSequenceDuration){
			++gearMovementTime;
		}else if(!gearUpCommand && gearMovementTime > 0){
			--gearMovementTime;
		}
		
		//Check that missiles are still valid.
		//If they are, update their distances. Otherwise, remove them.
		ParticleMissile missile;
		Iterator<Double> iterator = missilesIncoming.keySet().iterator();
		final TreeMap<Double, ParticleMissile> tempMap = new TreeMap<Double, ParticleMissile>();
		while(iterator.hasNext()) {
			double dist = iterator.next();
			missile = missilesIncoming.get(dist);
			iterator.remove();
			if(missile != null && missile.isValid) {
				tempMap.put(position.distanceTo(missile.position), missile);
			}
		}
		missilesIncoming.putAll(tempMap);
	}
	
	@Override
	public boolean addRider(WrapperEntity rider, Point3d riderLocation){
		if(super.addRider(rider, riderLocation)){
			if(world.isClient() && ConfigSystem.configObject.clientControls.autostartEng.value && rider.equals(InterfaceClient.getClientPlayer())){
				if(rider instanceof WrapperPlayer && locationRiderMap.containsValue(rider) && getPartAtLocation(locationRiderMap.inverse().get(rider)).vehicleDefinition.isController){
					for(PartEngine engine : engines.values()){
						if(!engine.state.running){
							InterfacePacket.sendToServer(new PacketVehiclePartEngine(engine, Signal.AS_ON));
						}
					}
					InterfacePacket.sendToServer(new PacketVehicleControlDigital((EntityVehicleF_Physics) this, PacketVehicleControlDigital.Controls.P_BRAKE, false));
				}
			}
			return true;
		}else{
			return false;
		}
	}
	
	@Override
	public void removeRider(WrapperEntity rider, Iterator<WrapperEntity> iterator){
		if(world.isClient() && ConfigSystem.configObject.clientControls.autostartEng.value && rider.equals(InterfaceClient.getClientPlayer())){
			if(rider instanceof WrapperPlayer && locationRiderMap.containsValue(rider)){
				APart riddenPart = getPartAtLocation(locationRiderMap.inverse().get(rider));
				boolean otherController = false;
				if(riddenPart.vehicleDefinition.isController){
					//Check if another player is in a controller seat.  If so, don't stop the engines.
					for(APart part : parts){
						if(!part.equals(riddenPart)){
							if(locationRiderMap.containsKey(part.placementOffset)){
								if(part.vehicleDefinition.isController){
									otherController = true;
									break;
								}
							}
						}
					}
					if(!otherController){
						for(PartEngine engine : engines.values()){
							InterfacePacket.sendToServer(new PacketVehiclePartEngine(engine, Signal.MAGNETO_OFF));
						}
						InterfacePacket.sendToServer(new PacketVehicleControlAnalog((EntityVehicleF_Physics) this, PacketVehicleControlAnalog.Controls.BRAKE, (short) 0, Byte.MAX_VALUE));
						InterfacePacket.sendToServer(new PacketVehicleControlDigital((EntityVehicleF_Physics) this, PacketVehicleControlDigital.Controls.P_BRAKE, true));
					}
				}
			}
		}
		super.removeRider(rider, iterator);
	}
	
	@Override
	public boolean isLitUp(){
		return ConfigSystem.configObject.clientRendering.vehicleBlklt.value && LightType.DAYTIMELIGHT.isInCollection(variablesOn);
	}
	
	@Override
	public void destroyAt(Point3d location){
		super.destroyAt(location);
		//Spawn instruments in the world.
		for(ItemInstrument instrument : instruments.values()){
			world.spawnItem(instrument, null, location);
		}
		
		//Oh, and add explosions.  Because those are always fun.
		//Note that this is done after spawning all parts here and in the super call,
		//so although all parts are DROPPED, not all parts may actually survive the explosion.
		if(ConfigSystem.configObject.damage.explosions.value){
			double explosivePower = 0;
			for(APart part : parts){
				if(part instanceof PartInteractable){
					explosivePower += ((PartInteractable) part).getExplosiveContribution();
				}
			}
			world.spawnExplosion(this, location, explosivePower + fuelTank.getExplosiveness() + 1D, true);
		}
		
		//If we are being towed, unhook us from our tower.
		if(towedByVehicle != null){
			towedByVehicle.towedVehicle = null;
			towedByVehicle = null;
		}
	}
	
	@Override
	protected float getCurrentMass(){
		return (float) (super.getCurrentMass() + fuelTank.getWeight());
	}
	
	@Override
	public void addPart(APart part){
		super.addPart(part);
		if(part instanceof PartEngine){
			//Because parts is a list, the #1 engine will always come before the #2 engine.
			//We can use this to determine where in the list this engine needs to go.
			byte engineNumber = 0;
			for(VehiclePart packPart : definition.parts){
				for(String type : packPart.types){
					if(type.startsWith("engine")){
						if(part.placementOffset.equals(packPart.pos)){
							engines.put(engineNumber, (PartEngine) part);
							return;
						}
						++engineNumber;
					}
				}
			}
		}else if(!part.vehicleDefinition.isSpare){
			if(part instanceof PartGroundDevice){
				if(part.definition.ground.isWheel || part.definition.ground.isTread){
					wheels.add((PartGroundDevice) part);
				}
			}else if(part instanceof PartGun){
				if(!guns.containsKey(part.getItem())){
					guns.put(part.getItem(), new ArrayList<PartGun>());
				}
				guns.get(part.getItem()).add((PartGun) part);
			}
		}
	}
	
	@Override
	public void removePart(APart part, Iterator<APart> iterator){
		super.removePart(part, iterator);
		byte engineNumber = 0;
		for(VehiclePart packPart : definition.parts){
			for(String type : packPart.types){
				if(type.startsWith("engine")){
					if(part.placementOffset.equals(packPart.pos)){
						engines.remove(engineNumber);
						return;
					}
					++engineNumber;
				}
			}
		}
		if(wheels.contains(part)){
			wheels.remove(part);
		}else if(part instanceof PartGun){
			for(List<PartGun> gunList : guns.values()){
				if(gunList.contains(part)){
					gunList.remove(part);
				}
			}
		}
	}
	
	public void acquireMissile(ParticleMissile missile) {
		//Add this missile with its current distance
		if(!missilesIncoming.containsValue(missile)) {
			missilesIncoming.put(position.distanceTo(missile.position), missile);
		}
	}
	
	//-----START OF SOUND AND ANIMATION CODE-----
	@Override
	public void startSounds(){
		if(hornOn){
			InterfaceSound.playQuickSound(new SoundInstance(this, definition.motorized.hornSound, true));
		}else if(sirenOn){
			InterfaceSound.playQuickSound(new SoundInstance(this, definition.motorized.sirenSound, true));
		}
	}
	
	@Override
	public void updateProviderSound(SoundInstance sound){
		if(!isValid){
			sound.stop();
		}else if(sound.soundName.equals(definition.motorized.hornSound)){
			if(!hornOn){
				sound.stop();
			}
		}else if(sound.soundName.equals(definition.motorized.sirenSound)){
			if(!sirenOn){
				sound.stop();
			}
		}
	}
    
	@Override
    public Point3d getProviderVelocity(){
		return motion;
	}
	
	@Override
	public Radio getRadio(){
		return radio;
	}
	
	@Override
	public float getLightPower(){
		return (float) (electricPower/12F);
	}
	
	@Override
	public LinkedHashMap<JSONText, String> getText(){
		return text;
	}
	
	@Override
	public String getSecondaryTextColor(){
		for(JSONSubDefinition subDefinition : definition.definitions){
			if(subDefinition.subName.equals(currentSubName)){
				return subDefinition.secondColor;
			}
		}
		throw new IllegalArgumentException("Tried to get the definition for a vehicle of subName:" + currentSubName + ".  But that isn't a valid subName for the vehicle:" + definition.packID + ":" + definition.systemName + ".  Report this to the pack author as this is a missing JSON component!");
	}
	
	@Override
	public boolean renderTextLit(){
		return (LightType.NAVIGATIONLIGHT.isInCollection(variablesOn) || LightType.RUNNINGLIGHT.isInCollection(variablesOn) || LightType.HEADLIGHT.isInCollection(variablesOn)) && electricPower > 3;
	}
	
	@Override
	public void save(WrapperNBT data){
		super.save(data);
		data.setBoolean("hornOn", hornOn);
		data.setBoolean("sirenOn", sirenOn);
		data.setBoolean("reverseThrust", reverseThrust);
		data.setBoolean("gearUpCommand", gearUpCommand);
		data.setInteger("throttle", throttle);
		data.setDouble("electricPower", electricPower);
		data.setString("selectedBeaconName", selectedBeaconName);
		fuelTank.save(data);
		
		int lineNumber = 0;
		for(String textLine : text.values()){
			data.setString("textLine" + lineNumber++, textLine);
		}
		
		String[] instrumentsInSlots = new String[definition.motorized.instruments.size()];
		for(int i=0; i<instrumentsInSlots.length; ++i){
			if(instruments.containsKey(i)){
				data.setString("instrument" + i + "_packID", instruments.get(i).definition.packID);
				data.setString("instrument" + i + "_systemName", instruments.get(i).definition.systemName);
			}
		}
		
		radio.save(data);
	}
}
