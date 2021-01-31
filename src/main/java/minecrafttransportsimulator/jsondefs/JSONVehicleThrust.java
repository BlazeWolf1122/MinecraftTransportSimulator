package minecrafttransportsimulator.jsondefs;

import java.util.List;

import minecrafttransportsimulator.packloading.JSONParser.JSONDescription;

public class JSONVehicleThrust{
	@JSONDescription("Test1")
	public boolean isGlobal;
	
	@JSONDescription("Test2")
	public List<JSONAnimationDefinition> forces;
}
