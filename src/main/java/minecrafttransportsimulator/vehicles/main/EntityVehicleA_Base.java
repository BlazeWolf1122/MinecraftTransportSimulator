package minecrafttransportsimulator.vehicles.main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import minecrafttransportsimulator.baseclasses.Point3d;
import minecrafttransportsimulator.items.instances.ItemPart;
import minecrafttransportsimulator.items.instances.ItemVehicle;
import minecrafttransportsimulator.jsondefs.JSONText;
import minecrafttransportsimulator.jsondefs.JSONVehicle;
import minecrafttransportsimulator.jsondefs.JSONVehicle.VehiclePart;
import minecrafttransportsimulator.mcinterface.InterfaceCore;
import minecrafttransportsimulator.mcinterface.WrapperEntity;
import minecrafttransportsimulator.mcinterface.WrapperNBT;
import minecrafttransportsimulator.mcinterface.WrapperWorld;
import minecrafttransportsimulator.packets.components.InterfacePacket;
import minecrafttransportsimulator.packets.instances.PacketVehiclePartChange;
import minecrafttransportsimulator.packloading.JSONParser;
import minecrafttransportsimulator.rendering.instances.AnimationsVehicle;
import minecrafttransportsimulator.systems.ConfigSystem;
import minecrafttransportsimulator.systems.PackParserSystem;
import minecrafttransportsimulator.vehicles.parts.APart;
import minecrafttransportsimulator.vehicles.parts.PartSeat;

/**Base vehicle class.  All vehicle entities should extend this class.
 * It is primarily responsible for the adding and removal of parts.
 * It is NOT responsible for custom data sets, sounds, or movement.
 * That should be done in sub-classes to keep methods segregated.
 * 
 * @author don_bruce
 */
abstract class EntityVehicleA_Base extends AEntityBase{
	/**The pack definition for this vehicle.*/
	public final JSONVehicle definition;
	
	/**The current subName for this vehicle.  Used to select which definition represents this vehicle.*/
	public String currentSubName;
	
	/**This list contains all parts this vehicle has.  Do NOT directly modify this list.  Instead,
	 * call {@link #addPart}, {@link #addPartFromItem}, or {@link #removePart} to ensure all sub-classed
	 * operations are performed.  Note that if you are iterating over this list when you call one of those
	 * methods, and you don't pass the method an iterator instance, you will get a CME!.
	 */
	public final List<APart> parts = new ArrayList<APart>();
	
	/**List for parts loaded from NBT.  We can't add these parts on construction as we'd error out
	 * due to the various sub-classed variables not being ready yet.  To compensate, we add the parts we
	 * wish to add to this list.  At the end of construction, these will be added to this vehicle, preventing NPEs.
	 * This means that any top-level classes MUST iterate over this list and add parts after construction!
	 */
	public final List<APart> partsFromNBT = new ArrayList<APart>();
	
	/**Cached pack definition mappings for sub-part packs.  First key is the parent vehicle part definition, which links to a map..
	 * This second map is keyed by a part vehicle definition, with the value equal to a corrected vehicle definition.  This means that
	 * in total, this object contains all sub-packs created on any vehicle for any part with sub-packs.  This is done as parts with
	 * sub-parts use relative locations, and thus we need to ensure we have the correct position for them on any vehicle part location.*/
	private static final Map<VehiclePart, Map<VehiclePart, VehiclePart>> SUBPACK_MAPPINGS = new HashMap<VehiclePart, Map<VehiclePart, VehiclePart>>();
	
	/**Animator for vehicles.*/
	private static final AnimationsVehicle animator = new AnimationsVehicle();
	
	/**Cached value for speedFactor.  Saves us from having to use the long form all over.  Not like it'll change in-game...*/
	public static final double SPEED_FACTOR = ConfigSystem.configObject.general.speedFactor.value;
	
	public EntityVehicleA_Base(WrapperWorld world, WrapperEntity wrapper, WrapperNBT data){
		super(world, wrapper, data);
		//Set definition and current subName.
		ItemVehicle item = PackParserSystem.getItem(data.getString("packID"), data.getString("systemName"), data.getString("subName")); 
		this.definition = item.definition;
		this.currentSubName = item.subName;
		
		//Add parts.
		//Also Replace ride-able locations with seat locations.
		//This ensures we use the proper location for mapping operations.
		for(int i=0; i<data.getInteger("totalParts"); ++i){
			//Use a try-catch for parts in case they've changed since this vehicle was last placed.
			//Don't want crashes due to pack updates.
			try{
				WrapperNBT partData = data.getData("part_" + i);
				ItemPart partItem = PackParserSystem.getItem(partData.getString("packID"), partData.getString("systemName"), partData.getString("subName"));
				Point3d partOffset = partData.getPoint3d("offset");
				addPartFromItem(partItem, partData, partOffset, true);
			}catch(Exception e){
				InterfaceCore.logError("Could not load part from NBT.  Did you un-install a pack?");
			}
		}
	}
	
	@Override
	public void update(){
		//Send update call down to all parts.
		//They need to get processed first to handle hitbox logic, or removal based on damage.
		//We call this before we call the super as they need to know the prev statuses..
		Iterator<APart> iterator = parts.iterator();
		while(iterator.hasNext()){
			APart part = iterator.next();
			part.update();
			if(!part.isValid){
				removePart(part, iterator);
			}
		}
		
		//Now call the super to update prev variables.
		super.update();
	}
    
    /**
	 * Adds the passed-part to this vehicle.  This method will check at the passed-in point
	 * if the item-based part can go to this vehicle.  If so, it is constructed and added,
	 * and a packet is sent to all clients to inform them of this change.  Returns true
	 * if all operations completed, false if the part was not able to be added.
	 * If the part is being added during construction, set doingConstruction to true to
	 * prevent calling the lists, maps, and other systems that aren't set up yet.
	 * This method returns true if the part was able to be added, false if something prevented it.
	 */
    public boolean addPartFromItem(ItemPart partItem, WrapperNBT partData, Point3d offset, boolean doingConstruction){
    	//Get the part pack to add.
		VehiclePart packPart = getPackDefForLocation(offset);
		APart partToAdd = null;
		APart parentPart = null;
		//Check to make sure the spot is free.
		if(getPartAtLocation(offset) == null){
			//Check to make sure the part is valid.
			if(partItem.isPartValidForPackDef(packPart)){
				//Try to find the parent part, if this part would have one.
				for(VehiclePart vehiclePack : definition.parts){
					if(vehiclePack.additionalParts != null){
						for(VehiclePart additionalPack : vehiclePack.additionalParts){
							if(offset.equals(additionalPack.pos)){
								parentPart = getPartAtLocation(vehiclePack.pos);
								break;
							}
						}
					}
					if(parentPart != null){
						break;
					}
				}
				
				//If we aren't an additional part, see if we are a sub-part.
				//This consists of both existing and NBT parts.
				List<APart> partsToCheck = new ArrayList<APart>();
				partsToCheck.addAll(parts);
				partsToCheck.addAll(partsFromNBT);
				for(APart part : partsToCheck){
					if(part.definition.subParts != null){
						for(VehiclePart subPartPack : part.definition.subParts){
							VehiclePart correctedPack = getPackForSubPart(part.vehicleDefinition, subPartPack);
							if(offset.equals(correctedPack.pos)){
								parentPart = part;
								break;
							}
							
							//Check sub-part additional parts.
							if(subPartPack.additionalParts != null){
								for(VehiclePart additionalPack : subPartPack.additionalParts){
									VehiclePart correctedAdditionalPack = getPackForSubPart(part.vehicleDefinition, additionalPack);
									if(offset.equals(correctedAdditionalPack.pos)){
										parentPart = getPartAtLocation(correctedPack.pos);
										break;
									}
								}
								if(parentPart != null){
									break;
								}
							}
						}
						if(parentPart != null){
							break;
						}
					}
				}
				
				//Part is valid.  Create it.
				partToAdd = partItem.createPart((EntityVehicleF_Physics) this, packPart, partData != null ? partData : new WrapperNBT(), parentPart); 
			}
		}
    	
    	//If the part isn't null, add it to the vehicle.
		//If we're in construction, it goes in the NBT maps and we need to add a rider position if it's a seat.
		//Otherwise, we use the regular add method.
    	if(partToAdd != null){
    		if(doingConstruction){
    			partsFromNBT.add(partToAdd);
				if(partToAdd instanceof PartSeat){
					ridableLocations.add(partToAdd.placementOffset);
				}
    		}else{
	    		addPart(partToAdd);
				
				//If we are a new part, we need to add text.
	    		boolean newPart = partData.getString("packID").isEmpty();
	    		if(newPart){
					if(partToAdd.definition.rendering != null && partToAdd.definition.rendering.textObjects != null){
						for(JSONText textObject : partToAdd.definition.rendering.textObjects){
							partToAdd.text.put(textObject, textObject.defaultText);
						}
					}
					partData = partToAdd.getData();
	    		}
				
				//Send packet to client with part data.
				InterfacePacket.sendToAllClients(new PacketVehiclePartChange((EntityVehicleF_Physics) this, offset, partItem, partData, partToAdd.parentPart));
				
				//If we are a new part, add default parts.  We need to do this after we send a packet.
				//We need to make sure to convert them to the right type as they're offset.
				if(newPart && partToAdd.definition.subParts != null){
					List<VehiclePart> subPartsToAdd = new ArrayList<VehiclePart>();
					for(VehiclePart subPartPack : partToAdd.definition.subParts){
						subPartsToAdd.add(this.getPackForSubPart(partToAdd.vehicleDefinition, subPartPack));
					}
					addDefaultParts(subPartsToAdd, this, partToAdd, true);
				}
    		}
			return true;
    	}else{
    		return false;
    	}
    }
	
    /**
   	 * Adds the passed-in part to the vehicle.  Also is responsible for modifying
   	 * and lists or maps that may have changed from adding the part.
   	 */
	public void addPart(APart part){
		parts.add(part);
		
		//Add a ride-able location.
		if(part instanceof PartSeat){
			ridableLocations.add(part.placementOffset);
		}
	}
	
	/**
   	 * Removes the passed-in part to the vehicle.  Calls the part's {@link APart#remove()} method to
   	 * let the part handle removal code.  Iterator is optional, but if you're in any code block that
   	 * is iterating over the parts list, and you don't pass that iterator in, you'll get a CME.
   	 */
	public void removePart(APart part, Iterator<APart> iterator){
		if(parts.contains(part)){
			//Remove part from main list of parts.
			if(iterator != null){
				iterator.remove();
			}else{
				parts.remove(part);
			}
			//Remove any riders riding this part from the riding map.
			if(locationRiderMap.containsKey(part.placementOffset)){
				removeRider(locationRiderMap.get(part.placementOffset), null);
			}
			//Call the part's removal code for it to process.
			part.remove();
			//If we are on the server, notify all clients of this change.
			if(!world.isClient()){
				InterfacePacket.sendToAllClients(new PacketVehiclePartChange((EntityVehicleF_Physics) this, part.placementOffset));
			}
		}
		
		//Remove a ride-able location.
		if(part instanceof PartSeat){
			ridableLocations.remove(part.placementOffset);
		}
	}
	
	/**
	 * Gets the part at the specified location.
	 * This also checks NBT parts in case we are doing
	 * this check for parent-part lookups during construction.
	 */
	public APart getPartAtLocation(Point3d offset){
		for(APart part : parts){
			if(part.placementOffset.equals(offset)){
				return part;
			}
		}
		for(APart part : partsFromNBT){
			if(part.placementOffset.equals(offset)){
				return part;
			}
		}
		return null;
	}
	
	/**
	 * Gets all possible pack parts.  This includes additional parts on the vehicle
	 * and extra parts of parts on other parts.  Map returned is the position of the
	 * part positions and the part pack information at those positions.
	 * Note that additional parts will not be added if no part is present
	 * in the primary location.
	 */
	public LinkedHashMap<Point3d, VehiclePart> getAllPossiblePackParts(){
		LinkedHashMap<Point3d, VehiclePart> packParts = new LinkedHashMap<Point3d, VehiclePart>();
		//First get all the regular part spots.
		for(VehiclePart packPart : definition.parts){
			packParts.put(packPart.pos, packPart);
			
			//Check to see if we can put a additional parts in this location.
			//If a part is present at a location that can have an additional parts, we allow them to be placed.
			if(packPart.additionalParts != null){
				for(APart part : parts){
					if(part.placementOffset.equals(packPart.pos)){
						for(VehiclePart additionalPack : packPart.additionalParts){
							packParts.put(additionalPack.pos, additionalPack);
						}
						break;
					}
				}
			}
		}
		
		//Next get any sub parts on parts that are present.
		for(APart part : parts){
			if(part.definition.subParts != null){
				VehiclePart parentPack = getPackDefForLocation(part.placementOffset);
				for(VehiclePart subPartPack : part.definition.subParts){
					VehiclePart correctedPack = getPackForSubPart(parentPack, subPartPack);
					packParts.put(correctedPack.pos, correctedPack);
					
					//Check to see if we can put a additional parts in this location.
					//If a part is present at a location that can have an additional parts, we allow them to be placed.
					if(subPartPack.additionalParts != null){
						for(APart part2 : parts){
							if(part2.placementOffset.equals(correctedPack.pos)){
								for(VehiclePart additionalPack : subPartPack.additionalParts){
									correctedPack = getPackForSubPart(parentPack, additionalPack);
									packParts.put(correctedPack.pos, correctedPack);
								}
								break;
							}
						}
					}
				}
			}
			
		}
		return packParts;
	}
	
	/**
	 * Gets the pack definition at the specified location.
	 */
	public VehiclePart getPackDefForLocation(Point3d offset){
		//Check to see if this is a main part.
		for(VehiclePart packPart : definition.parts){
			if(packPart.pos.equals(offset)){
				return packPart;
			}
			
			//Not a main part.  Check if this is an additional part.
			if(packPart.additionalParts != null){
				for(VehiclePart additionalPack : packPart.additionalParts){
					if(additionalPack.pos.equals(offset)){
						return additionalPack;
					}
				}
			}
		}
		
		//If this is not a main part or an additional part, check the sub-parts.
		//We check both the main parts, and those from NBT in case we're in a loading-loop.
		List<APart> allParts = new ArrayList<APart>();
		allParts.addAll(parts);
		allParts.addAll(partsFromNBT);
		for(APart part : allParts){
			if(part.definition.subParts != null){
				VehiclePart parentPack = getPackDefForLocation(part.placementOffset);
				for(VehiclePart subPartPack : part.definition.subParts){
					VehiclePart correctedPack = getPackForSubPart(parentPack, subPartPack);
					if(correctedPack.pos.equals(offset)){
						return correctedPack;
					}
					
					//Check additional part definitions.
					if(subPartPack.additionalParts != null){
						for(VehiclePart additionalPack : subPartPack.additionalParts){
							correctedPack = getPackForSubPart(parentPack, additionalPack);
							if(correctedPack.pos.equals(offset)){
								return correctedPack;
							}
						}
					}
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Returns a PackPart with the correct properties for a SubPart.  This is because
	 * subParts inherit some properties from their parent parts.  All created sub-part
	 * packs are cached locally once created, as they need to not create new Point3d instances.
	 * If they did, then the lookup relation between them and their spot in the vehicle would
	 * get broken for maps on each reference.
	 */
	public VehiclePart getPackForSubPart(VehiclePart parentPack, VehiclePart subPack){
		if(!SUBPACK_MAPPINGS.containsKey(parentPack)){
			SUBPACK_MAPPINGS.put(parentPack, new HashMap<VehiclePart, VehiclePart>());
		}
		
		VehiclePart correctedPack = SUBPACK_MAPPINGS.get(parentPack).get(subPack);
		if(correctedPack == null){
			//Use GSON to make a deep copy of the current pack definition.
			//Set the sub-part flag to ensure we know this is a subPart for rendering operations.
			correctedPack = JSONParser.duplicateJSON(subPack);
			correctedPack.isSubPart = true;
			
			//Now set parent-specific properties.  These pertain to position, rotation, mirroring, and the like.
			//First add the parent pack's position to the sub-pack.
			//We don't add rotation, as we need to stay relative to the parent part, as the parent part will rotate us.
			correctedPack.pos.add(parentPack.pos);
			
			//If the parent pack is mirrored, we need to invert our X-position to match.
			if(parentPack.pos.x < 0 ^ parentPack.inverseMirroring){
				correctedPack.pos.x -= 2*subPack.pos.x;
			}
			
			//Use the parent's turnsWithSteer variable, as that's based on the vehicle, not the part.
			correctedPack.turnsWithSteer = parentPack.turnsWithSteer;
			
			//Save the corrected pack into the mappings for later use.
	        SUBPACK_MAPPINGS.get(parentPack).put(subPack, correctedPack);
		}
		return correctedPack;
	}
	
	/**
	 * Helper method to allow for recursion when adding default parts.
	 * This method adds all default parts for the passed-in list of parts.
	 * The part list should consist of a "parts" JSON definition.
	 * This method should only be called when the vehicle or part with the
	 * passed-in definition is first placed, not when it's being loaded from saved data.
	 */
	public static void addDefaultParts(List<VehiclePart> partsToAdd, EntityVehicleA_Base vehicle, APart parentPart, boolean sendPacket){
		for(VehiclePart packDef : partsToAdd){
			if(packDef.defaultPart != null){
				try{
					String partPackID = packDef.defaultPart.substring(0, packDef.defaultPart.indexOf(':'));
					String partSystemName = packDef.defaultPart.substring(packDef.defaultPart.indexOf(':') + 1);
					try{
						ItemPart partItem = PackParserSystem.getItem(partPackID, partSystemName);
						APart newPart = partItem.createPart((EntityVehicleF_Physics) vehicle, packDef, new WrapperNBT(), parentPart);
						vehicle.addPart(newPart);
						
						//Set default text for the new part, if we have any.
						if(newPart.definition.rendering != null && newPart.definition.rendering.textObjects != null){
							for(JSONText textObject : newPart.definition.rendering.textObjects){
								newPart.text.put(textObject, textObject.defaultText);
							}
						}
						
						//Send a packet if required.
						if(sendPacket){
							InterfacePacket.sendToAllClients(new PacketVehiclePartChange((EntityVehicleF_Physics) vehicle, newPart.placementOffset, newPart.getItem(), newPart.getData(), parentPart));
						}
						
						//Check if we have an additional parts.
						//If so, we need to check that for default parts.
						if(packDef.additionalParts != null){
							addDefaultParts(packDef.additionalParts, vehicle, newPart, sendPacket);
						}
						
						//Check all sub-parts, if we have any.
						//We need to make sure to convert them to the right type as they're offset.
						if(newPart.definition.subParts != null){
							List<VehiclePart> subPartsToAdd = new ArrayList<VehiclePart>();
							for(VehiclePart subPartPack : newPart.definition.subParts){
								subPartsToAdd.add(vehicle.getPackForSubPart(packDef, subPartPack));
							}
							addDefaultParts(subPartsToAdd, vehicle, newPart, sendPacket);
						}
					}catch(NullPointerException e){
						throw new IllegalArgumentException("Attempted to add defaultPart: " + partPackID + ":" + partSystemName + " to: " + vehicle.definition.packID + ":" + vehicle.definition.systemName + " but that part doesn't exist in the pack item registry.");
					}
				}catch(IndexOutOfBoundsException e){
					throw new IllegalArgumentException("Could not parse defaultPart definition: " + packDef.defaultPart + ".  Format should be \"packId:partName\"");
				}
			}
		}
	}
	
	@Override
    public AnimationsVehicle getAnimationSystem(){
		return animator;
	}
	
	@Override
	public void save(WrapperNBT data){
		super.save(data);
		data.setString("packID", definition.packID);
		data.setString("systemName", definition.systemName);
		data.setString("subName", currentSubName);
		
		int totalParts = 0;
		for(APart part : parts){
			//Don't save the part if it's not valid or a fake part.
			if(part.isValid && !part.isFake()){
				WrapperNBT partData = part.getData();
				//We need to set some extra data here for the part to allow this vehicle to know where it went.
				//This only gets set here during saving/loading, and is NOT returned in the item that comes from the part.
				partData.setString("packID", part.definition.packID);
				partData.setString("systemName", part.definition.systemName);
				partData.setString("subName", part.currentSubName);
				partData.setPoint3d("offset", part.placementOffset);
				data.setData("part_" + totalParts, partData);
				++totalParts;
			}
		}
		data.setInteger("totalParts", totalParts);
	}
}
