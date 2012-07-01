package vacuum.changedamage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import vacuum.changedamage.equations.ExpressionParser;
import vacuum.changedamage.equations.PostfixNotation;
import vacuum.changedamage.equations.element.number.Variable;
import vacuum.changedamage.equations.element.number.VariablePool;
import vacuum.changedamage.hooks.ArmorHook;
import vacuum.changedamage.listener.DamageListener;
import vacuum.changedamage.listener.FallListener;
import vacuum.changedamage.listener.PotionListener;

public class ChangeDamagePlugin extends JavaPlugin{

	private static final String fileRepository = "http://vacuum-changedamage.googlecode.com/svn/trunk/resources/";
	private static final String experimentalUpdateRepository = fileRepository + "jars/experimental/";
	private static final String stableUpdateRepository = fileRepository + "jars/stable/";
	private static final String updateJAR = "ChangeDamage.jar";
	private static final String updateVersion = "version.txt";

	public static boolean research = false;
	private DamageListener dl;
	private boolean verbose;
	private static final String idFile = "items.txt";
	private static final String potionEffectFile = "potioneffects.txt";
	private ArmorHook armorHook;
	private PotionListener potionListener;
	//private PlayerJoinListener pjl;
	private FallListener fl;

	@Override
	public void onEnable() {
		
		if(update())
			return;
		
		getDataFile("config.yml", false);

		boolean b  = false;
		if(!getConfig().contains("pvponly")){
			getConfig().createSection("pvponly");
			getConfig().set("pvponly", true);
			b = true;
		}

		if(!getConfig().contains("verbose")){
			getConfig().createSection("verbose");
			getConfig().set("verbose", false);
			b = true;
		}

		if(!getConfig().contains("research")){
			getConfig().createSection("research");
			getConfig().set("research", true);
			b = true;
		}

		if(!getConfig().contains("damages")){
			getConfig().createSection("damages");
			b = true;
		}

		if(!getConfig().contains("damages.default")){
			getConfig().createSection("damages.default");

			//put some values in
			getConfig().createSection("damages.default.DIAMOND_SWORD");
			getConfig().set("damages.default.DIAMOND_SWORD", 9);
			b = true;
		}
		
		if(!getConfig().contains("damages.expression")){
			getConfig().createSection("damages.expression");
			b = true;
		}

		if(!getConfig().contains("damages.expression.critical") || getConfig().getString("damages.expression.critical").equals("i 2 / 2 + rand * fl")){
			getConfig().createSection("damages.expression.critical");
			getConfig().set("damages.expression.critical", "i i 2 / 2 + rand * fl +");
			b = true;
		}
		
		if(!getConfig().contains("damages.expression.weakness")){
			getConfig().createSection("damages.expression.weakness");
			getConfig().set("damages.expression.weakness", "i 2 w << -");
			b = true;
		}
		
		if(!getConfig().contains("damages.expression.strength")){
			getConfig().createSection("damages.expression.strength");
			getConfig().set("damages.expression.strength", "i 3 s << +");
			b = true;
		}

		if(!getConfig().contains("armor")){
			getConfig().createSection("armor");
			b = true;
		}

		if(!getConfig().contains("armor.default")){
			getConfig().createSection("armor.default");

			//put some values in
			getConfig().createSection("armor.default.DIAMOND_CHESTPLATE");
			getConfig().set("armor.default.DIAMOND_CHESTPLATE", 8);
			b = true;
		}
		
		if(!getConfig().contains("fall")){
			getConfig().createSection("fall");
			b = true;
		}
		
		if(!getConfig().contains("fall.expression")){
			getConfig().createSection("fall.expression");
			getConfig().set("fall.expression", "d 3 - a 0 * +");
			b = true;
		}

		/*if(!getConfig().contains("potion")){
			getConfig().createSection("potion");
			b = true;
		}

		if(!getConfig().contains("potion.duration")){
			getConfig().createSection("potion.duration.default");

			//put some values in
			getConfig().createSection("potion.duration.default.HEAL");
			getConfig().set("potion.duration.default.HEAL", 1.0);
			b = true;
		}

		if(!getConfig().contains("potion.amplifier")){
			getConfig().createSection("potion.amplifier.default");

			//put some values in
			getConfig().createSection("potion.amplifier.default.HEAL");
			getConfig().set("potion.amplifier.default.HEAL", 1.0);
			b = true;
		}*/

		if(b)
			try {
				getConfig().save(getDataFile("config.yml", false));
			} catch (IOException e) {
				e.printStackTrace();
			}

		verbose = getConfig().getBoolean("verbose", false);
		dl = new DamageListener();
		fl = new FallListener();
		try {
			armorHook = new ArmorHook();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		/*potionListener = new PotionListener();*/
		reload();
		getServer().getPluginManager().registerEvents(dl, this);
		//getServer().getPluginManager().registerEvents(pjl, this);
		getServer().getPluginManager().registerEvents(fl, this);
		/*getServer().getPluginManager().registerEvents(potionListener, this);*/
	}

	private void reload() {
		dl.clear();
		armorHook.restore();
		//pjl.close();
		/*potionListener.clear();*/
		loadDamageMap();
		loadArmor();
		loadDamageEquations();
		loadFall();
		/*loadPotionEffects();*/
		/*
		File f = getDataFile(damageFile, true);
		try {
			Scanner s = new Scanner(f);
			while(s.hasNext()){
				String line = s.nextLine();
				try {
					String name = line.substring(0, line.indexOf(' ')).toUpperCase().replace(" ", "_");
					if(name.equals("FLYING_ARROW")){
						dl.setArrowDamage(null, Double.parseDouble(line.substring(line.indexOf(' ') + 1)));
						continue;
					}
					int id = getID(name);
					if(id == -1){
						System.out.println("Failed to find item " + name);
						continue;
					}
					int damage = Integer.parseInt(line.substring(line.indexOf(' ') + 1));
					dl.put(null, id, damage);
					if(verbose)
						System.out.println("Put " + id + ", " + damage);
				} catch (Throwable t){
					//there was some funny syntax
					t.printStackTrace();
					System.err.println("[" + getDescription().getName() + "] File syntax error in id file. Skipping...");
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}*/
		research = getConfig().getBoolean("research", true);
		boolean pvpOnly = getConfig().getBoolean("pvponly", true);
		dl.setPVPOnly(pvpOnly);
		dl.setVerbose(verbose);
		/*potionListener.setPVPOnly(pvpOnly);
		potionListener.setVerbose(verbose);*/
		//		dl.setDefaultDamage(null, getConfig().getInt("defaultdamage", -1));
	}

	private void loadFall() {
		System.out.println("[ChangeDamage] Loading fall expression");
		
		VariablePool pool = new VariablePool(false);
		Variable d = pool.register("d", 0);
		Variable a = pool.register("a", 0);
		String eq = getConfig().getString("fall.equation", "");
		PostfixNotation expression = ExpressionParser.parsePostfix(eq, pool);
		fl.setExpression(expression, d, a);
		
		fl.setEventPriority(getConfig().getString("fall.priority"));
		
		System.out.println("[ChangeDamage] Successfully loaded fall damage");
	}

	private void loadDamageEquations() {
		System.out.println("[ChangeDamage] Loading damage expressions");
		VariablePool pool = dl.pool;
		String[] expressions = {
				"critical",
				"strength",
				"weakness",
		};
		for (String string : expressions) {
			String expression = getConfig().getString("damages.expression." + string);
			if(verbose)
				System.out.println("[ChangeDamage] Loading " + string + " expression: " + expression);
			dl.setEquation(string, ExpressionParser.parsePostfix(expression, pool));
		}
		System.out.println("[ChangeDamage] Successfully loaded damage expressions");
		/*PostfixNotation notation = ExpressionParser.parsePostfix(eq, pool);
		if(pjl == null)
			pjl = new PlayerJoinListener(notation, n);
		else
			pjl.open(notation, n);*/
	}

	@SuppressWarnings("unused")
	private void loadPotionEffects() {
		/* duration */
		{
			ConfigurationSection section = getConfig().getConfigurationSection("potion.duration");
			for(String s : section.getKeys(false)){
				ConfigurationSection sub = section.getConfigurationSection(s);
				World w = (s.equals("default")) ? null : getServer().getWorld(s);
				System.out.println("[" + getDescription().getName() + "]Loading potion effects for world " + ((w == null) ? "default" : w.getName()));
				for(String str : sub.getKeys(false)){
					try{
						potionListener.putDuration(getID(str, potionEffectFile), sub.getDouble(str));
					} catch (Exception ex){
						ex.printStackTrace();
						System.out.println("[" + getDescription().getName() + "]Configuration node potion." + s + "." + str + " is causing an issue.");
					}
				}
			}
		}
		/* amplifier */
		{
			ConfigurationSection section = getConfig().getConfigurationSection("potion.amplifier");
			for(String s : section.getKeys(false)){
				ConfigurationSection sub = section.getConfigurationSection(s);
				World w = (s.equals("default")) ? null : getServer().getWorld(s);
				System.out.println("[" + getDescription().getName() + "]Loading potion effects for world " + ((w == null) ? "default" : w.getName()));
				for(String str : sub.getKeys(false)){
					try{
						potionListener.putAmplifier(getID(str, potionEffectFile), sub.getDouble(str));
					} catch (Exception ex){
						ex.printStackTrace();
						System.out.println("[" + getDescription().getName() + "]Configuration node potion." + s + "." + str + " is causing an issue.");
					}
				}
			}
		}
		System.out.println("[" + getDescription().getName() + "]Successfully loaded potion effects!");

	}

	private void loadArmor() {
		ConfigurationSection section = getConfig().getConfigurationSection("armor");
		for(String s : section.getKeys(false)){
			ConfigurationSection sub = section.getConfigurationSection(s);
			World w = (s.equals("default")) ? null : getServer().getWorld(s);
			System.out.println("[" + getDescription().getName() + "] Loading armor modifications for world " + ((w == null) ? "default" : w.getName()));
			for(String str : sub.getKeys(false)){
				try{
					armorHook.modifyArmorValue(getID(str, idFile), sub.getInt(str));
				} catch (Exception ex){
					ex.printStackTrace();
					System.out.println("[" + getDescription().getName() + "] Configuration node armor." + s + "." + str + " is causing an issue.");
				}
			}
		}
		System.out.println("[" + getDescription().getName() + "] Successfully loaded armor modifications!");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command,
			String label, String[] args) {
		if(command.getName().equalsIgnoreCase("changedamage")){
			if(args[0].equalsIgnoreCase("release")){
				this.getPluginLoader().disablePlugin(this);
			} else if(args[0].equalsIgnoreCase("reload")){
				reload();
			} else
				return false;
			return true;
		}
		return false;
	}

	public int getID(String name, String file){

		try{
			return Integer.parseInt(name);
		} catch (NumberFormatException ex){
			//ignore b/c it means this isn't an ID, it's a name
		}
		File f = getDataFile(file, true);
		try {
			Scanner s = new Scanner(f);
			String line;
			while(s.hasNext()){
				line = s.nextLine();
				try{
					int i = Integer.parseInt(line.substring(0, line.indexOf(' ')));
					String n = line.substring(line.indexOf(' ') + 1);
					if(n.startsWith(name)){
						return i;
					}
				} catch (Throwable t){
					//there was some funny syntax
					t.printStackTrace();
					System.err.println("[" + getDescription().getName() + "] File syntax error in id file. Skipping...");
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return -1;

	}

	public File getDataFile(String name, boolean download){
		File f = new File(getDataFolder() + File.separator + name);
		if(f.exists())
			return f;
		try {
			f.getParentFile().mkdirs();
			f.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		if(download){
			try {
				URL url = new URL(fileRepository + name);
				BufferedInputStream in = new BufferedInputStream(url.openStream());
				BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
				int i;
				while((i = in.read()) != -1){
					out.write(i);
				}
				in.close();
				out.close();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return f;
	}

	private void loadDamageMap(){
		ConfigurationSection section = getConfig().getConfigurationSection("damages");
		for(String s : section.getKeys(false)){
			if(s.equals("priority"))
				continue;
			ConfigurationSection sub = section.getConfigurationSection(s);
			World w = (s.equals("default")) ? null : getServer().getWorld(s);
			System.out.println("[" + getDescription().getName() + "]Loading damages for world " + ((w == null) ? "default" : w.getName()));
			for(String str : sub.getKeys(false)){
				try{
					if(str.equalsIgnoreCase("FLYING_ARROW")){
						dl.setArrowDamage(w, sub.getDouble(str));
					} else if (str.equalsIgnoreCase("default")){
						dl.setDefaultDamage(w, sub.getInt(str));
					} else {
						dl.put(w, getID(str, idFile), sub.getInt(str));
					}
				} catch (Exception ex){
					ex.printStackTrace();
					System.out.println("[" + getDescription().getName() + "]Configuration node damage." + s + "." + str + " is causing an issue.");
				}
			}
		}
		
		dl.setEventPriority(getConfig().getString("damages.priority", "NORMAL"));
		
		System.out.println("[" + getDescription().getName() + "]Successfully loaded damages!");
	}
	
	private boolean update(){
		String mode = getConfig().getString("update.mode").toLowerCase();
		String baseURL;
		if(mode.equals("experimental"))
			baseURL = experimentalUpdateRepository;
		else if (mode.equals("stable"))
			baseURL = stableUpdateRepository;
		else return false;

		System.out.println("[ChangeDamage] WARNING! ENTERING EXPERIMENTAL AUTOUPDATE MODE. THIS MAY CORRUPT YOUR VERSION OF CHANGEDAMAGE. TO DISABLE THIS, CHANGE update.mode IN CONFIG.YML TO off. PRESS CTRL + C NOW TO FORCE-STOP BUKKIT AND PREVENT THE UPDATE.");
		System.out.println("[ChangeDamage] WARNING! UPDATE COMMENCING IN 10 SECONDS!!!");
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		String tempLoc = getDataFolder() + File.separator + "temp.tmp";
		String downloadLoc = getFile().toString();
		String version = getDescription().getVersion();
		try{
			Updater.update(baseURL + updateJAR, baseURL + updateVersion, downloadLoc, tempLoc, version);
			System.out.println("Successfully updated! Reloading...");
		} catch (Exception ex){
			System.out.println("Update failed!");
			ex.printStackTrace();
		}
		Bukkit.reload();
		return true;
		
	}

	@Override
	public void onDisable(){
		armorHook.restore();
	}

}