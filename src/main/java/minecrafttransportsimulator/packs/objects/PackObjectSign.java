package minecrafttransportsimulator.packs.objects;

public class PackObjectSign{
	public SignGeneralConfig general;

    public class SignGeneralConfig{
    	public String font;
    	public TextLines[] textLines;
    }
    
    public class TextLines{
    	public byte characters;
    	public float xPos;
    	public float yPos;
    	public float scale;
    	public String color;
    }
}