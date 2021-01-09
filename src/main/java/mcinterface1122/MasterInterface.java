package mcinterface1122;

import org.apache.logging.log4j.Logger;

import minecrafttransportsimulator.items.components.AItemBase;
import minecrafttransportsimulator.items.components.AItemPack;
import minecrafttransportsimulator.mcinterface.BuilderItem;
import minecrafttransportsimulator.mcinterface.MasterLoader;
import minecrafttransportsimulator.packets.components.NetworkSystem;
import minecrafttransportsimulator.systems.PackParserSystem;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = MasterInterface.MODID, name = MasterInterface.MODNAME, version = MasterInterface.MODVER)
public class MasterInterface{
	//Forge variables.
	static final String MODID = MasterLoader.MODID;
	static final String MODNAME = MasterLoader.MODNAME;
	static final String MODVER = MasterLoader.MODVER;
	
	//Variables common to interface systems
	static Logger logger;
	static final InterfaceCore coreInterface;
	static final InterfaceClient gameInterface;
	static final InterfaceGUI guiInterface;
	static final InterfaceInput inputInterface;
	static final InterfaceRender renderInterface;
	
	static{
		//Enable universal bucket so we can use buckets on fuel pumps.
		FluidRegistry.enableUniversalBucket();
		
		//Create instances of the various interfaces and send them to the loader.
		//If we're on a server, don't create the client interfaces.
		if(FMLCommonHandler.instance().getSide().isClient()){
			coreInterface = new InterfaceCore();
			gameInterface = new InterfaceClient();
			guiInterface = new InterfaceGUI();
			inputInterface = new InterfaceInput();
			renderInterface = new InterfaceRender();
		}else{
			coreInterface = new InterfaceCore();
			gameInterface = null;
			guiInterface = null;
			inputInterface = null;
			renderInterface = null;
		}
		MasterLoader.setInterfaces(MODID, coreInterface, gameInterface, guiInterface, inputInterface, renderInterface);
	}

	@EventHandler
	public void preInit(FMLPreInitializationEvent event){
		//Set logger and add log items from pre-boot operations.
		logger = event.getModLog();
		coreInterface.flushLogQueue();
		
		//Tell the loader where we are.  This allows the loader to start it's loading operations.
		MasterLoader.setMainDirectory(event.getModConfigurationDirectory().getParent());
	}
	
	@EventHandler
	public void init(FMLInitializationEvent event){
		//Init networking interface and fire off command to load packets.
		//Register our own packet for handshaking.
		NetworkSystem.init();
		byte packetIndex = 0;
		NetworkSystem.registerPacket(packetIndex++, PacketEntityCSHandshake.class);
		NetworkSystem.registerPacket(packetIndex++, PacketWorldSavedDataCSHandshake.class);
		MasterLoader.registerPackets(packetIndex);
	}
	
	/**
	 *  Creates a wrapper for the the passed-in Item, saving the wrapper to be registered later.
	 *  This wrapper instance will interact with all MC code via passthrough of the item's methods.
	 */
	public static void createItem(AItemBase item){
		BuilderItem builder = new BuilderItem(item);
		//TODO remove when packs don't register their own items.  Instead, auto-register items from pack creative tabs.
		if(item instanceof AItemPack){
			String packID = ((AItemPack<?>) item).definition.packID;
			if(PackParserSystem.getPackConfiguration(packID) == null){
				builder.setTranslationKey(packID + "." + item.getRegistrationName());
			}
		}
	}
}
