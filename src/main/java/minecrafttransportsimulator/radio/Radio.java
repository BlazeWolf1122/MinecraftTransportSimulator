package minecrafttransportsimulator.radio;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javazoom.jlgui.basicplayer.BasicPlayer;
import javazoom.jlgui.basicplayer.BasicPlayerException;
import mts_to_mc.interfaces.FileInterface;

/**Base class for radios.  Used to provide a common set of tools for all radio implementations.
*
* @author don_bruce
*/
public class Radio{
	//Reference to the RadioContainer that this player is for.
	private final RadioContainer container;
	//Instance of the player associated with this container.
	private final BasicPlayer player;
	//Songs this player will play.
	private List<File> songsToPlay = new ArrayList<File>();
	//Directory or URL this player is playing from.
	private String selectedSource = "";
	//Selected preset of this player.
	private int selectedPreset = -1;
	
	public Radio(RadioContainer container){
		this.container = container;
		this.player = new BasicPlayer();
	}
	
	/**Sets the player to play sound files from a directory in the mts_music folder.
	 * Actual play code is done during the update loop as it's sequential.**/
	public void playLocal(String directoryName, int presetPressed, boolean sorted){
		//Stop playing and reset if we are currently playing songs.
		if(!(player.getStatus() == BasicPlayer.STOPPED || player.getStatus() == BasicPlayer.UNKNOWN)){
			stopPlaying();
		}
		songsToPlay = RadioManager.getMusicFiles(directoryName, sorted);
		selectedPreset = presetPressed;
		selectedSource = directoryName;
	}
	
	/**Plays a sound file or stream from the web.  Returns true if the URL is able to be played.**/
	public boolean playInternet(URL url, int presetPressed){
		songsToPlay.clear();
		try{
			player.open(url);
			player.play();
			selectedPreset = presetPressed;
			selectedSource = url.toString();
			return true;
		}catch(Exception e){
			FileInterface.logError("ERROR: BASICPLAYER URL PLAY CODE HAS FAULTED.");
			FileInterface.logError(e.getMessage());
			return false;
		}
	}
	
	/**Plays a sound file from an InputStream.  Is a generic method for use with un-specified objects.
	 * Could be used for MC sounds in a jar, or music disks from other mods.**/
	public boolean playGeneric(InputStream stream){
		try{
			player.open(stream);
			player.play();
			selectedSource = "Streaming";
			return true;
		}catch(Exception e){
			FileInterface.logError("ERROR: BASICPLAYER INTERNAL PLAY CODE HAS FAULTED.");
			FileInterface.logError(e.getMessage());
			return false;
		}
	}
	
	/**Get the current play state of the radio.
	 * Returns -1 for not playing anything.
	 * Returns 1-10 for playing from the internet.
	 * Returns 11-20 for playing from a folder.**/
	public int getPlayState(){
		if(player.getStatus() == BasicPlayer.PLAYING){
			return songsToPlay.isEmpty() ? selectedPreset + 10 : selectedPreset;
		}
		return -1;
	}
	
	/**Gets the current preset pressed for this radio.**/
	public int getPresetSelected(){
		return selectedPreset;
	}
	
	/**Gets the current source for this radio.  If we are playing from the internet, it will
	 * be the URL.  If we are playing from a directory, it will be the directory name.**/
	public String getSource(){
		return selectedSource;
	}
	
	/**Stops all playing music.  Should be called when the class containing this RadioContainer is destroyed.
	 * If it's not, then the radio won't stop even if the thing is gone!**/
	public void stopPlaying(){
		songsToPlay.clear();
		selectedPreset = -1;
		selectedSource = "";
		try{
			player.stop();
		}catch(Exception e){
			FileInterface.logError("ERROR: BASICPLAYER STOP CODE HAS FAULTED.");
			FileInterface.logError(e.getMessage());
		}
	}
	
	/**Should be called every tick to update the volume and play/pause status.
	 * Return false if we need to remove this radio because it's invalid.**/
	public boolean update(boolean gamePaused){
		try{
			if(container.isValid()){
				if(player.getStatus() == BasicPlayer.PLAYING){
					//If we are playing, and the game is paused, pause the radio.
					//Otherwise, set the volume to the player distance.
					if(gamePaused){
						player.pause();
					}else{
						setVolume((float) (2F*1F/container.getDistanceToPlayer()));
					}
				}else if(player.getStatus() == BasicPlayer.PAUSED){
					//If we are paused, but the game isn't, un-pause us.
					if(!gamePaused){
						player.resume();
					}
				}else if(player.getStatus() == BasicPlayer.STOPPED || player.getStatus() == BasicPlayer.UNKNOWN){
					//If we are stopped, and we are have music files to play, go to the next song.
					//Otherwise, clear out our variables.
					if(!songsToPlay.isEmpty()){
						player.open(songsToPlay.get(0));
						player.play();
						songsToPlay.remove(0);
					}else{
						if(selectedPreset != -1){
							songsToPlay.clear();
							selectedPreset = -1;
							selectedSource = "";
						}
					}
				}
			}else{
				stopPlaying();
				return false;
			}
		}catch(Exception e){
			FileInterface.logError("ERROR: BASICPLAYER INTERNAL UPDATED CODE HAS FAULTED.");
			FileInterface.logError(e.getMessage());
			stopPlaying();
		}
		return true;
	}
	
	/**Sets the player volume.  Parameter should be from 0.0-1.0, but can be greater and will be clamped.**/
	public void setVolume(float volume){
		try{
			player.setGain(volume > 1 ? 1 : volume);
		}catch(BasicPlayerException e){
			FileInterface.logError("ERROR: BASICPLAYER VOLUME CODE HAS FAULTED.");
			FileInterface.logError(e.getMessage());
			stopPlaying();
		}
	}
	
	/**Gets the current volume as a normalized value.**/
	public float getVolume(){
		return (player.getMaximumGain() - player.getMinimumGain())/player.getGainValue();
	}
}
