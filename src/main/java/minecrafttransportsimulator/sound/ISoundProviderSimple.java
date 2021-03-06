package minecrafttransportsimulator.sound;

import minecrafttransportsimulator.baseclasses.Point3d;
import minecrafttransportsimulator.mcinterface.WrapperWorld;

/**Interface for classes that need to have simple sounds played via the audio system.
 * Designed to be server-safe and only query the class implementing this interface for information
 * about the sound state rather than having that class feed it all back to a non-existent system.
 *
 * @author don_bruce
 */
public interface ISoundProviderSimple{
    
    /**
	 *  Return the position of this provider as a Point3d.
	 */
    public Point3d getProviderPosition();
    
    /**
	 *  Return the world this provider is in.
	 */
    public WrapperWorld getProviderWorld();
}
